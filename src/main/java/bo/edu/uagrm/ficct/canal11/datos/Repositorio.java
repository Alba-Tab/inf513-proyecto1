package bo.edu.uagrm.ficct.canal11.datos;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio de Datos del documento: ejecuta consultas con {@link PreparedStatement}.
 *
 * <p>Los valores siempre van como parámetros ({@code ?}); el texto del correo nunca se concatena
 * al SQL. Recibe la conexión en cada llamada porque la transacción la maneja quien llama
 * ({@code CommandProcessor}, decisión D12). Toda consulta tiene un tiempo máximo.</p>
 */
public final class Repositorio {

    private final int timeoutSegundos;

    public Repositorio(int timeoutSegundos) {
        this.timeoutSegundos = timeoutSegundos;
    }

    /** SELECT: todas las filas. */
    public List<Fila> consultar(Connection c, String sql, Object... parametros) throws SQLException {
        try (PreparedStatement ps = preparar(c, sql, parametros);
             ResultSet rs = ps.executeQuery()) {
            return MapeadorEntidades.mapear(rs);
        }
    }

    /** SELECT que devuelve a lo sumo una fila; si devuelve más, falla. */
    public Optional<Fila> consultarUno(Connection c, String sql, Object... parametros) throws SQLException {
        List<Fila> filas = consultar(c, sql, parametros);
        if (filas.size() > 1) {
            throw new IllegalStateException("Se esperaba una sola fila y la consulta devolvió " + filas.size());
        }
        return filas.stream().findFirst();
    }

    /** INSERT, UPDATE o DELETE sin RETURNING: cantidad de filas afectadas. */
    public int ejecutar(Connection c, String sql, Object... parametros) throws SQLException {
        try (PreparedStatement ps = preparar(c, sql, parametros)) {
            return ps.executeUpdate();
        }
    }

    /** INSERT, UPDATE o DELETE con RETURNING: las filas devueltas. */
    public List<Fila> ejecutarDevolviendo(Connection c, String sql, Object... parametros) throws SQLException {
        return consultar(c, sql, parametros);
    }

    private PreparedStatement preparar(Connection c, String sql, Object... parametros) throws SQLException {
        PreparedStatement ps = c.prepareStatement(sql);
        try {
            ps.setQueryTimeout(timeoutSegundos);
            asignar(ps, parametros);
            return ps;
        } catch (SQLException | RuntimeException e) {
            ps.close();
            throw e;
        }
    }

    /**
     * Asigna los parámetros en orden. Acepta {@code String}, números, {@code Boolean}, {@code java.time}
     * y {@code null}.
     */
    static void asignar(PreparedStatement ps, Object... parametros) throws SQLException {
        if (parametros == null) {
            return;
        }
        for (int i = 0; i < parametros.length; i++) {
            Object v = parametros[i];
            if (v == null) {
                ps.setNull(i + 1, Types.NULL);
            } else {
                ps.setObject(i + 1, v);
            }
        }
    }
}
