package bo.edu.uagrm.ficct.canal11.comandos;

import bo.edu.uagrm.ficct.canal11.datos.Fila;

import java.util.ArrayList;
import java.util.List;

/**
 * Constructor de respuestas en HTML («HTMLResponseBuilder» del documento).
 *
 * <p>Los estilos van en línea, porque Gmail y Outlook ignoran buena parte de las hojas de estilo.
 * Todo texto que viene de un usuario o de la base pasa por {@link #escapar(String)}: un dato con
 * {@code <script>} se muestra como texto y no se ejecuta.</p>
 *
 * <p>Arma la página, los mensajes (plantillas P3, P4 y P5 de la revisión), el menú de AYUDA (P1),
 * las tablas de los listados y reportes (P2, P9) y las fichas de un registro.</p>
 */
public final class HTMLResponseBuilder {

    /** Tipo de mensaje: define el color del recuadro. */
    public enum Tipo {
        EXITO("#1e7e34", "#e8f5e9"),
        ERROR("#b00020", "#fdecea"),
        ADVERTENCIA("#8a6d00", "#fff8e1"),
        INFORMACION("#0b5394", "#e8f0fe");

        private final String borde;
        private final String fondo;

        Tipo(String borde, String fondo) {
            this.borde = borde;
            this.fondo = fondo;
        }
    }

    static final String RECORDATORIO_FORMATO =
            "Formato: CODIGO[\"param1\",\"param2\",...] · fechas AAAA-MM-DD · horas HH:MM · duraciones mm:ss. "
                    + "Envíe AYUDA para ver los comandos que puede usar.";

    private HTMLResponseBuilder() {
    }

