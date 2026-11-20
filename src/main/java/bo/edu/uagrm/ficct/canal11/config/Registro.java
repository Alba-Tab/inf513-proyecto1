package bo.edu.uagrm.ficct.canal11.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Configura el log de la aplicación: una línea por evento, en la consola y en
 * {@code logs/canal11.0.log}. Además, como pide la consigna, {@code logs/access.log} lleva una
 * línea por cada correo procesado y {@code logs/error.log} las fallas y las excepciones.
 */
public final class Registro {

    private static final String RAIZ = "bo.edu.uagrm.ficct.canal11";

    /** Logger de {@code access.log}: una línea por correo atendido, rechazado o descartado. */
    public static final String ACCESO = RAIZ + ".acceso";

    private Registro() {
    }

    /** Solo consola: se usa antes de leer la configuración. */
    public static void iniciarConsola(ZoneId zona) {
        Logger raiz = Logger.getLogger(RAIZ);
        quitarHandlers(raiz);
        raiz.setUseParentHandlers(false);
        raiz.setLevel(Level.INFO);
        Handler consola = new ConsoleHandler();
        consola.setFormatter(new FormatoLinea(zona));
        consola.setLevel(Level.ALL);
        raiz.addHandler(consola);
    }

    /**
     * Consola y, en la carpeta de logs: {@code canal11.%g.log} con todo (5 archivos de 5 MB),
     * {@code access.log} con las líneas del logger {@link #ACCESO} y {@code error.log} con los
     * avisos y los errores (WARNING o más).
     */
    public static void iniciar(Path carpetaLogs, ZoneId zona) throws IOException {
        iniciarConsola(zona);
        Files.createDirectories(carpetaLogs);
        Logger raiz = Logger.getLogger(RAIZ);
        raiz.addHandler(archivo(carpetaLogs.resolve("canal11.%g.log"), 5_000_000, 5, Level.ALL, zona));
        // shortcut: access.log y error.log no rotan; si crecen demasiado, rotarlos con logrotate en el servidor
        Handler acceso = archivo(carpetaLogs.resolve("access.log"), 0, 1, Level.ALL, zona);
        acceso.setFilter(r -> ACCESO.equals(r.getLoggerName()));
        raiz.addHandler(acceso);
        raiz.addHandler(archivo(carpetaLogs.resolve("error.log"), 0, 1, Level.WARNING, zona));
    }

    private static Handler archivo(Path ruta, int limite, int cantidad, Level nivel, ZoneId zona) throws IOException {
        FileHandler h = new FileHandler(ruta.toString(), limite, cantidad, true);
        h.setEncoding(StandardCharsets.UTF_8.name());
        h.setFormatter(new FormatoLinea(zona));
        h.setLevel(nivel);
        return h;
    }

    /**
     * Activa {@link GestorLog} como gestor del log. Debe llamarse antes de crear el primer
     * {@link Logger} del programa (por eso {@code App} lo hace en un bloque estático).
     */
    public static void usarGestorPropio() {
        System.setProperty("java.util.logging.manager", GestorLog.class.getName());
    }

    /**
     * Cierra el log (vacía y cierra el archivo). Se llama al final del apagado, después de que los
     * hilos escribieron sus últimas líneas.
     */
    public static void terminar() {
        LogManager lm = LogManager.getLogManager();
        if (lm instanceof GestorLog g) {
            g.cerrarDeVerdad();
        } else {
            for (Handler h : Logger.getLogger(RAIZ).getHandlers()) {
                h.flush();
            }
        }
    }

    /**
     * Gestor de log que no se cierra solo al apagar la JVM.
     *
     * <p>{@code java.util.logging} tiene su propio gancho de apagado que cierra los archivos de log
     * al mismo tiempo que corre el gancho de la aplicación: así se pierden las últimas líneas
     * («Deteniendo…», «Lectura del buzón detenida»). Este gestor ignora ese cierre automático y
     * cierra recién cuando {@code App} llama a {@link Registro#terminar()}.</p>
     */
    public static final class GestorLog extends LogManager {
        public GestorLog() {
            super();
        }

        @Override
        public void reset() {
            // lo cierra Registro.terminar() al final del apagado
        }

        void cerrarDeVerdad() {
            super.reset();
        }
    }

    private static void quitarHandlers(Logger logger) {
        for (Handler h : logger.getHandlers()) {
            logger.removeHandler(h);
            h.close();
        }
    }

    /** {@code 2026-10-13 10:45:02 INFO  [App] mensaje}, y la traza si hay excepción. */
    static final class FormatoLinea extends Formatter {
        private final DateTimeFormatter fecha;

        FormatoLinea(ZoneId zona) {
            this.fecha = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(zona);
        }

        @Override
        public String format(LogRecord r) {
            String origen = r.getLoggerName() == null ? "" : r.getLoggerName().substring(r.getLoggerName().lastIndexOf('.') + 1);
            StringBuilder sb = new StringBuilder()
                    .append(fecha.format(Instant.ofEpochMilli(r.getMillis())))
                    .append(' ').append(String.format("%-7s", r.getLevel().getName()))
                    .append(" [").append(origen).append("] ")
                    .append(formatMessage(r))
                    .append(System.lineSeparator());
            if (r.getThrown() != null) {
                Throwable t = r.getThrown();
                sb.append("    ").append(t).append(System.lineSeparator());
                for (StackTraceElement e : t.getStackTrace()) {
                    sb.append("        at ").append(e).append(System.lineSeparator());
                }
            }
            return sb.toString();
        }
    }
}
