package bo.edu.uagrm.ficct.canal11.pipeline.etapas;

import bo.edu.uagrm.ficct.canal11.comandos.ErrorNegocio;
import bo.edu.uagrm.ficct.canal11.comandos.catalogo.CommandSpec;
import bo.edu.uagrm.ficct.canal11.pipeline.ComandoParseado.Comando;
import bo.edu.uagrm.ficct.canal11.pipeline.Etapa;
import bo.edu.uagrm.ficct.canal11.pipeline.Pedido;

import java.util.List;

/**
 * Etapa 7: cantidad y tipo de los parámetros (sección 4.2.2). Un LIS sin corchetes lista todo
 * ({@code "*"}); un INS, UPD o DEL sin corchetes responde el formato con un ejemplo.
 */
public final class Validar implements Etapa {

    @Override
    public void procesar(Pedido p) {
        CommandSpec spec = p.spec();
        Comando cmd = (Comando) p.parseado();
        List<String> valores = cmd.parametros();
        if (spec.operacion().equals("LIS") && valores.isEmpty()) {
            valores = List.of("*");
        } else if (!cmd.conCorchetes()) {
            Respuestas.errorDeComando(p, "Error: formato esperado " + spec.formato(), "");
            return;
        }
        try {
            p.parametros(spec.validar(valores));
        } catch (ErrorNegocio e) {
            Respuestas.errorDeComando(p, e.getMessage(), "");
        }
    }
}
