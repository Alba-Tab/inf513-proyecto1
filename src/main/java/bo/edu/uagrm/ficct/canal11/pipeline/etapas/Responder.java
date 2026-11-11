package bo.edu.uagrm.ficct.canal11.pipeline.etapas;

import bo.edu.uagrm.ficct.canal11.comandos.Adjunto;
import bo.edu.uagrm.ficct.canal11.correo.CorreoSaliente;
import bo.edu.uagrm.ficct.canal11.pipeline.Etapa;
import bo.edu.uagrm.ficct.canal11.pipeline.Pedido;
import bo.edu.uagrm.ficct.canal11.pipeline.RespuestaCorreo;

import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Etapa 10: arma el correo de respuesta (HTML en base64, en el mismo hilo que el pedido) y lo
 * deja en la cola de {@code SendEmailThread}. No espera a que se envíe.
 */
public final class Responder implements Etapa {

    private static final Logger LOG = Logger.getLogger(Responder.class.getName());
    private static final String NOMBRE_REMITENTE = "Sistema Canal 11";

    private final String correoSistema;
    private final Consumer<CorreoSaliente> envio;

    public Responder(String correoSistema, Consumer<CorreoSaliente> envio) {
        this.correoSistema = correoSistema;
        this.envio = envio;
    }

    @Override
    public boolean siempre() {
        return true;
    }

    @Override
    public void procesar(Pedido p) {
        RespuestaCorreo r = p.respuesta();
        if (r == null) {
            return;
        }
        try {
            CorreoSaliente.Builder correo = CorreoSaliente.builder()
                    .de(correoSistema, NOMBRE_REMITENTE)
                    .para(p.remitenteCorreo())
                    .asunto(r.asunto())
                    .html(r.html())
                    .enRespuestaA(p.messageId());
            for (Adjunto a : r.adjuntos()) {
                correo.adjunto(a.nombre(), a.tipoContenido(), a.contenido());
            }
            envio.accept(correo.build());
        } catch (IllegalArgumentException e) {
            LOG.warning("No se puede responder a «" + p.remitenteCorreo() + "»: " + e.getMessage());
        }
    }
}
