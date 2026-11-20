package bo.edu.uagrm.ficct.canal11.comandos.cu02;

import bo.edu.uagrm.ficct.canal11.datos.Fila;
import bo.edu.uagrm.ficct.canal11.datos.TablaCrud;
import bo.edu.uagrm.ficct.canal11.comandos.Contexto;
import bo.edu.uagrm.ficct.canal11.comandos.ErrorNegocio;
import bo.edu.uagrm.ficct.canal11.comandos.Formato;
import bo.edu.uagrm.ficct.canal11.comandos.Resultado;
import bo.edu.uagrm.ficct.canal11.comandos.Sql;
import bo.edu.uagrm.ficct.canal11.comandos.catalogo.CommandSpec;
import bo.edu.uagrm.ficct.canal11.comandos.catalogo.Momento;
import bo.edu.uagrm.ficct.canal11.comandos.catalogo.Parametros;
import bo.edu.uagrm.ficct.canal11.comandos.catalogo.Tipo;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CU-02 Gestión de Personal de Prensa, Turnos y Disponibilidad (Tabla 80 del Anexo A).
 *
 * <ul>
 *   <li>Una ausencia de días completos se guarda de 00:00 del primer día a 00:00 del día siguiente
 *       al último (R11): {@code INSAUS[…,"2026-10-14","2026-10-15",…]} son dos días.</li>
 *   <li>DISPER cruza el turno, las ausencias y las coberturas que ocupan (R5), con la consulta ya
 *       probada en {@code db/consultas_negocio.sql}.</li>
 *   <li>La unidad académica solo se registra para los practicantes.</li>
 *   <li>Al registrar una ausencia o desactivar una ficha se avisa de las coberturas que la persona
 *       tiene asignadas en ese tiempo, para reasignarlas.</li>
 * </ul>
 */
public final class ComandosPersonal {

    private static final String CU = "CU-02";
    private static final List<String> CARGOS = List.of("periodista", "camarografo", "editor", "presentador", "conductor", "jefe_prensa");
    private static final List<String> CONDICIONES = List.of("planta", "contrato", "practicante");
    private static final String DIAS_POR_DEFECTO = "1,2,3,4,5";
    static final Tipo CARGO = Tipo.opcion(CARGOS.toArray(String[]::new));
    static final Tipo CONDICION = Tipo.opcion(CONDICIONES.toArray(String[]::new));
    static final Tipo TIPO_AUSENCIA = Tipo.opcion("permiso", "vacacion", "baja_medica", "comision");
    private ComandosPersonal() {
    }

