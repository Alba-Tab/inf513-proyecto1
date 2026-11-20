package bo.edu.uagrm.ficct.canal11.datos;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;

/**
 * Conexión para probar comandos sin dejar rastro en la base de pruebas.
 *
 * <p>Todo corre dentro de una transacción que se deshace al cerrar. Lo que {@code CommandProcessor}
 * cree que es una transacción (autocommit en falso, commit, rollback) se traduce a un savepoint
 * dentro de esa transacción grande: un comando que falla se deshace igual que en producción, y uno
 * que sale bien queda visible para el resto de la prueba.</p>
 */
public final class ConexionDePrueba implements AutoCloseable {

    private final Connection real;
    private final Connection proxy;
    private Savepoint savepoint;

    private ConexionDePrueba(Connection real) {
        this.real = real;
        this.proxy = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class}, new Manejador());
    }

    /** Abre la transacción sobre la base de pruebas compartida (salta la prueba si no hay base). */
    public static ConexionDePrueba abrir() throws Exception {
        Connection c = BaseDatosPrueba.conexion().obtener();
        c.setAutoCommit(false);
        return new ConexionDePrueba(c);
    }

    /** La conexión para entregar al código que se prueba. */
    public Connection conexion() {
        return proxy;
    }

    /** Deshace todo lo hecho durante la prueba. */
    @Override
    public void close() throws SQLException {
        try {
            real.rollback();
        } finally {
            real.setAutoCommit(true);
        }
    }

    private final class Manejador implements InvocationHandler {
        @Override
        public Object invoke(Object p, Method m, Object[] args) throws Throwable {
            switch (m.getName()) {
                case "setAutoCommit" -> {
                    boolean auto = (Boolean) args[0];
                    if (!auto && savepoint == null) {
                        savepoint = real.setSavepoint();
                        // Una transacción nueva empieza con las restricciones diferibles en su modo inicial
                        // (diferido); sin esto, el SET CONSTRAINTS ALL IMMEDIATE del comando anterior seguiría vigente
                        try (Statement st = real.createStatement()) {
                            st.execute("SET CONSTRAINTS ALL DEFERRED");
                        }
                    } else if (auto && savepoint != null) {
                        real.releaseSavepoint(savepoint);
                        savepoint = null;
                    }
                    return null;
                }
                case "getAutoCommit" -> {
                    return savepoint == null;
                }
                case "commit" -> {
                    if (savepoint != null) {
                        real.releaseSavepoint(savepoint);
                        savepoint = real.setSavepoint();
                    }
                    return null;
                }
                case "rollback" -> {
                    if (args == null && savepoint != null) {
                        real.rollback(savepoint);
                        return null;
                    }
                    if (args == null) {
                        return null;
                    }
                    break;
                }
                case "close" -> {
                    return null;
                }
                default -> {
                    // se delega
                }
            }
            try {
                return m.invoke(real, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }
}
