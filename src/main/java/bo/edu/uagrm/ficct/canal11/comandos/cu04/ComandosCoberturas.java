package bo.edu.uagrm.ficct.canal11.comandos.cu04;

import bo.edu.uagrm.ficct.canal11.datos.Fila;
import bo.edu.uagrm.ficct.canal11.datos.TablaCrud;
import bo.edu.uagrm.ficct.canal11.comandos.Contexto;
import bo.edu.uagrm.ficct.canal11.comandos.ErrorNegocio;
import bo.edu.uagrm.ficct.canal11.comandos.HTMLResponseBuilder;
import bo.edu.uagrm.ficct.canal11.comandos.Resultado;
import bo.edu.uagrm.ficct.canal11.comandos.Sql;
import bo.edu.uagrm.ficct.canal11.comandos.catalogo.CommandSpec;
import bo.edu.uagrm.ficct.canal11.comandos.catalogo.Parametros;
import bo.edu.uagrm.ficct.canal11.comandos.catalogo.Tipo;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * CU-04 Gestión de Planificar Coberturas, Validar Conflictos de Operación y Liberar Recursos
 * (Tabla 82 del Anexo A).
 *
 * <ul>
 *   <li>R10: el correo trae fecha, hora de inicio y hora de fin; si la de fin es menor, la
 *       cobertura termina al día siguiente (una de 22:00 a 02:00).</li>
 *   <li>R3, R6, R7: ver {@link Choques}. UPDCOB vuelve a validar al equipo ya asignado.</li>
 *   <li>L6: la función debe corresponder al cargo; la de conductor la hace un conductor o un
 *       presentador, y el Jefe de Prensa puede ir como periodista.</li>
 *   <li>R4: RETCOB con cualquier resultado libera al personal y los recursos y cierra las
 *       entregas; la nueva fecha de una reprogramada se planifica con un INSCOB nuevo (M7).</li>
 *   <li>Solo se modifican las coberturas que todavía ocupan (planificada o en curso).</li>
 * </ul>
 */
public final class ComandosCoberturas {

    private static final String CU = "CU-04";
    private static final List<String> TIPOS = List.of("conferencia", "hecho_noticioso", "deportes", "acto_institucional", "entrevista");
    private static final List<String> ESTADOS = List.of("planificada", "en_curso", "cumplida", "cancelada", "reprogramada");
    static final Tipo TIPO = Tipo.opcion(TIPOS.toArray(String[]::new));
    static final Tipo PRIORIDAD = Tipo.opcion("alta", "media", "baja");
    static final Tipo FUNCION = Tipo.opcion("periodista", "camarografo", "conductor");

    private ComandosCoberturas() {
    }

