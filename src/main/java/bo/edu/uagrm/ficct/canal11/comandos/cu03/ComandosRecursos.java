package bo.edu.uagrm.ficct.canal11.comandos.cu03;

import bo.edu.uagrm.ficct.canal11.datos.Fila;
import bo.edu.uagrm.ficct.canal11.datos.TablaCrud;
import bo.edu.uagrm.ficct.canal11.comandos.Contexto;
import bo.edu.uagrm.ficct.canal11.comandos.ErrorNegocio;
import bo.edu.uagrm.ficct.canal11.comandos.Resultado;
import bo.edu.uagrm.ficct.canal11.comandos.Sql;
import bo.edu.uagrm.ficct.canal11.comandos.catalogo.CommandSpec;
import bo.edu.uagrm.ficct.canal11.comandos.catalogo.Parametros;
import bo.edu.uagrm.ficct.canal11.comandos.catalogo.Tipo;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CU-03 Gestión de Inventario, Asignación y Mantenimiento de Equipos Técnicos y Unidades Móviles
 * (Tabla 81 del Anexo A).
 *
 * <ul>
 *   <li>R8: un recurso no tiene dos entregas abiertas; solo se entrega si está operativo.</li>
 *   <li>R9: INCREC lo pasa a mantenimiento, ALTREC lo devuelve a operativo y DELREC lo da de baja;
 *       cada paso queda en {@code mantenimiento}, que nunca se borra.</li>
 *   <li>R12: solo las unidades móviles llevan placa, y la placa no se repite.</li>
 *   <li>INCREC y DELREC avisan qué coberturas pendientes usan el recurso (paso 6 del CU-03).</li>
 * </ul>
 */
public final class ComandosRecursos {

    private static final String CU = "CU-03";
    private static final List<String> ESTADOS = List.of("operativo", "mantenimiento", "baja");

    private ComandosRecursos() {
    }

    public static List<CommandSpec> comandos() {
        return List.of(
                CommandSpec.de(CU, "LISREC", "Listar recursos")
                        .param("filtro", Tipo.FILTRO)
                        .ejemplo("LISREC[\"operativo\"]").accion(ComandosRecursos::lisrec).build(),
                CommandSpec.de(CU, "INSREC", "Registrar recurso")
                        .param("codigo", Tipo.CODIGO_RECURSO).param("id_tipo", Tipo.ID).param("marca", Tipo.texto(50))
                        .param("modelo", Tipo.texto(80)).opcional("placa", Tipo.texto(15)).opcional("caracteristicas", Tipo.texto(255))
                        .ejemplo("INSREC[\"CAM-06\",\"1\",\"Sony\",\"FX6\",\"\",\"Full frame 4K\"]").accion(ComandosRecursos::insrec).build(),
                CommandSpec.de(CU, "UPDREC", "Modificar recurso")
                        .param("id", Tipo.ID).opcional("marca", Tipo.texto(50)).opcional("modelo", Tipo.texto(80))
                        .opcional("placa", Tipo.texto(15)).opcional("caracteristicas", Tipo.texto(255)).opcional("observacion", Tipo.texto(255))
                        .ejemplo("UPDREC[\"5\",\"Canon\",\"XF605\",\"\",\"4K liviana\",\"\"]").accion(ComandosRecursos::updrec).build(),
                CommandSpec.de(CU, "DELREC", "Dar de baja un recurso")
                        .param("id", Tipo.ID).param("motivo", Tipo.texto(255))
                        .ejemplo("DELREC[\"4\",\"Visor irreparable\"]").accion(ComandosRecursos::delrec).build(),
                CommandSpec.de(CU, "INCREC", "Reportar daño o incidencia (pasa a mantenimiento)")
                        .param("codigo", Tipo.CODIGO_RECURSO).param("descripcion", Tipo.texto(255))
                        .ejemplo("INCREC[\"MOV-01\",\"Falla de frenos\"]").accion(ComandosRecursos::increc).build(),
                CommandSpec.de(CU, "ALTREC", "Dar de alta tras la reparación")
                        .param("codigo", Tipo.CODIGO_RECURSO).opcional("descripcion", Tipo.texto(255))
                        .ejemplo("ALTREC[\"CAM-04\",\"Visor reemplazado\"]").accion(ComandosRecursos::altrec).build(),
                CommandSpec.de(CU, "LISPRE", "Listar préstamos")
                        .param("filtro", Tipo.FILTRO)
                        .ejemplo("LISPRE[\"abiertos\"]").accion(ComandosRecursos::lispre).build(),
                CommandSpec.de(CU, "INSPRE", "Entregar recurso (préstamo)")
                        .param("codigo", Tipo.CODIGO_RECURSO).param("id_responsable", Tipo.ID).opcional("id_cobertura", Tipo.ID)
                        .opcional("observacion", Tipo.texto(255))
                        .ejemplo("INSPRE[\"CAM-01\",\"6\",\"3\",\"Con batería extra\"]").accion(ComandosRecursos::inspre).build(),
                CommandSpec.de(CU, "DEVPRE", "Registrar devolución")
                        .param("id", Tipo.ID).opcional("observacion", Tipo.texto(255))
                        .ejemplo("DEVPRE[\"8\",\"Sin novedad\"]").accion(ComandosRecursos::devpre).build());
    }