    public static List<CommandSpec> comandos() {
        return List.of(
                CommandSpec.de(CU, "LISPER", "Listar personal")
                        .param("cargo", Tipo.FILTRO)
                        .ejemplo("LISPER[\"camarografo\"]").accion(ComandosPersonal::lisper).build(),
                CommandSpec.de(CU, "INSPER", "Registrar ficha del personal")
                        .param("id_usuario", Tipo.ID).param("cargo", CARGO).param("condicion_laboral", CONDICION)
                        .param("id_turno", Tipo.ID).opcional("unidad_academica", Tipo.texto(150)).opcional("fecha_ingreso", Tipo.FECHA)
                        .ejemplo("INSPER[\"3\",\"periodista\",\"planta\",\"1\",\"\",\"2019-02-01\"]").accion(ComandosPersonal::insper).build(),
                CommandSpec.de(CU, "UPDPER", "Modificar ficha del personal")
                        .param("id", Tipo.ID).opcional("cargo", CARGO).opcional("condicion_laboral", CONDICION)
                        .opcional("id_turno", Tipo.ID).opcional("unidad_academica", Tipo.texto(150))
                        .omitible("estado", Tipo.opcion("activo", "inactivo"))
                        .ejemplo("UPDPER[\"1\",\"periodista\",\"planta\",\"2\",\"\"]").accion(ComandosPersonal::updper).build(),
                CommandSpec.de(CU, "DELPER", "Desactivar personal")
                        .param("id", Tipo.ID)
                        .ejemplo("DELPER[\"4\"]").accion(ComandosPersonal::delper).build(),
                CommandSpec.de(CU, "LISTUR", "Listar turnos")
                        .param("filtro", Tipo.FILTRO)
                        .ejemplo("LISTUR[\"*\"]").accion(ComandosPersonal::listur).build(),
                CommandSpec.de(CU, "INSTUR", "Registrar turno")
                        .param("nombre", Tipo.texto(50)).param("hora_inicio", Tipo.HORA).param("hora_fin", Tipo.HORA)
                        .opcional("dias", Tipo.DIAS).opcional("descripcion", Tipo.texto(150))
                        .ejemplo("INSTUR[\"Parcial 15-19\",\"15:00\",\"19:00\",\"1,2,3,4,5\",\"Practicantes\"]").accion(ComandosPersonal::instur).build(),
                CommandSpec.de(CU, "LISAUS", "Listar ausencias")
                        .param("id_personal", Tipo.ID_O_TODOS)
                        .ejemplo("LISAUS[\"6\"]").accion(ComandosPersonal::lisaus).build(),
                CommandSpec.de(CU, "INSAUS", "Registrar permiso, vacación o baja (días completos o con hora)")
                        .param("id_personal", Tipo.ID).param("tipo", TIPO_AUSENCIA).param("inicio", Tipo.MOMENTO)
                        .param("fin", Tipo.MOMENTO).opcional("motivo", Tipo.texto(255))
                        .ejemplo("INSAUS[\"6\",\"permiso\",\"2026-10-14 09:00\",\"2026-10-14 11:00\",\"Trámite personal\"]")
                        .accion(ComandosPersonal::insaus).build(),
                CommandSpec.de(CU, "DISPER", "Personal disponible en una fecha y hora")
                        .param("fecha", Tipo.FECHA).param("hora", Tipo.HORA).omitible("cargo", CARGO)
                        .ejemplo("DISPER[\"2026-10-12\",\"16:00\",\"camarografo\"]").accion(ComandosPersonal::disper).build());
    }

    // =================================================================== fichas

    private static final String PERSONAL = """
            SELECT p.id AS "Id", u.nombre || ' ' || u.apellido AS "Nombre", p.cargo AS "Cargo",
                   p.condicion_laboral AS "Condición", t.nombre AS "Turno",
                   to_char(t.hora_inicio, 'HH24:MI') || '–' || to_char(t.hora_fin, 'HH24:MI') AS "Horario",
                   p.unidad_academica AS "Unidad académica", p.fecha_ingreso AS "Ingreso", p.estado AS "Estado",
                   u.id AS "Id usuario"
            FROM personal p
            JOIN usuario u ON u.id = p.usuario_id
            JOIN turno t ON t.id = p.turno_id
            """;

    private static Resultado lisper(Contexto ctx) throws SQLException {
        String filtro = ctx.p().texto("cargo");
        List<Fila> filas;
        if (filtro == null) {
            filas = ctx.consultar(PERSONAL + " ORDER BY p.cargo, u.apellido");
        } else {
            String f = Tipo.normalizarOpcion(filtro);
            if (CARGOS.contains(f)) {
                filas = ctx.consultar(PERSONAL + " WHERE p.cargo = ? ORDER BY u.apellido", f);
            } else if (CONDICIONES.contains(f)) {
                filas = ctx.consultar(PERSONAL + " WHERE p.condicion_laboral = ? ORDER BY p.cargo, u.apellido", f);
            } else if (f.equals("activo") || f.equals("inactivo")) {
                filas = ctx.consultar(PERSONAL + " WHERE p.estado = ? ORDER BY p.cargo, u.apellido", f);
            } else {
                filas = ctx.consultar(PERSONAL + " WHERE " + Sql.sinTildes("u.nombre || ' ' || u.apellido") + " LIKE ? ORDER BY u.apellido",
                        Sql.contiene(filtro));
            }
        }
        return Resultado.listado("Personal", filas,
                "No hay personal con ese filtro. Use \"*\", un cargo (" + String.join(", ", CARGOS) + "), una condición o un nombre.");
    }

