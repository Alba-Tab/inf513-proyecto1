package bo.edu.uagrm.ficct.canal11.comandos.cu06;

import bo.edu.uagrm.ficct.canal11.datos.Fila;
import bo.edu.uagrm.ficct.canal11.comandos.Contexto;
import bo.edu.uagrm.ficct.canal11.comandos.ErrorNegocio;
import bo.edu.uagrm.ficct.canal11.comandos.Formato;
import bo.edu.uagrm.ficct.canal11.comandos.Resultado;
import bo.edu.uagrm.ficct.canal11.comandos.catalogo.CommandSpec;
import bo.edu.uagrm.ficct.canal11.comandos.catalogo.Parametros;
import bo.edu.uagrm.ficct.canal11.comandos.catalogo.Tipo;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * CU-06, tercera parte: los ítems de la escaleta (INSITE, UPDITE y DELITE) con el control de tiempos
 * de su bloque.
 *
 * <ul>
 *   <li>R21: los cortes los genera INSESC; aquí no se agregan, no se mueven ni se modifican.</li>
 *   <li>R22: el orden es único dentro del bloque y el bloque existe en la plantilla.</li>
 *   <li>R23: solo entran notas aprobadas. R24: una nota no se repite en la misma emisión.</li>
 *   <li>R25, R26: cada cambio devuelve el balance del bloque; flotados y bajas no suman.</li>
 *   <li>Solo se arma una escaleta en borrador; emitida, solo se cargan las duraciones reales.</li>
 * </ul>
 */
public final class ComandosItems {

    private static final String CU = "CU-06";
    static final Tipo TIPO_ITEM = Tipo.opcion("apertura", "titulares", "nota", "enlace_vivo", "entrevista", "corte", "despedida", "otro");
    /** Un ítem agregado menos de una hora antes del inicio cuenta como de última hora (se marca en el ítem). */
    private static final int MINUTOS_ULTIMA_HORA = 60;

    private ComandosItems() {
    }

    public static List<CommandSpec> comandos() {
        return List.of(
                CommandSpec.de(CU, "INSITE", "Agregar ítem al final del bloque (solo notas aprobadas; no acepta cortes)")
                        .param("id_escaleta", Tipo.ID).param("bloque", Tipo.BLOQUE).param("tipo", TIPO_ITEM)
                        .param("id_nota o titulo", Tipo.texto(200)).opcional("id_presentador", Tipo.ID)
                        .opcional("duracion", Tipo.DURACION)
                        .ejemplo("INSITE[\"1\",\"B\",\"enlace_vivo\",\"Enlace: paro de transporte\",\"10\",\"02:00\"]")
                        .accion(ComandosItems::insite).build(),
                CommandSpec.de(CU, "UPDITE", "Reordenar o ajustar un ítem")
                        .param("id", Tipo.ID).opcional("orden", Tipo.entero(1, 998)).opcional("bloque", Tipo.BLOQUE)
                        .opcional("id_presentador", Tipo.ID).opcional("duracion", Tipo.DURACION).opcional("duracion_real", Tipo.DURACION)
                        .omitible("estado", Tipo.opcion("programado"))
                        .ejemplo("UPDITE[\"9\",\"3\",\"B\",\"10\",\"02:30\",\"\"]").accion(ComandosItems::updite).build(),
                CommandSpec.de(CU, "DELITE", "Dar de baja un ítem")
                        .param("id", Tipo.ID)
                        .ejemplo("DELITE[\"9\"]").accion(ComandosItems::delite).build());
    }

    private static final String ITEMS = """
            SELECT i.id AS "Id", i.bloque AS "Bloque", CASE WHEN i.tipo = 'corte' THEN 'final' ELSE i.orden::text END AS "Orden",
                   i.tipo AS "Tipo", COALESCE(n.slug || ' (nota ' || n.id || ')', i.titulo) AS "Contenido",
                   u.nombre || ' ' || u.apellido AS "Presentador",
                   %s AS "Duración", %s AS "Real", i.estado AS "Estado", i.ultima_hora AS "Última hora"
            FROM item_escaleta i
            JOIN escaleta e ON e.id = i.escaleta_id
            JOIN plantilla pl ON pl.id = e.plantilla_id
            LEFT JOIN nota n ON n.id = i.nota_id
            LEFT JOIN v_nota_duracion d ON d.nota_id = i.nota_id
            LEFT JOIN personal pe ON pe.id = i.presentador_id
            LEFT JOIN usuario u ON u.id = pe.usuario_id
            """.formatted(Formato.mmss("CASE WHEN i.nota_id IS NOT NULL THEN CEIL(n.palabras * 60.0 / COALESCE(n.palabras_por_minuto, pl.palabras_por_minuto))::int + d.duracion_video ELSE i.duracion_estimada END"),
            Formato.mmss("i.duracion_real"));

