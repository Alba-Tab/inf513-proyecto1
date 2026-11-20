package bo.edu.uagrm.ficct.canal11.comandos.catalogo;

import java.util.Objects;

/**
 * Un parámetro de un comando, con el nombre que usa el Anexo A ({@code id_rol}, {@code fecha}…).
 *
 * @param nombre   nombre del parámetro, tal como aparece en el Anexo A
 * @param tipo     cómo se valida y se convierte
 * @param opcional {@code true} si se puede enviar vacío ({@code ""}); en un UPD, vacío significa
 *                 «no cambiar»
 * @param omitible {@code true} si se puede no enviar (va al final y es opcional)
 */
public record ParamSpec(String nombre, Tipo tipo, boolean opcional, boolean omitible) {
    public ParamSpec {
        Objects.requireNonNull(nombre);
        Objects.requireNonNull(tipo);
    }
}
