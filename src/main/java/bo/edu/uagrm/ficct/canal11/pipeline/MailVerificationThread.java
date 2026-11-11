package bo.edu.uagrm.ficct.canal11.pipeline;

import bo.edu.uagrm.ficct.canal11.config.Config;
import bo.edu.uagrm.ficct.canal11.correo.Pop3Client;
import bo.edu.uagrm.ficct.canal11.correo.Pop3Exception;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Fuente del pipeline: el receptor POP3 («MailVerificationThread» de la lección del docente). Es
 * el único hilo que saca correos del buzón y los pasa por el {@link Pipeline}, de a uno y en orden
 * de llegada (decisión D3).
 *
 * <p>Cada vuelta:</p>
 * <ol>
 *   <li>ejecuta la tarea previa (pasar a {@code en_curso} las coberturas que ya empezaron, D4);</li>
 *   <li>saca el primer correo del buzón en su propia sesión POP: {@code RETR 1} → {@code DELE 1} →
 *       {@code QUIT}, y lo procesa solo si {@code QUIT} respondió {@code +OK}. Si la app se cae a
 *       mitad de camino, ningún comando se ejecuta dos veces;</li>
 *   <li>repite hasta vaciar el buzón y espera el intervalo configurado.</li>
 * </ol>
 * <p>Que nada la tumbe (D9): un correo que falla recibe una respuesta de error y se sigue con el
 * siguiente; si se cae POP3 o la base, se escribe en el log y se reintenta en la vuelta siguiente.
 * Los correos de más del tamaño máximo no se descargan: se leen sus cabeceras con TOP, se borran y
 * se responde que el sistema no acepta adjuntos. A los correos automáticos (rebotes, respuestas
 * automáticas) y a los del propio sistema no se les responde.</p>
 * <p>Si la base de datos no responde en la tarea previa, esa vuelta no lee el buzón: los pedidos
 * esperan allí en vez de borrarse con un «intente más tarde».</p>
 * <p>Un correo que no se puede leer ({@code LIST}, {@code RETR} o {@code TOP} fallan) quedaría
 * primero en el buzón para siempre y taparía a los demás. Las fallas se cuentan por su {@code UIDL};
 * a la {@value #MAX_FALLAS}.ª se borra sin procesarlo y se anota en el log.</p>
 */
public final class MailVerificationThread extends Thread {

    private static final Logger LOG = Logger.getLogger(MailVerificationThread.class.getName());

    /** Tope por vuelta: si el buzón se llena de golpe, igual se ejecuta la tarea previa cada tanto. */
    static final int MAX_CORREOS_POR_VUELTA = 100;
    /** Fallas al leer un mismo correo antes de borrarlo sin procesarlo. */
    static final int MAX_FALLAS = 3;

    /** Algo que se ejecuta al inicio de cada vuelta. Si falla, la vuelta sigue igual. */
    @FunctionalInterface
    public interface TareaPrevia {
        void ejecutar() throws Exception;
    }

    /** Un correo sacado del buzón: completo, o solo sus cabeceras si pasaba el tamaño máximo. */
    private record Extraido(byte[] contenido, boolean demasiadoGrande, long tamano) {
    }

    private final Config config;
    private final Consumer<Pedido> pipeline;
    private final TareaPrevia tareaPrevia;
    private final Object pausa = new Object();
    /** Fallas de lectura por UIDL. Solo lo toca este hilo; se limpia al sacar el correo. */
    private final Map<String, Integer> fallas = new HashMap<>();
    private volatile boolean detenido;

    public MailVerificationThread(Config config, Consumer<Pedido> pipeline, TareaPrevia tareaPrevia) {
        super("MailVerificationThread");
        this.config = config;
        this.pipeline = pipeline;
        this.tareaPrevia = tareaPrevia;
    }

    @Override
    public void run() {
        LOG.info("Lectura del buzón iniciada: " + config.pop3Usuario() + "@" + config.pop3Host()
                + ", cada " + config.intervaloSegundos() + " s");
        while (!detenido) {
            try {
                procesarUnaVez();
            } catch (Throwable t) {
                LOG.log(Level.SEVERE, "Error inesperado en la vuelta; se reintenta en la siguiente", t);
            }
            dormir(config.intervaloSegundos() * 1000L);
        }
        LOG.info("Lectura del buzón detenida");
    }

    /** Detiene el bucle al terminar el correo en curso y espera a que el hilo termine (hasta {@code esperaMs}). */
    public void detener(long esperaMs) throws InterruptedException {
        detenido = true;
        synchronized (pausa) {
            pausa.notifyAll();
        }
        join(esperaMs);
    }

    /**
     * Una vuelta completa: tarea previa y todos los correos del buzón (hasta {@link #MAX_CORREOS_POR_VUELTA}).
     * Nunca lanza excepciones.
     *
     * @return cuántos correos se sacaron del buzón
     */
    public int procesarUnaVez() {
        try {
            tareaPrevia.ejecutar();
        } catch (SQLException e) {
            // Sin base, cada correo se borraría con «intente más tarde»: mejor que esperen en el buzón
            LOG.warning("La base de datos no responde (" + e.getMessage() + "); el buzón se lee en la próxima vuelta");
            return 0;
        } catch (Exception e) {
            LOG.warning("La tarea previa de la vuelta falló: " + e.getMessage());
        }
        int sacados = 0;
        while (!detenido && sacados < MAX_CORREOS_POR_VUELTA) {
            Extraido correo;
            try {
                correo = sacarPrimero();
            } catch (Pop3Exception e) {
                if (e.buzonEnUso()) {
                    LOG.info("El buzón está abierto en otra sesión POP3; se reintenta en la próxima vuelta");
                } else {
                    LOG.warning("POP3 respondió un error: " + e.getMessage());
                }
                break;
            } catch (IOException e) {
                LOG.warning("No se pudo leer el buzón (" + config.pop3Host() + ":" + config.pop3Puerto() + "): " + e);
                break;
            }
            if (correo == null) {
                break;
            }
            sacados++;
            procesarCorreo(correo);
        }
        return sacados;
    }

    /**
     * Saca el primer correo del buzón en una sesión POP propia.
     *
     * @return el correo, o {@code null} si el buzón está vacío o si el servidor no confirmó el QUIT
     *         (entonces el correo sigue en el buzón y se procesa en la próxima vuelta)
     */
    private Extraido sacarPrimero() throws IOException {
        try (Pop3Client pop = Pop3Client.conectar(config.pop3Host(), config.pop3Puerto(), config.timeoutSegundos() * 1000)) {
            pop.login(config.pop3Usuario(), config.pop3Contrasena());
            if (pop.stat().mensajes() == 0) {
                pop.quit();
                return null;
            }
            String uid = uidl(pop);
            if (uid != null && fallas.getOrDefault(uid, 0) >= MAX_FALLAS) {
                pop.dele(1);
                if (pop.quit()) {
                    fallas.remove(uid);
                    LOG.severe("Se borró sin procesar el correo " + uid + ": falló " + MAX_FALLAS
                            + " veces al leerlo y tapaba a los demás");
                }
                return null;
            }
            long tamano;
            byte[] contenido;
            boolean grande;
            try {
                tamano = pop.list(1);
                grande = tamano > config.maxBytesCorreo();
                contenido = grande ? pop.top(1, 0) : pop.retr(1);
            } catch (IOException e) {
                if (uid != null) {
                    fallas.merge(uid, 1, Integer::sum);
                }
                throw e;
            }
            pop.dele(1);
            if (!pop.quit()) {
                LOG.warning("POP3 no confirmó el QUIT: el correo queda en el buzón para la próxima vuelta");
                return null;
            }
            if (uid != null) {
                fallas.remove(uid);
            }
            return new Extraido(contenido, grande, tamano);
        }
    }

    /** UIDL del mensaje 1, o {@code null} si el servidor no lo admite (entonces no se cuentan fallas). */
    private static String uidl(Pop3Client pop) throws IOException {
        try {
            return pop.uidl(1);
        } catch (Pop3Exception e) {
            return null;
        }
    }

    private void procesarCorreo(Extraido e) {
        try {
            pipeline.accept(new Pedido(e.contenido(), e.demasiadoGrande(), e.tamano()));
        } catch (RuntimeException ex) {
            LOG.log(Level.SEVERE, "El pipeline falló con un correo; se sigue con el siguiente", ex);
        }
    }

    private void dormir(long ms) {
        synchronized (pausa) {
            if (detenido) {
                return;
            }
            try {
                pausa.wait(ms);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                detenido = true;
            }
        }
    }
}