    private static Resultado insite(Contexto ctx) throws SQLException, ErrorNegocio {
        Parametros p = ctx.p();
        int idEscaleta = p.entero("id_escaleta");
        String bloque = p.texto("bloque");
        String tipo = p.texto("tipo");
        Fila e = enBorrador(ctx, idEscaleta);
        if (tipo.equals("corte")) {
            throw ErrorNegocio.de("los cortes los genera el sistema");  // A5, R21
        }
        bloqueExistente(ctx, idEscaleta, bloque);
        String contenido = p.texto("id_nota o titulo");
        Integer idNota = null;
        String titulo = null;
        List<String> notas = new ArrayList<>();
        if (tipo.equals("nota")) {
            if (!contenido.matches("\\d+")) {
                throw ErrorNegocio.de("para un ítem de tipo nota indique el id de la nota (por ejemplo \"7\")");
            }
            idNota = Integer.parseInt(contenido);
            notaParaLaEscaleta(ctx, idEscaleta, idNota, 0);
            if (p.tiene("duracion")) {
                notas.add("La duración de una nota se calcula con su guion y su material: se ignoró la indicada.");
            }
        } else {
            titulo = contenido;
        }
        presentadorValido(ctx, p.entero("id_presentador"));
        boolean ultimaHora = esUltimaHora(e.fecha("fecha"), e.hora("hora_inicio"), LocalDateTime.now(ctx.reloj()));
        int id = ctx.devolviendo("""
                INSERT INTO item_escaleta (escaleta_id, orden, bloque, tipo, nota_id, titulo, presentador_id, duracion_estimada, ultima_hora)
                SELECT ?, COALESCE(max(orden) FILTER (WHERE tipo <> 'corte'), 0) + 1, ?, ?, ?, ?, ?, ?, ?
                FROM item_escaleta WHERE escaleta_id = ? AND bloque = ?
                RETURNING id
                """, idEscaleta, bloque, tipo, idNota, titulo, p.entero("id_presentador"),
                idNota == null && p.tiene("duracion") ? p.entero("duracion") : 0, ultimaHora, idEscaleta, bloque).entero("id");
        return conBalance(ctx, Resultado.exito("Escaleta", "ítem agregado"), id, idEscaleta, bloque, notas);
    }

    private static Resultado updite(Contexto ctx) throws SQLException, ErrorNegocio {
        Parametros p = ctx.p();
        p.requerirAlguno("orden", "bloque", "id_presentador", "duracion", "duracion_real", "estado");
        int id = p.entero("id");
        Fila i = ctx.requerir("SELECT escaleta_id, bloque, orden, tipo, nota_id, estado FROM item_escaleta WHERE id = ?", id);
        int idEscaleta = i.entero("escaleta_id");
        boolean soloReal = p.todosVacios("orden", "bloque", "id_presentador", "duracion", "estado");
        if (soloReal) {
            // la duración real se carga también con la escaleta cerrada o emitida
            ctx.ejecutar("UPDATE item_escaleta SET duracion_real = ?, actualizado_en = CURRENT_TIMESTAMP WHERE id = ?", p.entero("duracion_real"), id);
            return conBalance(ctx, Resultado.exito("Escaleta", "duración real registrada"), id, idEscaleta, i.texto("bloque"), List.of());
        }
        enBorrador(ctx, idEscaleta);
        if ("corte".equals(i.texto("tipo"))) {
            throw ErrorNegocio.de("los cortes los genera el sistema: no se mueven ni se modifican (solo su duración real)");
        }
        List<String> notas = new ArrayList<>();
        if (p.tiene("estado")) {  // D19: un ítem flotado vuelve a programarse
            if (!"flotado".equals(i.texto("estado"))) {
                throw ErrorNegocio.de("solo un ítem flotado vuelve a programarse (este está " + i.texto("estado") + ")");
            }
            if (i.get("nota_id") != null) {
                notaParaLaEscaleta(ctx, idEscaleta, i.entero("nota_id"), id);
            }
            ctx.ejecutar("UPDATE item_escaleta SET estado = 'programado', actualizado_en = CURRENT_TIMESTAMP WHERE id = ?", id);
        }
        if (p.tiene("id_presentador")) {
            presentadorValido(ctx, p.entero("id_presentador"));
        }
        if (p.tiene("duracion") && i.get("nota_id") != null) {
            notas.add("La duración de una nota se calcula con su guion y su material: se ignoró la indicada.");
        }
        ctx.ejecutar("""
                UPDATE item_escaleta SET presentador_id = COALESCE(?, presentador_id), duracion_estimada = COALESCE(?, duracion_estimada),
                                         duracion_real = COALESCE(?, duracion_real), actualizado_en = CURRENT_TIMESTAMP
                WHERE id = ?
                """, p.entero("id_presentador"), i.get("nota_id") == null ? p.entero("duracion") : null, p.entero("duracion_real"), id);
        String bloque = i.texto("bloque");
        if (p.tiene("orden") || p.tiene("bloque")) {
            String destino = p.tiene("bloque") ? p.texto("bloque") : bloque;
            bloqueExistente(ctx, idEscaleta, destino);
            mover(ctx, idEscaleta, id, bloque, destino, p.entero("orden"));
            bloque = destino;
        }
        return conBalance(ctx, Resultado.exito("Escaleta", "ítem modificado"), id, idEscaleta, bloque, notas);
    }

