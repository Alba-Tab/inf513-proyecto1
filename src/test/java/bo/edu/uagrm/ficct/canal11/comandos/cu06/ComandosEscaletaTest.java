package bo.edu.uagrm.ficct.canal11.comandos.cu06;

import bo.edu.uagrm.ficct.canal11.datos.Fila;
import bo.edu.uagrm.ficct.canal11.comandos.Adjunto;
import bo.edu.uagrm.ficct.canal11.comandos.Probador;
import bo.edu.uagrm.ficct.canal11.pipeline.RespuestaCorreo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

import static bo.edu.uagrm.ficct.canal11.comandos.Probador.CAMAROGRAFO;
import static bo.edu.uagrm.ficct.canal11.comandos.Probador.DIRECTOR;
import static bo.edu.uagrm.ficct.canal11.comandos.Probador.EDITOR;
import static bo.edu.uagrm.ficct.canal11.comandos.Probador.JEFE_PRENSA;
import static bo.edu.uagrm.ficct.canal11.comandos.Probador.PRESENTADOR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CU-06 (escaleta e ítems): un caso de éxito y uno de error por comando.
 *
 * <p>La escaleta 1 es la de hoy (borrador) y la 2 la de ayer (emitida). Ítems de la 1: cortes 1 a 3;
 * bloque A 7 a 11 (9 y 10 son las notas 1 y 2); bloque B 12 a 15; 16 la nota 8 flotada en C; 17 la
 * despedida en D.</p>
 */
@Tag("bd")
class ComandosEscaletaTest {

