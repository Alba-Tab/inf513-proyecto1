package bo.edu.uagrm.ficct.canal11.datos;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("bd")
class DBConnectionTest {

    private static String escalar(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }

    @Test
    void laSesionUsaLaZonaHorariaDeBolivia() throws Exception {
        Connection c = BaseDatosPrueba.conexion().obtener();
        assertEquals("America/La_Paz", escalar(c, "SHOW TimeZone"));
        assertTrue(c.getAutoCommit());
    }

    @Test
    void devuelveLaMismaConexionMientrasSigaViva() throws Exception {
        DBConnection db = BaseDatosPrueba.conexion();
        assertSame(db.obtener(), db.obtener());
    }

    @Test
    void abreOtraConexionSiSeCerro() throws Exception {
        try (DBConnection db = BaseDatosPrueba.nueva()) {
            Connection primera = db.obtener();
            primera.close();
            Connection segunda = db.obtener();
            assertTrue(segunda.isValid(2));
            assertNotEquals(primera, segunda);
        }
    }

    @Test
    void reconectaSiElServidorCortaLaConexion() throws Exception {
        try (DBConnection db = BaseDatosPrueba.nueva()) {
            String pidAnterior = escalar(db.obtener(), "SELECT pg_backend_pid()");
            // Desde otra conexión, el servidor «mata» la sesión, como si se hubiera caído la red
            try (PreparedStatement ps = BaseDatosPrueba.conexion().obtener()
                    .prepareStatement("SELECT pg_terminate_backend(?)")) {
                ps.setInt(1, Integer.parseInt(pidAnterior));
                ps.execute();
            }
            Thread.sleep(300);
            String pidNuevo = escalar(db.obtener(), "SELECT pg_backend_pid()");
            assertNotEquals(pidAnterior, pidNuevo);
        }
    }

    @Test
    void fallaConUnaExcepcionSiNoPuedeConectar() {
        DBConnection db = new DBConnection("jdbc:postgresql://127.0.0.1:1/no_existe", "nadie", "nada", ZoneId.of("America/La_Paz"), 2);
        SQLException e = assertThrows(SQLException.class, db::obtener);
        assertEquals("Error: el sistema no puede acceder a la base de datos en este momento; intente más tarde",
                TraductorErrores.traducir(e, TraductorErrores.Operacion.CONSULTAR));
    }
}
