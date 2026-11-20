package bo.edu.uagrm.ficct.canal11.pipeline.etapas;

import bo.edu.uagrm.ficct.canal11.comandos.HTMLResponseBuilder;
import bo.edu.uagrm.ficct.canal11.config.Config;
import bo.edu.uagrm.ficct.canal11.config.Registro;
import bo.edu.uagrm.ficct.canal11.pipeline.Etapa;
import bo.edu.uagrm.ficct.canal11.pipeline.Pedido;

import java.util.Locale;
import java.util.logging.Logger;

/**
 * Etapa 2: deja pasar solo los correos que hay que atender.
 *
 * <ul>
 *   <li>Rebotes, respuestas automáticas y los correos del propio sistema se descartan sin responder
 *       (así no se arman bucles entre robots).</li>
 *   <li>Un correo de más del tamaño máximo no se descargó entero: se le responde que el sistema
 *       no acepta adjuntos.</li>
 * </ul>
 */
public final class Filtrar implements Etapa {

    private static final Logger LOG = Logger.getLogger(Filtrar.class.getName());
    private static final Logger ACCESO = Logger.getLogger(Registro.ACCESO);

    private final String correoSistema;
    private final long maxBytes;

    public Filtrar(Config config) {
        this(config.correoSistema(), config.maxBytesCorreo());
    }

    public Filtrar(String correoSistema, long maxBytes) {
        this.correoSistema = correoSistema.toLowerCase(Locale.ROOT);
        this.maxBytes = maxBytes;
    }

    @Override
    public void procesar(Pedido p) {
        if (p.automatico()) {
            ACCESO.info(p.remitenteCorreo() + " | - | Descartado | «" + p.asuntoVisible() + "» | correo automático o rebote");
            p.descartar();
            return;
        }
        if (p.remitenteCorreo().equals(correoSistema)) {
            ACCESO.info(p.remitenteCorreo() + " | - | Descartado | «" + p.asuntoVisible() + "» | correo del propio sistema");
            p.descartar();
            return;
        }
        LOG.info("Correo de " + p.remitenteCorreo() + ": «" + p.asuntoVisible() + "»");
        if (p.demasiadoGrande()) {
            String m = "Error: el correo pesa " + kb(p.tamano()) + " KB y el máximo es " + kb(maxBytes)
                    + " KB. El sistema no acepta adjuntos: envíe el comando sin archivos.";
            p.error(HTMLResponseBuilder.error(m), m);
        }
    }

    private static long kb(long bytes) {
        return (bytes + 1023) / 1024;
    }
}