    // =================================================================== tipos de recurso

    // =================================================================== inventario

    private static final String RECURSOS = """
            SELECT r.id AS "Id", r.codigo AS "Código", t.nombre AS "Tipo", r.marca AS "Marca", r.modelo AS "Modelo",
                   r.placa AS "Placa", r.caracteristicas AS "Características", r.estado AS "Estado", r.observacion AS "Observación",
                   (SELECT u.nombre || ' ' || u.apellido FROM prestamo pr
                      JOIN personal pe ON pe.id = pr.responsable_id JOIN usuario u ON u.id = pe.usuario_id
                     WHERE pr.recurso_id = r.id AND pr.fecha_devolucion IS NULL) AS "Entregado a"
            FROM recurso r
            JOIN tipo_recurso t ON t.id = r.tipo_recurso_id
            """;

    private static Resultado lisrec(Contexto ctx) throws SQLException {
        String filtro = ctx.p().texto("filtro");
        List<Fila> filas;
        if (filtro == null) {
            filas = ctx.consultar(RECURSOS + " ORDER BY r.codigo");
        } else if (ESTADOS.contains(Tipo.normalizarOpcion(filtro))) {
            filas = ctx.consultar(RECURSOS + " WHERE r.estado = ? ORDER BY r.codigo", Tipo.normalizarOpcion(filtro));
        } else {
            filas = ctx.consultar(RECURSOS + " WHERE r.codigo = upper(?) OR " + Sql.sinTildes("t.nombre") + " LIKE ? ORDER BY r.codigo",
                    filtro, Sql.contiene(filtro));
        }
        return Resultado.listado("Recursos", filas,
                "No hay recursos con ese filtro. Use \"*\", un estado (operativo, mantenimiento, baja), un tipo o un código.");
    }

    private static Resultado insrec(Contexto ctx) throws SQLException, ErrorNegocio {
        Parametros p = ctx.p();
        String codigo = p.texto("codigo");
        if (ctx.existe("SELECT 1 FROM recurso WHERE codigo = ?", codigo)) {
            throw ErrorNegocio.de("el código " + codigo + " ya existe");  // A1
        }
        Fila tipo = ctx.uno("SELECT es_unidad_movil FROM tipo_recurso WHERE id = ?", p.entero("id_tipo"))
                .orElseThrow(() -> ErrorNegocio.de("el tipo de recurso no existe"));
        if (p.tiene("placa") && !tipo.logico("es_unidad_movil")) {
            throw ErrorNegocio.de("solo las unidades móviles llevan placa");  // A4, R12
        }
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("codigo", codigo);
        v.put("tipo_recurso_id", p.entero("id_tipo"));
        v.put("marca", p.texto("marca"));
        v.put("modelo", p.texto("modelo"));
        v.put("placa", p.tiene("placa") ? p.texto("placa").toUpperCase() : null);
        v.put("caracteristicas", p.texto("caracteristicas"));
        int id = TablaCrud.insertar(ctx.repositorio(), ctx.conexion(), "recurso", v);
        return Resultado.exito("Recursos", "recurso registrado").ficha(recurso(ctx, id));
    }

