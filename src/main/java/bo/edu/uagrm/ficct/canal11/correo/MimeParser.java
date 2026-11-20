package bo.edu.uagrm.ficct.canal11.correo;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lee un correo tal como llega por RETR (RFC 5322 y MIME) y lo convierte en un {@link MensajeCorreo}.
 *
 * <p>Contempla lo que mandan Gmail (web y Android), el Mail del iPhone y Outlook:</p>
 * <ul>
 *   <li>cabeceras plegadas en varias líneas y asunto codificado (RFC 2047), partido en varias palabras;</li>
 *   <li>remitente con nombre, entre comillas o no ({@code "Arteaga, Roberto" <jefe@…>});</li>
 *   <li>{@code multipart/alternative} y {@code multipart/mixed} anidados: se prefiere la parte
 *       {@code text/plain}, y si solo hay HTML se convierte a texto;</li>
 *   <li>cuerpos en {@code quoted-printable} o {@code base64}, con su charset (UTF-8, ISO-8859-1, Windows-1252);</li>
 *   <li>{@code format=flowed} de Apple (RFC 3676): las líneas partidas se vuelven a unir.</li>
 * </ul>
 *
 * <p><b>Nunca lanza excepciones:</b> un correo mal formado no puede tumbar la aplicación (D9). Lo
 * que no se puede leer queda vacío.</p>
 */
public final class MimeParser {

    private static final Logger LOG = Logger.getLogger(MimeParser.class.getName());

    /** Profundidad máxima de partes anidadas: protege contra correos armados para colgar al lector. */
    private static final int MAX_PROFUNDIDAD = 8;
    private static final Pattern DIRECCION_ENTRE_ANGULOS = Pattern.compile("<\\s*([^<>\\s]+@[^<>\\s]+)\\s*>");
    private static final Pattern DIRECCION_SUELTA = Pattern.compile("([^\\s<>\"(),;:]+@[^\\s<>\"(),;:]+)");

    /** Una parte MIME: sus cabeceras y su contenido crudo (bytes como texto Latin-1, uno a uno). */
    private record Parte(Map<String, List<String>> cabeceras, String contenido) {
        String cabecera(String nombre) {
            List<String> v = cabeceras.get(nombre);
            return v == null || v.isEmpty() ? null : v.get(0);
        }
    }

    /** Tipo de contenido y sus parámetros ({@code text/plain; charset=UTF-8; format=flowed}). */
    private record TipoContenido(String tipo, Map<String, String> parametros) {
        String parametro(String nombre) {
            return parametros.get(nombre);
        }

        boolean es(String prefijo) {
            return tipo.startsWith(prefijo);
        }
    }

    private MimeParser() {
    }

