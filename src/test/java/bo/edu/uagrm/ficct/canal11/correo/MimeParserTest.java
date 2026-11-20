package bo.edu.uagrm.ficct.canal11.correo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Correos reales de cada cliente (src/test/resources/eml) y correos rotos. */
class MimeParserTest {

    private static MensajeCorreo eml(String nombre) throws IOException {
        try (InputStream in = MimeParserTest.class.getResourceAsStream("/eml/" + nombre)) {
            assertNotNull(in, "falta el archivo de prueba " + nombre);
            return MimeParser.leer(in.readAllBytes());
        }
    }

    @Test
    void gmailWebConAsuntoPartidoEnVariasPalabras() throws IOException {
        MensajeCorreo m = eml("gmail_web_inscob.eml");
        assertEquals("carla.rojas@gmail.com", m.remitente());
        assertEquals("Carla Rojas", m.nombreRemitente());
        assertEquals("INSCOB[\"Clásico cruceño\",\"Conferencia previa\",\"deportes\",\"2026-10-12\",\"15:00\",\"16:30\","
                + "\"Estadio Tahuichi\",\"media\",\"Prensa Club Blooming\",\"Conferencia de técnicos\"]", m.asunto());
        assertEquals("Cobertura del clásico: llegar 30 minutos antes.", m.cuerpo().strip());
        assertEquals("<CAH7x9Kq3Zt5mPq8L2wR1nQv4sX6yB0cDe+F@mail.gmail.com>", m.messageId());
        assertFalse(m.esAutomatico());
    }

    @Test
    void gmailAndroidConAsuntoYCuerpoEnBase64() throws IOException {
        MensajeCorreo m = eml("gmail_android_insnot.eml");
        assertEquals("marcelo.justiniano@gmail.com", m.remitente());
        assertEquals("Marcelo Justiniano", m.nombreRemitente());
        assertEquals("INSNOT[\"PARO-TRANSPORTE\",\"Transportistas bloquean el 4to anillo\",\"vo_sot\",\"2026-10-12\",\"2\",\"\"]", m.asunto());
        assertEquals("Desde primeras horas de hoy, el transporte libre bloquea el cuarto anillo.\n"
                + "Los choferes exigen diésel y denuncian filas de dos días.", m.cuerpo().strip());
    }

    @Test
    void iphoneConComillasTipograficasYFormatoFlowed() throws IOException {
        MensajeCorreo m = eml("iphone_insaus.eml");
        assertEquals("jefe.prensa@canal11.test", m.remitente());
        // Las comillas “ ” las normaliza CommandParser; aquí llegan tal cual
        assertEquals("INSAUS[“6”,“permiso”,“2026-10-14 09:00”,“2026-10-14 11:00”,“Trámite personal”]", m.asunto());
        assertEquals("Permiso pedido por Rubén para un trámite que el celular partió en dos líneas al escribir.\n\n"
                + "Enviado desde mi iPhone", m.cuerpo().strip());
        assertEquals("<5E6F7A8B-1C2D-4E5F-9A0B-ABCDEF123456@canal11.test>", m.messageId());
    }

    @Test
    void outlookConNombreEntreComillasYWindows1252() throws IOException {
        MensajeCorreo m = eml("outlook_aprnot.eml");
        assertEquals("jefe.prensa@canal11.test", m.remitente());
        assertEquals("Arteaga, Roberto", m.nombreRemitente());
        assertEquals("APRNOT[\"3\"] aprobación", m.asunto());
        assertEquals("Nota revisada y aprobada. “Buen trabajo”, señores.", m.cuerpo().strip());
    }

    @Test
    void soloHtmlSeConvierteATexto() throws IOException {
        MensajeCorreo m = eml("solo_html.eml");
        assertEquals("El COED reporta 14 focos de calor.\nBomberos y voluntarios trabajan & piden «apoyo».\nFin.", m.cuerpo());
    }

    @Test
    void unReboteEsAutomatico() throws IOException {
        MensajeCorreo m = eml("rebote_mailer_daemon.eml");
        assertEquals("mailer-daemon@web.tecnoweb.org.bo", m.remitente());
        assertTrue(m.esAutomatico());
    }

    @Test
    void unaRespuestaSinFirmaNiCita() throws IOException {
        MensajeCorreo m = eml("respuesta_con_cita.eml");
        assertEquals("Re: GUINOT[\"1\"]", m.asunto());
        assertTrue(m.cuerpo().contains("> Éxito: guion registrado correctamente"));
        assertEquals("La Gobernación presentó hoy el presupuesto 2027.\nEl monto alcanza los 4.200 millones de bolivianos.",
                m.cuerpoSinCitas());
    }

    @Test
    void conAdjuntoTomaElTextoYNoElAdjunto() throws IOException {
        MensajeCorreo m = eml("gmail_con_adjunto.eml");
        assertEquals("Material listo para emisión.", m.cuerpo().strip());
    }

    @Test
    void unCorreoSinMimeConSaltosLfYRemitenteConComentario() throws IOException {
        MensajeCorreo m = eml("plano_sin_mime.eml");
        assertEquals("carla.rojas@gmail.com", m.remitente());
        assertEquals("Carla Rojas", m.nombreRemitente());
        assertEquals("LISCOB", m.asunto());
        assertEquals("Sin formato MIME.", m.cuerpo().strip());
    }

