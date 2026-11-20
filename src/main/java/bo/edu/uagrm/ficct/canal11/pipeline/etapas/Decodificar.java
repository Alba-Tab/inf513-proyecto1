package bo.edu.uagrm.ficct.canal11.pipeline.etapas;

import bo.edu.uagrm.ficct.canal11.correo.MensajeCorreo;
import bo.edu.uagrm.ficct.canal11.correo.MimeParser;
import bo.edu.uagrm.ficct.canal11.pipeline.Etapa;
import bo.edu.uagrm.ficct.canal11.pipeline.Pedido;

/**
 * Etapa 1: lee el correo tal como llegó por RETR (MIME) y deja en el pedido el remitente, el
 * asunto, el cuerpo sin firma ni citas y el Message-ID. Nunca falla: lo ilegible queda vacío.
 */
public final class Decodificar implements Etapa {

    @Override
    public void procesar(Pedido p) {
        if (p.crudo() == null) {
            return; // ya viene decodificado (pruebas de los comandos)
        }
        MensajeCorreo m = MimeParser.leer(p.crudo());
        p.datosDelCorreo(m.remitente(), m.asunto(), m.cuerpoSinCitas(), m.messageId(), m.esAutomatico());
    }
}
