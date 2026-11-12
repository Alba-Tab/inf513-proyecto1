package bo.edu.uagrm.ficct.canal11;

import bo.edu.uagrm.ficct.canal11.config.Config;
import bo.edu.uagrm.ficct.canal11.correo.FakePop3Server;
import bo.edu.uagrm.ficct.canal11.correo.FakeSmtpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(30)
class DiagnosticoTest {

    private FakePop3Server pop;
    private FakeSmtpServer smtp;
    private final ByteArrayOutputStream salida = new ByteArrayOutputStream();

    @BeforeEach
    void iniciar() throws IOException {
        pop = new FakePop3Server();
        smtp = new FakeSmtpServer();
    }

    @AfterEach
    void cerrar() throws IOException {
        pop.close();
        smtp.close();
    }

    private Config config(String bdUrl, String bdUsuario, String bdContrasena, String contrasenaPop) {
        Properties p = new Properties();
        p.setProperty("pop3.host", "127.0.0.1");
        p.setProperty("pop3.puerto", String.valueOf(pop.puerto()));
        p.setProperty("pop3.usuario", FakePop3Server.USUARIO);
        p.setProperty("pop3.contrasena", contrasenaPop);
        p.setProperty("smtp.host", "127.0.0.1");
        p.setProperty("smtp.puerto", String.valueOf(smtp.puerto()));
        p.setProperty("correo.direccion", "grupo14sa@tecnoweb.org.bo");
        p.setProperty("bd.url", bdUrl);
        p.setProperty("bd.usuario", bdUsuario);
        p.setProperty("bd.contrasena", bdContrasena);
        p.setProperty("app.timeout_segundos", "2");
        return Config.desde(p);
    }

    private int diagnosticar(Config config) {
        return new Diagnostico(config, new PrintStream(salida, true, StandardCharsets.UTF_8)).ejecutar("carla.rojas@gmail.com");
    }

    private String texto() {
        return salida.toString(StandardCharsets.UTF_8);
    }

    @Test
    void sinBaseYConReenvioDenegadoReportaDosFallasSinTocarNada() {
        pop.agregar("From: a@b.com\r\nSubject: LISCOB\r\n\r\nhola\r\n");
        smtp.rechazarReenvio(true);
        int fallas = diagnosticar(config("jdbc:postgresql://127.0.0.1:1/no_existe", "x", "x", FakePop3Server.CONTRASENA));

        assertEquals(2, fallas, texto());
        assertTrue(texto().contains("[FALLA] no se pudo usar la base"), texto());
        assertTrue(texto().contains("sesión iniciada como grupo14sa: 1 correo(s) en el buzón"), texto());
        assertTrue(texto().contains("acepta enviar a una cuenta local"), texto());
        assertTrue(texto().contains("Relaying denied"), texto());
        assertEquals(1, pop.mensajesEnBuzon(), "no borra correos");
        assertTrue(smtp.recibidos().isEmpty(), "no envía nada");
        assertTrue(smtp.comandos().contains("RSET"));
    }

    @Test
    void credencialesDelBuzonIncorrectas() {
        int fallas = diagnosticar(config("jdbc:postgresql://127.0.0.1:1/no_existe", "x", "x", "otra-clave"));
        assertTrue(texto().contains("[FALLA] no se pudo leer el buzón"), texto());
        assertTrue(texto().contains("acepta enviar a carla.rojas@gmail.com"), texto());
        assertEquals(2, fallas, texto());
    }

    @Test
    void explicaLosErroresDeRed() {
        assertEquals("no se encontró el servidor «x.invalid» (el DNS no lo resuelve; pruebe con su IP)",
                Diagnostico.describir(new java.net.UnknownHostException("x.invalid"), "x.invalid"));
        assertTrue(Diagnostico.describir(new java.net.ConnectException("Connection refused"), "h").contains("rechazó la conexión"));
        assertTrue(Diagnostico.describir(new java.net.SocketTimeoutException("Read timed out"), "h").contains("no respondió a tiempo"));
        assertEquals("otro", Diagnostico.describir(new IOException("otro"), "h"));
    }

    @Test
    @Tag("bd")
    void conLaBaseCargadaTodoEstaListo() throws Exception {
        String url = System.getenv("CANAL11_BD_PRUEBA_URL");
        Assumptions.assumeTrue(url != null && !url.isBlank(), "Sin CANAL11_BD_PRUEBA_URL se saltan las pruebas de base de datos");
        // carga el esquema y los datos si todavía no están
        bo.edu.uagrm.ficct.canal11.datos.BaseDatosPrueba.conexion();
        String usuario = System.getenv().getOrDefault("CANAL11_BD_PRUEBA_USUARIO", "postgres");
        String contrasena = System.getenv().getOrDefault("CANAL11_BD_PRUEBA_CONTRASENA", "");
        int fallas = diagnosticar(config(url, usuario, contrasena.isEmpty() ? "sin-clave" : contrasena, FakePop3Server.CONTRASENA));

        assertEquals(0, fallas, texto());
        assertTrue(texto().contains("tablas cargadas: 15 usuarios, 65 permisos"), texto());
        assertTrue(texto().contains("Resumen: todo listo"), texto());
    }
}
