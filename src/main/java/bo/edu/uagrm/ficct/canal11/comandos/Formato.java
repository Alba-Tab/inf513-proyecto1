package bo.edu.uagrm.ficct.canal11.comandos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Cómo se muestran los valores en las respuestas: fechas AAAA-MM-DD, horas HH:MM y duraciones
 * mm:ss, igual que se escriben en los comandos (sección 4.2.2).
 */
public final class Formato {

    public static final String VACIO = "—";

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter HORA_SEGUNDOS = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private Formato() {
    }

    /** Un valor de la base como texto para el usuario; {@code null} se muestra como «—». */
    public static String valor(Object v) {
        if (v == null) {
            return VACIO;
        }
        if (v instanceof LocalDate d) {
            return d.toString();
        }
        if (v instanceof LocalTime t) {
            return hora(t);
        }
        if (v instanceof LocalDateTime dt) {
            return dt.format(FECHA_HORA);
        }
        if (v instanceof Boolean b) {
            return b ? "sí" : "no";
        }
        if (v instanceof BigDecimal b) {
            return b.stripTrailingZeros().toPlainString();
        }
        String s = v.toString();
        return s.isEmpty() ? VACIO : s;
    }

    /** {@code 20:00} o, si tiene segundos, {@code 20:00:20}. */
    public static String hora(LocalTime t) {
        return t.getSecond() == 0 ? t.format(HORA) : t.format(HORA_SEGUNDOS);
    }

    /** Segundos como {@code mm:ss} (los minutos pueden pasar de 59: {@code 60:42}). */
    public static String duracion(Integer segundos) {
        if (segundos == null) {
            return VACIO;
        }
        int s = Math.abs(segundos);
        return (segundos < 0 ? "-" : "") + String.format("%02d:%02d", s / 60, s % 60);
    }

    /**
     * La misma conversión, como expresión SQL para usar dentro de una consulta:
     * {@code mmss("m.duracion")}. La columna sale del código, nunca del correo.
     */
    public static String mmss(String columnaSegundos) {
        return "CASE WHEN " + columnaSegundos + " IS NULL THEN NULL ELSE "
                + "(CASE WHEN " + columnaSegundos + " < 0 THEN '-' ELSE '' END) || "
                + "lpad((abs(" + columnaSegundos + ") / 60)::text, 2, '0') || ':' || lpad((abs(" + columnaSegundos + ") % 60)::text, 2, '0') END";
    }
}