    public static List<CommandSpec> comandos() {
        return List.of(
                CommandSpec.de(CU, "LISCOB", "Agenda de coberturas")
                        .param("fecha", Tipo.FILTRO)
                        .ejemplo("LISCOB[\"2026-10-12\"]").accion(ComandosCoberturas::liscob).build(),
                CommandSpec.de(CU, "INSCOB", "Planificar cobertura (si la hora de fin es menor, termina al día siguiente)")
                        .param("titulo", Tipo.texto(200)).opcional("tema", Tipo.texto(255)).param("tipo", TIPO)
                        .param("fecha", Tipo.FECHA).param("hora_inicio", Tipo.HORA).param("hora_fin", Tipo.HORA)
                        .param("lugar", Tipo.texto(200)).opcional("prioridad", PRIORIDAD).opcional("fuente", Tipo.texto(200))
                        .opcional("indicaciones", Tipo.TEXTO_LARGO_LIBRE)
                        .ejemplo("INSCOB[\"Clásico cruceño\",\"Conferencia previa\",\"deportes\",\"2026-10-12\",\"15:00\",\"16:30\","
                                + "\"Estadio Tahuichi\",\"media\",\"Prensa Club Blooming\",\"Conferencia de técnicos\"]")
                        .accion(ComandosCoberturas::inscob).build(),
                CommandSpec.de(CU, "UPDCOB", "Modificar o reprogramar cobertura (revalida conflictos)")
                        .param("id", Tipo.ID).opcional("titulo", Tipo.texto(200)).opcional("fecha", Tipo.FECHA)
                        .opcional("hora_inicio", Tipo.HORA).opcional("hora_fin", Tipo.HORA).opcional("lugar", Tipo.texto(200))
                        .opcional("prioridad", PRIORIDAD)
                        .ejemplo("UPDCOB[\"4\",\"Clásico cruceño\",\"2026-10-13\",\"15:00\",\"16:30\",\"Estadio Tahuichi\",\"media\"]")
                        .accion(ComandosCoberturas::updcob).build(),
                CommandSpec.de(CU, "DELCOB", "Cancelar cobertura (libera recursos)")
                        .param("id", Tipo.ID).opcional("motivo", Tipo.texto(1000))
                        .ejemplo("DELCOB[\"11\",\"Sesión suspendida\"]").accion(ComandosCoberturas::delcob).build(),
                CommandSpec.de(CU, "INSCPE", "Asignar personal (valida turno, ausencias y choques)")
                        .param("id_cobertura", Tipo.ID).param("id_personal", Tipo.ID).param("funcion", FUNCION)
                        .ejemplo("INSCPE[\"3\",\"1\",\"periodista\"]").accion(ComandosCoberturas::inscpe).build(),
                CommandSpec.de(CU, "DELCPE", "Quitar personal de la cobertura")
                        .param("id", Tipo.ID)
                        .ejemplo("DELCPE[\"5\"]").accion(ComandosCoberturas::delcpe).build(),
                CommandSpec.de(CU, "INSCRE", "Reservar recurso (valida estado y choques)")
                        .param("id_cobertura", Tipo.ID).param("codigo", Tipo.CODIGO_RECURSO)
                        .ejemplo("INSCRE[\"6\",\"MOV-02\"]").accion(ComandosCoberturas::inscre).build(),
                CommandSpec.de(CU, "DELCRE", "Liberar un recurso de la cobertura")
                        .param("id", Tipo.ID)
                        .ejemplo("DELCRE[\"10\"]").accion(ComandosCoberturas::delcre).build(),
                CommandSpec.de(CU, "HOJCOB", "Hoja de asignación de la cobertura")
                        .param("id_cobertura", Tipo.ID)
                        .ejemplo("HOJCOB[\"2\"]").accion(ComandosCoberturas::hojcob).build(),
                CommandSpec.de(CU, "RETCOB", "Registrar retorno y liberar personal y recursos")
                        .param("id_cobertura", Tipo.ID).param("resultado", Tipo.opcion("cumplida", "cancelada", "reprogramada"))
                        .opcional("parte", Tipo.TEXTO_LARGO_LIBRE).opcional("incidencias", Tipo.TEXTO_LARGO_LIBRE)
                        .ejemplo("RETCOB[\"2\",\"cumplida\",\"Imágenes del bloqueo y 3 testimonios\",\"Ninguna\"]")
                        .accion(ComandosCoberturas::retcob).build());
    }

    // =================================================================== coberturas

    private static final String AGENDA = """
            SELECT c.id AS "Id", c.titulo AS "Título", c.tipo AS "Tipo",
                   to_char(c.fecha_hora_inicio, 'YYYY-MM-DD') AS "Fecha", %s AS "Horario",
                   c.lugar AS "Lugar", c.prioridad AS "Prioridad", c.estado AS "Estado",
                   (SELECT string_agg(u.nombre || ' ' || u.apellido || ' (' || cp.funcion || ')', ', ' ORDER BY cp.funcion, u.apellido)
                      FROM cobertura_personal cp JOIN personal pe ON pe.id = cp.personal_id JOIN usuario u ON u.id = pe.usuario_id
                     WHERE cp.cobertura_id = c.id) AS "Equipo",
                   (SELECT string_agg(r.codigo, ', ' ORDER BY r.codigo)
                      FROM cobertura_recurso cr JOIN recurso r ON r.id = cr.recurso_id WHERE cr.cobertura_id = c.id) AS "Recursos"
            FROM cobertura c
            """.formatted(Sql.rango("c.fecha_hora_inicio", "c.fecha_hora_fin"));

