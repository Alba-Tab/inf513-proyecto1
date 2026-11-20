package bo.edu.uagrm.ficct.canal11.correo;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.EOFException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(10)
class Pop3ClientTest {

    private static final int TIMEOUT_MS = 2_000;

    /** Correo como lo guarda el servidor: cabeceras, línea vacía y cuerpo, con CRLF. */
    private static final String CORREO = String.join("\r\n",
            "From: Carla Rojas <carla.rojas@gmail.com>",
            "To: grupo14sa@tecnoweb.org.bo",
            "Subject: LISCOB[\"2026-10-12\"]",
            "",
            "Primera línea del cuerpo.",
            "",
            "Después de una línea vacía, sigue el cuerpo.") + "\r\n";

    private FakePop3Server servidor;

    @BeforeEach
    void iniciar() throws IOException {
        servidor = new FakePop3Server();
    }

    @AfterEach
    void cerrar() throws IOException {
        servidor.close();
    }

    private Pop3Client conectado() throws IOException {
        Pop3Client pop = Pop3Client.conectar("127.0.0.1", servidor.puerto(), TIMEOUT_MS);
        pop.login(FakePop3Server.USUARIO, FakePop3Server.CONTRASENA);
        return pop;
    }

    @Test
    void leeElSaludoDelServidor() throws IOException {
        try (Pop3Client pop = Pop3Client.conectar("127.0.0.1", servidor.puerto(), TIMEOUT_MS)) {
            assertEquals("+OK Dovecot ready.", pop.saludo());
        }
    }

    @Test
    void cuentaLosMensajesDelBuzon() throws IOException {
        String ayuda = "Subject: AYUDA\r\n\r\n\r\n";
        servidor.agregar(CORREO);
        servidor.agregar(ayuda);
        try (Pop3Client pop = conectado()) {
            Pop3Client.Estado estado = pop.stat();
            assertEquals(2, estado.mensajes());
            assertEquals(CORREO.getBytes(StandardCharsets.UTF_8).length + ayuda.getBytes(StandardCharsets.UTF_8).length,
                    estado.octetos());
            assertEquals(CORREO.getBytes(StandardCharsets.UTF_8).length, pop.list(1));
            assertEquals("uid-2", pop.uidl(2));
        }
    }

    @Test
    void descargaElCorreoCompletoAunqueTengaLineasVacias() throws IOException {
        // El getMultiline que corta en la primera línea vacía se quedaría en las cabeceras
        servidor.agregar(CORREO);
        try (Pop3Client pop = conectado()) {
            assertArrayEquals(CORREO.getBytes(StandardCharsets.UTF_8), pop.retr(1));
        }
    }

    @Test
    void respetaLasLineasQueEmpiezanConPunto() throws IOException {
        String conPuntos = "Subject: puntos\r\n\r\n.Empieza con punto\r\n..dos puntos\r\n.\r\nfin\r\n";
        servidor.agregar(conPuntos);
        try (Pop3Client pop = conectado()) {
            assertArrayEquals(conPuntos.getBytes(StandardCharsets.UTF_8), pop.retr(1));
        }
    }

    @Test
    void conservaLosBytesDeTildesYEnie() throws IOException {
        byte[] utf8 = "Subject: =?UTF-8?Q?Cl=C3=A1sico?=\r\n\r\nGuion: Clásico cruceño, señal en vivo.\r\n"
                .getBytes(StandardCharsets.UTF_8);
        byte[] latin1 = "Subject: prueba\r\n\r\nAñez Égüez\r\n".getBytes(StandardCharsets.ISO_8859_1);
        servidor.agregar(utf8);
        servidor.agregar(latin1);
        try (Pop3Client pop = conectado()) {
            assertArrayEquals(utf8, pop.retr(1));
            assertArrayEquals(latin1, pop.retr(2));
        }
    }

    @Test
    void topDevuelveLasCabecerasYPocasLineas() throws IOException {
        servidor.agregar(CORREO);
        try (Pop3Client pop = conectado()) {
            String cabeceras = new String(pop.top(1, 0), StandardCharsets.UTF_8);
            assertTrue(cabeceras.contains("Subject: LISCOB"));
            assertFalse(cabeceras.contains("Primera línea"));
        }
    }

    @Test
    void deleSeAplicaSoloConQuit() throws IOException {
        servidor.agregar(CORREO);
        Pop3Client pop = conectado();
        pop.retr(1);
        pop.dele(1);
        assertTrue(pop.quit());
        assertEquals(0, servidor.mensajesEnBuzon());
    }

