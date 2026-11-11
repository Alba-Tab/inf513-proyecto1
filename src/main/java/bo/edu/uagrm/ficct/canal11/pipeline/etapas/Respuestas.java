package bo.edu.uagrm.ficct.canal11.pipeline.etapas;

import bo.edu.uagrm.ficct.canal11.comandos.HTMLResponseBuilder;
import bo.edu.uagrm.ficct.canal11.comandos.catalogo.CommandSpec;
import bo.edu.uagrm.ficct.canal11.pipeline.Pedido;

/** Las respuestas de error que comparten las etapas (plantillas de la Tabla 77). */
final class Respuestas {

    static final String NO_REGISTRADO = "No está registrado en el sistema. Contacte al Director del canal.";

    private Respuestas() {
    }

    static void error(Pedido p, String mensaje) {
        p.error(HTMLResponseBuilder.error(mensaje), mensaje);
    }

    /** Error con el formato y el ejemplo del comando, si se sabe cuál es (plantilla P5). */
    static void errorDeComando(Pedido p, String mensaje, String detalleHtml) {
        CommandSpec spec = p.spec();
        String html = spec == null
                ? HTMLResponseBuilder.errorDeComando("Error", mensaje, detalleHtml, null, null)
                : HTMLResponseBuilder.errorDeComando(spec.codigo(), mensaje, detalleHtml, spec.formato(), spec.ejemplo());
        p.error(html, mensaje);
    }
}