    private static Resultado updrec(Contexto ctx) throws SQLException, ErrorNegocio {
        Parametros p = ctx.p();
        p.requerirAlguno("marca", "modelo", "placa", "caracteristicas", "observacion");
        int id = p.entero("id");
        Fila actual = ctx.requerir("""
                SELECT t.es_unidad_movil FROM recurso r JOIN tipo_recurso t ON t.id = r.tipo_recurso_id WHERE r.id = ?
                """, id);
        if (p.tiene("placa") && !actual.logico("es_unidad_movil")) {
            throw ErrorNegocio.de("solo las unidades móviles llevan placa");  // R12
        }
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("marca", p.texto("marca"));
        v.put("modelo", p.texto("modelo"));
        v.put("placa", p.tiene("placa") ? p.texto("placa").toUpperCase() : null);
        v.put("caracteristicas", p.texto("caracteristicas"));
        v.put("observacion", p.texto("observacion"));
        TablaCrud.actualizar(ctx.repositorio(), ctx.conexion(), "recurso", id, v);
        return Resultado.exito("Recursos", "recurso modificado").ficha(recurso(ctx, id));
    }

    private static Resultado delrec(Contexto ctx) throws SQLException, ErrorNegocio {
        int id = ctx.p().entero("id");
        Fila r = ctx.requerir("SELECT codigo, estado FROM recurso WHERE id = ?", id);
        String codigo = r.texto("codigo");
        if ("baja".equals(r.texto("estado"))) {
            throw ErrorNegocio.de(codigo + " ya está dado de baja");
        }
        if (prestamoAbierto(ctx, id)) {
            throw ErrorNegocio.de(codigo + " no fue devuelto: registre la devolución con DEVPRE antes de darlo de baja");
        }
        String motivo = ctx.p().texto("motivo");
        ctx.ejecutar("UPDATE recurso SET estado = 'baja', observacion = ?, actualizado_en = CURRENT_TIMESTAMP WHERE id = ?", motivo, id);
        registrarMantenimiento(ctx, id, "baja", motivo);
        Resultado res = Resultado.exito("Recursos", "recurso dado de baja").ficha(recurso(ctx, id))
                .parrafo("El recurso y su historial se conservan; ya no se puede reservar ni entregar.");
        avisarCoberturas(ctx, res, id, codigo);
        return res;
    }

    private static Fila recurso(Contexto ctx, int id) throws SQLException, ErrorNegocio {
        return ctx.requerir(RECURSOS + " WHERE r.id = ?", id);
    }

    // =================================================================== mantenimiento (R9)

    private static Resultado increc(Contexto ctx) throws SQLException, ErrorNegocio {
        Fila r = porCodigo(ctx);
        String codigo = r.texto("codigo");
        switch (r.texto("estado")) {
            case "baja" -> throw ErrorNegocio.de(codigo + " está dado de baja");
            case "mantenimiento" -> throw ErrorNegocio.de(codigo + " ya está en mantenimiento");
            default -> {
            }
        }
        String descripcion = ctx.p().texto("descripcion");
        ctx.ejecutar("UPDATE recurso SET estado = 'mantenimiento', observacion = ?, actualizado_en = CURRENT_TIMESTAMP WHERE id = ?",
                descripcion, r.entero("id"));
        registrarMantenimiento(ctx, r.entero("id"), "incidencia", descripcion);
        Resultado res = Resultado.exito("Mantenimiento", "incidencia registrada").ficha(recurso(ctx, r.entero("id")))
                .parrafo(codigo + " queda bloqueado hasta su alta (ALTREC).");
        avisarCoberturas(ctx, res, r.entero("id"), codigo);
        return res;
    }

