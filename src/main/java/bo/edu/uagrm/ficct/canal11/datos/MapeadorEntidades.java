package bo.edu.uagrm.ficct.canal11.datos;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mapeador de Entidades del documento: convierte un {@link ResultSet} en una lista de {@link Fila}.
 *
 * <p>Usa los metadatos del resultado, así que sirve para cualquier consulta. Las fechas y horas
 * pasan a {@code java.time}; el resto queda como lo entrega el driver.</p>
 */
public final class MapeadorEntidades {

    private MapeadorEntidades() {
    }

    public static List<Fila> mapear(ResultSet rs) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        int n = md.getColumnCount();
        List<Fila> filas = new ArrayList<>();
        while (rs.next()) {
            Map<String, Object> valores = new LinkedHashMap<>();
            for (int i = 1; i <= n; i++) {
                valores.put(md.getColumnLabel(i), leer(rs, md.getColumnType(i), i));
            }
            filas.add(new Fila(valores));
        }
        return filas;
    }

    private static Object leer(ResultSet rs, int tipo, int i) throws SQLException {
        return switch (tipo) {
            case Types.DATE -> rs.getObject(i, LocalDate.class);
            case Types.TIME -> rs.getObject(i, LocalTime.class);
            case Types.TIMESTAMP -> rs.getObject(i, LocalDateTime.class);
            default -> rs.getObject(i);
        };
    }
}
