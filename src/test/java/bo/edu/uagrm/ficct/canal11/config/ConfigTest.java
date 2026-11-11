package bo.edu.uagrm.ficct.canal11.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigTest {

    private static Properties minimas() {
        Properties p = new Properties();
        p.setProperty("pop3.host", "localhost");
        p.setProperty("pop3.usuario", "grupo14sa");
        p.setProperty("pop3.contrasena", "secreta-pop");
        p.setProperty("smtp.host", "localhost");
        p.setProperty("correo.direccion", "grupo14sa@tecnoweb.org.bo");
        p.setProperty("bd.url", "jdbc:postgresql://localhost:5432/db_grupo14sa");
        p.setProperty("bd.usuario", "grupo14sa");
        p.setProperty("bd.contrasena", "secreta-bd");
        return p;
    }

    @Test
    void usaLosValoresPorDefecto() {
        Config c = Config.desde(minimas());
        assertEquals(110, c.pop3Puerto());
        assertEquals(25, c.smtpPuerto());
        assertEquals(30, c.intervaloSegundos());
        assertEquals(1_048_576, c.maxBytesCorreo());
        assertEquals(ZoneId.of("America/La_Paz"), c.zonaHoraria());
        assertEquals(Path.of("canal11.lock"), c.archivoBloqueo());
    }

    @Test
    void fallaSiFaltaUnaClaveObligatoria() {
        Properties p = minimas();
        p.remove("bd.url");
        Config.ConfigException e = assertThrows(Config.ConfigException.class, () -> Config.desde(p));
        assertTrue(e.getMessage().contains("bd.url"));
    }

    @Test
    void fallaSiUnEnteroNoEsValido() {
        Properties p = minimas();
        p.setProperty("app.intervalo_segundos", "treinta");
        Config.ConfigException e = assertThrows(Config.ConfigException.class, () -> Config.desde(p));
        assertTrue(e.getMessage().contains("app.intervalo_segundos"));
    }

    @Test
    void elResumenNoMuestraContrasenas() {
        String resumen = Config.desde(minimas()).resumen();
        assertFalse(resumen.contains("secreta-pop"));
        assertFalse(resumen.contains("secreta-bd"));
        assertTrue(resumen.contains("grupo14sa@tecnoweb.org.bo"));
    }

    @Test
    void leeUnArchivoEnUtf8(@TempDir Path dir) throws Exception {
        Path archivo = dir.resolve("config.properties");
        StringBuilder sb = new StringBuilder();
        minimas().forEach((k, v) -> sb.append(k).append('=').append(v).append('\n'));
        sb.append("bd.contrasena=contraseña-con-ñ\n");
        Files.writeString(archivo, sb.toString(), StandardCharsets.UTF_8);
        assertEquals("contraseña-con-ñ", Config.cargar(archivo).bdContrasena());
    }

    @Test
    void avisaSiNoExisteElArchivo(@TempDir Path dir) {
        Config.ConfigException e = assertThrows(Config.ConfigException.class,
                () -> Config.cargar(dir.resolve("no-existe.properties")));
        assertTrue(e.getMessage().contains("config.example.properties"));
    }

    @Test
    void elArchivoDeEjemploEsValido() throws Exception {
        Config c = Config.cargar(Path.of("config.example.properties"));
        assertEquals("grupo14sa@tecnoweb.org.bo", c.correoSistema());
    }
}
