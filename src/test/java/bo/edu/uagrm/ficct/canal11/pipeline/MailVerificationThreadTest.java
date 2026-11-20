package bo.edu.uagrm.ficct.canal11.pipeline;

import bo.edu.uagrm.ficct.canal11.config.Config;
import bo.edu.uagrm.ficct.canal11.correo.CorreoSaliente;
import bo.edu.uagrm.ficct.canal11.correo.FakePop3Server;
import bo.edu.uagrm.ficct.canal11.pipeline.etapas.Decodificar;
import bo.edu.uagrm.ficct.canal11.pipeline.etapas.Filtrar;
import bo.edu.uagrm.ficct.canal11.pipeline.etapas.Responder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(20)
class MailVerificationThreadTest {

    static final String SISTEMA = "grupo14sa@tecnoweb.org.bo";

    private FakePop3Server pop;
    private final List<CorreoSaliente> enviados = new CopyOnWriteArrayList<>();
    private final List<SolicitudCorreo> procesadas = new CopyOnWriteArrayList<>();

    /** Lo que llega a la etapa de los comandos, ya decodificado. */
    record SolicitudCorreo(String remitente, String asunto, String cuerpo) {
    }

    /** Un sustituto de las etapas del comando: recibe la solicitud y devuelve la respuesta. */
    @FunctionalInterface
    interface ProcesadorCorreo {
        RespuestaCorreo procesar(SolicitudCorreo s);
    }

    /** La tubería real de los extremos (Decodificar, Filtrar, Responder) con un procesador falso en el medio. */
    private Pipeline pipeline(ProcesadorCorreo procesador, long maxBytes) {
        Etapa comando = p -> {
            RespuestaCorreo r = procesador.procesar(new SolicitudCorreo(p.remitenteCorreo(), p.asunto(), p.cuerpo()));
            p.responder(true, r.html(), "ok", r.adjuntos());
        };
        return new Pipeline(List.of(new Decodificar(), new Filtrar(SISTEMA, maxBytes), comando, new Responder(SISTEMA, enviados::add)));
    }

    /** Procesador falso: responde con el asunto y anota lo que recibió. */
    private final ProcesadorCorreo eco = s -> {
        procesadas.add(s);
        return new RespuestaCorreo("Re: " + s.asunto(), "<p>recibido de " + s.remitente() + "</p>", List.of());
    };

    @BeforeEach
    void iniciar() throws IOException {
        pop = new FakePop3Server();
    }

    @AfterEach
    void cerrar() throws IOException {
        pop.close();
    }

    static Config config(int puertoPop, int puertoSmtp, int maxBytes) {
        Properties p = new Properties();
        p.setProperty("pop3.host", "127.0.0.1");
        p.setProperty("pop3.puerto", String.valueOf(puertoPop));
        p.setProperty("pop3.usuario", FakePop3Server.USUARIO);
        p.setProperty("pop3.contrasena", FakePop3Server.CONTRASENA);
        p.setProperty("smtp.host", "127.0.0.1");
        p.setProperty("smtp.puerto", String.valueOf(puertoSmtp));
        p.setProperty("correo.direccion", SISTEMA);
        p.setProperty("bd.url", "jdbc:postgresql://127.0.0.1:1/no_se_usa");
        p.setProperty("bd.usuario", "x");
        p.setProperty("bd.contrasena", "x");
        p.setProperty("app.intervalo_segundos", "1");
        p.setProperty("app.timeout_segundos", "2");
        p.setProperty("app.max_bytes_correo", String.valueOf(maxBytes));
        return Config.desde(p);
    }

    static String correo(String de, String asunto, String cuerpo) {
        return "From: " + de + "\r\nTo: " + SISTEMA + "\r\nSubject: " + asunto
                + "\r\nMessage-ID: <" + Math.abs(asunto.hashCode()) + "@mail.gmail.com>\r\nMIME-Version: 1.0"
                + "\r\nContent-Type: text/plain; charset=UTF-8\r\n\r\n" + cuerpo + "\r\n";
    }