    private static final String COBERTURA = """
            SELECT c.id AS "Id", c.titulo AS "Título", c.tema AS "Tema", c.tipo AS "Tipo",
                   to_char(c.fecha_hora_inicio, 'YYYY-MM-DD') AS "Fecha", %s AS "Horario",
                   c.lugar AS "Lugar", c.prioridad AS "Prioridad", c.fuente AS "Fuente", c.indicaciones AS "Indicaciones",
                   c.estado AS "Estado", c.cobertura_origen_id AS "Cobertura de origen",
                   c.parte_retorno AS "Parte", c.incidencias_retorno AS "Incidencias", c.fecha_hora_retorno AS "Retorno"
            FROM cobertura c
            WHERE c.id = ?
            """.formatted(Sql.rango("c.fecha_hora_inicio", "c.fecha_hora_fin"));

    private static Resultado liscob(Contexto ctx) throws SQLException, ErrorNegocio {
        String filtro = ctx.p().texto("fecha");
        if (filtro == null) {
            return Resultado.listado("Coberturas", ctx.consultar(AGENDA + " ORDER BY c.fecha_hora_inicio"), "No hay coberturas registradas.");
        }
        String f = Tipo.normalizarOpcion(filtro);
        LocalDate hoy = ctx.hoy();
        if (f.equals("semana")) {
            return delPeriodo(ctx, hoy, hoy.plusDays(6), "Coberturas de la semana (" + hoy + " al " + hoy.plusDays(6) + ")");
        }
        if (f.equals("hoy") || f.equals("manana")) {
            LocalDate dia = f.equals("hoy") ? hoy : hoy.plusDays(1);
            return delPeriodo(ctx, dia, dia, "Coberturas del " + dia);
        }
        if (ESTADOS.contains(f)) {
            return Resultado.listado("Coberturas " + f.replace('_', ' '),
                    ctx.consultar(AGENDA + " WHERE c.estado = ? ORDER BY c.fecha_hora_inicio", f), "No hay coberturas en ese estado.");
        }
        if (TIPOS.contains(f)) {
            return Resultado.listado("Coberturas de tipo " + f,
                    ctx.consultar(AGENDA + " WHERE c.tipo = ? ORDER BY c.fecha_hora_inicio", f), "No hay coberturas de ese tipo.");
        }
        LocalDate dia;
        try {
            dia = (LocalDate) Tipo.FECHA.convertir(filtro, "fecha");
        } catch (ErrorNegocio e) {
            throw ErrorNegocio.de("indique una fecha AAAA-MM-DD, \"hoy\", \"semana\", \"*\", un estado o un tipo de cobertura (llegó «" + filtro + "»)");
        }
        return delPeriodo(ctx, dia, dia, "Coberturas del " + dia);
    }

    /** Coberturas que ocupan algún momento entre {@code desde} y {@code hasta} (incluye las que cruzan la medianoche). */
    private static Resultado delPeriodo(Contexto ctx, LocalDate desde, LocalDate hasta, String titulo) throws SQLException {
        List<Fila> filas = ctx.consultar(AGENDA + " WHERE c.fecha_hora_inicio < ? AND c.fecha_hora_fin > ? ORDER BY c.fecha_hora_inicio",
                hasta.plusDays(1).atStartOfDay(), desde.atStartOfDay());
        return Resultado.listado(titulo, filas, "No hay coberturas en ese período.");
    }

    private static Resultado inscob(Contexto ctx) throws SQLException, ErrorNegocio {
        Parametros p = ctx.p();
        LocalDateTime[] rango = rango(p.fecha("fecha"), p.hora("hora_inicio"), p.hora("hora_fin"));
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("titulo", p.texto("titulo"));
        v.put("tema", p.texto("tema"));
        v.put("tipo", p.texto("tipo"));
        v.put("fecha_hora_inicio", rango[0]);
        v.put("fecha_hora_fin", rango[1]);
        v.put("lugar", p.texto("lugar"));
        v.put("prioridad", p.tiene("prioridad") ? p.texto("prioridad") : "media");
        v.put("fuente", p.texto("fuente"));
        v.put("indicaciones", p.texto("indicaciones"));
        v.put("creado_por", ctx.remitente().usuarioId());
        int id = TablaCrud.insertar(ctx.repositorio(), ctx.conexion(), "cobertura", v);
        Resultado r = Resultado.exito("Coberturas", "cobertura registrada").ficha(cobertura(ctx, id));
        avisosDeHorario(ctx, r, rango);
        return r.parrafo("Siguiente paso: asigne el equipo con INSCPE[\"" + id + "\",\"id_personal\",\"funcion\"] y reserve los recursos con INSCRE[\""
                + id + "\",\"codigo\"].");
    }

