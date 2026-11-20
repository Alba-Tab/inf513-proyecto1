package bo.edu.uagrm.ficct.canal11.datos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Una fila de un resultado: columnas en el mismo orden de la consulta, con sus valores.
 *
 * <p>Las fechas y horas llegan como {@code java.time} ({@link LocalDate}, {@link LocalTime},
 * {@link LocalDateTime}). Las respuestas en HTML arman sus tablas a partir de filas, sin una clase
 * por cada una de las 26 tablas.</p>
 */
public final class Fila {

    private final Map<String, Object> valores;

    public Fila(Map<String, Object> valores) {
        this.valores = Collections.unmodifiableMap(new LinkedHashMap<>(valores));
    }

    /** Nombres de las columnas, en el orden de la consulta. */
    public List<String> columnas() {
        return new ArrayList<>(valores.keySet());
    }

    /** Valores, en el orden de las columnas. */
    public List<Object> valores() {
        return new ArrayList<>(valores.values());
    }

    public boolean tiene(String columna) {
        return valores.containsKey(columna);
    }

    /** Valor tal cual; {@code null} si es NULL. Falla si la columna no está en la consulta. */
    public Object get(String columna) {
        if (!valores.containsKey(columna)) {
            throw new IllegalArgumentException("La consulta no tiene la columna «" + columna + "»; tiene " + valores.keySet());
        }
        return valores.get(columna);
    }

    public String texto(String columna) {
        Object v = get(columna);
        return v == null ? null : v.toString();
    }

    public Integer entero(String columna) {
        Object v = get(columna);
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        return Integer.valueOf(v.toString());
    }

    public Long largo(String columna) {
        Object v = get(columna);
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        return Long.valueOf(v.toString());
    }

    public BigDecimal decimal(String columna) {
        Object v = get(columna);
        if (v == null) {
            return null;
        }
        if (v instanceof BigDecimal b) {
            return b;
        }
        return new BigDecimal(v.toString());
    }

    public Boolean logico(String columna) {
        Object v = get(columna);
        return v == null ? null : (Boolean) v;
    }

    public LocalDate fecha(String columna) {
        return (LocalDate) get(columna);
    }

    public LocalTime hora(String columna) {
        return (LocalTime) get(columna);
    }

    public LocalDateTime fechaHora(String columna) {
        return (LocalDateTime) get(columna);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Fila f && valores.equals(f.valores);
    }

    @Override
    public int hashCode() {
        return Objects.hash(valores);
    }

    @Override
    public String toString() {
        return valores.toString();
    }
}