    private MailVerificationThread lector(ProcesadorCorreo procesador, MailVerificationThread.TareaPrevia tarea) {
        return new MailVerificationThread(config(pop.puerto(), 1, 1_048_576), pipeline(procesador, 1_048_576), tarea);
    }

    private MailVerificationThread lector() {
        return lector(eco, () -> { });
    }

    private static long contar(List<String> comandos, String comando) {
        return comandos.stream().filter(comando::equals).count();
    }

    @Test
    void procesaUnCorreoYEncolaLaRespuesta() {
        pop.agregar(correo("Carla Rojas <Carla.Rojas@gmail.com>", "LISCOB[\"2026-10-12\"]", "hola"));
        assertEquals(1, lector().procesarUnaVez());

        assertEquals(1, procesadas.size());
        assertEquals(new SolicitudCorreo("carla.rojas@gmail.com", "LISCOB[\"2026-10-12\"]", "hola"), procesadas.get(0));
        assertEquals(1, enviados.size());
        CorreoSaliente r = enviados.get(0);
        assertEquals("carla.rojas@gmail.com", r.destinatario());
        assertEquals(SISTEMA, r.remitente());
        assertEquals("Re: LISCOB[\"2026-10-12\"]", r.asunto());
        String bytes = new String(r.aBytes(Clock.systemUTC()), StandardCharsets.US_ASCII);
        assertTrue(bytes.contains("In-Reply-To: <" + Math.abs("LISCOB[\"2026-10-12\"]".hashCode()) + "@mail.gmail.com>"));
        assertEquals(0, pop.mensajesEnBuzon());
    }

    @Test
    void losAdjuntosDeLaRespuestaVanEnElCorreo() {
        pop.agregar(correo("carla.rojas@gmail.com", "EXPESC[\"1\"]", ""));
        ProcesadorCorreo conAdjunto = s -> new RespuestaCorreo("Re: " + s.asunto(), "<p>adjunta</p>",
                List.of(new bo.edu.uagrm.ficct.canal11.comandos.Adjunto("escaleta.html", "text/html; charset=UTF-8",
                        "<html>escaleta</html>".getBytes(StandardCharsets.UTF_8))));
        lector(conAdjunto, () -> { }).procesarUnaVez();
        String bytes = new String(enviados.get(0).aBytes(Clock.systemUTC()), StandardCharsets.US_ASCII);
        assertTrue(bytes.contains("multipart/mixed"), bytes);
        assertTrue(bytes.contains("filename=\"escaleta.html\""), bytes);
    }

    @Test
    void procesaVariosEnOrdenUnoPorSesion() {
        for (String a : new String[]{"LISCOB[\"1\"]", "LISCOB[\"2\"]", "LISCOB[\"3\"]"}) {
            pop.agregar(correo("carla.rojas@gmail.com", a, ""));
        }
        assertEquals(3, lector().procesarUnaVez());
        assertEquals(List.of("LISCOB[\"1\"]", "LISCOB[\"2\"]", "LISCOB[\"3\"]"),
                procesadas.stream().map(SolicitudCorreo::asunto).toList());
        List<String> comandos = pop.comandos();
        assertEquals(3, contar(comandos, "RETR 1"));
        assertEquals(3, contar(comandos, "DELE 1"));
        assertEquals(4, contar(comandos, "QUIT"), "tres sesiones con correo y una que encuentra el buzón vacío");
        assertEquals(0, pop.mensajesEnBuzon());
    }

    @Test
    void elGuionLlegaSinFirmaNiCita() {
        pop.agregar(correo("carla.rojas@gmail.com", "Re: GUINOT[\"1\"]",
                "Nuevo guion.\r\n\r\n-- \r\nCarla\r\n\r\nEl lun, 12 oct 2026 a las 10:00, Sistema escribió:\r\n> viejo"));
        lector().procesarUnaVez();
        assertEquals("Nuevo guion.", procesadas.get(0).cuerpo());
    }