    /** Lee un correo. Nunca devuelve {@code null} ni lanza excepciones. */
    public static MensajeCorreo leer(byte[] crudo) {
        if (crudo == null) {
            crudo = new byte[0];
        }
        try {
            Parte raiz = parte(new String(crudo, StandardCharsets.ISO_8859_1));
            String from = textoCabecera(raiz.cabecera("from"));
            String[] remitente = remitente(from);
            String asunto = textoCabecera(raiz.cabecera("subject"));
            String messageId = raiz.cabecera("message-id");
            String cuerpo = cuerpoTexto(raiz, 0);
            Map<String, List<String>> cabeceras = new LinkedHashMap<>();
            raiz.cabeceras().forEach((k, v) -> cabeceras.put(k, v.stream().map(MimeParser::textoCabecera).toList()));
            return new MensajeCorreo(remitente[0], remitente[1], asunto == null ? "" : asunto.strip(),
                    cuerpo, messageId == null ? "" : messageId.strip(), crudo.length, cabeceras);
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "No se pudo leer un correo de " + crudo.length + " bytes", e);
            return new MensajeCorreo("", "", "", "", "", crudo.length, Map.of());
        }
    }

    // ---------------------------------------------------------------- estructura

    /** Separa cabeceras y contenido en la primera línea vacía, y despliega las cabeceras. */
    private static Parte parte(String texto) {
        Matcher separador = Pattern.compile("\\r?\\n\\r?\\n").matcher(texto);
        String bloque;
        String contenido;
        if (separador.find()) {
            bloque = texto.substring(0, separador.start());
            contenido = texto.substring(separador.end());
        } else if (texto.contains(":")) {
            bloque = texto;
            contenido = "";
        } else {
            bloque = "";
            contenido = texto;
        }
        Map<String, List<String>> cabeceras = new LinkedHashMap<>();
        String nombre = null;
        StringBuilder valor = new StringBuilder();
        for (String linea : bloque.split("\\r?\\n")) {
            if (!linea.isEmpty() && (linea.charAt(0) == ' ' || linea.charAt(0) == '\t') && nombre != null) {
                valor.append(linea); // desplegar: se quita el salto y se conserva el espacio (RFC 5322, §2.2.3)
                continue;
            }
            agregar(cabeceras, nombre, valor);
            int dos = linea.indexOf(':');
            if (dos > 0) {
                nombre = linea.substring(0, dos).trim().toLowerCase(Locale.ROOT);
                valor = new StringBuilder(linea.substring(dos + 1).trim());
            } else {
                nombre = null;
            }
        }
        agregar(cabeceras, nombre, valor);
        return new Parte(cabeceras, contenido);
    }

    private static void agregar(Map<String, List<String>> cabeceras, String nombre, StringBuilder valor) {
        if (nombre != null) {
            cabeceras.computeIfAbsent(nombre, k -> new ArrayList<>()).add(valor.toString());
        }
    }

    private static TipoContenido tipoContenido(Parte p) {
        String valor = p.cabecera("content-type");
        if (valor == null || valor.isBlank()) {
            return new TipoContenido("text/plain", Map.of());
        }
        String[] partes = valor.split(";");
        Map<String, String> parametros = new LinkedHashMap<>();
        for (int i = 1; i < partes.length; i++) {
            int igual = partes[i].indexOf('=');
            if (igual > 0) {
                String clave = partes[i].substring(0, igual).trim().toLowerCase(Locale.ROOT);
                String v = partes[i].substring(igual + 1).trim();
                if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) {
                    v = v.substring(1, v.length() - 1);
                }
                parametros.put(clave, v);
            }
        }
        return new TipoContenido(partes[0].trim().toLowerCase(Locale.ROOT), parametros);
    }

    /** Partes de un multipart, separadas por {@code --límite}; termina en {@code --límite--}. */
    private static List<Parte> subpartes(String contenido, String limite) {
        List<Parte> partes = new ArrayList<>();
        String marca = "--" + limite;
        String[] lineas = contenido.split("\\r?\\n", -1);
        StringBuilder actual = null;
        for (String linea : lineas) {
            String l = linea.stripTrailing();
            if (l.equals(marca) || l.equals(marca + "--")) {
                if (actual != null) {
                    partes.add(parte(actual.toString()));
                }
                if (l.endsWith("--") && l.length() == marca.length() + 2) {
                    return partes;
                }
                actual = new StringBuilder();
            } else if (actual != null) {
                actual.append(linea).append("\r\n");
            }
        }
        if (actual != null && !actual.isEmpty()) {
            partes.add(parte(actual.toString()));
        }
        return partes;
    }

    // ---------------------------------------------------------------- cuerpo

    /** El texto del correo: primero busca text/plain en todo el árbol; si no hay, text/html convertido. */
    private static String cuerpoTexto(Parte raiz, int profundidad) {
        String plano = buscar(raiz, "text/plain", profundidad);
        if (plano != null) {
            return plano;
        }
        String html = buscar(raiz, "text/html", profundidad);
        return html == null ? "" : htmlATexto(html);
    }

    private static String buscar(Parte p, String tipoBuscado, int profundidad) {
        if (profundidad > MAX_PROFUNDIDAD) {
            return null;
        }
        TipoContenido tipo = tipoContenido(p);
        if (tipo.es("multipart/")) {
            String limite = tipo.parametro("boundary");
            if (limite == null || limite.isEmpty()) {
                return null;
            }
            for (Parte sub : subpartes(p.contenido(), limite)) {
                String encontrado = buscar(sub, tipoBuscado, profundidad + 1);
                if (encontrado != null) {
                    return encontrado;
                }
            }
            return null;
        }
        String disposicion = p.cabecera("content-disposition");
        boolean adjunto = disposicion != null && disposicion.trim().toLowerCase(Locale.ROOT).startsWith("attachment");
        if (adjunto || !tipo.tipo().equals(tipoBuscado)) {
            return null;
        }
        String texto = CodificacionMime.texto(decodificar(p), tipo.parametro("charset"));
        texto = texto.replace("\r\n", "\n").replace('\r', '\n');
        if ("flowed".equalsIgnoreCase(tipo.parametro("format"))) {
            texto = desfluir(texto, "yes".equalsIgnoreCase(tipo.parametro("delsp")));
        }
        return texto;
    }

    /** Aplica el Content-Transfer-Encoding (base64, quoted-printable o nada). */
    private static byte[] decodificar(Parte p) {
        byte[] crudo = p.contenido().getBytes(StandardCharsets.ISO_8859_1);
        String cte = p.cabecera("content-transfer-encoding");
        String codificacion = cte == null ? "" : cte.trim().toLowerCase(Locale.ROOT);
        return switch (codificacion) {
            case "base64" -> CodificacionMime.decodificarBase64(crudo);
            case "quoted-printable" -> CodificacionMime.decodificarQuotedPrintable(crudo);
            default -> crudo;
        };
    }

    /**
     * {@code format=flowed} (RFC 3676): una línea que termina en espacio continúa en la siguiente. Se
     * quita el espacio inicial agregado por el emisor y, con {@code delsp=yes}, el espacio final.
     */
    private static String desfluir(String texto, boolean delsp) {
        StringBuilder sb = new StringBuilder(texto.length());
        String[] lineas = texto.split("\n", -1);
        boolean terminaEnSalto = texto.endsWith("\n");
        int cantidad = terminaEnSalto ? lineas.length - 1 : lineas.length; // el último "" del split no es una línea
        for (int i = 0; i < cantidad; i++) {
            String l = lineas[i].startsWith(" ") ? lineas[i].substring(1) : lineas[i];
            boolean fluye = l.endsWith(" ") && !l.equals("-- ");
            if (fluye) {
                sb.append(delsp ? l.substring(0, l.length() - 1) : l);
            } else {
                sb.append(l);
                if (i < cantidad - 1 || terminaEnSalto) {
                    sb.append('\n');
                }
            }
        }
        return sb.toString();
    }

    /** Más que esto no es el texto de un comando: se recorta para que el único hilo no se frene. */
    static final int MAX_CHARS_HTML = 64 * 1024;

    /** Etiquetas cuyo contenido no es texto: se quitan enteras. */
    private static final List<String> SIN_TEXTO = List.of("script", "style", "head");
    /** Etiquetas que terminan un bloque: dejan un salto de línea. */
    private static final List<String> BLOQUES = List.of("/p", "/div", "/tr", "/li", "/table", "/blockquote",
            "/h1", "/h2", "/h3", "/h4", "/h5", "/h6", "br", "br/");

    /**
     * HTML a texto: saltos en los bloques, sin etiquetas ni estilos, con las entidades decodificadas.
     *
     * <p>Recorre el HTML una sola vez (tiempo lineal). Con expresiones regulares como {@code <[^>]*>},
     * un HTML con miles de {@code <} sin cerrar tardaba minutos y frenaba la lectura del buzón.</p>
     */
    static String htmlATexto(String html) {
        String h = html.length() > MAX_CHARS_HTML ? html.substring(0, MAX_CHARS_HTML) : html;
        StringBuilder sb = new StringBuilder(h.length());
        int n = h.length();
        int i = 0;
        while (i < n) {
            char ch = h.charAt(i);
            if (ch != '<') {
                sb.append(ch);
                i++;
                continue;
            }
            int fin = h.indexOf('>', i);
            if (fin < 0) {
                sb.append(h, i, n); // un «<» que nunca cierra no es etiqueta; y después no hay más
                break;
            }
            String nombre = nombreEtiqueta(h, i + 1, fin);
            if (SIN_TEXTO.contains(nombre)) {
                int cierre = buscarCierre(h, nombre, fin + 1);
                if (cierre >= 0) {
                    i = cierre;
                    continue;
                }
                // sin cierre: se quita solo la etiqueta de apertura y su contenido queda como texto
            } else if (BLOQUES.contains(nombre)) {
                sb.append('\n');
            }
            i = fin + 1;
        }
        String t = entidades(sb.toString()).replace('\u00a0', ' ');
        StringBuilder limpio = new StringBuilder(t.length());
        for (String linea : t.split("\n", -1)) {
            limpio.append(linea.stripTrailing()).append('\n');
        }
        return limpio.toString().replaceAll("\n{3,}", "\n\n").strip();
    }

    /** {@code "p"}, {@code "/div"}, {@code "br/"}…: el nombre de la etiqueta entre {@code desde} y {@code hasta}, en minúsculas. */
    private static String nombreEtiqueta(String h, int desde, int hasta) {
        int j = desde;
        if (j < hasta && h.charAt(j) == '/') {
            j++;
        }
        while (j < hasta && Character.isLetterOrDigit(h.charAt(j))) {
            j++;
        }
        if (j < hasta && h.charAt(j) == '/' && h.charAt(desde) != '/') {
            j++; // <br/>
        }
        return h.substring(desde, j).toLowerCase(Locale.ROOT);
    }

    /** Posición siguiente al {@code </nombre ...>} que cierra, o -1 si no hay. Avanza siempre hacia adelante. */
    private static int buscarCierre(String h, String nombre, int desde) {
        int k = desde;
        while ((k = h.indexOf("</", k)) >= 0) {
            if (h.regionMatches(true, k + 2, nombre, 0, nombre.length())) {
                int fin = h.indexOf('>', k);
                return fin < 0 ? -1 : fin + 1;
            }
            k += 2;
        }
        return -1;
    }

    private static String entidades(String t) {
        Matcher m = Pattern.compile("&(#x[0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);").matcher(t);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String e = m.group(1);
            String reemplazo;
            try {
                if (e.startsWith("#x") || e.startsWith("#X")) {
                    reemplazo = new String(Character.toChars(Integer.parseInt(e.substring(2), 16)));
                } else if (e.startsWith("#")) {
                    reemplazo = new String(Character.toChars(Integer.parseInt(e.substring(1))));
                } else {
                    reemplazo = ENTIDADES.getOrDefault(e, m.group());
                }
            } catch (IllegalArgumentException ex) {
                reemplazo = m.group();
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(reemplazo));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static final Map<String, String> ENTIDADES = Map.ofEntries(
            Map.entry("nbsp", " "), Map.entry("amp", "&"), Map.entry("lt", "<"), Map.entry("gt", ">"),
            Map.entry("quot", "\""), Map.entry("apos", "'"), Map.entry("laquo", "«"), Map.entry("raquo", "»"),
            Map.entry("ldquo", "“"), Map.entry("rdquo", "”"), Map.entry("lsquo", "‘"), Map.entry("rsquo", "’"),
            Map.entry("ndash", "–"), Map.entry("mdash", "—"), Map.entry("hellip", "…"),
            Map.entry("aacute", "á"), Map.entry("eacute", "é"), Map.entry("iacute", "í"), Map.entry("oacute", "ó"),
            Map.entry("uacute", "ú"), Map.entry("Aacute", "Á"), Map.entry("Eacute", "É"), Map.entry("Iacute", "Í"),
            Map.entry("Oacute", "Ó"), Map.entry("Uacute", "Ú"), Map.entry("ntilde", "ñ"), Map.entry("Ntilde", "Ñ"),
            Map.entry("uuml", "ü"), Map.entry("Uuml", "Ü"), Map.entry("iquest", "¿"), Map.entry("iexcl", "¡"));

    // ---------------------------------------------------------------- cabeceras

    /**
     * Valor de una cabecera como texto: bytes de 8 bits (algunos clientes mandan UTF-8 sin codificar,
     * RFC 6532) y palabras codificadas (RFC 2047).
     */
    private static String textoCabecera(String crudo) {
        if (crudo == null) {
            return null;
        }
        String texto = CodificacionMime.texto(crudo.getBytes(StandardCharsets.ISO_8859_1), null);
        return CodificacionMime.decodificarCabecera(texto);
    }

    /** {dirección en minúsculas, nombre} a partir del valor de From. */
    private static String[] remitente(String from) {
        if (from == null || from.isBlank()) {
            return new String[]{"", ""};
        }
        Matcher angulos = DIRECCION_ENTRE_ANGULOS.matcher(from);
        if (angulos.find()) {
            String nombre = from.substring(0, angulos.start()).trim();
            if (nombre.length() >= 2 && nombre.startsWith("\"") && nombre.endsWith("\"")) {
                nombre = nombre.substring(1, nombre.length() - 1).replace("\\\"", "\"");
            }
            return new String[]{angulos.group(1).toLowerCase(Locale.ROOT), nombre.trim()};
        }
        Matcher suelta = DIRECCION_SUELTA.matcher(from);
        if (suelta.find()) {
            // «carla@gmail.com (Carla Rojas)»: el comentario hace de nombre
            String resto = from.replace(suelta.group(1), "").trim();
            String nombre = resto.startsWith("(") && resto.endsWith(")") ? resto.substring(1, resto.length() - 1).trim() : "";
            return new String[]{suelta.group(1).toLowerCase(Locale.ROOT), nombre};
        }
        return new String[]{"", from.trim()};
    }
}