    private static Resultado insper(Contexto ctx) throws SQLException, ErrorNegocio {
        Parametros p = ctx.p();
        int idUsuario = p.entero("id_usuario");
        Fila usuario = ctx.uno("SELECT estado FROM usuario WHERE id = ?", idUsuario)
                .orElseThrow(() -> ErrorNegocio.de("el usuario indicado no existe; regístrelo antes con INSUSU"));
        if (!"activo".equals(usuario.texto("estado"))) {
            throw ErrorNegocio.de("el usuario está inactivo");
        }
        if (ctx.existe("SELECT 1 FROM personal WHERE usuario_id = ?", idUsuario)) {
            throw ErrorNegocio.de("el usuario ya está registrado como personal");  // A2
        }
        turnoExistente(ctx, p.entero("id_turno"));
        unidadSoloPracticantes(p.texto("condicion_laboral"), p.texto("unidad_academica"));
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("usuario_id", idUsuario);
        v.put("cargo", p.texto("cargo"));
        v.put("condicion_laboral", p.texto("condicion_laboral"));
        v.put("unidad_academica", p.texto("unidad_academica"));
        v.put("turno_id", p.entero("id_turno"));
        v.put("fecha_ingreso", p.fecha("fecha_ingreso"));
        int id = TablaCrud.insertar(ctx.repositorio(), ctx.conexion(), "personal", v);
        return Resultado.exito("Personal", "ficha registrada").ficha(ficha(ctx, id));
    }

    private static Resultado updper(Contexto ctx) throws SQLException, ErrorNegocio {
        Parametros p = ctx.p();
        p.requerirAlguno("cargo", "condicion_laboral", "id_turno", "unidad_academica", "estado");
        int id = p.entero("id");
        Fila actual = ctx.requerir("SELECT condicion_laboral FROM personal WHERE id = ?", id);
        if (p.tiene("id_turno")) {
            turnoExistente(ctx, p.entero("id_turno"));
        }
        String condicion = p.tiene("condicion_laboral") ? p.texto("condicion_laboral") : actual.texto("condicion_laboral");
        if (p.tiene("unidad_academica")) {
            unidadSoloPracticantes(condicion, p.texto("unidad_academica"));
        }
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("cargo", p.texto("cargo"));
        v.put("condicion_laboral", p.texto("condicion_laboral"));
        v.put("turno_id", p.entero("id_turno"));
        v.put("unidad_academica", p.texto("unidad_academica"));
        v.put("estado", p.texto("estado"));
        TablaCrud.actualizar(ctx.repositorio(), ctx.conexion(), "personal", id, v);
        if (!"practicante".equals(condicion)) {
            // si deja de ser practicante, la unidad académica ya no corresponde
            ctx.ejecutar("UPDATE personal SET unidad_academica = NULL WHERE id = ? AND unidad_academica IS NOT NULL", id);
        }
        Resultado r = Resultado.exito("Personal", "ficha modificada").ficha(ficha(ctx, id));
        if (p.tiene("id_turno")) {
            avisarCoberturasFueraDeTurno(ctx, r, id);
        }
        return r;
    }

    private static Resultado delper(Contexto ctx) throws SQLException, ErrorNegocio {
        int id = ctx.p().entero("id");
        Fila actual = ctx.requerir("SELECT estado FROM personal WHERE id = ?", id);
        if ("inactivo".equals(actual.texto("estado"))) {
            throw ErrorNegocio.de("la ficha ya está inactiva");
        }
        TablaCrud.cambiarEstado(ctx.repositorio(), ctx.conexion(), "personal", id, "inactivo");
        Resultado r = Resultado.exito("Personal", "ficha desactivada").ficha(ficha(ctx, id))
                .parrafo("La ficha no se elimina, para conservar su historial. Para reactivarla: UPDPER[\"" + id + "\",\"\",\"\",\"\",\"\",\"activo\"].");
        List<Fila> pendientes = ctx.consultar("""
                SELECT o.cobertura_id AS "Cobertura", o.titulo AS "Título", o.fecha_hora_inicio AS "Inicio", o.funcion AS "Función"
                FROM v_ocupacion_personal o
                WHERE o.personal_id = ? AND o.fecha_hora_fin > LOCALTIMESTAMP
                ORDER BY o.fecha_hora_inicio
                """, id);
        if (!pendientes.isEmpty()) {
            r.aviso("Tiene coberturas pendientes: reasígnelas con DELCPE e INSCPE.").tabla(pendientes);
        }
        return r;
    }

