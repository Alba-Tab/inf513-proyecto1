package bo.edu.uagrm.ficct.canal11.comandos;

import bo.edu.uagrm.ficct.canal11.datos.Fila;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fila «Total» al pie de un reporte (plantilla P9, decisión D30): suma las columnas indicadas y deja
 * las demás vacías.
 */
public final class Totales {

    private Totales() {
    }

    /** Las filas más una fila final con la etiqueta «Total» en la primera columna. */
    public static List<Fila> conTotal(List<Fila> filas, String... columnasSumadas) {
        if (filas.isEmpty()) {
            return filas;
        }
        Set<String> sumadas = Set.of(columnasSumadas);
        Map<String, Object> total = new LinkedHashMap<>();
        List<String> columnas = filas.get(0).columnas();
        for (int i = 0; i < columnas.size(); i++) {
            String c = columnas.get(i);
            if (i == 0) {
                total.put(c, "Total");
            } else if (sumadas.contains(c)) {
                BigDecimal suma = BigDecimal.ZERO;
                for (Fila f : filas) {
                    Object v = f.get(c);
                    if (v != null) {
                        suma = suma.add(new BigDecimal(v.toString()));
                    }
                }
                total.put(c, suma);
            } else {
                total.put(c, "");
            }
        }
        List<Fila> resultado = new ArrayList<>(filas);
        resultado.add(new Fila(total));
        return resultado;
    }
}
