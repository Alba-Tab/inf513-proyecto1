package bo.edu.uagrm.ficct.canal11.comandos;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Fragmentos de SQL que se repiten en los comandos. Reciben nombres de columnas escritos en el
 * código, nunca datos del correo: los datos van siempre como {@code ?}.
 */
public final class Sql {

    private Sql() {
    }

    /** La columna en minúsculas y sin tildes, para comparar con {@link #normal(String)}. */
    public static String sinTildes(String columna) {
        return "translate(lower(" + columna + "), 'áéíóúüñ', 'aeiouun')";
    }

    /** El texto en minúsculas y sin tildes (el lado Java de {@link #sinTildes(String)}). */
    public static String normal(String texto) {
        String sin = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sin.toLowerCase(Locale.ROOT).strip();
    }

    /** Patrón para {@code LIKE}: contiene el texto (escapa {@code % _ \}). */
    public static String contiene(String texto) {
        return "%" + normal(texto).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    /**
     * Un rango de fecha y hora legible: {@code 11:30–13:00} si es en el mismo día, o
     * {@code 2026-10-15 22:00 a 2026-10-16 02:00} si cruza la medianoche.
     */
    public static String rango(String inicio, String fin) {
        return "CASE WHEN " + inicio + "::date = " + fin + "::date THEN to_char(" + inicio + ", 'HH24:MI') || '–' || to_char(" + fin
                + ", 'HH24:MI') ELSE to_char(" + inicio + ", 'YYYY-MM-DD HH24:MI') || ' a ' || to_char(" + fin + ", 'YYYY-MM-DD HH24:MI') END";
    }
}
