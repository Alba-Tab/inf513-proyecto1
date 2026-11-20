package bo.edu.uagrm.ficct.canal11.comandos.cu06;

import bo.edu.uagrm.ficct.canal11.datos.Fila;
import bo.edu.uagrm.ficct.canal11.comandos.Contexto;
import bo.edu.uagrm.ficct.canal11.comandos.ErrorNegocio;
import bo.edu.uagrm.ficct.canal11.comandos.Formato;
import bo.edu.uagrm.ficct.canal11.comandos.HTMLResponseBuilder;
import bo.edu.uagrm.ficct.canal11.comandos.Resultado;
import bo.edu.uagrm.ficct.canal11.comandos.catalogo.CommandSpec;
import bo.edu.uagrm.ficct.canal11.comandos.catalogo.Parametros;
import bo.edu.uagrm.ficct.canal11.comandos.catalogo.Tipo;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * CU-06, segunda parte: la escaleta del noticiero (Tabla 84 del Anexo A). Los ítems y su control de
 * tiempos están en {@link ComandosItems}.
 *
 * <ul>
 *   <li>R20: una escaleta por horario y fecha; la grilla y la plantilla son del mismo programa, la
 *       fecha cae en el día y la vigencia de la grilla, y la plantilla está lista (R18).</li>
 *   <li>R21: INSESC genera los cortes (uno por bloque, al final).</li>
 *   <li>R25, R26: duración, acumulado, hora de salida y balance por bloque salen de las vistas
 *       {@code v_escaleta_grilla} y {@code v_bloque_balance}; flotados y bajas no suman.</li>
 *   <li>LISESC marca cada ítem con su alerta: nota que volvió a revisión (D24), material sin
 *       terminar (D28) o agregado a última hora.</li>
 *   <li>Solo una escaleta en borrador se arma; cerrada se puede reabrir; emitida queda como
 *       historial (solo se cargan las duraciones reales).</li>
 * </ul>
 */
public final class ComandosEscaleta {

    private static final String CU = "CU-06";
    private ComandosEscaleta() {
    }

    public static List<CommandSpec> comandos() {
        return List.of(
                CommandSpec.de(CU, "LISESC", "Escaleta con tiempos y alertas por bloque")
                        .param("id_escaleta", Tipo.ID_O_TODOS)
                        .ejemplo("LISESC[\"1\"]").accion(ComandosEscaleta::lisesc).build(),
                CommandSpec.de(CU, "INSESC", "Generar la escaleta del día y copiar los cortes de la plantilla")
                        .param("id_grilla", Tipo.ID).param("fecha", Tipo.FECHA).param("id_plantilla", Tipo.ID)
                        .ejemplo("INSESC[\"1\",\"2026-10-12\",\"1\"]").accion(ComandosEscaleta::insesc).build(),
                CommandSpec.de(CU, "UPDESC", "Cerrar, reabrir o registrar como emitida")
                        .param("id", Tipo.ID).param("estado", Tipo.opcion("borrador", "cerrada", "emitida"))
                        .opcional("hora_inicio_real", Tipo.HORA).opcional("duracion_real", Tipo.DURACION)
                        .ejemplo("UPDESC[\"2\",\"emitida\",\"20:00:20\",\"60:42\"]").accion(ComandosEscaleta::updesc).build(),
                CommandSpec.de(CU, "EXPESC", "Exportar la escaleta (adjunto HTML)")
                        .param("id_escaleta", Tipo.ID)
                        .ejemplo("EXPESC[\"1\"]").accion(ComandosEscaleta::expesc).build());
    }

    // =================================================================== escaleta (P8)

    private static final String CABECERA = """
            SELECT e.id AS "Id", p.nombre AS "Programa", e.fecha AS "Fecha", to_char(e.hora_inicio, 'HH24:MI') AS "Hora",
                   %s AS "Duración", pl.nombre AS "Plantilla", e.estado AS "Estado", u.nombre || ' ' || u.apellido AS "Responsable",
                   e.hora_inicio_real AS "Inicio real", %s AS "Duración real"
            FROM escaleta e
            JOIN grilla g ON g.id = e.grilla_id
            JOIN programa p ON p.id = g.programa_id
            JOIN plantilla pl ON pl.id = e.plantilla_id
            JOIN usuario u ON u.id = e.responsable_id
            WHERE e.id = ?
            """.formatted(Formato.mmss("e.duracion_total"), Formato.mmss("e.duracion_real"));

