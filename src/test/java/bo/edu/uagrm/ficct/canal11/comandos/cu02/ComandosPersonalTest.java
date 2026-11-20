package bo.edu.uagrm.ficct.canal11.comandos.cu02;

import bo.edu.uagrm.ficct.canal11.comandos.Probador;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

import static bo.edu.uagrm.ficct.canal11.comandos.Probador.DIRECTOR;
import static bo.edu.uagrm.ficct.canal11.comandos.Probador.JEFE_PRENSA;
import static bo.edu.uagrm.ficct.canal11.comandos.Probador.PERIODISTA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CU-02: un caso de éxito y uno de error por comando, con los ejemplos del Anexo A. */
@Tag("bd")
class ComandosPersonalTest {

    private static final LocalDate HOY = LocalDate.now(ZoneId.of("America/La_Paz"));
    /** Un lunes y un domingo dentro de tres semanas: sin coberturas ni ausencias en los datos de prueba. */
    private static final LocalDate LUNES = HOY.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).plusWeeks(3);
    private static final LocalDate DOMINGO = LUNES.plusDays(6);

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

    // ------------------------------------------------------------------ fichas

    @Test
    void lisper() {
        contiene(p.texto(JEFE_PRENSA, "LISPER[\"camarografo\"]"), "3 registros.", "Jorge Mendoza Ribera", "Mañana", "06:00–14:00");
        contiene(p.texto(DIRECTOR, "LISPER[\"*\"]"), "13 registros.");
        contiene(p.texto(DIRECTOR, "LISPER[\"practicante\"]"), "2 registros.");
        contiene(p.texto(DIRECTOR, "LISPER[\"carla\"]"), "1 registro.");
        contiene(p.texto(PERIODISTA, "LISPER[\"*\"]"), "Error: no tiene permiso para ejecutar LISPER");
    }

    @Test
    void insper() {
        contiene(p.texto(JEFE_PRENSA, "INSPER[\"15\",\"camarografo\",\"contrato\",\"2\",\"\",\"2026-01-05\"]"),
                "Éxito: ficha registrada correctamente", "Hugo Flores Arias", "camarografo", "Tarde", "2026-01-05");
    }

    @Test
    void insperErrores() {
        contiene(p.texto(JEFE_PRENSA, "INSPER[\"3\",\"periodista\",\"planta\",\"1\",\"\",\"2019-02-01\"]"),
                "Error: el usuario ya está registrado como personal");
        contiene(p.texto(JEFE_PRENSA, "INSPER[\"15\",\"periodista\",\"planta\",\"99\",\"\",\"\"]"), "Error: el turno no existe");
        contiene(p.texto(JEFE_PRENSA, "INSPER[\"99\",\"periodista\",\"planta\",\"1\",\"\",\"\"]"), "Error: el usuario indicado no existe");
        contiene(p.texto(JEFE_PRENSA, "INSPER[\"15\",\"periodista\",\"planta\",\"1\",\"Humanidades\",\"\"]"),
                "Error: la unidad académica solo se registra para los practicantes");
        contiene(p.texto(JEFE_PRENSA, "INSPER[\"15\",\"chofer\",\"planta\",\"1\",\"\",\"\"]"),
                "Error: «cargo» debe ser periodista, camarografo, editor, presentador, conductor o jefe_prensa");
    }

    @Test
    void updperCambiaElTurnoYAvisaLasCoberturasFueraDeHorario() {
        contiene(p.texto(JEFE_PRENSA, "UPDPER[\"1\",\"periodista\",\"planta\",\"2\",\"\"]"),
                "Éxito: ficha modificada correctamente", "Tarde", "quedan fuera de su horario");
    }

    @Test
    void updperAlDejarDeSerPracticanteBorraLaUnidad() throws Exception {
        contiene(p.texto(JEFE_PRENSA, "UPDPER[\"4\",\"\",\"planta\",\"\",\"\"]"), "Éxito: ficha modificada correctamente");
        assertNull(p.uno("SELECT unidad_academica FROM personal WHERE id = 4").orElseThrow().get("unidad_academica"));
    }

    @Test
    void updperErrores() {
        contiene(p.texto(JEFE_PRENSA, "UPDPER[\"99\",\"editor\",\"\",\"\",\"\"]"), "Error: el registro no existe");
        contiene(p.texto(JEFE_PRENSA, "UPDPER[\"1\",\"\",\"\",\"\",\"\"]"), "Error: indique al menos un dato");
        contiene(p.texto(JEFE_PRENSA, "UPDPER[\"1\",\"\",\"\",\"\",\"Humanidades\"]"),
                "Error: la unidad académica solo se registra para los practicantes");
    }

    @Test
    void delperDesactivaYUpdperReactiva() throws Exception {
        contiene(p.texto(JEFE_PRENSA, "DELPER[\"4\"]"), "Éxito: ficha desactivada correctamente", "inactivo");
        contiene(p.texto(JEFE_PRENSA, "DELPER[\"4\"]"), "Error: la ficha ya está inactiva");
        contiene(p.texto(JEFE_PRENSA, "UPDPER[\"4\",\"\",\"\",\"\",\"\",\"activo\"]"), "Éxito: ficha modificada correctamente");
        assertEquals("activo", p.uno("SELECT estado FROM personal WHERE id = 4").orElseThrow().texto("estado"));
        contiene(p.texto(JEFE_PRENSA, "DELPER[\"99\"]"), "Error: el registro no existe");
    }

    // ------------------------------------------------------------------ turnos

    @Test
    void listur() {
        contiene(p.texto(PERIODISTA, "LISTUR[\"*\"]"), "6 registros.", "22:00–06:00 (cruza la medianoche)", "lun, mar, mié, jue, vie", "sáb, dom");
        contiene(p.texto(PERIODISTA, "LISTUR[\"parcial\"]"), "2 registros.");
        contiene(p.texto(PERIODISTA, "LISTUR[\"*\",\"x\"]"), "Error: se esperaba 1 parámetro (filtro) y llegaron 2");
    }

    @Test
    void instur() throws Exception {
        contiene(p.texto(JEFE_PRENSA, "INSTUR[\"Parcial 15-19\",\"15:00\",\"19:00\",\"1,2,3,4,5\",\"Practicantes\"]"),
                "Éxito: turno registrado correctamente", "15:00–19:00");
        // sin días: lunes a viernes
        contiene(p.texto(JEFE_PRENSA, "INSTUR[\"Guardia\",\"09:00\",\"13:00\",\"\",\"\"]"), "lun, mar, mié, jue, vie");
        contiene(p.texto(PERIODISTA, "INSTUR[\"X\",\"07:00\",\"15:00\",\"1\",\"\"]"), "Error: no tiene permiso para ejecutar INSTUR");
    }

    @Test
    void insturErrores() {
        contiene(p.texto(JEFE_PRENSA, "INSTUR[\"Raro\",\"15:00\",\"15:00\",\"1\",\"\"]"),
                "Error: la hora de inicio y la de fin del turno no pueden ser iguales");
        contiene(p.texto(JEFE_PRENSA, "INSTUR[\"Mañana\",\"07:00\",\"15:00\",\"1\",\"\"]"), "Error: ya existe un turno con ese nombre");
        contiene(p.texto(JEFE_PRENSA, "INSTUR[\"X\",\"07:00\",\"15:00\",\"1,8\",\"\"]"), "Error: los días van de 1 (lunes) a 7 (domingo)");
    }




    // ------------------------------------------------------------------ ausencias

    @Test
    void lisaus() {
        contiene(p.texto(JEFE_PRENSA, "LISAUS[\"6\"]"), "Ausencias de Rubén Cuéllar Antelo", "Trámite personal", "(día completo)");
        contiene(p.texto(DIRECTOR, "LISAUS[\"*\"]"), "3 registros.", "09:00 a 11:00");
        contiene(p.texto(JEFE_PRENSA, "LISAUS[\"99\"]"), "Error: la persona indicada no existe");
        contiene(p.texto(PERIODISTA, "LISAUS[\"*\"]"), "Error: no tiene permiso para ejecutar LISAUS");
    }

    @Test
    void insausConHoraYDeDiasCompletos() throws Exception {
        contiene(p.texto(JEFE_PRENSA, "INSAUS[\"6\",\"permiso\",\"" + LUNES + " 09:00\",\"" + LUNES + " 11:00\",\"Trámite personal\"]"),
                "Éxito: ausencia registrada correctamente", LUNES + " 09:00 a 11:00");
        contiene(p.texto(JEFE_PRENSA, "INSAUS[\"5\",\"vacacion\",\"" + LUNES + "\",\"" + LUNES.plusDays(1) + "\",\"Vacación anual\"]"),
                LUNES + " al " + LUNES.plusDays(1));
        // R11: de 00:00 del primer día a 00:00 del día siguiente al último
        assertEquals(LUNES.plusDays(2).atStartOfDay(),
                p.uno("SELECT fecha_hora_fin FROM ausencia WHERE personal_id = 5").orElseThrow().fechaHora("fecha_hora_fin"));
    }

    @Test
    void insausAvisaLasCoberturasQueSeCruzan() {
        LocalDate dia = HOY.plusDays(2);  // Carla cubre «Concejo trata la tarifa del micro» de 09:00 a 11:00
        contiene(p.texto(JEFE_PRENSA, "INSAUS[\"1\",\"comision\",\"" + dia + "\",\"" + dia + "\",\"Viaje\"]"),
                "Éxito: ausencia registrada correctamente", "tiene coberturas asignadas", "Concejo trata la tarifa del micro");
    }

    @Test
    void insausErrores() {
        contiene(p.texto(JEFE_PRENSA, "INSAUS[\"6\",\"permiso\",\"" + LUNES + " 11:00\",\"" + LUNES + " 09:00\",\"x\"]"),
                "Error: el fin debe ser posterior al inicio");
        contiene(p.texto(JEFE_PRENSA, "INSAUS[\"99\",\"permiso\",\"" + LUNES + "\",\"" + LUNES + "\",\"x\"]"),
                "Error: la persona indicada no existe");
        contiene(p.texto(JEFE_PRENSA, "INSAUS[\"6\",\"feriado\",\"" + LUNES + "\",\"" + LUNES + "\",\"x\"]"),
                "Error: «tipo» debe ser permiso, vacacion, baja_medica o comision");
    }




    // ------------------------------------------------------------------ disponibilidad (R5)

    @Test
    void disperCruzaTurnoAusenciasYCoberturas() {
        String manana = p.texto(DIRECTOR, "DISPER[\"" + LUNES + "\",\"10:00\",\"camarografo\"]");
        contiene(manana, "2 registros.", "Jorge Mendoza Ribera", "Rubén Cuéllar Antelo");
        assertFalse(manana.contains("Wilmer"), "Wilmer es del turno tarde");
        contiene(p.texto(DIRECTOR, "DISPER[\"" + LUNES + "\",\"16:00\",\"camarografo\"]"), "1 registro.", "Wilmer Choque Mamani");
        // con una ausencia de 09:00 a 11:00, Jorge deja de estar disponible a las 10:00
        p.texto(JEFE_PRENSA, "INSAUS[\"5\",\"permiso\",\"" + LUNES + " 09:00\",\"" + LUNES + " 11:00\",\"\"]");
        String conAusencia = p.texto(DIRECTOR, "DISPER[\"" + LUNES + "\",\"10:00\",\"camarografo\"]");
        assertFalse(conAusencia.contains("Jorge"), conAusencia);
        contiene(p.texto(DIRECTOR, "DISPER[\"" + LUNES + "\",\"10:00\"]"), "Carla Rojas Suárez", "Freddy Montaño Vargas");
    }

    @Test
    void disperSinNadieYErrores() {
        contiene(p.texto(DIRECTOR, "DISPER[\"" + DOMINGO + "\",\"03:00\",\"editor\"]"), "No hay personal disponible en ese horario");
        contiene(p.texto(DIRECTOR, "DISPER[\"" + LUNES + "\",\"25:00\"]"), "Error: la hora «25:00» de «hora» no es válida");
        contiene(p.texto(DIRECTOR, "DISPER"), "Error: formato esperado DISPER[\"fecha\",\"hora\",\"cargo\"]");
    }
}