    @Test
    void noRespondeAUnRebote() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/eml/rebote_mailer_daemon.eml")) {
            pop.agregar(in.readAllBytes());
        }
        assertEquals(1, lector().procesarUnaVez());
        assertTrue(procesadas.isEmpty());
        assertTrue(enviados.isEmpty());
        assertEquals(0, pop.mensajesEnBuzon(), "el rebote igual se borra del buzón");
    }

    @Test
    void noRespondeAlPropioSistema() {
        pop.agregar(correo("Sistema Canal 11 <" + SISTEMA.toUpperCase() + ">", "Re: AYUDA", ""));
        lector().procesarUnaVez();
        assertTrue(procesadas.isEmpty());
        assertTrue(enviados.isEmpty());
    }

    @Test
    void unCorreoDemasiadoGrandeNoSeDescargaYSeResponde() {
        pop.agregar(correo("carla.rojas@gmail.com", "INSNOT[\"X\"]", "adjunto ".repeat(2_000)));
        MailVerificationThread lector = new MailVerificationThread(config(pop.puerto(), 1, 2_000), pipeline(eco, 2_000), () -> { });
        assertEquals(1, lector.procesarUnaVez());

        List<String> comandos = pop.comandos();
        assertTrue(comandos.contains("TOP 1 0"));
        assertFalse(comandos.contains("RETR 1"));
        assertTrue(procesadas.isEmpty(), "no se procesa el comando");
        assertEquals(1, enviados.size());
        assertEquals("carla.rojas@gmail.com", enviados.get(0).destinatario());
        String html = new String(java.util.Base64.getMimeDecoder().decode(
                cuerpoDe(enviados.get(0))), StandardCharsets.UTF_8);
        assertTrue(html.contains("no acepta adjuntos"), html);
        assertEquals(0, pop.mensajesEnBuzon());
    }

    private static String cuerpoDe(CorreoSaliente c) {
        String t = new String(c.aBytes(Clock.systemUTC()), StandardCharsets.US_ASCII);
        return t.substring(t.indexOf("\r\n\r\n") + 4);
    }

    @Test
    void siElProcesadorFallaRespondeUnErrorYSigueConElSiguiente() {
        pop.agregar(correo("carla.rojas@gmail.com", "FALLAR", ""));
        pop.agregar(correo("carla.rojas@gmail.com", "LISCOB", ""));
        ProcesadorCorreo fallaElPrimero = s -> {
            if (s.asunto().equals("FALLAR")) {
                throw new IllegalStateException("error simulado");
            }
            return eco.procesar(s);
        };
        assertEquals(2, lector(fallaElPrimero, () -> { }).procesarUnaVez());
        assertEquals(2, enviados.size());
        String html = new String(java.util.Base64.getMimeDecoder().decode(cuerpoDe(enviados.get(0))), StandardCharsets.UTF_8);
        assertTrue(html.contains(Pipeline.ERROR_GENERAL), html);
        assertEquals("Re: LISCOB", enviados.get(1).asunto());
    }

    @Test
    void siElQuitNoSeConfirmaNoProcesaYElCorreoQuedaEnElBuzon() {
        pop.agregar(correo("carla.rojas@gmail.com", "INSCOB[\"x\"]", ""));
        pop.falla(FakePop3Server.Falla.QUIT_SIN_CONFIRMAR);
        assertEquals(0, lector().procesarUnaVez());
        assertTrue(procesadas.isEmpty(), "sin QUIT confirmado no se ejecuta el comando");
        assertEquals(1, pop.mensajesEnBuzon());
    }

    @Test
    void conElBuzonEnUsoNoProcesaNiLanza() {
        pop.agregar(correo("carla.rojas@gmail.com", "LISCOB", ""));
        pop.falla(FakePop3Server.Falla.BUZON_EN_USO);
        assertEquals(0, lector().procesarUnaVez());
        assertEquals(1, pop.mensajesEnBuzon());
    }

    @Test
    void conElServidorCaidoNoLanza() {
        MailVerificationThread lector = new MailVerificationThread(config(1, 1, 1_048_576), pipeline(eco, 1_048_576), () -> { });
        assertEquals(0, lector.procesarUnaVez());
    }

    @Test
    void conLaBaseCaidaLosCorreosEsperanEnElBuzon() {
        pop.agregar(correo("carla.rojas@gmail.com", "LISCOB", ""));
        MailVerificationThread lector = lector(eco, () -> {
            throw new java.sql.SQLException("Connection refused");
        });
        assertEquals(0, lector.procesarUnaVez());
        assertEquals(1, pop.mensajesEnBuzon(), "el pedido no se borra");
        assertTrue(enviados.isEmpty());
    }

    @Test
    void unCorreoQueNuncaSePuedeLeerSeBorraALaTerceraFalla() {
        pop.agregar(correo("carla.rojas@gmail.com", "LISCOB", "no se puede leer"));
        pop.falla(FakePop3Server.Falla.CORTE_EN_RETR);
        MailVerificationThread lector = lector();
        for (int i = 0; i < MailVerificationThread.MAX_FALLAS; i++) {
            assertEquals(0, lector.procesarUnaVez());
            assertEquals(1, pop.mensajesEnBuzon(), "todavía se reintenta");
        }
        lector.procesarUnaVez();
        assertEquals(0, pop.mensajesEnBuzon(), "a la cuarta vuelta se borra y deja pasar a los demás");
        assertTrue(procesadas.isEmpty());

        pop.falla(FakePop3Server.Falla.NINGUNA);
        pop.agregar(correo("carla.rojas@gmail.com", "LISCOB", "el siguiente"));
        assertEquals(1, lector.procesarUnaVez());
    }

    @Test
    void laTareaPreviaCorreCadaVueltaYSiFallaSigue() {
        AtomicInteger veces = new AtomicInteger();
        pop.agregar(correo("carla.rojas@gmail.com", "LISCOB", ""));
        MailVerificationThread lector = lector(eco, () -> {
            veces.incrementAndGet();
            throw new IllegalStateException("la base no responde");
        });
        assertEquals(1, lector.procesarUnaVez());
        lector.procesarUnaVez();
        assertEquals(2, veces.get());
        assertEquals(1, enviados.size());
    }

    @Test
    void laContrasenaDeInsusuNoApareceEnElLog() {
        pop.agregar(correo("director@canal11.test",
                "INSUSU[\"Carla\",\"Rojas\",\"3100003\",\"carla@gmail.com\",\"71234580\",\"clave-secreta-99\",\"3\"]", ""));
        Logger log = Logger.getLogger("bo.edu.uagrm.ficct.canal11"); // las etapas escriben con su propio logger
        List<String> registros = new ArrayList<>();
        Handler captura = new Handler() {
            @Override
            public void publish(LogRecord r) {
                registros.add(r.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        log.addHandler(captura);
        log.setLevel(Level.ALL);
        try {
            lector().procesarUnaVez();
        } finally {
            log.removeHandler(captura);
            log.setLevel(null);
        }
        assertTrue(registros.stream().anyMatch(r -> r.contains("INSUSU[") && r.contains("******")), registros.toString());
        assertTrue(registros.stream().noneMatch(r -> r.contains("clave-secreta-99")), registros.toString());
    }

    @Test
    void elHiloSeDetieneRapido() throws InterruptedException {
        MailVerificationThread lector = lector();
        lector.start();
        Thread.sleep(300);
        long inicio = System.nanoTime();
        lector.detener(5_000);
        assertFalse(lector.isAlive());
        assertTrue((System.nanoTime() - inicio) / 1_000_000 < 3_000);
    }
}
