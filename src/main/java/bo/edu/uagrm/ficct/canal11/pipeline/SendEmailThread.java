package bo.edu.uagrm.ficct.canal11.pipeline;

import bo.edu.uagrm.ficct.canal11.config.Config;
import bo.edu.uagrm.ficct.canal11.correo.CorreoSaliente;
import bo.edu.uagrm.ficct.canal11.correo.SmtpClient;
import bo.edu.uagrm.ficct.canal11.correo.SmtpException;

import java.io.IOException;
import java.time.Clock;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Sumidero del pipeline: el emisor SMTP («SendEmailThread» de la lección del docente). Envía las
 * respuestas desde una cola, en su propio hilo, para que un SMTP lento no frene la lectura del buzón.
 *
 * <p>Cada respuesta va en su propia sesión SMTP. Si falla por la red o por un error temporal (4xx),
 * se reintenta hasta tres veces; un error permanente (5xx, por ejemplo «Relaying denied») no se
 * reintenta. Ningún error detiene el hilo. Al detenerse, intenta enviar lo que quedó en la cola.</p>
 */
public final class SendEmailThread extends Thread {

    private static final Logger LOG = Logger.getLogger(SendEmailThread.class.getName());
    private static final int CAPACIDAD = 1_000;
    private static final int INTENTOS = 3;

    private final BlockingQueue<CorreoSaliente> cola = new LinkedBlockingQueue<>(CAPACIDAD);
    private final String host;
    private final int puerto;
    private final int timeoutMs;
    private final String dominio;
    private final Clock reloj;
    private final long esperaReintentoMs;
    private volatile boolean detenido;
    private volatile int enviados;
    private volatile int descartados;

    public SendEmailThread(Config config, Clock reloj) {
        this(config.smtpHost(), config.smtpPuerto(), config.timeoutSegundos() * 1000,
                config.correoSistema().substring(config.correoSistema().indexOf('@') + 1), reloj, 5_000);
    }

    SendEmailThread(String host, int puerto, int timeoutMs, String dominio, Clock reloj, long esperaReintentoMs) {
        super("SendEmailThread");
        this.host = host;
        this.puerto = puerto;
        this.timeoutMs = timeoutMs;
        this.dominio = dominio;
        this.reloj = reloj;
        this.esperaReintentoMs = esperaReintentoMs;
    }

    /** Deja la respuesta para enviar. No bloquea al hilo que lee el buzón. */
    public void encolar(CorreoSaliente respuesta) {
        if (!cola.offer(respuesta)) {
            descartados++;
            LOG.severe("La cola de envío está llena (" + CAPACIDAD + "): se descarta la respuesta a " + respuesta.destinatario());
        }
    }

    @Override
    public void run() {
        LOG.info("Emisor SMTP iniciado (" + host + ":" + puerto + ")");
        while (!detenido || !cola.isEmpty()) {
            try {
                CorreoSaliente c = cola.poll(500, TimeUnit.MILLISECONDS);
                if (c != null) {
                    entregar(c);
                }
            } catch (InterruptedException e) {
                // detener() no interrumpe; si otro lo hace, se sigue hasta vaciar la cola
            } catch (Throwable t) {
                LOG.log(Level.SEVERE, "Error inesperado en el emisor; sigue con la próxima respuesta", t);
            }
        }
        LOG.info("Emisor SMTP detenido: " + enviados + " enviadas, " + descartados + " descartadas");
    }

    /**
     * Envía una respuesta, con reintentos si el error es temporal.
     *
     * @return {@code true} si el servidor la aceptó
     */
    boolean entregar(CorreoSaliente c) {
        int intentos = detenido ? 1 : INTENTOS;
        for (int intento = 1; intento <= intentos; intento++) {
            try (SmtpClient smtp = SmtpClient.conectar(host, puerto, timeoutMs)) {
                smtp.ehlo(dominio);
                smtp.enviar(c, reloj);
                smtp.quit();
                enviados++;
                LOG.info("Respuesta enviada a " + c.destinatario() + " («" + c.asunto() + "»)");
                return true;
            } catch (SmtpException e) {
                if (e.esPermanente()) {
                    descartados++;
                    LOG.warning("SMTP rechazó la respuesta a " + c.destinatario() + " y no se reintenta: " + e.getMessage()
                            + (e.reenvioDenegado() ? " (desde fuera del servidor tecnoweb no se puede enviar a otros dominios)" : ""));
                    return false;
                }
                LOG.warning("Intento " + intento + "/" + intentos + " de enviar a " + c.destinatario() + " falló: " + e.getMessage());
            } catch (IOException | RuntimeException e) {
                LOG.warning("Intento " + intento + "/" + intentos + " de enviar a " + c.destinatario() + " falló: " + e);
            }
            if (intento < intentos && !esperar(esperaReintentoMs * intento)) {
                break;
            }
        }
        descartados++;
        LOG.severe("No se pudo enviar la respuesta a " + c.destinatario() + " («" + c.asunto() + "»)");
        return false;
    }

    private boolean esperar(long ms) {
        try {
            Thread.sleep(ms);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** Deja de recibir respuestas, envía lo pendiente y espera a que el hilo termine (hasta {@code esperaMs}). */
    public void detener(long esperaMs) throws InterruptedException {
        detenido = true;
        join(esperaMs);
    }

    /** Respuestas en la cola, todavía sin enviar. */
    public int pendientes() {
        return cola.size();
    }

    public int enviados() {
        return enviados;
    }

    public int descartados() {
        return descartados;
    }
}
