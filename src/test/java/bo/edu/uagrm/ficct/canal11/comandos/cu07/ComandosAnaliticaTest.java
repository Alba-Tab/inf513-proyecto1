package bo.edu.uagrm.ficct.canal11.comandos.cu07;

import bo.edu.uagrm.ficct.canal11.comandos.Probador;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;

import static bo.edu.uagrm.ficct.canal11.comandos.Probador.DIRECTOR;
import static bo.edu.uagrm.ficct.canal11.comandos.Probador.JEFE_PRENSA;
import static bo.edu.uagrm.ficct.canal11.comandos.Probador.PERIODISTA;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CU-07: un caso de éxito y uno de error por comando. El período de las dos últimas semanas incluye
 * las notas y coberturas de ayer y de hoy y la emisión de ayer.
 */
@Tag("bd")
class ComandosAnaliticaTest {

    private static final LocalDate HOY = LocalDate.now(ZoneId.of("America/La_Paz"));
    private static final String PERIODO = "[\"" + HOY.minusDays(13) + "\",\"" + HOY + "\"]";
    private static final String SIN_DATOS = "[\"2020-01-01\",\"2020-01-31\"]";

    private Probador p;

    @BeforeEach
    void abrir() throws Exception {
        p = Probador.abrir();
    }

    @AfterEach
    void cerrar() throws Exception {
        if (p != null) {
            p.close();
        }
    }

    private void contiene(String texto, String... esperados) {
        for (String e : esperados) {
            assertTrue(texto.contains(e), "Falta «" + e + "» en:\n" + texto);
        }
    }

    /** Período invertido, período sin datos y rol sin permiso (A1, A2 y R1). */
    private void erroresComunes(String codigo) {
        contiene(p.texto(DIRECTOR, codigo + "[\"" + HOY + "\",\"" + HOY.minusDays(1) + "\"]"), "Error: período inválido");
        contiene(p.texto(JEFE_PRENSA, codigo + SIN_DATOS), "No hay datos para el período indicado");
        contiene(p.texto(PERIODISTA, codigo + PERIODO), "Error: no tiene permiso para ejecutar " + codigo);
    }

    @Test
    void anaper() {
        // 11 notas de 4 autores; Marcelo tuvo una observada (FUL-CONFERENCIA): 2 de 3 a la primera
        contiene(p.texto(DIRECTOR, "ANAPER" + PERIODO), "Producción por periodista", "4 filas.", "Marcelo Justiniano Égüez",
                "67%", "100%", "Total", "11", "(h:mm)");
        erroresComunes("ANAPER");
    }

    @Test
    void anacob() {
        // 12 coberturas en el período; los actos institucionales: una pendiente y una cancelada
        contiene(p.texto(DIRECTOR, "ANACOB" + PERIODO), "Cumplimiento de coberturas por tipo", "5 filas.",
                "acto_institucional", "0%", "100%", "Total", "12");
        erroresComunes("ANACOB");
    }

}
