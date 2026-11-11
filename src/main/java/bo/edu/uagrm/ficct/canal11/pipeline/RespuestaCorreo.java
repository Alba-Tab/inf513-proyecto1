package bo.edu.uagrm.ficct.canal11.pipeline;

import bo.edu.uagrm.ficct.canal11.comandos.Adjunto;

import java.util.List;
import java.util.Objects;

/**
 * Lo que el pipeline devuelve para responder: el asunto y el cuerpo en HTML.
 *
 * <p>El asunto nunca repite un dato sensible (decisión D6): se arma con {@link Enmascarador}.</p>
 *
 * @param adjuntos archivos que viajan con la respuesta (EXPESC); casi siempre vacío
 */
public record RespuestaCorreo(String asunto, String html, List<Adjunto> adjuntos) {
    public RespuestaCorreo {
        Objects.requireNonNull(asunto);
        Objects.requireNonNull(html);
        adjuntos = adjuntos == null ? List.of() : List.copyOf(adjuntos);
    }
}