    /** La grilla de la escaleta (R25) con la alerta de cada ítem (D24, D28). */
    private static final String GRILLA = """
            SELECT g.pagina AS "Pág.", g.tipo AS "Tipo", g.slug AS "Slug", g.titulo AS "Título", g.presentador AS "Presentador",
                   %s AS "Duración", %s AS "Acumulado", to_char(g.hora_salida, 'HH24:MI:SS') AS "Sale", %s AS "Real",
                   concat_ws('; ',
                       CASE WHEN g.estado_nota IS NOT NULL AND g.estado_nota NOT IN ('aprobada', 'emitida')
                            THEN 'nota ' || replace(replace(g.estado_nota, 'en_revision', 'en revisión'), '_', ' ') END,
                       CASE WHEN EXISTS (SELECT 1 FROM material_audiovisual m WHERE m.nota_id = i.nota_id AND m.estado <> 'listo')
                            THEN 'material sin terminar' END,
                       CASE WHEN g.ultima_hora THEN 'última hora' END) AS "Alertas"
            FROM v_escaleta_grilla g
            JOIN item_escaleta i ON i.id = g.item_id
            WHERE g.escaleta_id = ?
            ORDER BY g.posicion
            """.formatted(Formato.mmss("g.duracion"), Formato.mmss("g.acumulado_seg::int"), Formato.mmss("g.duracion_real"));

    /** R26: usado frente a disponible por bloque; alerta si sobran o faltan más de 15 s. */
    static final String BALANCE = """
            SELECT b.bloque AS "Bloque", %s AS "Disponible", %s AS "Usado", %s AS "Diferencia", b.alerta AS "Alerta"
            FROM v_bloque_balance b WHERE b.escaleta_id = ? ORDER BY b.bloque
            """.formatted(Formato.mmss("b.tiempo_disponible"), Formato.mmss("b.tiempo_usado::int"), Formato.mmss("b.diferencia::int"));

    private static Resultado lisesc(Contexto ctx) throws SQLException, ErrorNegocio {
        Integer id = ctx.p().entero("id_escaleta");
        if (id == null) {
            List<Fila> filas = ctx.consultar("""
                    SELECT e.id AS "Id", e.fecha AS "Fecha", p.nombre AS "Programa", to_char(e.hora_inicio, 'HH24:MI') AS "Hora",
                           e.estado AS "Estado",
                           (SELECT count(*) FROM item_escaleta i WHERE i.escaleta_id = e.id AND i.tipo <> 'corte'
                               AND i.estado IN ('programado', 'emitido')) AS "Ítems",
                           (SELECT count(*) FROM v_bloque_balance b WHERE b.escaleta_id = e.id AND b.alerta <> 'ok') AS "Bloques con alerta"
                    FROM escaleta e JOIN grilla g ON g.id = e.grilla_id JOIN programa p ON p.id = g.programa_id
                    ORDER BY e.fecha DESC, e.hora_inicio
                    """);
            return Resultado.listado("Escaletas", filas, "No hay escaletas registradas.")
                    .parrafo("Para ver una escaleta con sus tiempos: LISESC[\"id\"].");
        }
        return escaleta(ctx, id);
    }

    /** La escaleta completa (plantilla P8): cabecera, grilla con alertas y balance por bloque. */
    private static Resultado escaleta(Contexto ctx, int id) throws SQLException, ErrorNegocio {
        Fila cab = ctx.requerir(CABECERA, id);
        List<Fila> grilla = ctx.consultar(GRILLA, id);
        List<Fila> balance = ctx.consultar(BALANCE, id);
        List<String> alertasBloque = new ArrayList<>();
        for (Fila b : balance) {
            if (!"ok".equals(b.texto("Alerta"))) {
                alertasBloque.add("Bloque " + b.texto("Bloque") + ": " + b.texto("Alerta") + " de "
                        + b.texto("Diferencia").replace("-", "") + " (disponible " + b.texto("Disponible") + ", usado " + b.texto("Usado") + ")");
            }
        }
        long itemsConAlerta = grilla.stream().filter(f -> f.texto("Alertas") != null && !f.texto("Alertas").isEmpty()).count();
        String titulo = "Escaleta " + cab.texto("Programa") + " " + cab.fecha("Fecha");
        String mensaje = alertasBloque.isEmpty() && itemsConAlerta == 0
                ? "Escaleta " + id + ": todos los bloques cuadran."
                : "Escaleta " + id + ": " + alertasBloque.size() + " bloque(s) con alerta de tiempo y " + itemsConAlerta + " ítem(s) con alerta.";
        Resultado r = Resultado.informacion(titulo, mensaje).ficha(cab);
        for (String a : alertasBloque) {
            r.aviso(a);
        }
        r.subtitulo("Escaleta");
        if (grilla.isEmpty()) {
            r.parrafo("Todavía no tiene ítems.");
        } else {
            r.tabla(grilla);
        }
        return r.subtitulo("Tiempos por bloque").tabla(balance);
    }