    private static Resultado altrec(Contexto ctx) throws SQLException, ErrorNegocio {
        Fila r = porCodigo(ctx);
        String codigo = r.texto("codigo");
        if (!"mantenimiento".equals(r.texto("estado"))) {
            throw ErrorNegocio.de(codigo + " no está en mantenimiento (está " + r.texto("estado") + ")");
        }
        String descripcion = ctx.p().tiene("descripcion") ? ctx.p().texto("descripcion") : "Alta tras la reparación";
        ctx.ejecutar("UPDATE recurso SET estado = 'operativo', observacion = NULL, actualizado_en = CURRENT_TIMESTAMP WHERE id = ?",
                r.entero("id"));
        registrarMantenimiento(ctx, r.entero("id"), "alta", descripcion);
        return Resultado.exito("Mantenimiento", "alta registrada").ficha(recurso(ctx, r.entero("id")))
                .parrafo(codigo + " vuelve a estar operativo.");
    }

    private static Fila porCodigo(Contexto ctx) throws SQLException, ErrorNegocio {
        String codigo = ctx.p().texto("codigo");
        return ctx.uno("SELECT id, codigo, estado FROM recurso WHERE codigo = ?", codigo)
                .orElseThrow(() -> ErrorNegocio.de("el recurso " + codigo + " no existe"));
    }

    private static void registrarMantenimiento(Contexto ctx, int idRecurso, String tipo, String descripcion) throws SQLException {
        ctx.ejecutar("INSERT INTO mantenimiento (recurso_id, tipo, descripcion, usuario_id, fecha_hora) VALUES (?, ?, ?, ?, LOCALTIMESTAMP(0))",
                idRecurso, tipo, descripcion, ctx.remitente().usuarioId());
    }

    /** Coberturas pendientes que tienen reservado el recurso (para liberarlo con DELCRE y reservar otro). */
    private static void avisarCoberturas(Contexto ctx, Resultado res, int idRecurso, String codigo) throws SQLException {
        List<Fila> afectadas = ctx.consultar("""
                SELECT o.cobertura_id AS "Cobertura", o.titulo AS "Título", o.fecha_hora_inicio AS "Inicio", o.fecha_hora_fin AS "Fin"
                FROM v_ocupacion_recurso o
                WHERE o.recurso_id = ? AND o.fecha_hora_fin >= LOCALTIMESTAMP
                ORDER BY o.fecha_hora_inicio
                """, idRecurso);
        if (!afectadas.isEmpty()) {
            res.aviso(codigo + " está reservado en coberturas pendientes: libérelo con DELCRE y reserve otro con INSCRE.").tabla(afectadas);
        }
    }

    // =================================================================== préstamos (R8)

    private static final String PRESTAMOS = """
            SELECT pr.id AS "Id", r.codigo AS "Código", u.nombre || ' ' || u.apellido AS "Responsable",
                   pr.cobertura_id AS "Cobertura", pr.fecha_entrega AS "Entrega", pr.fecha_devolucion AS "Devolución",
                   pr.observacion_entrega AS "Obs. entrega", pr.observacion_devolucion AS "Obs. devolución",
                   CASE WHEN pr.fecha_devolucion IS NULL THEN 'abierto' ELSE 'devuelto' END AS "Estado"
            FROM prestamo pr
            JOIN recurso r ON r.id = pr.recurso_id
            JOIN personal pe ON pe.id = pr.responsable_id
            JOIN usuario u ON u.id = pe.usuario_id
            """;

    private static Resultado lispre(Contexto ctx) throws SQLException {
        String filtro = ctx.p().texto("filtro");
        List<Fila> filas;
        if (filtro == null) {
            filas = ctx.consultar(PRESTAMOS + " ORDER BY pr.fecha_entrega DESC, pr.id DESC");
        } else if (Sql.normal(filtro).startsWith("abierto")) {
            filas = ctx.consultar(PRESTAMOS + " WHERE pr.fecha_devolucion IS NULL ORDER BY pr.fecha_entrega, pr.id");
        } else {
            filas = ctx.consultar(PRESTAMOS + " WHERE r.codigo = upper(?) ORDER BY pr.fecha_entrega DESC, pr.id DESC", filtro);
        }
        return Resultado.listado("Préstamos", filas, "No hay préstamos con ese filtro. Use \"*\", \"abiertos\" o el código de un recurso.");
    }