    private static Fila ficha(Contexto ctx, int id) throws SQLException, ErrorNegocio {
        return ctx.requerir(PERSONAL + " WHERE p.id = ?", id);
    }

    private static void turnoExistente(Contexto ctx, int idTurno) throws SQLException, ErrorNegocio {
        if (!ctx.existe("SELECT 1 FROM turno WHERE id = ?", idTurno)) {
            throw ErrorNegocio.de("el turno no existe");  // A3
        }
    }

    private static void unidadSoloPracticantes(String condicion, String unidad) throws ErrorNegocio {
        if (unidad != null && !"practicante".equals(condicion)) {
            throw ErrorNegocio.de("la unidad académica solo se registra para los practicantes");
        }
    }

    /** Si cambió el turno, avisa de las coberturas pendientes que ahora quedan fuera de él. */
    private static void avisarCoberturasFueraDeTurno(Contexto ctx, Resultado r, int idPersonal) throws SQLException {
        List<Fila> fuera = ctx.consultar("""
                SELECT o.cobertura_id AS "Cobertura", o.titulo AS "Título", o.fecha_hora_inicio AS "Inicio"
                FROM v_ocupacion_personal o
                JOIN personal p ON p.id = o.personal_id
                JOIN turno t ON t.id = p.turno_id
                WHERE o.personal_id = ? AND o.fecha_hora_fin > LOCALTIMESTAMP
                  AND NOT EXISTS (SELECT 1
                                  FROM (VALUES (o.fecha_hora_inicio::date), (o.fecha_hora_inicio::date - 1)) AS d(dia)
                                  WHERE EXTRACT(ISODOW FROM d.dia)::int = ANY (string_to_array(t.dias, ',')::int[])
                                    AND o.fecha_hora_inicio >= d.dia + t.hora_inicio
                                    AND o.fecha_hora_fin <= d.dia + t.hora_fin
                                           + CASE WHEN t.hora_fin <= t.hora_inicio THEN INTERVAL '1 day' ELSE INTERVAL '0' END)
                ORDER BY o.fecha_hora_inicio
                """, idPersonal);
        if (!fuera.isEmpty()) {
            r.aviso("Con el turno nuevo, estas coberturas asignadas quedan fuera de su horario:").tabla(fuera);
        }
    }

    // =================================================================== turnos

    private static final String TURNOS = """
            SELECT t.id AS "Id", t.nombre AS "Turno",
                   to_char(t.hora_inicio, 'HH24:MI') || '–' || to_char(t.hora_fin, 'HH24:MI')
                       || CASE WHEN t.hora_fin < t.hora_inicio THEN ' (cruza la medianoche)' ELSE '' END AS "Horario",
                   (SELECT string_agg((ARRAY['lun','mar','mié','jue','vie','sáb','dom'])[d::int], ', ' ORDER BY d::int)
                      FROM unnest(string_to_array(t.dias, ',')) AS d) AS "Días",
                   t.descripcion AS "Descripción",
                   (SELECT count(*) FROM personal p WHERE p.turno_id = t.id AND p.estado = 'activo') AS "Personal activo"
            FROM turno t
            """;

    private static Resultado listur(Contexto ctx) throws SQLException {
        String filtro = ctx.p().texto("filtro");
        List<Fila> filas = filtro == null
                ? ctx.consultar(TURNOS + " ORDER BY t.hora_inicio, t.id")
                : ctx.consultar(TURNOS + " WHERE " + Sql.sinTildes("t.nombre") + " LIKE ? ORDER BY t.hora_inicio", Sql.contiene(filtro));
        return Resultado.listado("Turnos", filas, "No hay turnos con ese nombre.");
    }

    private static Resultado instur(Contexto ctx) throws SQLException, ErrorNegocio {
        Parametros p = ctx.p();
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("nombre", p.texto("nombre"));
        v.put("hora_inicio", p.hora("hora_inicio"));
        v.put("hora_fin", p.hora("hora_fin"));
        v.put("dias", p.tiene("dias") ? p.texto("dias") : DIAS_POR_DEFECTO);
        v.put("descripcion", p.texto("descripcion"));
        int id = TablaCrud.insertar(ctx.repositorio(), ctx.conexion(), "turno", v);
        return Resultado.exito("Turnos", "turno registrado").ficha(ctx.requerir(TURNOS + " WHERE t.id = ?", id));
    }