    private static Resultado delite(Contexto ctx) throws SQLException, ErrorNegocio {
        return cambiarEstadoItem(ctx, "baja", "ítem dado de baja");
    }

    /** DELITE: el ítem deja de sumar tiempo (R26) pero queda en la escaleta. */
    private static Resultado cambiarEstadoItem(Contexto ctx, String estado, String operacion) throws SQLException, ErrorNegocio {
        int id = ctx.p().entero("id");
        Fila i = ctx.requerir("SELECT escaleta_id, bloque, tipo, estado FROM item_escaleta WHERE id = ?", id);
        enBorrador(ctx, i.entero("escaleta_id"));
        if ("corte".equals(i.texto("tipo"))) {
            throw ErrorNegocio.de("los cortes los genera el sistema: no se dan de baja");
        }
        if (!"programado".equals(i.texto("estado"))) {
            throw ErrorNegocio.de("el ítem ya está " + i.texto("estado"));
        }
        ctx.ejecutar("UPDATE item_escaleta SET estado = ?, actualizado_en = CURRENT_TIMESTAMP WHERE id = ?", estado, id);
        return conBalance(ctx, Resultado.exito("Escaleta", operacion), id, i.entero("escaleta_id"), i.texto("bloque"), List.of());
    }

    /** La escaleta existe y está en borrador (A4). */
    private static Fila enBorrador(Contexto ctx, int idEscaleta) throws SQLException, ErrorNegocio {
        Fila e = ctx.requerir("SELECT estado, fecha, hora_inicio FROM escaleta WHERE id = ?", idEscaleta);
        if (!"borrador".equals(e.texto("estado"))) {
            throw ErrorNegocio.de("la escaleta no está en borrador");
        }
        return e;
    }

    /** R22: el bloque existe en la plantilla de la escaleta. */
    private static void bloqueExistente(Contexto ctx, int idEscaleta, String bloque) throws SQLException, ErrorNegocio {
        Fila pl = ctx.requerir("SELECT pl.cantidad_bloques FROM escaleta e JOIN plantilla pl ON pl.id = e.plantilla_id WHERE e.id = ?", idEscaleta);
        int bloques = pl.entero("cantidad_bloques");
        if (bloque.charAt(0) - 'A' + 1 > bloques) {
            throw ErrorNegocio.de("la escaleta tiene los bloques de la A a la " + (char) ('A' + bloques - 1));
        }
    }

    /** R23 y R24: la nota existe, está aprobada y no está ya programada en la escaleta. */
    private static void notaParaLaEscaleta(Contexto ctx, int idEscaleta, int idNota, int exceptoItem) throws SQLException, ErrorNegocio {
        Fila n = ctx.uno("SELECT slug, estado FROM nota WHERE id = ?", idNota).orElseThrow(() -> ErrorNegocio.de("la nota indicada no existe"));
        if (!"aprobada".equals(n.texto("estado"))) {
            throw ErrorNegocio.de("solo se agregan notas aprobadas (" + n.texto("slug") + " está " + n.texto("estado").replace('_', ' ') + ")");  // A3
        }
        Optional<Fila> otro = ctx.uno("""
                SELECT id, estado FROM item_escaleta WHERE escaleta_id = ? AND nota_id = ? AND id <> ? AND estado IN ('programado', 'flotado')
                ORDER BY estado LIMIT 1
                """, idEscaleta, idNota, exceptoItem);
        if (otro.isPresent()) {
            if ("flotado".equals(otro.get().texto("estado"))) {
                throw ErrorNegocio.de("la nota está flotada en esta escaleta: vuelva a programarla con UPDITE[\"" + otro.get().entero("id")
                        + "\",\"\",\"\",\"\",\"\",\"\",\"programado\"]");
            }
            throw ErrorNegocio.de("esa nota ya está en la escaleta");  // R24
        }
    }

