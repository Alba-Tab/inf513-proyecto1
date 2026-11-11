package bo.edu.uagrm.ficct.canal11.correo;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Codificaciones MIME que no trae Java, en los dos sentidos:
 * <ul>
 *   <li>palabras codificadas de las cabeceras (RFC 2047), por ejemplo el asunto con tildes;</li>
 *   <li>cuerpos en base64 y en quoted-printable (RFC 2045);</li>
 *   <li>bytes a texto según el juego de caracteres declarado, con un respaldo si es inválido.</li>
 * </ul>
 *
 * <p>Una cabecera de correo solo admite ASCII. Un asunto como {@code Éxito: nota registrada} viaja
 * como {@code =?UTF-8?B?w4l4aXRvOiBub3RhIHJlZ2lzdHJhZGE=?=} y el cliente de correo lo muestra con
 * sus tildes.</p>
 */
public final class CodificacionMime {

    /** {@code =?charset?B|Q?texto?=}; el charset puede traer idioma ({@code UTF-8*es}, RFC 2231). */
    private static final Pattern PALABRA = Pattern.compile("=\\?([^?\\s]+)\\?([BbQq])\\?([^?\\s]*)\\?=");

    /** Una palabra codificada no puede pasar de 75 caracteres (RFC 2047, §2). */
    static final int MAX_PALABRA_CODIFICADA = 75;
    private static final String INICIO = "=?UTF-8?B?";
    private static final String FIN = "?=";
    /**
     * 42 bytes en base64 son 56 caracteres; con el inicio y el fin, 68. Así la primera línea,
     * {@code "Subject: " + palabra}, no pasa de los 78 caracteres recomendados (RFC 5322, §2.1.1).
     */
    private static final int MAX_BYTES_POR_PALABRA = 42;

    private CodificacionMime() {
    }

