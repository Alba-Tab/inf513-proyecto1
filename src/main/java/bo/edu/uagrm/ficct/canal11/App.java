package bo.edu.uagrm.ficct.canal11;

import bo.edu.uagrm.ficct.canal11.config.BloqueoInstancia;
import bo.edu.uagrm.ficct.canal11.config.Config;
import bo.edu.uagrm.ficct.canal11.config.Registro;
import bo.edu.uagrm.ficct.canal11.datos.DBConnection;
import bo.edu.uagrm.ficct.canal11.datos.Repositorio;
import bo.edu.uagrm.ficct.canal11.comandos.CoberturasEnCurso;
import bo.edu.uagrm.ficct.canal11.comandos.catalogo.Catalogo;
import bo.edu.uagrm.ficct.canal11.pipeline.MailVerificationThread;
import bo.edu.uagrm.ficct.canal11.pipeline.Pipeline;
import bo.edu.uagrm.ficct.canal11.pipeline.SendEmailThread;

import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneId;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Punto de entrada de la aplicación de consola.
 *
 * <p>Uso: {@code java -Duser.timezone=America/La_Paz -jar canal11-mail.jar [config.properties]}</p>
 *
 * <p>Verificación previa al despliegue (no borra correos ni envía nada; ver {@link Diagnostico}):
 * {@code java -jar canal11-mail.jar --verificar config.properties su.correo@gmail.com}</p>
 *
 * <p>Arma la tubería y arranca sus dos extremos: {@link MailVerificationThread}, la fuente que
 * saca los correos del buzón, y {@link SendEmailThread}, el sumidero que envía las respuestas. En
 * el medio, cada correo pasa por las etapas del {@link Pipeline}. Se detiene con Ctrl+C o con {@code kill}: el
 * gancho de apagado termina el correo en curso y envía las respuestas pendientes.</p>
 */
public final class App {

    static {
        // Antes del primer Logger: el log debe sobrevivir hasta el final del apagado
        Registro.usarGestorPropio();
    }

    private static final Logger LOG = Logger.getLogger(App.class.getName());
    private static final long ESPERA_APAGADO_MS = 30_000;

    private App() {
    }

    public static void main(String[] args) {
        Registro.iniciarConsola(ZoneId.of("America/La_Paz"));
        if (args.length > 0 && args[0].equals("--verificar")) {
            // Modo de línea de comandos: el código de salida es la cantidad de fallas (para los scripts)
            System.exit(verificar(args));
        }
        Path archivo = Path.of(args.length > 0 ? args[0] : Config.ARCHIVO_POR_DEFECTO);
        Config config;
        try {
            config = Config.cargar(archivo);
        } catch (Config.ConfigException e) {
            LOG.severe(e.getMessage());
            Registro.terminar();
            return;
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "No se pudo leer la configuración", e);
            Registro.terminar();
            return;
        }

        try {
            // Primero el bloqueo: una copia rechazada no abre el archivo de log de la que está corriendo
            Optional<BloqueoInstancia> bloqueo = BloqueoInstancia.tomar(config.archivoBloqueo());
            if (bloqueo.isEmpty()) {
                LOG.severe("Ya hay otra copia de la aplicación corriendo (bloqueo en " + config.archivoBloqueo().toAbsolutePath()
                        + "). Esta copia termina.");
                Registro.terminar();
                return;
            }
            try (BloqueoInstancia b = bloqueo.get()) {
                Registro.iniciar(config.carpetaLogs(), config.zonaHoraria());
                ejecutar(config);
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "La aplicación terminó por un error", e);
            Registro.terminar();
        }
    }

    /** {@code --verificar [config.properties] correo@externo}. Devuelve la cantidad de fallas. */
    private static int verificar(String[] args) {
        if (args.length < 2) {
            System.err.println("Uso: java -jar canal11-mail.jar --verificar [config.properties] su.correo@gmail.com");
            return 2;
        }
        String archivo = args.length >= 3 ? args[1] : Config.ARCHIVO_POR_DEFECTO;
        String correoExterno = args[args.length - 1];
        Logger.getLogger("bo.edu.uagrm.ficct.canal11").setLevel(Level.WARNING);
        try {
            return new Diagnostico(Config.cargar(Path.of(archivo)), System.out).ejecutar(correoExterno);
        } catch (Exception e) {
            System.err.println("No se pudo leer la configuración: " + e.getMessage());
            return 1;
        }
    }

    private static void ejecutar(Config config) throws InterruptedException {
        LOG.info("Canal 11 vía correo: " + config.resumen());
        Clock reloj = Clock.system(config.zonaHoraria());
        DBConnection db = new DBConnection(config);
        Repositorio repositorio = new Repositorio(config.timeoutSegundos());
        CoberturasEnCurso enCurso = new CoberturasEnCurso(repositorio);

        SendEmailThread emisor = new SendEmailThread(config, reloj);
        Pipeline pipeline = Pipeline.completo(config, db::obtener, repositorio, Catalogo.completo(), reloj, emisor::encolar);
        MailVerificationThread lector = new MailVerificationThread(config, pipeline,
                () -> {
                    int cambiadas = enCurso.actualizar(db.obtener());
                    if (cambiadas > 0) {
                        LOG.info(cambiadas + " cobertura(s) pasaron a en_curso");
                    }
                });

        Thread apagado = new Thread(() -> {
            LOG.info("Deteniendo la aplicación…");
            try {
                lector.detener(ESPERA_APAGADO_MS);
                emisor.detener(ESPERA_APAGADO_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                db.cerrar();
                LOG.info("Aplicación detenida");
                Registro.terminar();
            }
        }, "apagado");
        Runtime.getRuntime().addShutdownHook(apagado);

        emisor.start();
        lector.start();
        lector.join();
        emisor.join();
    }
}