    @Test
    void unAsuntoConUtf8SinCodificar() throws IOException {
        MensajeCorreo m = eml("asunto_utf8_crudo.eml");
        assertEquals("INCREC[\"MOV-01\",\"Falla de frenos en la subida al Urubó\"]", m.asunto());
        assertEquals("Reporte del camarógrafo.", m.cuerpo().strip());
    }

    @Test
    void leeLaRespuestaQueArmaElPropioSistema() {
        String asunto = "Re: INSCOB — Éxito: cobertura «Clásico cruceño» registrada";
        byte[] propio = CorreoSaliente.builder()
                .de("grupo14sa@tecnoweb.org.bo", "Sistema Canal 11").para("carla.rojas@gmail.com")
                .asunto(asunto).html("<p>Éxito: cobertura registrada correctamente.</p>")
                .enRespuestaA("<CAH7x9@mail.gmail.com>").build().aBytes(CorreoSalienteTest.RELOJ);
        MensajeCorreo m = MimeParser.leer(propio);
        assertEquals("grupo14sa@tecnoweb.org.bo", m.remitente());
        assertEquals("Sistema Canal 11", m.nombreRemitente());
        assertEquals(asunto, m.asunto());
        assertEquals("Éxito: cobertura registrada correctamente.", m.cuerpo());
        // si una respuesta del sistema volviera al buzón, no se le responde
        assertTrue(m.esAutomatico());
    }

    // ---------------------------------------------------------------- correos rotos: nunca lanza

    @Test
    void unCorreoVacioONulo() {
        for (byte[] crudo : new byte[][]{null, new byte[0]}) {
            MensajeCorreo m = MimeParser.leer(crudo);
            assertEquals("", m.remitente());
            assertEquals("", m.asunto());
            assertEquals("", m.cuerpo());
            assertTrue(m.esAutomatico(), "sin remitente no se responde");
        }
    }

    @Test
    void bytesAlAzar() {
        Random azar = new Random(42);
        for (int i = 0; i < 200; i++) {
            byte[] basura = new byte[azar.nextInt(4_000)];
            azar.nextBytes(basura);
            assertNotNull(MimeParser.leer(basura));
        }
    }

    @Test
    void multipartSinLimiteOConBase64Roto() {
        String sinLimite = "From: a@b.com\r\nSubject: x\r\nContent-Type: multipart/mixed\r\n\r\nhola\r\n";
        assertEquals("", MimeParser.leer(sinLimite.getBytes(StandardCharsets.US_ASCII)).cuerpo());
        String base64Roto = "From: a@b.com\r\nContent-Type: text/plain\r\nContent-Transfer-Encoding: base64\r\n\r\naG9s*YQ=x=\r\n";
        assertNotNull(MimeParser.leer(base64Roto.getBytes(StandardCharsets.US_ASCII)).cuerpo());
    }

    @Test
    void charsetDesconocidoUsaUtf8() {
        String crudo = "From: a@b.com\r\nContent-Type: text/plain; charset=x-inventado\r\n\r\nCamarógrafo\r\n";
        assertEquals("Camarógrafo", MimeParser.leer(crudo.getBytes(StandardCharsets.UTF_8)).cuerpo().strip());
    }

    @Test
    @Timeout(5)
    void unaBombaDePartesAnidadasNoCuelgaAlLector() {
        StringBuilder sb = new StringBuilder("From: a@b.com\r\nContent-Type: multipart/mixed; boundary=\"b0\"\r\n\r\n");
        for (int i = 0; i < 200; i++) {
            sb.append("--b").append(i).append("\r\nContent-Type: multipart/mixed; boundary=\"b").append(i + 1).append("\"\r\n\r\n");
        }
        sb.append("--b200\r\nContent-Type: text/plain\r\n\r\nmuy adentro\r\n");
        MensajeCorreo m = MimeParser.leer(sb.toString().getBytes(StandardCharsets.US_ASCII));
        assertEquals("", m.cuerpo());
        assertEquals("a@b.com", m.remitente());
    }

    @Test
    @Timeout(2)
    void unHtmlHostilNoFrenaAlLector() {
        // Antes: 40 000 «<» sin cerrar tardaban más de un segundo y 1 MB, minutos (tiempo cuadrático)
        String hostil = "<".repeat(900_000) + " ".repeat(50_000) + "x" + "<script".repeat(10_000);
        assertNotNull(MimeParser.htmlATexto(hostil));
    }

    @Test
    void htmlATextoQuitaEtiquetasYEstilos() {
        String html = "<html><head><title>t</title></head><body><STYLE>p{}</STYLE><p>Hola &amp; chau</p>"
                + "<div>2 &lt; 3<br/>fin   </div><script>alert(1)</script></body></html>";
        assertEquals("Hola & chau\n2 < 3\nfin", MimeParser.htmlATexto(html));
        assertEquals("a < b", MimeParser.htmlATexto("a < b"));
    }
}
