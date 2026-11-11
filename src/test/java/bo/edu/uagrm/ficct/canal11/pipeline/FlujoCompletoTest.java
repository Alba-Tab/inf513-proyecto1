package bo.edu.uagrm.ficct.canal11.pipeline;

import bo.edu.uagrm.ficct.canal11.datos.BaseDatosPrueba;
import bo.edu.uagrm.ficct.canal11.datos.Repositorio;
import bo.edu.uagrm.ficct.canal11.comandos.catalogo.Catalogo;
import bo.edu.uagrm.ficct.canal11.correo.FakePop3Server;
import bo.edu.uagrm.ficct.canal11.correo.FakeSmtpServer;
import bo.edu.uagrm.ficct.canal11.correo.MensajeCorreo;
import bo.edu.uagrm.ficct.canal11.correo.MimeParser;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Clock;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * De punta a punta: un correo entra por el POP3 falso, se procesa contra PostgreSQL y la respuesta
 * sale por el SMTP falso. Es el mismo camino que en el servidor tecnoweb.
 */
@Tag("bd")
@Timeout(30)
class FlujoCompletoTest {

    @Test
    void ayudaYRemitenteNoRegistradoDePuntaAPunta() throws Exception {
        try (FakePop3Server pop = new FakePop3Server(); FakeSmtpServer smtp = new FakeSmtpServer()) {
            pop.agregar(MailVerificationThreadTest.correo("Américo Claros <director@canal11.test>", "AYUDA", ""));
            pop.agregar(MailVerificationThreadTest.correo("intruso@gmail.com", "LISUSU[\"*\"]", ""));

            var config = MailVerificationThreadTest.config(pop.puerto(), smtp.puerto(), 1_048_576);
            SendEmailThread emisor = new SendEmailThread("127.0.0.1", smtp.puerto(), 2_000, "tecnoweb.org.bo", Clock.systemUTC(), 10);
            Pipeline pipeline = Pipeline.completo(config, BaseDatosPrueba.conexion()::obtener, new Repositorio(10),
                    Catalogo.completo(), Clock.systemUTC(), emisor::encolar);
            MailVerificationThread lector = new MailVerificationThread(config, pipeline, () -> { });

            // Este flujo confirma de verdad (no usa la transacción de las pruebas): se borra su bitácora al final
            long ultimaBitacora;
            try (var st = BaseDatosPrueba.conexion().obtener().createStatement();
                 var rs = st.executeQuery("SELECT coalesce(max(id), 0) FROM bitacora_comando")) {
                rs.next();
                ultimaBitacora = rs.getLong(1);
            }
            emisor.start();
            try {
                assertEquals(2, lector.procesarUnaVez());
            } finally {
                emisor.detener(10_000);
                try (var ps = BaseDatosPrueba.conexion().obtener().prepareStatement("DELETE FROM bitacora_comando WHERE id > ?")) {
                    ps.setLong(1, ultimaBitacora);
                    ps.executeUpdate();
                }
            }

            List<FakeSmtpServer.Recibido> recibidos = smtp.recibidos();
            assertEquals(2, recibidos.size());

            MensajeCorreo ayuda = MimeParser.leer(recibidos.get(0).datos());
            assertEquals(List.of("director@canal11.test"), recibidos.get(0).destinatarios());
            assertEquals("grupo14sa@tecnoweb.org.bo", ayuda.remitente());
            assertEquals("Re: AYUDA", ayuda.asunto());
            assertTrue(ayuda.cuerpo().contains("Hola, Américo Claros (Director)"), ayuda.cuerpo());

            MensajeCorreo intruso = MimeParser.leer(recibidos.get(1).datos());
            assertEquals(List.of("intruso@gmail.com"), recibidos.get(1).destinatarios());
            assertTrue(intruso.cuerpo().contains("no tiene permiso para ejecutar LISUSU"), intruso.cuerpo());
            assertEquals(0, pop.mensajesEnBuzon());
        }
    }
}