    /** {@code true} si el texto se puede poner tal cual en una cabecera (ASCII imprimible y espacios). */
    static boolean esAsciiImprimible(String texto) {
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (c < 0x20 || c > 0x7E) {
                return false;
            }
        }
        return true;
    }

    /**
     * Valor listo para una cabecera. Si es ASCII imprimible queda igual; si no, se codifica en una o
     * más palabras {@code =?UTF-8?B?…?=}, sin partir nunca un carácter entre dos palabras.
     *
     * @return las palabras separadas por {@code "\r\n "} (cabecera plegada, RFC 5322 §2.2.3)
     */
    public static String codificarCabecera(String texto) {
        if (esAsciiImprimible(texto)) {
            return texto;
        }
        return String.join("\r\n ", palabrasCodificadas(texto));
    }

    static List<String> palabrasCodificadas(String texto) {
        List<String> palabras = new ArrayList<>();
        ByteArrayOutputStream actual = new ByteArrayOutputStream();
        texto.codePoints().forEach(cp -> {
            byte[] caracter = new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8);
            if (actual.size() + caracter.length > MAX_BYTES_POR_PALABRA) {
                palabras.add(palabra(actual.toByteArray()));
                actual.reset();
            }
            actual.write(caracter, 0, caracter.length);
        });
        if (actual.size() > 0 || palabras.isEmpty()) {
            palabras.add(palabra(actual.toByteArray()));
        }
        return palabras;
    }

    private static String palabra(byte[] bytes) {
        return INICIO + Base64.getEncoder().encodeToString(bytes) + FIN;
    }

    /** Cuerpo en base64 con líneas de 76 caracteres separadas por {@code \r\n} (RFC 2045, §6.8). */
    public static String base64EnLineas(byte[] contenido) {
        return Base64.getMimeEncoder().encodeToString(contenido);
    }

    // ------------------------------------------------------------------ lectura (correos entrantes)

    /**
     * Decodifica las palabras {@code =?charset?B?…?=} y {@code =?charset?Q?…?=} de una cabecera.
     *
     * <p>El espacio entre dos palabras codificadas seguidas se descarta (RFC 2047, §6.2), y las
     * palabras seguidas con el mismo charset se unen antes de convertir a texto: así un carácter
     * partido entre dos palabras se recupera bien. Una palabra mal formada queda tal cual.</p>
     */
    public static String decodificarCabecera(String valor) {
        if (valor == null || !valor.contains("=?")) {
            return valor;
        }
        StringBuilder salida = new StringBuilder(valor.length());
        Matcher m = PALABRA.matcher(valor);
        int fin = 0;
        String charsetPendiente = null;
        ByteArrayOutputStream pendiente = new ByteArrayOutputStream();
        while (m.find()) {
            String entre = valor.substring(fin, m.start());
            boolean seguida = charsetPendiente != null && entre.isBlank();
            String charset = m.group(1);
            int asterisco = charset.indexOf('*');
            if (asterisco >= 0) {
                charset = charset.substring(0, asterisco);
            }
            byte[] bytes;
            try {
                bytes = m.group(2).equalsIgnoreCase("B")
                        ? Base64.getMimeDecoder().decode(m.group(3))
                        : decodificarQ(m.group(3));
            } catch (IllegalArgumentException e) {
                // palabra rota: se deja el texto original
                volcar(salida, pendiente, charsetPendiente);
                charsetPendiente = null;
                salida.append(entre).append(m.group());
                fin = m.end();
                continue;
            }
            if (!(seguida && charset.equalsIgnoreCase(charsetPendiente))) {
                volcar(salida, pendiente, charsetPendiente);
                if (!seguida) {
                    salida.append(entre);
                }
                charsetPendiente = charset;
            }
            pendiente.write(bytes, 0, bytes.length);
            fin = m.end();
        }
        volcar(salida, pendiente, charsetPendiente);
        salida.append(valor.substring(fin));
        return salida.toString();
    }

    private static void volcar(StringBuilder salida, ByteArrayOutputStream pendiente, String charset) {
        if (pendiente.size() > 0) {
            salida.append(texto(pendiente.toByteArray(), charset));
            pendiente.reset();
        }
    }

    /** Codificación Q de RFC 2047: como quoted-printable, y además {@code _} es un espacio. */
    private static byte[] decodificarQ(String texto) {
        return decodificarQuotedPrintable(texto.replace('_', ' ').getBytes(StandardCharsets.ISO_8859_1));
    }

    /**
     * Quoted-printable (RFC 2045, §6.7): {@code =XX} es el byte XX y {@code =} al final de la línea
     * une con la siguiente. Una secuencia inválida queda tal cual.
     */
    public static byte[] decodificarQuotedPrintable(byte[] entrada) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream(entrada.length);
        for (int i = 0; i < entrada.length; i++) {
            byte b = entrada[i];
            if (b != '=') {
                salida.write(b);
                continue;
            }
            // salto suave: "=" + espacios opcionales + fin de línea
            int j = i + 1;
            while (j < entrada.length && (entrada[j] == ' ' || entrada[j] == '\t')) {
                j++;
            }
            if (j < entrada.length && (entrada[j] == '\r' || entrada[j] == '\n')) {
                i = entrada[j] == '\r' && j + 1 < entrada.length && entrada[j + 1] == '\n' ? j + 1 : j;
                continue;
            }
            if (j >= entrada.length) {
                i = j;
                continue;
            }
            if (i + 2 < entrada.length) {
                int alto = Character.digit(entrada[i + 1], 16);
                int bajo = Character.digit(entrada[i + 2], 16);
                if (alto >= 0 && bajo >= 0) {
                    salida.write(alto * 16 + bajo);
                    i += 2;
                    continue;
                }
            }
            salida.write(b);
        }
        return salida.toByteArray();
    }

    /** Base64 de un cuerpo; ignora saltos de línea y caracteres ajenos al alfabeto. */
    public static byte[] decodificarBase64(byte[] entrada) {
        try {
            return Base64.getMimeDecoder().decode(entrada);
        } catch (IllegalArgumentException e) {
            // relleno incorrecto u otra rotura: se decodifica lo que se pueda
            String limpio = new String(entrada, StandardCharsets.ISO_8859_1).replaceAll("[^A-Za-z0-9+/]", "");
            limpio = limpio.substring(0, limpio.length() - limpio.length() % 4);
            return Base64.getDecoder().decode(limpio);
        }
    }

    /**
     * Bytes a texto con el charset declarado. Si no hay charset, o es inválido, o es ASCII pero llegan
     * bytes de 8 bits, intenta UTF-8 y, si no es UTF-8 válido, Windows-1252 (que incluye Latin-1).
     */
    public static String texto(byte[] bytes, String charset) {
        Charset declarado = charset(charset);
        if (declarado != null && !declarado.equals(StandardCharsets.US_ASCII)) {
            return new String(bytes, declarado);
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, Charset.forName("windows-1252"));
        }
    }

    private static Charset charset(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return null;
        }
        String n = nombre.trim().replace("\"", "").toLowerCase(Locale.ROOT);
        if (n.equals("iso-8859-1") || n.equals("latin1")) {
            // Los clientes de Windows declaran iso-8859-1 y mandan comillas tipográficas de windows-1252
            return Charset.forName("windows-1252");
        }
        try {
            return Charset.forName(n);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Quita saltos de línea y caracteres de control: evita que un dato agregue cabeceras (inyección). */
    static String sinSaltos(String texto) {
        StringBuilder sb = new StringBuilder(texto.length());
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            sb.append(Character.isISOControl(c) ? ' ' : c);
        }
        return sb.toString().strip();
    }
}
