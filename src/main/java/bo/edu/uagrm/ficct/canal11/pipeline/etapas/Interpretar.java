package bo.edu.uagrm.ficct.canal11.pipeline.etapas;

import bo.edu.uagrm.ficct.canal11.comandos.catalogo.Catalogo;
import bo.edu.uagrm.ficct.canal11.comandos.catalogo.CommandSpec;
import bo.edu.uagrm.ficct.canal11.pipeline.ComandoParseado;
import bo.edu.uagrm.ficct.canal11.pipeline.ComandoParseado.Ayuda;
import bo.edu.uagrm.ficct.canal11.pipeline.ComandoParseado.Comando;
import bo.edu.uagrm.ficct.canal11.pipeline.ComandoParseado.Invalido;
import bo.edu.uagrm.ficct.canal11.pipeline.CommandParser;
import bo.edu.uagrm.ficct.canal11.pipeline.Etapa;
import bo.edu.uagrm.ficct.canal11.pipeline.Pedido;

/**
 * Etapa 4: entiende el asunto con {@link CommandParser} y busca la ficha del comando en el
 * catálogo. Un asunto mal formado responde el formato con el ejemplo del comando; un código que
 * no existe responde «comando no reconocido».
 */
public final class Interpretar implements Etapa {

    private final Catalogo catalogo;

    public Interpretar(Catalogo catalogo) {
        this.catalogo = catalogo;
    }

    @Override
    public void procesar(Pedido p) {
        ComandoParseado parseado = CommandParser.analizar(p.asunto());
        if (parseado instanceof Ayuda) {
            p.interpretado(parseado, null, "AYUDA");
            return;
        }
        if (parseado instanceof Invalido i) {
            CommandSpec spec = catalogo.buscar(i.codigo()).orElse(null);
            p.interpretado(parseado, spec, spec == null ? null : spec.codigo());
            Respuestas.errorDeComando(p, i.mensaje(), "");
            return;
        }
        Comando c = (Comando) parseado;
        CommandSpec spec = catalogo.buscar(c.codigo()).orElse(null);
        p.interpretado(parseado, spec, spec == null ? null : spec.codigo());
        if (spec == null) {
            Respuestas.error(p, CommandParser.NO_RECONOCIDO);
        }
    }
}
