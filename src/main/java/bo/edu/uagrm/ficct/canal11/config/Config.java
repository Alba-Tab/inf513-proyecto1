package bo.edu.uagrm.ficct.canal11.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.Properties;

/**
 * Configuración de la aplicación, leída de {@code config.properties}.
 *
 * <p>El archivo real no se versiona: tiene las contraseñas del buzón y de la base de datos.
 * La plantilla es {@code config.example.properties}.</p>
 */
public final class Config {

    public static final String ARCHIVO_POR_DEFECTO = "config.properties";

    private final String pop3Host;
    private final int pop3Puerto;
    private final String pop3Usuario;
    private final String pop3Contrasena;
    private final String smtpHost;
    private final int smtpPuerto;
    private final String correoSistema;
    private final String bdUrl;
    private final String bdUsuario;
    private final String bdContrasena;
    private final int intervaloSegundos;
    private final int maxBytesCorreo;
    private final int timeoutSegundos;
    private final ZoneId zonaHoraria;
    private final Path archivoBloqueo;
    private final Path carpetaLogs;

    private Config(Properties p) {
        pop3Host = requerido(p, "pop3.host");
        pop3Puerto = entero(p, "pop3.puerto", 110);
        pop3Usuario = requerido(p, "pop3.usuario");
        pop3Contrasena = requerido(p, "pop3.contrasena");
        smtpHost = requerido(p, "smtp.host");
        smtpPuerto = entero(p, "smtp.puerto", 25);
        correoSistema = requerido(p, "correo.direccion");
        bdUrl = requerido(p, "bd.url");
        bdUsuario = requerido(p, "bd.usuario");
        bdContrasena = requerido(p, "bd.contrasena");
        intervaloSegundos = entero(p, "app.intervalo_segundos", 30);
        maxBytesCorreo = entero(p, "app.max_bytes_correo", 1_048_576);
        timeoutSegundos = entero(p, "app.timeout_segundos", 30);
        zonaHoraria = ZoneId.of(opcional(p, "app.zona_horaria", "America/La_Paz"));
        archivoBloqueo = Path.of(opcional(p, "app.archivo_bloqueo", "canal11.lock"));
        carpetaLogs = Path.of(opcional(p, "app.carpeta_logs", "logs"));
    }

    /** Lee la configuración de un archivo {@code .properties} en UTF-8. */
    public static Config cargar(Path archivo) throws IOException {
        if (!Files.isRegularFile(archivo)) {
            throw new ConfigException("No existe el archivo de configuración: " + archivo.toAbsolutePath()
                    + ". Copie config.example.properties como config.properties y complételo.");
        }
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(archivo, StandardCharsets.UTF_8)) {
            p.load(r);
        }
        return desde(p);
    }

    /** Arma la configuración a partir de propiedades ya cargadas (útil en las pruebas). */
    public static Config desde(Properties p) {
        return new Config(p);
    }

    private static String requerido(Properties p, String clave) {
        String valor = p.getProperty(clave);
        if (valor == null || valor.isBlank()) {
            throw new ConfigException("Falta la clave obligatoria «" + clave + "» en la configuración");
        }
        return valor.trim();
    }

    private static String opcional(Properties p, String clave, String porDefecto) {
        String valor = p.getProperty(clave);
        return valor == null || valor.isBlank() ? porDefecto : valor.trim();
    }

    private static int entero(Properties p, String clave, int porDefecto) {
        String valor = opcional(p, clave, null);
        if (valor == null) {
            return porDefecto;
        }
        try {
            int n = Integer.parseInt(valor);
            if (n <= 0) {
                throw new NumberFormatException();
            }
            return n;
        } catch (NumberFormatException e) {
            throw new ConfigException("La clave «" + clave + "» debe ser un entero positivo, pero vale «" + valor + "»");
        }
    }

    public String pop3Host() { return pop3Host; }
    public int pop3Puerto() { return pop3Puerto; }
    public String pop3Usuario() { return pop3Usuario; }
    public String pop3Contrasena() { return pop3Contrasena; }
    public String smtpHost() { return smtpHost; }
    public int smtpPuerto() { return smtpPuerto; }
    public String correoSistema() { return correoSistema; }
    public String bdUrl() { return bdUrl; }
    public String bdUsuario() { return bdUsuario; }
    public String bdContrasena() { return bdContrasena; }
    public int intervaloSegundos() { return intervaloSegundos; }
    public int maxBytesCorreo() { return maxBytesCorreo; }
    public int timeoutSegundos() { return timeoutSegundos; }
    public ZoneId zonaHoraria() { return zonaHoraria; }
    public Path archivoBloqueo() { return archivoBloqueo; }
    public Path carpetaLogs() { return carpetaLogs; }

    /** Resumen para el log, sin contraseñas. */
    public String resumen() {
        return "POP3 " + pop3Usuario + "@" + pop3Host + ":" + pop3Puerto
                + " | SMTP " + smtpHost + ":" + smtpPuerto
                + " | correo " + correoSistema
                + " | BD " + bdUsuario + "@" + bdUrl
                + " | cada " + intervaloSegundos + " s"
                + " | zona " + zonaHoraria;
    }

    @Override
    public String toString() {
        return resumen();
    }

    /** Error en la configuración: falta una clave o tiene un valor inválido. */
    public static final class ConfigException extends RuntimeException {
        public ConfigException(String mensaje) {
            super(mensaje);
        }
    }
}
