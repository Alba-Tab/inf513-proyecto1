package bo.edu.uagrm.ficct.canal11.datos;

import org.junit.jupiter.api.Assumptions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.ZoneId;
import java.util.stream.Collectors;

/**
 * Base de datos para las pruebas {@code @Tag("bd")}.
 *
 * <p>Se activa con la variable de entorno {@code CANAL11_BD_PRUEBA_URL}
 * (por ejemplo {@code jdbc:postgresql://127.0.0.1:5432/db_grupo14sa_test}), más
 * {@code CANAL11_BD_PRUEBA_USUARIO} y {@code CANAL11_BD_PRUEBA_CONTRASENA}. Sin la variable, esas
 * pruebas se saltan.</p>
 *
 * <p>La primera vez carga {@code db/script_db_grupo14sa.sql} y {@code db/datos_prueba.sql}. Como el
 * script borra todas las tablas, solo acepta una base cuyo nombre contenga «test».</p>
 */
public final class BaseDatosPrueba {

    private static final String URL = System.getenv("CANAL11_BD_PRUEBA_URL");
    private static final String USUARIO = valorOPorDefecto("CANAL11_BD_PRUEBA_USUARIO", "postgres");
    private static final String CONTRASENA = valorOPorDefecto("CANAL11_BD_PRUEBA_CONTRASENA", "");
    static final ZoneId ZONA = ZoneId.of("America/La_Paz");

    private static DBConnection compartida;

    private BaseDatosPrueba() {
    }

    /** Conexión compartida, con el esquema y los datos de prueba cargados. Salta la prueba si no hay base. */
    public static synchronized DBConnection conexion() throws IOException, SQLException {
        requerirBase();
        if (compartida == null) {
            DBConnection db = nueva();
            Connection c;
            try {
                c = db.obtener();
            } catch (SQLException e) {
                Assumptions.abort("No se pudo conectar a la base de pruebas: " + e.getMessage());
                return null;
            }
            String nombre = nombreBase(c);
            if (!nombre.contains("test")) {
                db.cerrar();
                throw new IllegalStateException("La base de pruebas «" + nombre
                        + "» debe tener «test» en el nombre: los scripts borran todas las tablas");
            }
            ejecutarArchivo(c, Path.of("db/script_db_grupo14sa.sql"));
            ejecutarArchivo(c, Path.of("db/datos_prueba.sql"));
            compartida = db;
        }
        return compartida;
    }

    /** Una conexión aparte a la misma base (para probar cortes y reconexiones). Salta la prueba si no hay base. */
    public static DBConnection nueva() {
        requerirBase();
        return new DBConnection(URL, USUARIO, CONTRASENA, ZONA, 30);
    }

    private static void requerirBase() {
        Assumptions.assumeTrue(URL != null && !URL.isBlank(),
                "Sin CANAL11_BD_PRUEBA_URL se saltan las pruebas de base de datos");
    }

    /**
     * Ejecuta un archivo SQL completo. Quita las líneas de psql que empiezan con «\» ({@code \set},
     * {@code \echo}…), que no son SQL.
     */
    public static void ejecutarArchivo(Connection c, Path archivo) throws IOException, SQLException {
        try (Statement st = c.createStatement()) {
            st.execute(sinComandosPsql(Files.readString(archivo, StandardCharsets.UTF_8)));
        }
    }

    static String sinComandosPsql(String sql) {
        return sql.lines()
                .filter(l -> !l.stripLeading().startsWith("\\"))
                .collect(Collectors.joining("\n"));
    }

    /** Ejecuta {@code accion} dentro de una transacción que siempre se deshace. */
    public static <T> T enTransaccion(AccionSql<T> accion) throws Exception {
        Connection c = conexion().obtener();
        c.setAutoCommit(false);
        try {
            return accion.ejecutar(c);
        } finally {
            c.rollback();
            c.setAutoCommit(true);
        }
    }

    private static String nombreBase(Connection c) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT current_database()")) {
            rs.next();
            return rs.getString(1);
        }
    }

    private static String valorOPorDefecto(String variable, String porDefecto) {
        String v = System.getenv(variable);
        return v == null ? porDefecto : v;
    }

    @FunctionalInterface
    public interface AccionSql<T> {
        T ejecutar(Connection c) throws Exception;
    }
}