    /** Escapa {@code & < > " '} para mostrar un texto tal cual dentro del HTML. */
    public static String escapar(String texto) {
        if (texto == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(texto.length() + 16);
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Página completa: encabezado del canal, el contenido y el recordatorio del formato. */
    public static String pagina(String titulo, String contenidoHtml) {
        return "<!DOCTYPE html><html lang=\"es\"><head><meta charset=\"UTF-8\"><title>" + escapar(titulo) + "</title></head>"
                + "<body style=\"margin:0;padding:16px;background:#f4f5f7;font-family:Arial,Helvetica,sans-serif;color:#1f2328;\">"
                + "<div style=\"max-width:760px;margin:0 auto;background:#ffffff;border:1px solid #d0d7de;border-radius:8px;overflow:hidden;\">"
                + "<div style=\"background:#0b2e59;color:#ffffff;padding:12px 16px;\">"
                + "<div style=\"font-size:12px;opacity:.85;\">Canal 11 · Televisión Universitaria UAGRM</div>"
                + "<div style=\"font-size:18px;font-weight:bold;\">" + escapar(titulo) + "</div></div>"
                + "<div style=\"padding:16px;font-size:14px;line-height:1.5;\">" + contenidoHtml + "</div>"
                + "<div style=\"padding:10px 16px;border-top:1px solid #d0d7de;font-size:12px;color:#57606a;\">"
                + escapar(RECORDATORIO_FORMATO) + "</div>"
                + "</div></body></html>";
    }

    /** Recuadro con un mensaje («Éxito: …», «Error: …»). */
    public static String mensaje(Tipo tipo, String texto) {
        return "<div style=\"border-left:4px solid " + tipo.borde + ";background:" + tipo.fondo
                + ";padding:10px 12px;margin:0 0 12px 0;\">" + escapar(texto) + "</div>";
    }

    public static String parrafo(String texto) {
        return "<p style=\"margin:0 0 12px 0;\">" + escapar(texto) + "</p>";
    }

    /** Texto con formato de código (comandos y ejemplos). */
    public static String codigo(String texto) {
        return "<code style=\"background:#f6f8fa;border:1px solid #d0d7de;border-radius:4px;padding:1px 4px;"
                + "font-family:Consolas,Menlo,monospace;font-size:13px;\">" + escapar(texto) + "</code>";
    }

    /** Lista con viñetas; cada elemento ya es HTML (armado con estos métodos). */
    public static String lista(List<String> elementosHtml) {
        StringBuilder sb = new StringBuilder("<ul style=\"margin:0 0 12px 0;padding-left:20px;\">");
        for (String e : elementosHtml) {
            sb.append("<li style=\"margin:2px 0;\">").append(e).append("</li>");
        }
        return sb.append("</ul>").toString();
    }

    /** Respuesta de error completa: página con el mensaje en rojo. */
    public static String error(String mensaje) {
        return pagina("Error", mensaje(Tipo.ERROR, mensaje));
    }

    /**
     * Error de un comando (plantilla P5): el motivo, el detalle opcional, el formato esperado y un
     * ejemplo para copiar.
     *
     * @param detalleHtml HTML ya escapado (puede ser vacío)
     */
    public static String errorDeComando(String titulo, String mensaje, String detalleHtml, String formato, String ejemplo) {
        StringBuilder sb = new StringBuilder(mensaje(Tipo.ERROR, mensaje));
        sb.append(detalleHtml == null ? "" : detalleHtml);
        if (formato != null) {
            sb.append("<p style=\"margin:0 0 6px 0;\">Formato: ").append(codigo(formato)).append("</p>");
        }
        if (ejemplo != null) {
            sb.append("<p style=\"margin:0 0 12px 0;\">Ejemplo: ").append(codigo(ejemplo)).append("</p>");
        }
        return pagina(titulo, sb.toString());
    }

    public static String subtitulo(String texto) {
        return "<h3 style=\"margin:16px 0 8px 0;font-size:15px;color:#0b2e59;\">" + escapar(texto) + "</h3>";
    }

    /** Un texto para una celda: escapado y con los saltos de línea como {@code <br>} (guiones). */
    public static String celda(String texto) {
        return escapar(texto).replace("\r\n", "\n").replace("\n", "<br>");
    }

    /** Tabla con las filas de una consulta: las columnas son los alias de la consulta. */
    public static String tabla(List<Fila> filas) {
        if (filas.isEmpty()) {
            return "";
        }
        List<String> encabezados = filas.get(0).columnas();
        List<List<String>> celdas = new ArrayList<>();
        for (Fila f : filas) {
            List<String> fila = new ArrayList<>();
            for (Object v : f.valores()) {
                fila.add(celda(Formato.valor(v)));
            }
            celdas.add(fila);
        }
        return tablaHtml(encabezados.stream().map(HTMLResponseBuilder::escapar).toList(), celdas);
    }

    /** Tabla con celdas ya armadas en HTML (por ejemplo, con {@link #codigo(String)}). */
    public static String tablaHtml(List<String> encabezadosHtml, List<List<String>> filasHtml) {
        StringBuilder sb = new StringBuilder("<table style=\"border-collapse:collapse;width:100%;margin:0 0 12px 0;font-size:13px;\">");
        sb.append("<thead><tr>");
        for (String e : encabezadosHtml) {
            sb.append("<th style=\"text-align:left;background:#eef2f7;border:1px solid #d0d7de;padding:6px 8px;\">").append(e).append("</th>");
        }
        sb.append("</tr></thead><tbody>");
        int i = 0;
        for (List<String> fila : filasHtml) {
            String fondo = i++ % 2 == 0 ? "#ffffff" : "#f8fafc";
            sb.append("<tr style=\"background:").append(fondo).append(";\">");
            for (String c : fila) {
                sb.append("<td style=\"border:1px solid #d0d7de;padding:6px 8px;vertical-align:top;\">").append(c).append("</td>");
            }
            sb.append("</tr>");
        }
        return sb.append("</tbody></table>").toString();
    }

    /** Los datos de un registro en dos columnas: campo y valor (plantilla P3). */
    public static String ficha(Fila fila) {
        List<List<String>> filas = new ArrayList<>();
        List<String> columnas = fila.columnas();
        List<Object> valores = fila.valores();
        for (int i = 0; i < columnas.size(); i++) {
            filas.add(List.of("<b>" + escapar(columnas.get(i)) + "</b>", celda(Formato.valor(valores.get(i)))));
        }
        return tablaHtml(List.of("Campo", "Valor"), filas);
    }
}
