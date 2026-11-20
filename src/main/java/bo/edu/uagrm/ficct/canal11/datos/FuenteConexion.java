package bo.edu.uagrm.ficct.canal11.datos;

import java.sql.Connection;
import java.sql.SQLException;

/** Quién da la conexión: {@link DBConnection} en la aplicación, una conexión de prueba en los tests. */
@FunctionalInterface
public interface FuenteConexion {
    Connection obtener() throws SQLException;
}