    private static Resultado insesc(Contexto ctx) throws SQLException, ErrorNegocio {
        Parametros p = ctx.p();
        int idGrilla = p.entero("id_grilla");
        int idPlantilla = p.entero("id_plantilla");
        LocalDate fecha = p.fecha("fecha");
        Fila g = ctx.uno("""
                SELECT g.programa_id, g.dia_semana, g.estado, g.vigente_desde, g.vigente_hasta, pr.nombre, pr.tipo, pr.estado AS estado_programa
                FROM grilla g JOIN programa pr ON pr.id = g.programa_id WHERE g.id = ?
                """, idGrilla).orElseThrow(() -> ErrorNegocio.de("el horario de grilla indicado no existe"));
        Fila pl = ctx.uno("""
                SELECT pl.programa_id, pl.estado, pc.observacion FROM plantilla pl
                JOIN v_plantilla_control pc ON pc.plantilla_id = pl.id WHERE pl.id = ?
                """, idPlantilla).orElseThrow(() -> ErrorNegocio.de("la plantilla indicada no existe"));
        if ("otro".equals(g.texto("tipo"))) {
            throw ErrorNegocio.de(g.texto("nombre") + " no es un noticiero ni un especial: no tiene escaleta");
        }
        if (!"vigente".equals(g.texto("estado")) || !"activo".equals(g.texto("estado_programa"))) {
            throw ErrorNegocio.de("el horario de grilla no está vigente");
        }
        if (!g.entero("programa_id").equals(pl.entero("programa_id"))) {
            throw ErrorNegocio.de("la plantilla es de otro programa: use una plantilla de " + g.texto("nombre"));  // R20
        }
        if (!"vigente".equals(pl.texto("estado"))) {
            throw ErrorNegocio.de("la plantilla está inactiva");
        }
        if (!"ok".equals(pl.texto("observacion"))) {
            throw ErrorNegocio.de("la plantilla no está lista (" + pl.texto("observacion") + ")");  // A2, R18
        }
        if (fecha.getDayOfWeek().getValue() != g.entero("dia_semana")) {
            throw ErrorNegocio.de("el " + fecha + " no es " + dia(g.entero("dia_semana")) + ", el día de ese horario de grilla");  // R20
        }
        if (fecha.isBefore(g.fecha("vigente_desde")) || (g.get("vigente_hasta") != null && fecha.isAfter(g.fecha("vigente_hasta")))) {
            throw ErrorNegocio.de("el horario de grilla no está vigente el " + fecha);
        }
        if (ctx.existe("SELECT 1 FROM escaleta WHERE grilla_id = ? AND fecha = ?", idGrilla, fecha)) {
            throw ErrorNegocio.de("ya existe una escaleta para ese horario y esa fecha");
        }
        int id = ctx.devolviendo("""
                INSERT INTO escaleta (grilla_id, plantilla_id, fecha, hora_inicio, duracion_total, responsable_id)
                SELECT g.id, pl.id, ?, g.hora_inicio, pl.duracion_total, ?
                FROM grilla g JOIN plantilla pl ON pl.id = ?
                WHERE g.id = ?
                RETURNING id
                """, fecha, ctx.remitente().usuarioId(), idPlantilla, idGrilla).entero("id");
        // R21: un corte por bloque, al final (orden 999), copiado de la plantilla
        ctx.ejecutar("""
                INSERT INTO item_escaleta (escaleta_id, orden, bloque, tipo, titulo, duracion_estimada)
                SELECT e.id, 999, CHR(64 + c.numero), 'corte', 'Corte comercial ' || c.numero, c.duracion
                FROM escaleta e JOIN plantilla_corte c ON c.plantilla_id = e.plantilla_id
                WHERE e.id = ?
                ORDER BY c.numero
                """, id);
        Resultado r = escaleta(ctx, id);
        return Resultado.exito(r.titulo(), "escaleta generada").html(r.contenidoHtml())
                .parrafo("Siguiente paso: agregue los ítems con INSITE[\"" + id + "\",\"bloque\",\"tipo\",\"id_nota o titulo\",\"id_presentador\",\"duracion\"].");
    }

