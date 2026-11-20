package bo.edu.uagrm.ficct.canal11.datos;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Corre {@code db/pruebas_integridad.sql} desde JUnit: las 21 pruebas del esquema deben dar OK. */
@Tag("bd")
class PruebasIntegridadTest {

    @Test
    void lasVeintiunPruebasDeIntegridadDanOk() throws Exception {
        String sql = BaseDatosPrueba.sinComandosPsql(
                Files.readString(Path.of("db/pruebas_integridad.sql"), StandardCharsets.UTF_8));
        Connection c = BaseDatosPrueba.conexion().obtener();
        List<String> resultados = new ArrayList<>();
        try (Statement st = c.createStatement()) {
            boolean hayResultado = st.execute(sql);
            while (true) {
                if (hayResultado) {
                    try (ResultSet rs = st.getResultSet()) {
                        if (rs.getMetaData().getColumnLabel(1).equals("resultado")) {
                            while (rs.next()) {
                                resultados.add(rs.getString(1));
                            }
                        }
                    }
                } else if (st.getUpdateCount() == -1) {
                    break;
                }
                hayResultado = st.getMoreResults();
            }
        } finally {
            // El script termina con ROLLBACK; si algo falló a mitad de camino, igual se deshace
            try (Statement st = c.createStatement()) {
                st.execute("ROLLBACK");
            }
        }
        assertEquals(21, resultados.size(), "Resultados: " + resultados);
        for (String r : resultados) {
            assertTrue(r.startsWith("OK"), r);
        }
    }
}