    private static Resultado updcob(Contexto ctx) throws SQLException, ErrorNegocio {
        Parametros p = ctx.p();
        p.requerirAlguno("titulo", "fecha", "hora_inicio", "hora_fin", "lugar", "prioridad");
        int id = p.entero("id");
        Fila actual = activa(ctx, id);
        LocalDateTime inicio = actual.fechaHora("fecha_hora_inicio");
        LocalDateTime fin = actual.fechaHora("fecha_hora_fin");
        boolean cambiaHorario = p.tiene("fecha") || p.tiene("hora_inicio") || p.tiene("hora_fin");
        LocalDateTime[] rango = {inicio, fin};
        if (cambiaHorario) {
            rango = rango(p.tiene("fecha") ? p.fecha("fecha") : inicio.toLocalDate(),
                    p.tiene("hora_inicio") ? p.hora("hora_inicio") : inicio.toLocalTime(),
                    p.tiene("hora_fin") ? p.hora("hora_fin") : fin.toLocalTime());
        }
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("titulo", p.texto("titulo"));
        v.put("fecha_hora_inicio", cambiaHorario ? rango[0] : null);
        v.put("fecha_hora_fin", cambiaHorario ? rango[1] : null);
        v.put("lugar", p.texto("lugar"));
        v.put("prioridad", p.texto("prioridad"));
        TablaCrud.actualizar(ctx.repositorio(), ctx.conexion(), "cobertura", id, v);

        List<String> advertencias = new ArrayList<>();
        if (cambiaHorario) {
            List<String> errores = Choques.delEquipo(ctx, id, advertencias);
            if (!errores.isEmpty()) {
                throw ErrorNegocio.conDetalle("con el horario nuevo hay conflictos del equipo asignado; la cobertura no se modificó",
                        HTMLResponseBuilder.lista(errores.stream().map(HTMLResponseBuilder::escapar).toList()));
            }
        }
        Resultado r = Resultado.exito("Coberturas", "cobertura modificada").ficha(cobertura(ctx, id));
        for (String a : advertencias) {
            r.aviso(a);
        }
        if (cambiaHorario) {
            avisosDeHorario(ctx, r, rango);
        }
        return r;
    }

    private static Resultado delcob(Contexto ctx) throws SQLException, ErrorNegocio {
        int id = ctx.p().entero("id");
        activa(ctx, id);
        ctx.ejecutar("""
                UPDATE cobertura SET estado = 'cancelada', parte_retorno = COALESCE(?, parte_retorno), actualizado_en = CURRENT_TIMESTAMP
                WHERE id = ?
                """, ctx.p().texto("motivo"), id);
        int devueltos = cerrarEntregas(ctx, id, "Cobertura cancelada");
        Resultado r = Resultado.exito("Coberturas", "cobertura cancelada").ficha(cobertura(ctx, id))
                .parrafo("El personal y los recursos quedan libres para otras coberturas.");
        if (devueltos > 0) {
            r.parrafo("Se registró la devolución de " + devueltos + " recurso(s) entregado(s) para esta cobertura.");
        }
        return r;
    }

    /** {inicio, fin} desde la fecha y las horas del correo (R10). */
    private static LocalDateTime[] rango(LocalDate fecha, LocalTime horaInicio, LocalTime horaFin) throws ErrorNegocio {
        if (horaFin.equals(horaInicio)) {
            throw ErrorNegocio.de("la cobertura debe terminar después de empezar");  // A1
        }
        LocalDateTime inicio = fecha.atTime(horaInicio);
        LocalDateTime fin = fecha.atTime(horaFin);
        if (horaFin.isBefore(horaInicio)) {
            fin = fin.plusDays(1);  // termina al día siguiente
        }
        return new LocalDateTime[]{inicio, fin};
    }

    private static void avisosDeHorario(Contexto ctx, Resultado r, LocalDateTime[] rango) {
        if (!rango[1].toLocalDate().equals(rango[0].toLocalDate())) {
            r.parrafo("La cobertura cruza la medianoche: termina el " + rango[1].toLocalDate() + ".");
        }
        if (rango[0].isBefore(LocalDateTime.now(ctx.reloj()))) {
            r.aviso("La cobertura empieza en el pasado.");
        }
    }

