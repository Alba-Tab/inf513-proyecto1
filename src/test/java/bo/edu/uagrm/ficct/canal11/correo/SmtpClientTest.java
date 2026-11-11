package bo.edu.uagrm.ficct.canal11.correo;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(10)
class SmtpClientTest {

    private static final int TIMEOUT_MS = 2_000;

    private FakeSmtpServer servidor;

    @BeforeEach
    void iniciar() throws IOException {
        servidor = new FakeSmtpServer();
    }

    @AfterEach
    void cerrar() throws IOException {
        servidor.close();
    }

    private SmtpClient conectado() throws IOException {
        SmtpClient smtp = SmtpClient.conectar("127.0.0.1", servidor.puerto(), TIMEOUT_MS);
        smtp.ehlo("tecnoweb.org.bo");
        return smtp;
    }

    private static CorreoSaliente respuesta(String para) {
        return CorreoSaliente.builder()
                .de("grupo14sa@tecnoweb.org.bo", "Sistema Canal 11")
                .para(para)
                .asunto("Re: AYUDA — Éxito")
                .html(CorreoSalienteTest.HTML)
                .build();
    }

    @Test
    void leeElSaludoDeVariasLineas() throws IOException {
        try (SmtpClient smtp = SmtpClient.conectar("127.0.0.1", servidor.puerto(), TIMEOUT_MS)) {
            assertEquals(220, smtp.saludo().codigo());
            assertEquals(2, smtp.saludo().lineas().size());
        }
    }

    @Test
    void guardaLasCapacidadesDeEhlo() throws IOException {
        try (SmtpClient smtp = conectado()) {
            assertTrue(smtp.admite("8BITMIME"));
            assertTrue(smtp.admite("AUTH"));
            assertTrue(smtp.admite("AUTH CRAM-MD5"));
            assertTrue(smtp.admite("size"));
            assertFalse(smtp.admite("STARTTLS"));
        }
    }

    @Test
    void siNoEntiendeEhloUsaHelo() throws IOException {
        servidor.falla(FakeSmtpServer.Falla.SIN_EHLO);
        try (SmtpClient smtp = conectado()) {
            assertTrue(smtp.capacidades().isEmpty());
        }
        assertEquals(List.of("EHLO tecnoweb.org.bo", "HELO tecnoweb.org.bo"), servidor.comandos());
    }

    @Test
    void enviaUnaRespuestaCompleta() throws IOException {
        SmtpClient smtp = conectado();
        SmtpClient.Respuesta r = smtp.enviar(respuesta("carla.rojas@gmail.com"), CorreoSalienteTest.RELOJ);
        smtp.quit();
        assertEquals(250, r.codigo());

        List<FakeSmtpServer.Recibido> recibidos = servidor.recibidos();
        assertEquals(1, recibidos.size());
        FakeSmtpServer.Recibido c = recibidos.get(0);
        assertEquals("grupo14sa@tecnoweb.org.bo", c.remitente());
        assertEquals(List.of("carla.rojas@gmail.com"), c.destinatarios());
        assertEquals("Re: AYUDA — Éxito", CorreoSalienteTest.decodificar(CorreoSalienteTest.cabeceras(c.texto()).get("Subject")));
        assertEquals(CorreoSalienteTest.HTML, new String(
                Base64.getMimeDecoder().decode(CorreoSalienteTest.cuerpo(c.texto())), StandardCharsets.UTF_8));
        assertEquals(List.of("EHLO tecnoweb.org.bo", "MAIL FROM:<grupo14sa@tecnoweb.org.bo>",
                "RCPT TO:<carla.rojas@gmail.com>", "DATA", "QUIT"), servidor.comandos());
    }

    @Test
    void elReenvioDenegadoEsUnErrorPermanente() throws IOException {
        servidor.rechazarReenvio(true);
        try (SmtpClient smtp = conectado()) {
            SmtpException e = assertThrows(SmtpException.class,
                    () -> smtp.enviar(respuesta("carla.rojas@gmail.com"), CorreoSalienteTest.RELOJ));
            assertEquals(550, e.codigo());
            assertTrue(e.esPermanente());
            assertTrue(e.reenvioDenegado());
            // la sesión sigue usable: RSET y una entrega local
            smtp.rset();
            assertEquals(250, smtp.enviar(respuesta("grupo14sa@tecnoweb.org.bo"), CorreoSalienteTest.RELOJ).codigo());
        }
        assertEquals(1, servidor.recibidos().size());
    }

    @Test
    void duplicaElPuntoInicialYElServidorRecibeElTextoIntacto() throws IOException {
        byte[] crudo = "Subject: puntos\r\n\r\n.Empieza con punto\r\n.\r\n..dos\r\nfin\r\n".getBytes(StandardCharsets.US_ASCII);
        try (SmtpClient smtp = conectado()) {
            smtp.mailFrom("grupo14sa@tecnoweb.org.bo");
            smtp.rcptTo("grupo14sa@tecnoweb.org.bo");
            smtp.data(crudo);
        }
        assertArrayEquals(crudo, servidor.recibidos().get(0).datos());
    }

    @Test
    void normalizaLosFinalesDeLinea() {
        byte[] mezcla = "a\nb\r\nc\rd".getBytes(StandardCharsets.US_ASCII);
        assertArrayEquals("a\r\nb\r\nc\r\nd\r\n".getBytes(StandardCharsets.US_ASCII), SmtpClient.conPuntosDuplicados(mezcla));
        assertArrayEquals("..x\r\n".getBytes(StandardCharsets.US_ASCII),
                SmtpClient.conPuntosDuplicados(".x".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    void todasLasLineasTerminanEnCrlfYSonCortas() throws IOException {
        String tabla = "<table>" + "<tr><td>Fila con tildes: cobertura en el Paraninfo</td></tr>".repeat(5_000) + "</table>";
        try (SmtpClient smtp = conectado()) {
            smtp.enviar(CorreoSaliente.builder().de("grupo14sa@tecnoweb.org.bo").para("carla.rojas@gmail.com")
                    .asunto("Re: LISCOB").html(tabla).build(), CorreoSalienteTest.RELOJ);
        }
        assertEquals(0, servidor.lineasSinCrlf());
        assertTrue(servidor.lineaMasLarga() <= 998, "línea de " + servidor.lineaMasLarga());
    }

    @Test
    void unServidorQueNoRespondeSeCortaPorTiempo() throws IOException {
        servidor.falla(FakeSmtpServer.Falla.SILENCIO_TRAS_SALUDO);
        try (SmtpClient smtp = SmtpClient.conectar("127.0.0.1", servidor.puerto(), 500)) {
            assertThrows(SocketTimeoutException.class, () -> smtp.ehlo("tecnoweb.org.bo"));
        }
    }

    @Test
    void sinServidorFallaAlConectar() {
        assertThrows(IOException.class, () -> SmtpClient.conectar("127.0.0.1", 1, 500));
    }
}