    // =================================================================== ausencias

    /** Período legible: «2026-10-14 al 2026-10-15» para días completos, «2026-10-14 09:00 a 11:00» con hora. */
    private static final String PERIODO = """
            CASE WHEN a.fecha_hora_inicio::time = '00:00' AND a.fecha_hora_fin::time = '00:00' THEN
                     to_char(a.fecha_hora_inicio, 'YYYY-MM-DD')
                     || CASE WHEN a.fecha_hora_fin - a.fecha_hora_inicio > INTERVAL '1 day'
                             THEN ' al ' || to_char(a.fecha_hora_fin - INTERVAL '1 day', 'YYYY-MM-DD')
                             ELSE ' (día completo)' END
                 ELSE to_char(a.fecha_hora_inicio, 'YYYY-MM-DD HH24:MI') || ' a '
                     || CASE WHEN a.fecha_hora_inicio::date = a.fecha_hora_fin::date THEN to_char(a.fecha_hora_fin, 'HH24:MI')
                             ELSE to_char(a.fecha_hora_fin, 'YYYY-MM-DD HH24:MI') END
            END""";

    private static final String AUSENCIAS = """
            SELECT a.id AS "Id", a.personal_id AS "Id personal", u.nombre || ' ' || u.apellido AS "Persona", a.tipo AS "Tipo",
                   %s AS "Período", a.motivo AS "Motivo"
            FROM ausencia a
            JOIN personal p ON p.id = a.personal_id
            JOIN usuario u ON u.id = p.usuario_id
            """.formatted(PERIODO);

    private static Resultado lisaus(Contexto ctx) throws SQLException, ErrorNegocio {
        Integer idPersonal = ctx.p().entero("id_personal");
        if (idPersonal == null) {
            return Resultado.listado("Ausencias", ctx.consultar(AUSENCIAS + " ORDER BY a.fecha_hora_inicio DESC"), "No hay ausencias registradas.");
        }
        Fila persona = personaExistente(ctx, idPersonal);
        return Resultado.listado("Ausencias de " + persona.texto("nombre"),
                ctx.consultar(AUSENCIAS + " WHERE a.personal_id = ? ORDER BY a.fecha_hora_inicio DESC", idPersonal),
                persona.texto("nombre") + " no tiene ausencias registradas.");
    }

    private static Resultado insaus(Contexto ctx) throws SQLException, ErrorNegocio {
        Parametros p = ctx.p();
        int idPersonal = p.entero("id_personal");
        Fila persona = personaExistente(ctx, idPersonal);
        if (!"activo".equals(persona.texto("estado"))) {
            throw ErrorNegocio.de("la ficha de " + persona.texto("nombre") + " está inactiva");
        }
        LocalDateTime inicio = p.momento("inicio").comoInicio();
        LocalDateTime fin = p.momento("fin").comoFin();
        rangoValido(inicio, fin);
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("personal_id", idPersonal);
        v.put("tipo", p.texto("tipo"));
        v.put("fecha_hora_inicio", inicio);
        v.put("fecha_hora_fin", fin);
        v.put("motivo", p.texto("motivo"));
        int id = TablaCrud.insertar(ctx.repositorio(), ctx.conexion(), "ausencia", v);
        Resultado r = Resultado.exito("Ausencias", "ausencia registrada").ficha(ctx.requerir(AUSENCIAS + " WHERE a.id = ?", id));
        avisarCruces(ctx, r, id);
        return r;
    }

    private static void rangoValido(LocalDateTime inicio, LocalDateTime fin) throws ErrorNegocio {
        if (!fin.isAfter(inicio)) {
            throw ErrorNegocio.de("el fin debe ser posterior al inicio");  // A1
        }
    }

    private static Fila personaExistente(Contexto ctx, int idPersonal) throws SQLException, ErrorNegocio {
        return ctx.uno("""
                SELECT u.nombre || ' ' || u.apellido AS nombre, p.estado
                FROM personal p JOIN usuario u ON u.id = p.usuario_id WHERE p.id = ?
                """, idPersonal).orElseThrow(() -> ErrorNegocio.de("la persona indicada no existe"));
    }