    /** La cobertura existe y todavía ocupa (planificada o en curso); si no, error. */
    private static Fila activa(Contexto ctx, int id) throws SQLException, ErrorNegocio {
        Fila c = ctx.requerir("SELECT id, titulo, estado, fecha_hora_inicio, fecha_hora_fin FROM cobertura WHERE id = ?", id);
        if (!List.of("planificada", "en_curso").contains(c.texto("estado"))) {
            throw ErrorNegocio.de("la cobertura " + id + " ya terminó (" + c.texto("estado") + "): no se modifica");
        }
        return c;
    }

    private static Fila cobertura(Contexto ctx, int id) throws SQLException, ErrorNegocio {
        return ctx.requerir(COBERTURA, id);
    }

    /** Cierra las entregas abiertas de la cobertura; devuelve cuántas. */
    private static int cerrarEntregas(Contexto ctx, int idCobertura, String observacion) throws SQLException {
        return ctx.ejecutar("""
                UPDATE prestamo SET fecha_devolucion = GREATEST(LOCALTIMESTAMP(0), fecha_entrega), observacion_devolucion = ?,
                                    actualizado_en = CURRENT_TIMESTAMP
                WHERE cobertura_id = ? AND fecha_devolucion IS NULL
                """, observacion, idCobertura);
    }

    // =================================================================== personal asignado

    private static Resultado inscpe(Contexto ctx) throws SQLException, ErrorNegocio {
        Parametros p = ctx.p();
        int idCobertura = p.entero("id_cobertura");
        int idPersonal = p.entero("id_personal");
        String funcion = p.texto("funcion");
        activa(ctx, idCobertura);
        Fila persona = persona(ctx, idPersonal);
        String nombre = persona.texto("nombre");
        funcionSegunCargo(nombre, persona.texto("cargo"), funcion);
        if (ctx.existe("SELECT 1 FROM cobertura_personal WHERE cobertura_id = ? AND personal_id = ?", idCobertura, idPersonal)) {
            throw ErrorNegocio.de(nombre + " ya está en el equipo de esta cobertura");
        }
        Optional<String> choque = Choques.dePersona(ctx, idCobertura, idPersonal, nombre);
        if (choque.isPresent()) {
            throw ErrorNegocio.de(choque.get());  // A2
        }
        Optional<String> fueraDeTurno = Choques.fueraDeTurno(ctx, idCobertura, idPersonal, nombre);
        int id = ctx.devolviendo("INSERT INTO cobertura_personal (cobertura_id, personal_id, funcion) VALUES (?, ?, ?) RETURNING id",
                idCobertura, idPersonal, funcion).entero("id");
        Resultado r = fueraDeTurno.isPresent()
                ? Resultado.exitoConAdvertencia("Coberturas", fueraDeTurno.get())  // A4
                : Resultado.exito("Coberturas", "personal asignado");
        return r.ficha(asignacion(ctx, id));
    }

    private static Resultado delcpe(Contexto ctx) throws SQLException, ErrorNegocio {
        int id = ctx.p().entero("id");
        Fila a = ctx.requerir("SELECT cobertura_id FROM cobertura_personal WHERE id = ?", id);
        activa(ctx, a.entero("cobertura_id"));
        Fila antes = asignacion(ctx, id);
        TablaCrud.eliminar(ctx.repositorio(), ctx.conexion(), "cobertura_personal", id);
        return Resultado.exito("Coberturas", "personal quitado de la cobertura").ficha(antes);
    }

    private static Fila asignacion(Contexto ctx, int id) throws SQLException, ErrorNegocio {
        return ctx.requerir("""
                SELECT cp.id AS "Id", c.id AS "Cobertura", c.titulo AS "Título", to_char(c.fecha_hora_inicio, 'YYYY-MM-DD') AS "Fecha",
                       %s AS "Horario", u.nombre || ' ' || u.apellido AS "Persona", cp.funcion AS "Función"
                FROM cobertura_personal cp
                JOIN cobertura c ON c.id = cp.cobertura_id
                JOIN personal pe ON pe.id = cp.personal_id JOIN usuario u ON u.id = pe.usuario_id
                WHERE cp.id = ?
                """.formatted(Sql.rango("c.fecha_hora_inicio", "c.fecha_hora_fin")), id);
    }

