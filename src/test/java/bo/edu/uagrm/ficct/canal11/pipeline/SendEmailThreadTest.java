package bo.edu.uagrm.ficct.canal11.pipeline;

import bo.edu.uagrm.ficct.canal11.correo.CorreoSaliente;
import bo.edu.uagrm.ficct.canal11.correo.FakeSmtpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(20)
class SendEmailThreadTest {

    private FakeSmtpServer smtp;

    @BeforeEach
    void iniciar() throws IOException {
        smtp = new FakeSmtpServer();
    }

    @AfterEach
    void cerrar() throws IOException {
        smtp.close();
    }

    private SendEmailThread emisor(int puerto) {
        return new SendEmailThread("127.0.0.1", puerto, 2_000, "tecnoweb.org.bo", Clock.systemUTC(), 10);
    }

    private static CorreoSaliente respuesta(String para, String asunto) {
        return CorreoSaliente.builder().de("grupo14sa@tecnoweb.org.bo", "Sistema Canal 11").para(para)
                .asunto(asunto).html("<p>ok</p>").build();
    }

    private void esperarRecibidos(int cantidad) throws InterruptedException {
        for (int i = 0; i < 100 && smtp.recibidos().size() < cantidad; i++) {
            Thread.sleep(50);
        }
    }

    @Test
    void enviaLoQueSeEncola() throws InterruptedException {
        SendEmailThread e = emisor(smtp.puerto());
        e.start();
        e.encolar(respuesta("carla.rojas@gmail.com", "Re: AYUDA"));
        esperarRecibidos(1);
        e.detener(5_000);
        assertEquals(1, smtp.recibidos().size());
        assertEquals("carla.rojas@gmail.com", smtp.recibidos().get(0).destinatarios().get(0));
        assertEquals(1, e.enviados());
        assertFalse(e.isAlive());
    }

    @Test
    void unRechazoPermanenteNoSeReintenta() {
        smtp.rechazarReenvio(true);
        SendEmailThread e = emisor(smtp.puerto());
        assertFalse(e.entregar(respuesta("carla.rojas@gmail.com", "Re: AYUDA")));
        assertEquals(1, smtp.comandos().stream().filter(c -> c.startsWith("MAIL FROM")).count());
        assertEquals(1, e.descartados());
        // una entrega local sí pasa
        assertTrue(e.entregar(respuesta("grupo14sa@tecnoweb.org.bo", "Re: AYUDA")));
    }

    @Test
    void conElServidorCaidoReintentaYDescarta() {
        SendEmailThread e = emisor(1);
        long inicio = System.nanoTime();
        assertFalse(e.entregar(respuesta("carla.rojas@gmail.com", "Re: AYUDA")));
        assertEquals(1, e.descartados());
        assertTrue((System.nanoTime() - inicio) / 1_000_000 < 5_000);
    }

    @Test
    void alDetenerseEnviaLoPendiente() throws InterruptedException {
        SendEmailThread e = emisor(smtp.puerto());
        for (int i = 1; i <= 3; i++) {
            e.encolar(respuesta("carla.rojas@gmail.com", "Re: LISCOB " + i));
        }
        e.start();
        e.detener(10_000);
        assertEquals(3, smtp.recibidos().size());
        assertEquals(0, e.pendientes());
    }
}
