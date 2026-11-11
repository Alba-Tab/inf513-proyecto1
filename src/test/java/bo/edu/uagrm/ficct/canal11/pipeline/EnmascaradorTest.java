package bo.edu.uagrm.ficct.canal11.pipeline;

import bo.edu.uagrm.ficct.canal11.pipeline.ComandoParseado.Comando;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnmascaradorTest {

    private static final String INSUSU =
            "INSUSU[\"Carla\",\"Rojas Suárez\",\"3100003\",\"carla.rojas@gmail.com\",\"71234580\",\"clave123\",\"3\"]";

    @Test
    void ocultaLaContrasenaDeInsusu() {
        String asunto = Enmascarador.asunto("Re: " + INSUSU);
        assertEquals("INSUSU[\"Carla\",\"Rojas Suárez\",\"3100003\",\"carla.rojas@gmail.com\",\"71234580\",\"******\",\"3\"]", asunto);
        assertFalse(asunto.contains("clave123"));
    }

    @Test
    void unInsusuMalFormadoOcultaTodo() {
        String roto = INSUSU.substring(0, INSUSU.length() - 1); // sin el corchete de cierre
        assertEquals("INSUSU[******]", Enmascarador.asunto(roto));
    }

    @Test
    void sinCorchetesQuedaSoloElCodigo() {
        assertEquals("INSUSU", Enmascarador.asunto("insusu clave123"));
    }

    @Test
    void losDemasComandosQuedanIgual() {
        assertEquals("LISCOB[\"2026-10-12\"]", Enmascarador.asunto("  LISCOB[\"2026-10-12\"]  "));
        assertEquals("Hola", Enmascarador.asunto("Hola"));
        assertEquals("", Enmascarador.asunto(null));
        assertFalse(Enmascarador.esSensible("UPDUSU"));
        assertTrue(Enmascarador.esSensible("INSUSU"));
    }

    @Test
    void enmascaraLaListaDeParametros() {
        Comando c = (Comando) CommandParser.analizar(INSUSU);
        assertEquals(List.of("Carla", "Rojas Suárez", "3100003", "carla.rojas@gmail.com", "71234580", "******", "3"),
                Enmascarador.parametros(c));
        Comando corto = (Comando) CommandParser.analizar("INSUSU[\"Carla\"]");
        assertEquals(List.of("******"), Enmascarador.parametros(corto));
    }

    @Test
    void conOtraCantidadDeParametrosOcultaTodos() {
        // Sin teléfono (6 parámetros): la clave queda en la posición 4
        String sinTelefono = Enmascarador.asunto("INSUSU[\"Ana\",\"Paz\",\"9100001\",\"ana@x.com\",\"clave99\",\"3\"]");
        assertFalse(sinTelefono.contains("clave99"), sinTelefono);
        assertEquals("INSUSU[\"******\",\"******\",\"******\",\"******\",\"******\",\"******\"]", sinTelefono);
        // Un parámetro de más (8): tampoco se sabe dónde está la clave
        String conDeMas = Enmascarador.asunto("INSUSU[\"Ana\",\"Paz\",\"9100001\",\"ana@x.com\",\"\",\"x\",\"clave99\",\"3\"]");
        assertFalse(conDeMas.contains("clave99"), conDeMas);
    }
}