    /** Avisa si la ausencia se cruza con otra ausencia o con coberturas asignadas (para reasignarlas). */
    private static void avisarCruces(Contexto ctx, Resultado r, int idAusencia) throws SQLException {
        List<Fila> coberturas = ctx.consultar("""
                SELECT o.cobertura_id AS "Cobertura", o.titulo AS "Título", o.fecha_hora_inicio AS "Inicio", o.fecha_hora_fin AS "Fin"
                FROM ausencia a
                JOIN v_ocupacion_personal o ON o.personal_id = a.personal_id
                WHERE a.id = ? AND o.fecha_hora_inicio < a.fecha_hora_fin AND a.fecha_hora_inicio < o.fecha_hora_fin
                ORDER BY o.fecha_hora_inicio
                """, idAusencia);
        if (!coberturas.isEmpty()) {
            r.aviso("La persona tiene coberturas asignadas en ese período: reasígnelas con DELCPE e INSCPE.").tabla(coberturas);
        }
        List<Fila> otras = ctx.consultar(AUSENCIAS + """
                 JOIN ausencia esta ON esta.id = ?
                WHERE a.personal_id = esta.personal_id AND a.id <> esta.id
                  AND a.fecha_hora_inicio < esta.fecha_hora_fin AND esta.fecha_hora_inicio < a.fecha_hora_fin
                """, idAusencia);
        if (!otras.isEmpty()) {
            r.aviso("Se superpone con otra ausencia registrada.").tabla(otras);
        }
    }

    // =================================================================== disponibilidad

    /** R5: dentro de su turno, sin ausencia en ese momento y sin una cobertura que lo ocupe. */
    private static final String DISPONIBLES = """
            SELECT p.id AS "Id", u.nombre || ' ' || u.apellido AS "Persona", p.cargo AS "Cargo", t.nombre AS "Turno",
                   u.telefono AS "Teléfono"
            FROM personal p
            JOIN usuario u ON u.id = p.usuario_id
            JOIN turno t   ON t.id = p.turno_id
            WHERE p.estado = 'activo' AND u.estado = 'activo'
              AND EXISTS (SELECT 1
                          FROM (VALUES (CAST(? AS timestamp)::date), (CAST(? AS timestamp)::date - 1)) AS d(dia)
                          WHERE EXTRACT(ISODOW FROM d.dia)::int = ANY (string_to_array(t.dias, ',')::int[])
                            AND CAST(? AS timestamp) >= d.dia + t.hora_inicio
                            AND CAST(? AS timestamp) <  d.dia + t.hora_fin
                                   + CASE WHEN t.hora_fin <= t.hora_inicio THEN INTERVAL '1 day' ELSE INTERVAL '0' END)
              AND NOT EXISTS (SELECT 1 FROM ausencia a
                              WHERE a.personal_id = p.id
                                AND CAST(? AS timestamp) >= a.fecha_hora_inicio AND CAST(? AS timestamp) < a.fecha_hora_fin)
              AND NOT EXISTS (SELECT 1 FROM v_ocupacion_personal o
                              WHERE o.personal_id = p.id
                                AND CAST(? AS timestamp) >= o.fecha_hora_inicio AND CAST(? AS timestamp) < o.fecha_hora_fin)
            """;

    private static Resultado disper(Contexto ctx) throws SQLException {
        Parametros p = ctx.p();
        LocalDateTime momento = LocalDateTime.of(p.fecha("fecha"), p.hora("hora"));
        Object[] m = {momento, momento, momento, momento, momento, momento, momento, momento};
        String cargo = p.texto("cargo");
        List<Fila> filas;
        if (cargo == null) {
            filas = ctx.consultar(DISPONIBLES + " ORDER BY p.cargo, u.apellido", m);
        } else {
            Object[] conCargo = Arrays.copyOf(m, m.length + 1);
            conCargo[m.length] = cargo;
            filas = ctx.consultar(DISPONIBLES + " AND p.cargo = ? ORDER BY u.apellido", conCargo);
        }
        String titulo = "Disponibles el " + p.fecha("fecha") + " a las " + Formato.hora(p.hora("hora"))
                + (cargo == null ? "" : " (" + cargo + ")");
        return Resultado.listado(titulo, filas, "No hay personal disponible en ese horario");  // A4
    }
}
