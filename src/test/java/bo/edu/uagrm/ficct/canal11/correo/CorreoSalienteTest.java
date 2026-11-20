package bo.edu.uagrm.ficct.canal11.correo;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorreoSalienteTest {

    /** 13/10/2026 10:00 en Bolivia. */
    static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-13T14:00:00Z"), ZoneId.of("America/La_Paz"));

    static final String HTML = "<html><body><p>Éxito: nota registrada correctamente. Señal en vivo.</p></body></html>";

    private static CorreoSaliente.Builder base() {
        return CorreoSaliente.builder()
                .de("grupo14sa@tecnoweb.org.bo", "Sistema Canal 11")
                .para("carla.rojas@gmail.com")
                .asunto("Re: INSNOT")
                .html(HTML);
    }

    // ------------------------------------------------------------- ayudantes de lectura

    /** Cabeceras desplegadas (une las líneas que empiezan con espacio), en orden. */
    static Map<String, String> cabeceras(String correo) {
        String bloque = correo.substring(0, correo.indexOf("\r\n\r\n")).replace("\r\n ", " ");
        Map<String, String> m = new LinkedHashMap<>();
        for (String l : bloque.split("\r\n")) {
            int dos = l.indexOf(':');
            m.putIfAbsent(l.substring(0, dos), l.substring(dos + 2));
        }
        return m;
    }

    static String cuerpo(String correo) {
        return correo.substring(correo.indexOf("\r\n\r\n") + 4);
    }

    /** Decodifica las palabras {@code =?UTF-8?B?…?=} (entre palabras se ignora el espacio, RFC 2047 §6.2). */
    static String decodificar(String valor) {
        String unido = valor.replaceAll("\\?=\\s+=\\?", "?==?");
        Matcher m = Pattern.compile("=\\?UTF-8\\?B\\?([^?]*)\\?=").matcher(unido);
        StringBuilder antes = new StringBuilder();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int fin = 0;
        while (m.find()) {
            antes.append(unido, fin, m.start());
            byte[] b = Base64.getDecoder().decode(m.group(1));
            bytes.write(b, 0, b.length);
            fin = m.end();
        }
        if (bytes.size() == 0) {
            return valor;
        }
        return antes + new String(bytes.toByteArray(), StandardCharsets.UTF_8) + unido.substring(fin);
    }

    // ------------------------------------------------------------- pruebas

    @Test
    void armaLasCabecerasDeUnaRespuesta() {
        String correo = new String(base().enRespuestaA("<CAB123@mail.gmail.com>").build().aBytes(RELOJ), StandardCharsets.US_ASCII);
        Map<String, String> c = cabeceras(correo);
        assertEquals("Sistema Canal 11 <grupo14sa@tecnoweb.org.bo>", c.get("From"));
        assertEquals("carla.rojas@gmail.com", c.get("To"));
        assertEquals("Re: INSNOT", c.get("Subject"));
        assertEquals("Tue, 13 Oct 2026 10:00:00 -0400", c.get("Date"));
        assertTrue(c.get("Message-ID").matches("<[0-9a-f-]{36}@tecnoweb\\.org\\.bo>"), c.get("Message-ID"));
        assertEquals("<CAB123@mail.gmail.com>", c.get("In-Reply-To"));
        assertEquals("<CAB123@mail.gmail.com>", c.get("References"));
        assertEquals("auto-replied", c.get("Auto-Submitted"));
        assertEquals("1.0", c.get("MIME-Version"));
        assertEquals("text/html; charset=UTF-8", c.get("Content-Type"));
        assertEquals("base64", c.get("Content-Transfer-Encoding"));
    }

    @Test
    void sinCorreoOriginalNoLlevaInReplyTo() {
        Map<String, String> c = cabeceras(new String(base().build().aBytes(RELOJ), StandardCharsets.US_ASCII));
        assertNull(c.get("In-Reply-To"));
        assertNull(c.get("References"));
    }

    @Test
    void elCuerpoEsElHtmlEnUtf8YBase64() {
        String correo = new String(base().build().aBytes(RELOJ), StandardCharsets.US_ASCII);
        byte[] html = Base64.getMimeDecoder().decode(cuerpo(correo));
        assertEquals(HTML, new String(html, StandardCharsets.UTF_8));
    }

    @Test
    void ningunaLineaPasaDe78CaracteresNiTieneBytesNoAscii() {
        String tablaEnUnaLinea = "<table>" + "<tr><td>Clásico cruceño</td><td>Estadio Tahuichi</td></tr>".repeat(2_000) + "</table>";
        byte[] bytes = base().asunto("Éxito: " + "cobertura «Clásico cruceño» registrada ".repeat(5)).html(tablaEnUnaLinea)
                .build().aBytes(RELOJ);
        for (byte b : bytes) {
            assertTrue(b >= 0 && b < 128, "byte no ASCII: " + b);
        }
        for (String linea : new String(bytes, StandardCharsets.US_ASCII).split("\r\n")) {
            assertTrue(linea.length() <= 78, "línea de " + linea.length() + ": " + linea);
        }
    }

    @Test
    void elAsuntoConTildesSeCodificaYSeRecupera() {
        String asunto = "Éxito: cobertura «Clásico cruceño» registrada en el Estadio Ramón Tahuichi Aguilera";
        String correo = new String(base().asunto(asunto).build().aBytes(RELOJ), StandardCharsets.US_ASCII);
        String valor = cabeceras(correo).get("Subject");
        assertTrue(valor.startsWith("=?UTF-8?B?"), valor);
        assertEquals(asunto, decodificar(valor));
    }

    @Test
    void lasPalabrasCodificadasNoPartenCaracteres() {
        String texto = "ñ".repeat(100) + "😀".repeat(30);
        List<String> palabras = CodificacionMime.palabrasCodificadas(texto);
        assertTrue(palabras.size() > 3);
        StringBuilder reconstruido = new StringBuilder();
        for (String p : palabras) {
            assertTrue(p.length() <= CodificacionMime.MAX_PALABRA_CODIFICADA, p);
            // cada palabra se decodifica sola a UTF-8 válido
            String sola = new String(Base64.getDecoder().decode(p.substring(10, p.length() - 2)), StandardCharsets.UTF_8);
            assertFalse(sola.contains("�"), "carácter partido en: " + p);
            reconstruido.append(sola);
        }
        assertEquals(texto, reconstruido.toString());
    }

    @Test
    void unAsuntoAsciiQuedaIgual() {
        assertEquals("Re: LISCOB[\"2026-10-12\"]", CodificacionMime.codificarCabecera("Re: LISCOB[\"2026-10-12\"]"));
    }

    @Test
    void nadiePuedeAgregarCabecerasDesdeElAsunto() {
        String correo = new String(base().asunto("Hola\r\nBcc: espia@malo.com\r\n\r\n<h1>falso</h1>")
                .enRespuestaA("<id@x>\r\nBcc: otro@malo.com").build().aBytes(RELOJ), StandardCharsets.US_ASCII);
        Map<String, String> c = cabeceras(correo);
        assertNull(c.get("Bcc"));
        assertFalse(correo.substring(0, correo.indexOf("\r\n\r\n")).contains("\r\nBcc"));
    }

    @Test
    void rechazaDireccionesInvalidas() {
        assertThrows(IllegalArgumentException.class, () -> base().para("sin-arroba").build());
        assertThrows(IllegalArgumentException.class, () -> base().para("a@b.com\r\nBcc: x@y.com").build());
        assertThrows(IllegalArgumentException.class, () -> base().para("Carla <carla@gmail.com>").build());
    }

    @Test
    void conAdjuntoEsMultipart() {
        byte[] escaleta = "<html><body>Escaleta del Noticiero Central</body></html>".getBytes(StandardCharsets.UTF_8);
        String correo = new String(base().adjunto("escaleta 2026-10-13 (Central).html", "text/html; charset=UTF-8", escaleta)
                .build().aBytes(RELOJ), StandardCharsets.US_ASCII);
        String tipo = cabeceras(correo).get("Content-Type");
        assertTrue(tipo.startsWith("multipart/mixed; boundary=\""), tipo);
        String limite = tipo.substring(tipo.indexOf('"') + 1, tipo.lastIndexOf('"'));
        String[] partes = cuerpo(correo).split("--" + Pattern.quote(limite));
        // [preámbulo vacío, html, adjunto, "--\r\n"]
        assertEquals(4, partes.length);
        assertTrue(partes[1].contains("Content-Type: text/html; charset=UTF-8"));
        assertTrue(partes[2].contains("Content-Disposition: attachment; filename=\"escaleta_2026-10-13__Central_.html\""));
        String base64Adjunto = partes[2].substring(partes[2].indexOf("\r\n\r\n") + 4).trim();
        assertEquals(new String(escaleta, StandardCharsets.UTF_8),
                new String(Base64.getMimeDecoder().decode(base64Adjunto), StandardCharsets.UTF_8));
        assertTrue(partes[3].startsWith("--"));
    }
}