    @Test
    void sinQuitElServidorNoBorraNada() throws IOException {
        servidor.agregar(CORREO);
        try (Pop3Client pop = conectado()) {
            pop.dele(1);
        } // close() sin QUIT, como tras un error
        try (Pop3Client pop = conectado()) {
            assertEquals(1, pop.stat().mensajes());
        }
        assertEquals(1, servidor.mensajesEnBuzon());
    }

    @Test
    void rsetDesmarcaLosMensajes() throws IOException {
        servidor.agregar(CORREO);
        Pop3Client pop = conectado();
        pop.dele(1);
        pop.rset();
        pop.noop();
        assertTrue(pop.quit());
        assertEquals(1, servidor.mensajesEnBuzon());
    }

    @Test
    void credencialesInvalidas() throws IOException {
        try (Pop3Client pop = Pop3Client.conectar("127.0.0.1", servidor.puerto(), TIMEOUT_MS)) {
            Pop3Exception e = assertThrows(Pop3Exception.class, () -> pop.login(FakePop3Server.USUARIO, "otra"));
            assertTrue(e.credencialesInvalidas());
            assertFalse(e.buzonEnUso());
            assertEquals("PASS ********", e.comando());
        }
    }

    @Test
    void buzonEnUsoPorOtraSesion() throws IOException {
        servidor.falla(FakePop3Server.Falla.BUZON_EN_USO);
        try (Pop3Client pop = Pop3Client.conectar("127.0.0.1", servidor.puerto(), TIMEOUT_MS)) {
            Pop3Exception e = assertThrows(Pop3Exception.class,
                    () -> pop.login(FakePop3Server.USUARIO, FakePop3Server.CONTRASENA));
            assertTrue(e.buzonEnUso());
        }
    }

    @Test
    void mensajeQueNoExiste() throws IOException {
        try (Pop3Client pop = conectado()) {
            Pop3Exception e = assertThrows(Pop3Exception.class, () -> pop.retr(5));
            assertEquals("RETR 5", e.comando());
            assertTrue(e.respuesta().startsWith("-ERR"));
        }
    }

    @Test
    void unServidorQueNoRespondeSeCortaPorTiempo() throws IOException {
        servidor.falla(FakePop3Server.Falla.SILENCIO_TRAS_SALUDO);
        try (Pop3Client pop = Pop3Client.conectar("127.0.0.1", servidor.puerto(), 500)) {
            long inicio = System.nanoTime();
            assertThrows(SocketTimeoutException.class, () -> pop.login(FakePop3Server.USUARIO, FakePop3Server.CONTRASENA));
            assertTrue((System.nanoTime() - inicio) / 1_000_000 < 3_000);
        }
    }

    @Test
    void unCorteAMitadDelRetrEsUnError() throws IOException {
        servidor.agregar(CORREO);
        servidor.falla(FakePop3Server.Falla.CORTE_EN_RETR);
        try (Pop3Client pop = conectado()) {
            assertThrows(EOFException.class, () -> pop.retr(1));
        }
        assertEquals(1, servidor.mensajesEnBuzon());
    }

    @Test
    void sinServidorFallaAlConectar() {
        assertThrows(IOException.class, () -> Pop3Client.conectar("127.0.0.1", 1, 500));
    }

    @Test
    void todosLosComandosTerminanEnCrlf() throws IOException {
        servidor.agregar(CORREO);
        Pop3Client pop = conectado();
        pop.stat();
        pop.list(1);
        pop.retr(1);
        pop.dele(1);
        pop.quit();
        assertEquals(0, servidor.lineasSinCrlf());
        assertEquals(List.of("USER grupo14sa", "PASS " + FakePop3Server.CONTRASENA, "STAT", "LIST 1", "RETR 1", "DELE 1", "QUIT"),
                servidor.comandos());
    }

    @Test
    void laContrasenaNoApareceEnElLog() throws IOException {
        Logger log = Logger.getLogger(Pop3Client.class.getName());
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
        Level anterior = log.getLevel();
        log.setLevel(Level.ALL);
        log.addHandler(captura);
        try (Pop3Client pop = conectado()) {
            pop.stat();
        } finally {
            log.removeHandler(captura);
            log.setLevel(anterior);
        }
        assertTrue(registros.contains("C: PASS ********"), registros.toString());
        assertTrue(registros.stream().noneMatch(r -> r.contains(FakePop3Server.CONTRASENA)), registros.toString());
    }
}