    private static Resultado updesc(Contexto ctx) throws SQLException, ErrorNegocio {
        Parametros p = ctx.p();
        int id = p.entero("id");
        Fila e = ctx.requerir("SELECT estado FROM escaleta WHERE id = ?", id);
        String actual = e.texto("estado");
        String nuevo = p.texto("estado");
        if (actual.equals("emitida")) {
            throw ErrorNegocio.de("la escaleta ya fue emitida: queda como historial");
        }
        if (actual.equals(nuevo)) {
            throw ErrorNegocio.de("la escaleta ya está " + nuevo);
        }
        boolean conReales = p.tiene("hora_inicio_real") || p.tiene("duracion_real");
        if (!nuevo.equals("emitida") && conReales) {
            throw ErrorNegocio.de("la hora y la duración reales se registran al marcarla como emitida");
        }
        Resultado r;
        switch (nuevo) {
            case "cerrada" -> {
                ctx.ejecutar("UPDATE escaleta SET estado = 'cerrada', actualizado_en = CURRENT_TIMESTAMP WHERE id = ?", id);
                r = Resultado.exito("Escaleta", "escaleta cerrada");
                List<Fila> pendientes = notasNoAprobadas(ctx, id);
                if (!pendientes.isEmpty()) {
                    r.aviso("Hay notas programadas que no están aprobadas: apruébelas o flótelas antes de emitir.").tabla(pendientes);
                }
            }
            case "borrador" -> {
                ctx.ejecutar("UPDATE escaleta SET estado = 'borrador', actualizado_en = CURRENT_TIMESTAMP WHERE id = ?", id);
                r = Resultado.exito("Escaleta", "escaleta reabierta");
            }
            default -> {  // emitida
                if (!actual.equals("cerrada")) {
                    throw ErrorNegocio.de("cierre la escaleta antes de registrarla como emitida");
                }
                if (!p.tiene("hora_inicio_real") || !p.tiene("duracion_real")) {
                    throw ErrorNegocio.de("indique la hora de inicio y la duración reales, por ejemplo UPDESC[\"" + id + "\",\"emitida\",\"20:00:20\",\"60:42\"]");
                }
                List<Fila> pendientes = notasNoAprobadas(ctx, id);
                if (!pendientes.isEmpty()) {
                    throw ErrorNegocio.conDetalle("hay notas programadas que no están aprobadas (R23): apruébelas o flótelas",
                            HTMLResponseBuilder.tabla(pendientes));
                }
                ctx.ejecutar("""
                        UPDATE escaleta SET estado = 'emitida', hora_inicio_real = ?, duracion_real = ?, actualizado_en = CURRENT_TIMESTAMP
                        WHERE id = ?
                        """, p.hora("hora_inicio_real"), p.entero("duracion_real"), id);
                ctx.ejecutar("UPDATE item_escaleta SET estado = 'emitido', actualizado_en = CURRENT_TIMESTAMP WHERE escaleta_id = ? AND estado = 'programado'", id);
                int notas = ctx.ejecutar("""
                        UPDATE nota SET estado = 'emitida', actualizado_en = CURRENT_TIMESTAMP
                        WHERE id IN (SELECT nota_id FROM item_escaleta WHERE escaleta_id = ? AND estado = 'emitido' AND nota_id IS NOT NULL)
                        """, id);
                r = Resultado.exito("Escaleta", "emisión registrada").parrafo(notas + " nota(s) pasaron a emitidas.");
            }
        }
        return r.ficha(ctx.requerir(CABECERA, id));
    }

    private static List<Fila> notasNoAprobadas(Contexto ctx, int idEscaleta) throws SQLException {
        return ctx.consultar("""
                SELECT i.id AS "Ítem", n.id AS "Nota", n.slug AS "Slug", n.estado AS "Estado"
                FROM item_escaleta i JOIN nota n ON n.id = i.nota_id
                WHERE i.escaleta_id = ? AND i.estado = 'programado' AND n.estado <> 'aprobada'
                ORDER BY i.bloque, i.orden
                """, idEscaleta);
    }

    private static String dia(int diaSemana) {
        return List.of("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo").get(diaSemana - 1);
    }

    // =================================================================== ítems

    private static Resultado expesc(Contexto ctx) throws SQLException, ErrorNegocio {
        int id = ctx.p().entero("id_escaleta");
        Resultado esc = escaleta(ctx, id);
        Fila cab = ctx.requerir(CABECERA, id);
        String nombre = "escaleta-" + cab.fecha("Fecha") + "-" + cab.texto("Programa").toLowerCase().replaceAll("[^a-z0-9]+", "-") + ".html";
        return Resultado.exito(esc.titulo(), "escaleta exportada")
                .parrafo("Se adjunta la escaleta " + id + " (" + cab.texto("Programa") + ", " + cab.fecha("Fecha") + ") como " + nombre
                        + ": se abre en el navegador y se puede imprimir.")
                .adjuntoHtml(nombre, HTMLResponseBuilder.pagina(esc.titulo(), esc.contenidoHtml()));
    }

    // =================================================================== reglas comunes
}