    private static final LocalDate HOY = LocalDate.now(ZoneId.of("America/La_Paz"));
    private static final LocalDate LUNES = HOY.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).plusWeeks(3);

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

    private Fila item(int id) throws Exception {
        return p.uno("SELECT bloque, orden, estado, duracion_estimada, presentador_id FROM item_escaleta WHERE id = ?", id).orElseThrow();
    }

    @Test
    void ultimaHora() {
        LocalDate d = LocalDate.of(2026, 10, 12);
        assertFalse(ComandosEscaleta.esUltimaHora(d, LocalTime.of(20, 0), LocalDateTime.of(2026, 10, 12, 18, 59)));
        assertTrue(ComandosEscaleta.esUltimaHora(d, LocalTime.of(20, 0), LocalDateTime.of(2026, 10, 12, 19, 0)));
    }

    // ------------------------------------------------------------------ escaleta

    @Test
    void lisescConTiemposYAlertas() {
        contiene(p.texto(PRESENTADOR, "LISESC[\"1\"]"), "Escaleta Noticiero Central", "bloque(s) con alerta de tiempo",
                "Bloque B: exceso", "PARO-TRANSPORTE", "material sin terminar", "CORTE 1", "Tiempos por bloque", "Sale");
        contiene(p.texto(DIRECTOR, "LISESC[\"*\"]"), "2 registros.", "emitida", "Bloques con alerta");
        contiene(p.texto(DIRECTOR, "LISESC[\"99\"]"), "Error: el registro no existe");
        contiene(p.texto(CAMAROGRAFO, "LISESC[\"1\"]"), "Error: no tiene permiso para ejecutar LISESC");
    }

    @Test
    void lisescMarcaUnaNotaQueVolvioARevision() {
        p.texto(Probador.PERIODISTA, "GUINOT[\"1\"]", "Guion corregido a último momento");
        contiene(p.texto(PRESENTADOR, "LISESC[\"1\"]"), "nota en revisión");
    }

    @Test
    void insescGeneraLaEscaletaConSusCortes() throws Exception {
        contiene(p.texto(JEFE_PRENSA, "INSESC[\"1\",\"" + LUNES + "\",\"1\"]"),
                "Éxito: escaleta generada correctamente", "CORTE 1", "CORTE 3", "INSITE[\"");
        long id = p.numero("SELECT id FROM escaleta WHERE fecha = ?", LUNES);
        assertEquals(3, p.numero("SELECT count(*) FROM item_escaleta WHERE escaleta_id = ? AND tipo = 'corte' AND orden = 999", (int) id));
    }

    @Test
    void updescCierraEmiteYPasaLasNotasAEmitidas() throws Exception {
        contiene(p.texto(JEFE_PRENSA, "UPDESC[\"1\",\"emitida\",\"20:00:20\",\"60:42\"]"), "Error: cierre la escaleta antes de registrarla como emitida");
        contiene(p.texto(JEFE_PRENSA, "UPDESC[\"1\",\"cerrada\",\"\",\"\"]"), "Éxito: escaleta cerrada correctamente", "cerrada");
        contiene(p.texto(JEFE_PRENSA, "INSITE[\"1\",\"B\",\"otro\",\"Breve\",\"\",\"00:20\"]"), "Error: la escaleta no está en borrador");
        contiene(p.texto(JEFE_PRENSA, "UPDESC[\"1\",\"emitida\",\"\",\"\"]"), "Error: indique la hora de inicio y la duración reales");
        contiene(p.texto(JEFE_PRENSA, "UPDESC[\"1\",\"emitida\",\"20:00:20\",\"60:42\"]"),
                "Éxito: emisión registrada correctamente", "4 nota(s) pasaron a emitidas", "20:00:20", "60:42");
        assertEquals("emitida", p.uno("SELECT estado FROM nota WHERE id = 1").orElseThrow().texto("estado"));
        assertEquals("flotado", item(16).texto("estado"));
        assertEquals("emitido", item(9).texto("estado"));
    }

    @Test
    void updescReabreYRechazaLoEmitido() {
        contiene(p.texto(JEFE_PRENSA, "UPDESC[\"1\",\"cerrada\",\"\",\"\"]"), "cerrada");
        contiene(p.texto(JEFE_PRENSA, "UPDESC[\"1\",\"borrador\",\"\",\"\"]"), "Éxito: escaleta reabierta correctamente");
        contiene(p.texto(JEFE_PRENSA, "UPDESC[\"1\",\"borrador\",\"\",\"\"]"), "Error: la escaleta ya está borrador");
        contiene(p.texto(JEFE_PRENSA, "UPDESC[\"1\",\"cerrada\",\"20:00\",\"\"]"), "Error: la hora y la duración reales se registran al marcarla como emitida");
        contiene(p.texto(JEFE_PRENSA, "UPDESC[\"2\",\"emitida\",\"20:00:20\",\"60:42\"]"), "Error: la escaleta ya fue emitida");
    }


    // ------------------------------------------------------------------ ítems

    @Test
    void insiteAlFinalDelBloque() throws Exception {
        contiene(p.texto(JEFE_PRENSA, "INSITE[\"1\",\"B\",\"enlace_vivo\",\"Enlace: paro de transporte\",\"10\",\"02:00\"]"),
                "Éxito: ítem agregado correctamente", "Enlace: paro de transporte", "Mariana Terceros Lara", "Bloque B: usado");
        Fila nuevo = p.uno("SELECT orden FROM item_escaleta WHERE titulo = 'Enlace: paro de transporte' AND escaleta_id = 1 AND orden = 5")
                .orElseThrow();
        assertEquals(5, nuevo.entero("orden"));
        p.ejecutar("UPDATE nota SET estado = 'aprobada' WHERE id = 3");
        contiene(p.texto(JEFE_PRENSA, "INSITE[\"1\",\"C\",\"nota\",\"3\",\"11\",\"\"]"), "UAGRM-AUTORIDADES (nota 3)");
    }






    // ------------------------------------------------------------------ exportar

    @Test
    void expescAdjuntaLaEscaletaEnHtml() {
        RespuestaCorreo r = p.enviar(EDITOR, "EXPESC[\"1\"]", "");
        contiene(Probador.textoVisible(r.html()), "Éxito: escaleta exportada correctamente", "Se adjunta la escaleta 1");
        assertEquals(1, r.adjuntos().size());
        Adjunto a = r.adjuntos().get(0);
        assertEquals("escaleta-" + HOY + "-noticiero-central.html", a.nombre());
        String html = new String(a.contenido(), StandardCharsets.UTF_8);
        assertTrue(html.startsWith("<!DOCTYPE html>"));
        assertTrue(html.contains("Tiempos por bloque"));
        contiene(p.texto(EDITOR, "EXPESC[\"99\"]"), "Error: el registro no existe");
        contiene(p.texto(CAMAROGRAFO, "EXPESC[\"1\"]"), "Error: no tiene permiso para ejecutar EXPESC");
    }

    @Test
    void insescErrores() throws Exception {
        contiene(p.texto(JEFE_PRENSA, "INSESC[\"1\",\"" + LUNES + "\",\"2\"]"), "Error: la plantilla es de otro programa");
        contiene(p.texto(JEFE_PRENSA, "INSESC[\"1\",\"" + LUNES.plusDays(1) + "\",\"1\"]"), "no es lunes, el día de ese horario de grilla");
        int hoy = HOY.getDayOfWeek().getValue();
        contiene(p.texto(JEFE_PRENSA, "INSESC[\"" + hoy + "\",\"" + HOY + "\",\"1\"]"), "Error: ya existe una escaleta para ese horario y esa fecha");
        contiene(p.texto(JEFE_PRENSA, "INSESC[\"13\",\"" + LUNES + "\",\"1\"]"), "Error: Película de la noche no es un noticiero ni un especial");
    }

    @Test
    void deliteDejaDeSumarTiempo() throws Exception {
        contiene(p.texto(JEFE_PRENSA, "DELITE[\"9\"]"), "Éxito: ítem dado de baja correctamente", "baja");
        contiene(p.texto(JEFE_PRENSA, "DELITE[\"9\"]"), "Error: el ítem ya está baja");
        assertEquals(0, p.numero("SELECT count(*) FROM v_escaleta_grilla WHERE item_id = 9"));
        contiene(p.texto(JEFE_PRENSA, "DELITE[\"1\"]"), "Error: los cortes los genera el sistema: no se dan de baja");
        contiene(p.texto(JEFE_PRENSA, "DELITE[\"19\"]"), "Error: la escaleta no está en borrador");
    }
}