    private static Resultado inspre(Contexto ctx) throws SQLException, ErrorNegocio {
        Parametros p = ctx.p();
        Fila r = porCodigo(ctx);
        String codigo = r.texto("codigo");
        if (!"operativo".equals(r.texto("estado"))) {
            throw ErrorNegocio.de(codigo + " está " + ("baja".equals(r.texto("estado")) ? "dado de baja" : "en mantenimiento"));  // A2
        }
        if (prestamoAbierto(ctx, r.entero("id"))) {
            throw ErrorNegocio.de(codigo + " no fue devuelto");  // A3, R8
        }
        responsableActivo(ctx, p.entero("id_responsable"));
        Integer idCobertura = p.entero("id_cobertura");
        boolean reservado = true;
        if (idCobertura != null) {
            Fila c = ctx.uno("SELECT estado FROM cobertura WHERE id = ?", idCobertura)
                    .orElseThrow(() -> ErrorNegocio.de("la cobertura indicada no existe"));
            if (!List.of("planificada", "en_curso").contains(c.texto("estado"))) {
                throw ErrorNegocio.de("la cobertura " + idCobertura + " ya terminó (" + c.texto("estado") + ")");
            }
            reservado = ctx.existe("SELECT 1 FROM cobertura_recurso WHERE cobertura_id = ? AND recurso_id = ?", idCobertura, r.entero("id"));
        }
        int id = ctx.devolviendo("""
                INSERT INTO prestamo (recurso_id, responsable_id, cobertura_id, fecha_entrega, observacion_entrega)
                VALUES (?, ?, ?, LOCALTIMESTAMP(0), ?) RETURNING id
                """, r.entero("id"), p.entero("id_responsable"), idCobertura, p.texto("observacion")).entero("id");
        Resultado res = Resultado.exito("Préstamos", "entrega registrada").ficha(prestamo(ctx, id));
        if (!reservado) {
            res.aviso(codigo + " no está reservado para la cobertura " + idCobertura + ": resérvelo con INSCRE para que figure en la hoja de asignación.");
        }
        return res;
    }

    private static Resultado devpre(Contexto ctx) throws SQLException, ErrorNegocio {
        int id = ctx.p().entero("id");
        Fila actual = ctx.requerir("SELECT fecha_devolucion FROM prestamo WHERE id = ?", id);
        if (actual.get("fecha_devolucion") != null) {
            throw ErrorNegocio.de("el préstamo ya fue devuelto");
        }
        ctx.ejecutar("""
                UPDATE prestamo SET fecha_devolucion = GREATEST(LOCALTIMESTAMP(0), fecha_entrega), observacion_devolucion = ?,
                                    actualizado_en = CURRENT_TIMESTAMP
                WHERE id = ?
                """, ctx.p().texto("observacion"), id);
        return Resultado.exito("Préstamos", "devolución registrada").ficha(prestamo(ctx, id));
    }

    private static Fila prestamo(Contexto ctx, int id) throws SQLException, ErrorNegocio {
        return ctx.requerir(PRESTAMOS + " WHERE pr.id = ?", id);
    }

    private static boolean prestamoAbierto(Contexto ctx, int idRecurso) throws SQLException {
        return ctx.existe("SELECT 1 FROM prestamo WHERE recurso_id = ? AND fecha_devolucion IS NULL", idRecurso);
    }

    private static void responsableActivo(Contexto ctx, int idPersonal) throws SQLException, ErrorNegocio {
        Fila f = ctx.uno("SELECT estado FROM personal WHERE id = ?", idPersonal)
                .orElseThrow(() -> ErrorNegocio.de("el responsable indicado no existe"));
        if (!"activo".equals(f.texto("estado"))) {
            throw ErrorNegocio.de("el responsable indicado está inactivo");
        }
    }
}
