package bo.edu.uagrm.ficct.canal11.comandos.cu08;

import bo.edu.uagrm.ficct.canal11.datos.Fila;
import bo.edu.uagrm.ficct.canal11.comandos.Contexto;
import bo.edu.uagrm.ficct.canal11.comandos.ErrorNegocio;
import bo.edu.uagrm.ficct.canal11.comandos.Periodo;
import bo.edu.uagrm.ficct.canal11.comandos.Resultado;
import bo.edu.uagrm.ficct.canal11.comandos.Totales;
import bo.edu.uagrm.ficct.canal11.comandos.catalogo.CommandSpec;
import bo.edu.uagrm.ficct.canal11.comandos.catalogo.Tipo;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * CU-08 Reportes y Estadísticas (Tabla 86 del Anexo A), con las consultas ya probadas en
 * {@code db/consultas_negocio.sql}.
 *
 * <ul>
 *   <li>Los REP reciben un período (ver {@link Periodo}) y responden una tabla con su fila de
 *       totales (plantilla P9); sin datos, «No hay datos para el período indicado».</li>
 *   <li>REPREC muestra solo los recursos que se usaron y cuenta los que no (D30).</li>
 *   <li>LISBIT (solo el Director) filtra por fecha, por remitente o por comando; sin filtro, los
 *       últimos {@value #ULTIMOS_BITACORA} registros. El asunto ya está enmascarado (D6).</li>
 * </ul>
 */
public final class ComandosReportes {

    private static final String CU = "CU-08";
    static final int ULTIMOS_BITACORA = 100;
    private static final int LARGO_RESPUESTA = 120;

    private ComandosReportes() {
    }

    public static List<CommandSpec> comandos() {
        return List.of(
                periodo("REPCOB", "Coberturas por periodista").accion(ComandosReportes::repcob).build(),
                periodo("REPREC", "Uso de equipos y unidades móviles (horas)").accion(ComandosReportes::reprec).build(),
                CommandSpec.de(CU, "LISBIT", "Bitácora de comandos recibidos (RNF-24)")
                        .param("filtro", Tipo.FILTRO)
                        .ejemplo("LISBIT[\"2026-10-12\"]").accion(ComandosReportes::lisbit).build());
    }

    private static CommandSpec.Builder periodo(String codigo, String descripcion) {
        return CommandSpec.de(CU, codigo, descripcion)
                .param("fecha_desde", Tipo.FECHA).param("fecha_hasta", Tipo.FECHA)
                .ejemplo(codigo + "[\"2026-10-01\",\"2026-10-31\"]");
    }

    // =================================================================== reportes (P9)

    private static Resultado repcob(Contexto ctx) throws SQLException, ErrorNegocio {
        Periodo per = Periodo.de(ctx.p());
        List<Fila> filas = ctx.consultar("""
                SELECT u.nombre || ' ' || u.apellido AS "Periodista",
                       COUNT(*) FILTER (WHERE c.estado = 'cumplida')     AS "Cumplidas",
                       COUNT(*) FILTER (WHERE c.estado = 'cancelada')    AS "Canceladas",
                       COUNT(*) FILTER (WHERE c.estado = 'reprogramada') AS "Reprogramadas",
                       COUNT(*) FILTER (WHERE c.estado IN ('planificada', 'en_curso')) AS "Pendientes",
                       COUNT(*) AS "Total"
                FROM cobertura c
                JOIN cobertura_personal cp ON cp.cobertura_id = c.id AND cp.funcion = 'periodista'
                JOIN personal p ON p.id = cp.personal_id
                JOIN usuario u ON u.id = p.usuario_id
                WHERE c.fecha_hora_inicio::date BETWEEN ? AND ?
                GROUP BY u.nombre, u.apellido
                ORDER BY "Total" DESC, u.apellido
                """, per.desde(), per.hasta());
        return reporte("Coberturas por periodista " + per.texto(), filas,
                "Cumplidas", "Canceladas", "Reprogramadas", "Pendientes", "Total");
    }

    private static Resultado reprec(Contexto ctx) throws SQLException, ErrorNegocio {
        Periodo per = Periodo.de(ctx.p());
        List<Fila> filas = ctx.consultar("""
                SELECT r.codigo AS "Código", tr.nombre AS "Tipo", r.marca || ' ' || r.modelo AS "Equipo",
                       COUNT(c.id) AS "Coberturas",
                       ROUND(SUM(EXTRACT(EPOCH FROM (c.fecha_hora_fin - c.fecha_hora_inicio)) / 3600)::numeric, 1) AS "Horas"
                FROM recurso r
                JOIN tipo_recurso tr ON tr.id = r.tipo_recurso_id
                JOIN cobertura_recurso cr ON cr.recurso_id = r.id
                JOIN cobertura c ON c.id = cr.cobertura_id AND c.estado <> 'cancelada'
                WHERE c.fecha_hora_inicio::date BETWEEN ? AND ?
                GROUP BY r.codigo, tr.nombre, r.marca, r.modelo
                ORDER BY "Horas" DESC, r.codigo
                """, per.desde(), per.hasta());
        Resultado r = reporte("Uso de equipos y unidades móviles " + per.texto(), filas, "Coberturas", "Horas");
        if (!filas.isEmpty()) {
            long sinUso = ctx.uno("""
                    SELECT count(*) AS n FROM recurso r
                    WHERE r.estado <> 'baja'
                      AND NOT EXISTS (SELECT 1 FROM cobertura_recurso cr JOIN cobertura c ON c.id = cr.cobertura_id
                                      WHERE cr.recurso_id = r.id AND c.estado <> 'cancelada'
                                        AND c.fecha_hora_inicio::date BETWEEN ? AND ?)
                    """, per.desde(), per.hasta()).orElseThrow().largo("n");
            r.parrafo(sinUso == 0 ? "Todos los recursos se usaron en el período."
                    : sinUso + " recurso(s) no se usaron en el período (no se cuentan los dados de baja).");
        }
        return r;
    }

    /** Plantilla P9: tabla con su fila de totales, o «No hay datos para el período indicado». */
    private static Resultado reporte(String titulo, List<Fila> filas, String... columnasSumadas) {
        if (filas.isEmpty()) {
            return Resultado.informacion(titulo, Periodo.SIN_DATOS);
        }
        return Resultado.informacion(titulo, filas.size() + (filas.size() == 1 ? " fila." : " filas."))
                .tabla(Totales.conTotal(filas, columnasSumadas));
    }

    // =================================================================== bitácora (RNF-24)

    private static final String BITACORA = """
            SELECT b.fecha_hora AS "Fecha", b.remitente AS "Remitente", b.comando AS "Comando", b.asunto AS "Asunto",
                   b.resultado AS "Resultado",
                   CASE WHEN length(b.respuesta) > %d THEN left(b.respuesta, %d) || '…' ELSE b.respuesta END AS "Respuesta"
            FROM bitacora_comando b
            """.formatted(LARGO_RESPUESTA, LARGO_RESPUESTA);

    private static Resultado lisbit(Contexto ctx) throws SQLException {
        String filtro = ctx.p().texto("filtro");
        List<Fila> filas;
        String titulo;
        if (filtro == null) {
            titulo = "Bitácora: últimos registros";
            filas = ctx.consultar(BITACORA + " ORDER BY b.fecha_hora DESC, b.id DESC LIMIT ?", ULTIMOS_BITACORA);
        } else if (comoFecha(filtro) != null) {
            LocalDate dia = comoFecha(filtro);
            titulo = "Bitácora del " + dia;
            filas = ctx.consultar(BITACORA + " WHERE b.fecha_hora::date = ? ORDER BY b.fecha_hora DESC, b.id DESC", dia);
        } else if (ctx.catalogo().existe(filtro.toUpperCase(Locale.ROOT)) || filtro.equalsIgnoreCase("AYUDA")) {
            String comando = filtro.toUpperCase(Locale.ROOT);
            titulo = "Bitácora de " + comando;
            filas = ctx.consultar(BITACORA + " WHERE b.comando = ? ORDER BY b.fecha_hora DESC, b.id DESC LIMIT ?", comando, ULTIMOS_BITACORA);
        } else {
            titulo = "Bitácora de «" + filtro + "»";
            filas = ctx.consultar(BITACORA + " WHERE lower(b.remitente) LIKE ? ESCAPE '\\' ORDER BY b.fecha_hora DESC, b.id DESC LIMIT ?",
                    "%" + filtro.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%",
                    ULTIMOS_BITACORA);
        }
        Resultado r = Resultado.listado(titulo, filas, "No hay registros en la bitácora con ese filtro. Use \"*\", una fecha AAAA-MM-DD, "
                + "un remitente o un código de comando.");
        if (filas.size() == ULTIMOS_BITACORA) {
            r.parrafo("Se muestran los últimos " + ULTIMOS_BITACORA + ": filtre por fecha, remitente o comando para ver otros.");
        }
        return r;
    }

    /** El filtro como fecha, o {@code null} si no es una fecha. */
    private static LocalDate comoFecha(String texto) {
        try {
            return (LocalDate) Tipo.FECHA.convertir(texto, "filtro");
        } catch (ErrorNegocio e) {
            return null;
        }
    }
}