    private static Fila persona(Contexto ctx, int idPersonal) throws SQLException, ErrorNegocio {
        Fila f = ctx.uno("""
                SELECT u.nombre || ' ' || u.apellido AS nombre, pe.cargo, pe.estado
                FROM personal pe JOIN usuario u ON u.id = pe.usuario_id WHERE pe.id = ?
                """, idPersonal).orElseThrow(() -> ErrorNegocio.de("la persona indicada no existe"));
        if (!"activo".equals(f.texto("estado"))) {
            throw ErrorNegocio.de("la ficha de " + f.texto("nombre") + " está inactiva");
        }
        return f;
    }

    /** L6: la función corresponde al cargo (conductor: también un presentador; periodista: también el Jefe de Prensa). */
    private static void funcionSegunCargo(String nombre, String cargo, String funcion) throws ErrorNegocio {
        boolean puede = switch (funcion) {
            case "periodista" -> cargo.equals("periodista") || cargo.equals("jefe_prensa");
            case "camarografo" -> cargo.equals("camarografo");
            case "conductor" -> cargo.equals("conductor") || cargo.equals("presentador");
            default -> false;
        };
        if (!puede) {
            throw ErrorNegocio.de(nombre + " es " + cargo.replace('_', ' ') + ": no puede ir como " + funcion);
        }
    }

    // =================================================================== recursos reservados

    private static Resultado inscre(Contexto ctx) throws SQLException, ErrorNegocio {
        int idCobertura = ctx.p().entero("id_cobertura");
        activa(ctx, idCobertura);
        Fila r = recursoLibre(ctx, idCobertura);
        int id = ctx.devolviendo("INSERT INTO cobertura_recurso (cobertura_id, recurso_id) VALUES (?, ?) RETURNING id",
                idCobertura, r.entero("id")).entero("id");
        return Resultado.exito("Coberturas", "recurso reservado").ficha(reserva(ctx, id));
    }

    private static Resultado delcre(Contexto ctx) throws SQLException, ErrorNegocio {
        int id = ctx.p().entero("id");
        Fila actual = ctx.requerir("SELECT cobertura_id FROM cobertura_recurso WHERE id = ?", id);
        activa(ctx, actual.entero("cobertura_id"));
        Fila antes = reserva(ctx, id);
        TablaCrud.eliminar(ctx.repositorio(), ctx.conexion(), "cobertura_recurso", id);
        return Resultado.exito("Coberturas", "recurso liberado").ficha(antes);
    }

    /** El recurso del parámetro {@code codigo}, operativo y sin otra reserva que se cruce (R7, A3). */
    private static Fila recursoLibre(Contexto ctx, int idCobertura) throws SQLException, ErrorNegocio {
        String codigo = ctx.p().texto("codigo");
        Fila r = ctx.uno("SELECT id, codigo, estado FROM recurso WHERE codigo = ?", codigo)
                .orElseThrow(() -> ErrorNegocio.de("el recurso " + codigo + " no existe"));
        if (ctx.existe("SELECT 1 FROM cobertura_recurso WHERE cobertura_id = ? AND recurso_id = ?", idCobertura, r.entero("id"))) {
            throw ErrorNegocio.de(codigo + " ya está reservado para esta cobertura");
        }
        Optional<String> choque = Choques.deRecurso(ctx, idCobertura, r.entero("id"), codigo, r.texto("estado"));
        if (choque.isPresent()) {
            throw ErrorNegocio.de(choque.get());
        }
        return r;
    }

    private static Fila reserva(Contexto ctx, int id) throws SQLException, ErrorNegocio {
        return ctx.requerir("""
                SELECT cr.id AS "Id", c.id AS "Cobertura", c.titulo AS "Título", to_char(c.fecha_hora_inicio, 'YYYY-MM-DD') AS "Fecha",
                       %s AS "Horario", r.codigo AS "Código", r.marca || ' ' || r.modelo AS "Equipo"
                FROM cobertura_recurso cr
                JOIN cobertura c ON c.id = cr.cobertura_id
                JOIN recurso r ON r.id = cr.recurso_id
                WHERE cr.id = ?
                """.formatted(Sql.rango("c.fecha_hora_inicio", "c.fecha_hora_fin")), id);
    }

    // =================================================================== hoja de asignación (P7) y retorno

