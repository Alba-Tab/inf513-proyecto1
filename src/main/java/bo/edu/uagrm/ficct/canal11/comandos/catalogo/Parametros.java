package bo.edu.uagrm.ficct.canal11.comandos.catalogo;

import bo.edu.uagrm.ficct.canal11.comandos.ErrorNegocio;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Los parámetros de un comando ya validados y convertidos, por nombre.
 *
 * <p>Un parámetro opcional que llegó vacío ({@code ""}), uno omitible que no llegó y un
 * {@code "*"} de un filtro valen {@code null}. El texto original se conserva en
 * {@link #crudo(String)}.</p>
 */
public final class Parametros {

    private final Map<String, Object> valores;
    private final Map<String, String> crudos;

    Parametros(Map<String, Object> valores, Map<String, String> crudos) {
        this.valores = Collections.unmodifiableMap(new LinkedHashMap<>(valores));
        this.crudos = Collections.unmodifiableMap(new LinkedHashMap<>(crudos));
    }

    /** {@code true} si el parámetro tiene valor (no llegó vacío, ni omitido, ni como «*»). */
    public boolean tiene(String nombre) {
        return get(nombre) != null;
    }

    /** {@code true} si ningún parámetro tiene valor (un UPD sin nada que cambiar). */
    public boolean todosVacios(String... nombres) {
        for (String n : nombres) {
            if (tiene(n)) {
                return false;
            }
        }
        return true;
    }

    /** En un UPD: falla si no llegó ningún dato para cambiar. */
    public void requerirAlguno(String... nombres) throws ErrorNegocio {
        if (todosVacios(nombres)) {
            throw ErrorNegocio.de("indique al menos un dato para modificar (un parámetro vacío deja el dato como está)");
        }
    }

    public Object get(String nombre) {
        if (!valores.containsKey(nombre)) {
            throw new IllegalArgumentException("El comando no tiene el parámetro «" + nombre + "»; tiene " + valores.keySet());
        }
        return valores.get(nombre);
    }

    public String texto(String nombre) {
        return (String) get(nombre);
    }

    public Integer entero(String nombre) {
        return (Integer) get(nombre);
    }

    public LocalDate fecha(String nombre) {
        return (LocalDate) get(nombre);
    }

    public LocalTime hora(String nombre) {
        return (LocalTime) get(nombre);
    }

    public Momento momento(String nombre) {
        return (Momento) get(nombre);
    }

    public Boolean logico(String nombre) {
        return (Boolean) get(nombre);
    }

    /** El texto tal como llegó en el asunto (sin espacios a los costados); vacío si no llegó. */
    public String crudo(String nombre) {
        return crudos.getOrDefault(nombre, "");
    }

    @Override
    public String toString() {
        return valores.toString();
    }
}
