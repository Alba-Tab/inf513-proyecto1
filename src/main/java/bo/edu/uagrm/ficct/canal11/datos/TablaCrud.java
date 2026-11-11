package bo.edu.uagrm.ficct.canal11.datos;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Altas, cambios y bajas genéricos para los comandos simples (decisión D11): INSREC, UPDPER,
 * DELCOB…
 *
 * <p>Los nombres de la tabla y de las columnas vienen del catálogo de comandos, nunca del correo, y
 * además se verifica que sean identificadores simples. Los valores siempre van como parámetros.</p>
 *
 * <p>En {@link #actualizar}, un valor {@code null} deja la columna como está: es el {@code ""} de
 * un UPD («no cambiar»).</p>
 */
public final class TablaCrud {

    private static final Pattern IDENTIFICADOR = Pattern.compile("^[a-z_][a-z0-9_]*$");

    /** Tablas de historial, sin columna {@code actualizado_en}. */
    private static final Set<String> SIN_MARCA = Set.of("mantenimiento", "nota_version", "revision_nota", "bitacora_comando");

    private TablaCrud() {
    }

    /** {@code INSERT INTO tabla (…) VALUES (…) RETURNING id}: el id nuevo. */
    public static int insertar(Repositorio r, Connection c, String tabla, Map<String, Object> valores) throws SQLException {
        verificar(tabla);
        List<String> columnas = new ArrayList<>(valores.keySet());
        columnas.forEach(TablaCrud::verificar);
        String sql = "INSERT INTO " + tabla + " (" + String.join(", ", columnas) + ") VALUES ("
                + String.join(", ", columnas.stream().map(x -> "?").toList()) + ") RETURNING id";
        return r.ejecutarDevolviendo(c, sql, valores.values().toArray()).get(0).entero("id");
    }

    /**
     * {@code UPDATE tabla SET col = COALESCE(?, col)… WHERE id = ?}. Devuelve vacío si el id no existe.
     */
    public static Optional<Integer> actualizar(Repositorio r, Connection c, String tabla, int id,
                                               Map<String, Object> valores) throws SQLException {
        verificar(tabla);
        List<String> asignaciones = new ArrayList<>();
        List<Object> parametros = new ArrayList<>();
        for (Map.Entry<String, Object> e : valores.entrySet()) {
            verificar(e.getKey());
            asignaciones.add(e.getKey() + " = COALESCE(?, " + e.getKey() + ")");
            parametros.add(e.getValue());
        }
        if (!SIN_MARCA.contains(tabla)) {
            asignaciones.add("actualizado_en = CURRENT_TIMESTAMP");
        }
        parametros.add(id);
        String sql = "UPDATE " + tabla + " SET " + String.join(", ", asignaciones) + " WHERE id = ? RETURNING id";
        return r.ejecutarDevolviendo(c, sql, parametros.toArray()).stream().findFirst().map(f -> f.entero("id"));
    }

    /** {@code DELETE FROM tabla WHERE id = ?}: {@code true} si borró. */
    public static boolean eliminar(Repositorio r, Connection c, String tabla, int id) throws SQLException {
        verificar(tabla);
        return r.ejecutar(c, "DELETE FROM " + tabla + " WHERE id = ?", id) > 0;
    }

    /** Pasa {@code estado} a {@code inactivo} (o el valor dado): {@code true} si existía. */
    public static boolean cambiarEstado(Repositorio r, Connection c, String tabla, int id, String estado) throws SQLException {
        verificar(tabla);
        String marca = SIN_MARCA.contains(tabla) ? "" : ", actualizado_en = CURRENT_TIMESTAMP";
        return r.ejecutar(c, "UPDATE " + tabla + " SET estado = ?" + marca + " WHERE id = ?", estado, id) > 0;
    }

    private static void verificar(String identificador) {
        if (!IDENTIFICADOR.matcher(identificador).matches()) {
            throw new IllegalArgumentException("Identificador SQL inválido: " + identificador);
        }
    }
}