    private static void presentadorValido(Contexto ctx, Integer idPersonal) throws SQLException, ErrorNegocio {
        if (idPersonal == null) {
            return;
        }
        Fila f = ctx.uno("""
                SELECT u.nombre || ' ' || u.apellido AS nombre, pe.cargo, pe.estado
                FROM personal pe JOIN usuario u ON u.id = pe.usuario_id WHERE pe.id = ?
                """, idPersonal).orElseThrow(() -> ErrorNegocio.de("el presentador indicado no existe"));
        if (!List.of("presentador", "conductor").contains(f.texto("cargo"))) {
            throw ErrorNegocio.de(f.texto("nombre") + " es " + f.texto("cargo").replace('_', ' ') + ": el ítem lo presenta un presentador o un conductor");
        }
        if (!"activo".equals(f.texto("estado"))) {
            throw ErrorNegocio.de("la ficha de " + f.texto("nombre") + " está inactiva");
        }
    }

    private static void mover(Contexto ctx, int idEscaleta, int idItem, String origen, String destino, Integer orden) throws SQLException {
        List<Integer> destinoIds = new ArrayList<>();
        for (Fila f : ctx.consultar("""
                SELECT id FROM item_escaleta WHERE escaleta_id = ? AND bloque = ? AND tipo <> 'corte' AND id <> ? ORDER BY orden
                """, idEscaleta, destino, idItem)) {
            destinoIds.add(f.entero("id"));
        }
        int posicion = orden == null ? destinoIds.size() : Math.min(Math.max(orden - 1, 0), destinoIds.size());
        destinoIds.add(posicion, idItem);
        ctx.ejecutar("UPDATE item_escaleta SET bloque = ?, actualizado_en = CURRENT_TIMESTAMP WHERE id = ?", destino, idItem);
        renumerar(ctx, destinoIds);
        if (!origen.equals(destino)) {
            List<Integer> origenIds = new ArrayList<>();
            for (Fila f : ctx.consultar("""
                    SELECT id FROM item_escaleta WHERE escaleta_id = ? AND bloque = ? AND tipo <> 'corte' ORDER BY orden
                    """, idEscaleta, origen)) {
                origenIds.add(f.entero("id"));
            }
            renumerar(ctx, origenIds);
        }
    }

    private static void renumerar(Contexto ctx, List<Integer> ids) throws SQLException {
        for (int k = 0; k < ids.size(); k++) {
            ctx.ejecutar("UPDATE item_escaleta SET orden = ? WHERE id = ?", k + 1, ids.get(k));
        }
    }

    /** La ficha del ítem y cómo quedó el tiempo de su bloque. */
    private static Resultado conBalance(Contexto ctx, Resultado r, int idItem, int idEscaleta, String bloque, List<String> notas)
            throws SQLException, ErrorNegocio {
        r.ficha(ctx.requerir(ITEMS + " WHERE i.id = ?", idItem));
        for (String n : notas) {
            r.parrafo(n);
        }
        Fila b = ctx.requerir(ComandosEscaleta.BALANCE.replace("ORDER BY b.bloque", "AND b.bloque = ?"), idEscaleta, bloque);
        String texto = "Bloque " + bloque + ": usado " + b.texto("Usado") + " de " + b.texto("Disponible");
        if ("ok".equals(b.texto("Alerta"))) {
            r.parrafo(texto + " (cuadra).");
        } else {
            r.aviso(texto + ": " + b.texto("Alerta") + " de " + b.texto("Diferencia").replace("-", "") + ".");
        }
        return r.parrafo("La escaleta se guarda igual; para verla completa: LISESC[\"" + idEscaleta + "\"].");
    }

    /** Un ítem es de última hora si se agrega menos de una hora antes del inicio de la emisión (o después). */
    static boolean esUltimaHora(LocalDate fecha, LocalTime inicio, LocalDateTime ahora) {
        return !ahora.isBefore(LocalDateTime.of(fecha, inicio).minusMinutes(MINUTOS_ULTIMA_HORA));
    }
}