    private static Resultado hojcob(Contexto ctx) throws SQLException, ErrorNegocio {
        int id = ctx.p().entero("id_cobertura");
        Fila c = ctx.requerir("""
                SELECT c.titulo AS "Cobertura", c.tema AS "Tema", c.tipo AS "Tipo", to_char(c.fecha_hora_inicio, 'YYYY-MM-DD') AS "Fecha",
                       %s AS "Horario", c.lugar AS "Lugar", c.prioridad AS "Prioridad", c.fuente AS "Fuente / contacto",
                       c.indicaciones AS "Indicaciones", c.estado AS "Estado"
                FROM cobertura c WHERE c.id = ?
                """.formatted(Sql.rango("c.fecha_hora_inicio", "c.fecha_hora_fin")), id);
        Resultado r = Resultado.informacion("Hoja de asignación", "Hoja de asignación de la cobertura " + id + ": «" + c.texto("Cobertura") + "».")
                .ficha(c);
        List<Fila> equipo = ctx.consultar("""
                SELECT cp.funcion AS "Función", u.nombre || ' ' || u.apellido AS "Persona", u.telefono AS "Teléfono"
                FROM cobertura_personal cp JOIN personal pe ON pe.id = cp.personal_id JOIN usuario u ON u.id = pe.usuario_id
                WHERE cp.cobertura_id = ? ORDER BY cp.funcion, u.apellido
                """, id);
        r.subtitulo("Equipo humano");
        if (equipo.isEmpty()) {
            r.parrafo("Todavía no hay personal asignado.");
        } else {
            r.tabla(equipo);
        }
        List<Fila> recursos = ctx.consultar("""
                SELECT r.codigo AS "Código", t.nombre AS "Tipo", r.marca || ' ' || r.modelo AS "Equipo", r.placa AS "Placa"
                FROM cobertura_recurso cr JOIN recurso r ON r.id = cr.recurso_id JOIN tipo_recurso t ON t.id = r.tipo_recurso_id
                WHERE cr.cobertura_id = ? ORDER BY r.codigo
                """, id);
        r.subtitulo("Equipos y unidad móvil");
        if (recursos.isEmpty()) {
            r.parrafo("Todavía no hay recursos reservados.");
        } else {
            r.tabla(recursos);
        }
        return r;
    }

    private static Resultado retcob(Contexto ctx) throws SQLException, ErrorNegocio {
        Parametros p = ctx.p();
        int id = p.entero("id_cobertura");
        Fila c = ctx.requerir("SELECT estado, fecha_hora_inicio FROM cobertura WHERE id = ?", id);
        if (!List.of("planificada", "en_curso").contains(c.texto("estado"))) {
            throw ErrorNegocio.de("la cobertura " + id + " ya tiene registrado su retorno (" + c.texto("estado") + ")");
        }
        if (!ctx.remitente().esJefePrensa() && (!ctx.remitente().tieneFicha()
                || !ctx.existe("SELECT 1 FROM cobertura_personal WHERE cobertura_id = ? AND personal_id = ?", id, ctx.remitente().personalId()))) {
            throw ErrorNegocio.de("solo el Jefe de Prensa o el personal asignado a la cobertura registra su retorno");
        }
        String resultado = p.texto("resultado");
        if (resultado.equals("cumplida") && c.fechaHora("fecha_hora_inicio").isAfter(LocalDateTime.now(ctx.reloj()))) {
            throw ErrorNegocio.de("la cobertura todavía no empezó: no puede estar cumplida");
        }
        ctx.ejecutar("""
                UPDATE cobertura SET estado = ?, parte_retorno = ?, incidencias_retorno = ?,
                                     fecha_hora_retorno = GREATEST(LOCALTIMESTAMP(0), fecha_hora_inicio), actualizado_en = CURRENT_TIMESTAMP
                WHERE id = ?
                """, resultado, p.texto("parte"), p.texto("incidencias"), id);
        int devueltos = cerrarEntregas(ctx, id, "Retorno de cobertura");
        Resultado r = Resultado.exito("Coberturas", "retorno registrado").ficha(cobertura(ctx, id))
                .parrafo("El personal y los recursos quedan libres para otras coberturas (R4).");
        if (devueltos > 0) {
            r.parrafo("Se registró la devolución de " + devueltos + " recurso(s) entregado(s) para esta cobertura.");
        }
        if (resultado.equals("reprogramada")) {
            r.parrafo("Para la nueva fecha, planifique otra cobertura con INSCOB.");
        }
        return r;
    }
}
