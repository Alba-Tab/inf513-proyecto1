package bo.edu.uagrm.ficct.canal11.comandos.cu08;

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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CU-08: un caso de éxito y uno de error por comando. Los datos de prueba son relativos a hoy: el
 * período de las dos últimas semanas incluye las coberturas, notas y emisiones de ayer y de hoy.
 */
@Tag("bd")
class ComandosReportesTest {

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

    /** Los tres errores comunes de los reportes: período invertido, sin datos y sin permiso. */
    private void erroresComunes(String codigo) {
        contiene(p.texto(DIRECTOR, codigo + "[\"" + HOY + "\",\"" + HOY.minusDays(1) + "\"]"), "Error: período inválido");
        contiene(p.texto(JEFE_PRENSA, codigo + SIN_DATOS), "No hay datos para el período indicado");
        contiene(p.texto(PERIODISTA, codigo + PERIODO), "Error: no tiene permiso para ejecutar " + codigo);
    }

    @Test
    void repcob() {
        // Carla: 1, 3 y 9; Marcelo: 2, 10 y 14; Daniela: 5, 11 y 13; Luis: 4 y 12
        contiene(p.texto(DIRECTOR, "REPCOB" + PERIODO), "Coberturas por periodista del " + HOY.minusDays(13), "4 filas.",
                "Carla Rojas Suárez", "Luis Fernando Añez Rivero", "Total", "11");
        erroresComunes("REPCOB");
    }

    @Test
    void reprec() {
        String t = p.texto(JEFE_PRENSA, "REPREC" + PERIODO);
        contiene(t, "Uso de equipos y unidades móviles", "MOV-01", "Horas", "Total", "recurso(s) no se usaron en el período");
        assertFalse(t.contains("CAM-04"), "CAM-04 no se usó: no aparece en la tabla");
        erroresComunes("REPREC");
    }

    @Test
    void lisbit() {
        contiene(p.texto(DIRECTOR, "LISBIT[\"" + HOY + "\"]"), "Bitácora del " + HOY, "6 registros.", "desconocido@gmail.com");
        contiene(p.texto(DIRECTOR, "LISBIT[\"desconocido\"]"), "1 registro.");
        contiene(p.texto(DIRECTOR, "LISBIT[\"inscpe\"]"), "Bitácora de INSCPE", "1 registro.", "tiene permiso ese día");
        contiene(p.texto(DIRECTOR, "LISBIT"), "Bitácora: últimos registros");
        contiene(p.texto(DIRECTOR, "LISBIT[\"nadie@nada.com\"]"), "No hay registros en la bitácora con ese filtro");
        contiene(p.texto(JEFE_PRENSA, "LISBIT[\"*\"]"), "Error: no tiene permiso para ejecutar LISBIT");
    }

    @Test
    void lisbitNoMuestraLaContrasena() {
        p.texto(DIRECTOR, "INSUSU[\"Ana\",\"Paz\",\"9100001\",\"ana.paz@gmail.com\",\"\",\"clave-secreta\",\"3\"]");
        String t = p.texto(DIRECTOR, "LISBIT[\"INSUSU\"]");
        contiene(t, "Bitácora de INSUSU", "******", "Éxito: usuario registrado correctamente");
        assertFalse(t.contains("clave-secreta"), t);
    }
}
