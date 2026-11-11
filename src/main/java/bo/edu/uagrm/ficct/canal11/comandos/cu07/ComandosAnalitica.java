package bo.edu.uagrm.ficct.canal11.comandos.cu07;

import bo.edu.uagrm.ficct.canal11.datos.Fila;
import bo.edu.uagrm.ficct.canal11.comandos.Contexto;
import bo.edu.uagrm.ficct.canal11.comandos.ErrorNegocio;
import bo.edu.uagrm.ficct.canal11.comandos.Formato;
import bo.edu.uagrm.ficct.canal11.comandos.Periodo;
import bo.edu.uagrm.ficct.canal11.comandos.Resultado;
import bo.edu.uagrm.ficct.canal11.comandos.Totales;
import bo.edu.uagrm.ficct.canal11.comandos.catalogo.CommandSpec;
import bo.edu.uagrm.ficct.canal11.comandos.catalogo.Tipo;

import java.sql.SQLException;
import java.util.List;

/**
 * CU-07 Gestión de Analítica Periodística (Tabla 85 del Anexo A): indicadores por período, con las
 * consultas ya probadas en {@code db/consultas_negocio.sql}.
 *
 * <p>Las tres últimas letras de un ANA nombran el indicador, no una tabla (PER: periodistas, EMI:
 * emisiones, COB: coberturas, NOT: notas). Los porcentajes se muestran con «%», los tiempos de
 * aprobación en {@code h:mm} y las duraciones en {@code mm:ss} (R25, R26). Sin datos, «No hay
 * datos para el período indicado» (A2).</p>
 */
public final class ComandosAnalitica {

    private static final String CU = "CU-07";

    private ComandosAnalitica() {
    }

    public static List<CommandSpec> comandos() {
        return List.of(
                periodo("ANAPER", "Producción por periodista: notas, % aprobadas a la primera y tiempo medio de aprobación")
                        .accion(ComandosAnalitica::anaper).build(),
                periodo("ANACOB", "Cumplimiento de coberturas por tipo").accion(ComandosAnalitica::anacob).build());
    }

    private static CommandSpec.Builder periodo(String codigo, String descripcion) {
        return CommandSpec.de(CU, codigo, descripcion)
                .param("fecha_desde", Tipo.FECHA).param("fecha_hasta", Tipo.FECHA)
                .ejemplo(codigo + "[\"2026-10-01\",\"2026-10-31\"]");
    }

    /** Un porcentaje entero con «%», o {@code NULL} si no hay base para calcularlo. */
    private static String porcentaje(String parte, String total) {
        return "(ROUND(100.0 * " + parte + " / NULLIF(" + total + ", 0), 0)::int)::text || '%'";
    }

    /** Minutos como {@code h:mm}. */
    private static String horasMinutos(String minutos) {
        return "CASE WHEN " + minutos + " IS NULL THEN NULL ELSE (ROUND(" + minutos + ")::int / 60)::text || ':' || "
                + "lpad((ROUND(" + minutos + ")::int % 60)::text, 2, '0') END";
    }

    // =================================================================== indicadores

    private static Resultado anaper(Contexto ctx) throws SQLException, ErrorNegocio {
        Periodo per = Periodo.de(ctx.p());
        List<Fila> filas = ctx.consultar("""
                WITH rev AS (
                  SELECT nota_id,
                         MIN(fecha_hora) FILTER (WHERE accion = 'envio')       AS primer_envio,
                         MAX(fecha_hora) FILTER (WHERE accion = 'aprobacion')  AS aprobada_en,
                         COUNT(*)        FILTER (WHERE accion = 'observacion') AS observaciones
                  FROM revision_nota GROUP BY nota_id),
                base AS (
                  SELECT u.nombre || ' ' || u.apellido AS autor, n.estado, r.aprobada_en, r.observaciones,
                         EXTRACT(EPOCH FROM (r.aprobada_en - r.primer_envio)) / 60 AS minutos
                  FROM nota n
                  JOIN personal p ON p.id = n.autor_id JOIN usuario u ON u.id = p.usuario_id
                  LEFT JOIN rev r ON r.nota_id = n.id
                  WHERE n.fecha BETWEEN ? AND ?)
                SELECT autor AS "Autor",
                       COUNT(*) AS "Notas",
                       COUNT(*) FILTER (WHERE estado IN ('aprobada', 'emitida')) AS "Aprobadas o emitidas",
                       %s AS "Aprobadas a la primera",
                       %s AS "Tiempo medio de aprobación (h:mm)"
                FROM base
                GROUP BY autor
                ORDER BY "Notas" DESC, autor
                """.formatted(porcentaje("COUNT(*) FILTER (WHERE aprobada_en IS NOT NULL AND observaciones = 0)",
                        "COUNT(*) FILTER (WHERE aprobada_en IS NOT NULL)"),
                horasMinutos("AVG(minutos)")), per.desde(), per.hasta());
        return indicador("Producción por periodista " + per.texto(), filas, "Notas", "Aprobadas o emitidas")
                .parrafo("«A la primera»: aprobadas sin ninguna observación. El tiempo de aprobación va del primer envío a revisión a la aprobación.");
    }

    private static Resultado anacob(Contexto ctx) throws SQLException, ErrorNegocio {
        Periodo per = Periodo.de(ctx.p());
        List<Fila> filas = ctx.consultar("""
                SELECT tipo AS "Tipo",
                       COUNT(*) AS "Coberturas",
                       COUNT(*) FILTER (WHERE estado = 'cumplida')     AS "Cumplidas",
                       COUNT(*) FILTER (WHERE estado = 'cancelada')    AS "Canceladas",
                       COUNT(*) FILTER (WHERE estado = 'reprogramada') AS "Reprogramadas",
                       COUNT(*) FILTER (WHERE estado IN ('planificada', 'en_curso')) AS "Sin retorno",
                       %s AS "Cumplimiento"
                FROM cobertura
                WHERE fecha_hora_inicio::date BETWEEN ? AND ?
                GROUP BY tipo
                ORDER BY "Coberturas" DESC, tipo
                """.formatted(porcentaje("COUNT(*) FILTER (WHERE estado = 'cumplida')",
                "COUNT(*) FILTER (WHERE estado IN ('cumplida', 'cancelada', 'reprogramada'))")), per.desde(), per.hasta());
        return indicador("Cumplimiento de coberturas por tipo " + per.texto(), filas,
                "Coberturas", "Cumplidas", "Canceladas", "Reprogramadas", "Sin retorno")
                .parrafo("Cumplimiento: cumplidas sobre las que ya tienen retorno (las pendientes no cuentan).");
    }

    /** Tabla del indicador con su fila de totales, o «No hay datos para el período indicado». */
    private static Resultado indicador(String titulo, List<Fila> filas, String... columnasSumadas) {
        if (filas.isEmpty()) {
            return Resultado.informacion(titulo, Periodo.SIN_DATOS);
        }
        return Resultado.informacion(titulo, filas.size() + (filas.size() == 1 ? " fila." : " filas."))
                .tabla(Totales.conTotal(filas, columnasSumadas));
    }
}
