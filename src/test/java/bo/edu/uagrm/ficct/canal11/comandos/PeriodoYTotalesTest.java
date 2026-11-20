package bo.edu.uagrm.ficct.canal11.comandos;

import bo.edu.uagrm.ficct.canal11.datos.Fila;
import bo.edu.uagrm.ficct.canal11.comandos.catalogo.Catalogo;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PeriodoYTotalesTest {

    private static Periodo periodo(String desde, String hasta) throws ErrorNegocio {
        return Periodo.de(Catalogo.completo().buscar("REPCOB").orElseThrow().validar(List.of(desde, hasta)));
    }

    @Test
    void periodoValido() throws ErrorNegocio {
        Periodo p = periodo("2026-10-01", "2026-10-31");
        assertEquals(LocalDate.of(2026, 10, 1), p.desde());
        assertEquals("del 2026-10-01 al 2026-10-31", p.texto());
        assertEquals("del 2026-10-12", periodo("2026-10-12", "2026-10-12").texto());
        periodo("2026-01-01", "2026-12-31");  // 365 días
    }

    @Test
    void periodoInvalido() {
        assertTrue(assertThrows(ErrorNegocio.class, () -> periodo("2026-10-31", "2026-10-01")).getMessage()
                .startsWith("Error: período inválido"));
        assertTrue(assertThrows(ErrorNegocio.class, () -> periodo("2025-01-01", "2026-10-01")).getMessage()
                .contains("pida hasta 366 días"));
    }

    private static Fila fila(String nombre, Object a, Object b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("Nombre", nombre);
        m.put("Cantidad", a);
        m.put("Horas", b);
        return new Fila(m);
    }

    @Test
    void filaDeTotales() {
        List<Fila> con = Totales.conTotal(List.of(fila("CAM-01", 2L, new BigDecimal("1.5")), fila("CAM-02", 3L, null)), "Cantidad", "Horas");
        assertEquals(3, con.size());
        Fila total = con.get(2);
        assertEquals("Total", total.texto("Nombre"));
        assertEquals(new BigDecimal("5"), total.decimal("Cantidad"));
        assertEquals(new BigDecimal("1.5"), total.decimal("Horas"));
        assertTrue(Totales.conTotal(List.of(), "Cantidad").isEmpty());
    }
}
