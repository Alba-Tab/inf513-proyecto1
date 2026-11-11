package bo.edu.uagrm.ficct.canal11.datos;

import bo.edu.uagrm.ficct.canal11.config.Config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.ZoneId;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controlador de conexión a PostgreSQL ({@code db_grupo14sa}), componente «DBConnection» del documento.
 *
 * <p>Mantiene una sola conexión, porque un solo hilo procesa los correos (decisión D3). Antes de
 * entregarla verifica que siga viva y, si se cortó, abre otra. Cada conexión nueva fija la zona
 * horaria de la sesión (D10), para que {@code CURRENT_DATE} y los choques de horario no dependan
 * de cómo arrancó la JVM.</p>
 */
public final class DBConnection implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(DBConnection.class.getName());
    private static final int SEGUNDOS_VALIDACION = 5;

    private final String url;
    private final String usuario;
    private final String contrasena;
    private final ZoneId zonaHoraria;
    private final int timeoutSegundos;

    private Connection conexion;

    public DBConnection(Config config) {
        this(config.bdUrl(), config.bdUsuario(), config.bdContrasena(), config.zonaHoraria(), config.timeoutSegundos());
    }

    public DBConnection(String url, String usuario, String contrasena, ZoneId zonaHoraria, int timeoutSegundos) {
        this.url = url;
        this.usuario = usuario;
        this.contrasena = contrasena;
        this.zonaHoraria = zonaHoraria;
        this.timeoutSegundos = timeoutSegundos;
    }

    /**
     * Devuelve una conexión válida, en modo autocommit. Si la anterior se cerró o se cortó, abre una nueva.
     *
     * @throws SQLException si no se puede conectar (la base no responde, credenciales inválidas…)
     */
    public synchronized Connection obtener() throws SQLException {
        if (conexion != null && !estaViva(conexion)) {
            LOG.warning("La conexión a la base de datos se cortó; se abre una nueva");
            cerrarSilencioso(conexion);
            conexion = null;
        }
        if (conexion == null) {
            conexion = abrir();
        }
        return conexion;
    }

    /** Cierra la conexión actual, si hay una. La siguiente llamada a {@link #obtener()} abre otra. */
    public synchronized void cerrar() {
        if (conexion != null) {
            cerrarSilencioso(conexion);
            conexion = null;
        }
    }

    @Override
    public void close() {
        cerrar();
    }

    private Connection abrir() throws SQLException {
        Properties p = new Properties();
        p.setProperty("user", usuario);
        p.setProperty("password", contrasena);
        p.setProperty("ApplicationName", "canal11-mail");
        p.setProperty("connectTimeout", String.valueOf(timeoutSegundos));
        p.setProperty("loginTimeout", String.valueOf(timeoutSegundos));
        // El socket espera un poco más que la consulta más lenta permitida (Repositorio usa timeoutSegundos)
        p.setProperty("socketTimeout", String.valueOf(timeoutSegundos * 2));
        p.setProperty("tcpKeepAlive", "true");

        Connection c = DriverManager.getConnection(url, p);
        try {
            try (PreparedStatement ps = c.prepareStatement("SELECT set_config('TimeZone', ?, false)")) {
                ps.setString(1, zonaHoraria.getId());
                ps.execute();
            }
            c.setAutoCommit(true);
        } catch (SQLException e) {
            cerrarSilencioso(c);
            throw e;
        }
        LOG.info("Conectado a la base de datos " + url + " (zona " + zonaHoraria + ")");
        return c;
    }

    private static boolean estaViva(Connection c) {
        try {
            return !c.isClosed() && c.isValid(SEGUNDOS_VALIDACION);
        } catch (SQLException e) {
            return false;
        }
    }

    private static void cerrarSilencioso(Connection c) {
        try {
            c.close();
        } catch (SQLException e) {
            LOG.log(Level.FINE, "Error al cerrar la conexión", e);
        }
    }
}
