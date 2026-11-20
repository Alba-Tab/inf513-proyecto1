package bo.edu.uagrm.ficct.canal11.comandos;

import java.util.Objects;

/**
 * Un archivo que viaja con la respuesta (EXPESC adjunta la escaleta en HTML). La capa de
 * Presentación lo convierte en una parte {@code multipart/mixed} del correo.
 *
 * @param nombre        nombre del archivo, por ejemplo {@code escaleta-2026-10-12.html}
 * @param tipoContenido tipo MIME, por ejemplo {@code text/html; charset=UTF-8}
 */
public record Adjunto(String nombre, String tipoContenido, byte[] contenido) {
    public Adjunto {
        Objects.requireNonNull(nombre);
        Objects.requireNonNull(tipoContenido);
        Objects.requireNonNull(contenido);
    }
}
