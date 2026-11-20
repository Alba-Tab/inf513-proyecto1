package bo.edu.uagrm.ficct.canal11.pipeline;

import java.util.List;
import java.util.Objects;

/**
 * Lo que {@link CommandParser} entendió del asunto de un correo: un pedido de AYUDA, un comando
 * con sus parámetros o un asunto inválido.
 */
public sealed interface ComandoParseado {

    /** El asunto es AYUDA: se responde con los comandos que puede usar el rol del remitente. */
    record Ayuda() implements ComandoParseado {
    }

    /**
     * Un comando con la forma correcta. Que el código exista y que el rol tenga permiso lo decide
     * {@code CommandProcessor}.
     *
     * @param codigo       las seis letras, en mayúsculas (por ejemplo {@code LISCOB})
     * @param parametros   los valores entre corchetes, sin comillas y sin espacios a los costados;
     *                     vacía si no hubo corchetes o si estaban vacíos
     * @param conCorchetes {@code false} si el asunto era solo el código (por ejemplo {@code LISCOB}):
     *                     un LIS se trata como {@code ["*"]} y los demás responden el formato esperado
     */
    record Comando(String codigo, List<String> parametros, boolean conCorchetes) implements ComandoParseado {
        public Comando {
            Objects.requireNonNull(codigo);
            parametros = List.copyOf(parametros);
        }

        /** Las tres primeras letras: LIS, INS, UPD, DEL, APR… */
        public String operacion() {
            return codigo.substring(0, 3);
        }

        /** Las tres últimas letras: la tabla (USU, COB, NOT…). */
        public String tabla() {
            return codigo.substring(3);
        }
    }

    /** Por qué no se pudo leer el asunto. */
    enum Motivo {
        /** No empieza con un código de seis letras ni es AYUDA. */
        NO_RECONOCIDO,
        /** Empieza con un código, pero los corchetes o las comillas están mal. */
        FORMATO
    }

    /**
     * Un asunto que no se pudo leer.
     *
     * @param motivo  si no hay código o si el formato está mal
     * @param codigo  el código, si se llegó a leer (para agregar el ejemplo del comando); si no, vacío
     * @param mensaje respuesta para el usuario, con el estilo de la Tabla 77 («Error: …»)
     */
    record Invalido(Motivo motivo, String codigo, String mensaje) implements ComandoParseado {
    }
}
