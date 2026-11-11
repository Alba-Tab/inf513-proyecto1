package bo.edu.uagrm.ficct.canal11.comandos.cu04;

import bo.edu.uagrm.ficct.canal11.datos.Fila;
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

import static bo.edu.uagrm.ficct.canal11.comandos.Probador.AREA_TECNICA;
import static bo.edu.uagrm.ficct.canal11.comandos.Probador.DIRECTOR;
import static bo.edu.uagrm.ficct.canal11.comandos.Probador.JEFE_PRENSA;
import static bo.edu.uagrm.ficct.canal11.comandos.Probador.OTRO_PERIODISTA;
import static bo.edu.uagrm.ficct.canal11.comandos.Probador.PERIODISTA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CU-04: un caso de éxito y uno de error por comando, con los ejemplos del Anexo A. */
@Tag("bd")
class ComandosCoberturasTest {

    private static final LocalDate HOY = LocalDate.now(ZoneId.of("America/La_Paz"));
    /** Un lunes dentro de tres semanas, sin coberturas en los datos de prueba. */
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

    /** Registra una cobertura y devuelve su id. */
    private long nueva(LocalDate fecha, String inicio, String fin) throws Exception {
        String t = p.texto(JEFE_PRENSA, "INSCOB[\"Prueba " + fecha + " " + inicio + "\",\"\",\"entrevista\",\"" + fecha + "\",\"" + inicio
                + "\",\"" + fin + "\",\"Canal 11\",\"\",\"\",\"\"]");
        contiene(t, "Éxito: cobertura registrada correctamente");
        return p.numero("SELECT max(id) FROM cobertura");
    }

    // ------------------------------------------------------------------ coberturas

    @Test
    void liscob() {
        contiene(p.texto(DIRECTOR, "LISCOB[\"" + HOY + "\"]"), "6 registros.", "Paro de transporte en el 4to anillo", "Marcelo Justiniano Égüez (periodista)");
        contiene(p.texto(PERIODISTA, "LISCOB[\"semana\"]"), "9 registros.", "Llegada de Oriente Petrolero", "22:00 a ");
        contiene(p.texto(PERIODISTA, "LISCOB"), "15 registros.");
        contiene(p.texto(PERIODISTA, "LISCOB[\"cumplida\"]"), "6 registros.");
        contiene(p.texto(PERIODISTA, "LISCOB[\"2026-13-40\"]"), "Error: indique una fecha AAAA-MM-DD");
    }

    @Test
    void inscob() throws Exception {
        contiene(p.texto(JEFE_PRENSA, "INSCOB[\"Clásico cruceño\",\"Conferencia previa\",\"deportes\",\"" + LUNES + "\",\"15:00\",\"16:30\","
                        + "\"Estadio Tahuichi\",\"media\",\"Prensa Club Blooming\",\"Conferencia de técnicos\"]"),
                "Éxito: cobertura registrada correctamente", "Clásico cruceño", "15:00–16:30", "planificada", "INSCPE[\"");
        Fila c = p.uno("SELECT creado_por, estado FROM cobertura WHERE titulo = 'Clásico cruceño'").orElseThrow();
        assertEquals(2, c.entero("creado_por"));
    }

    @Test
    void inscobQueCruzaLaMedianoche() throws Exception {
        long id = nueva(LUNES, "22:00", "02:00");
        Fila c = p.uno("SELECT fecha_hora_inicio, fecha_hora_fin FROM cobertura WHERE id = ?", (int) id).orElseThrow();
        assertEquals(LUNES.atTime(22, 0), c.fechaHora("fecha_hora_inicio"));
        assertEquals(LUNES.plusDays(1).atTime(2, 0), c.fechaHora("fecha_hora_fin"));
    }

    @Test
    void inscobErrores() {
        contiene(p.texto(JEFE_PRENSA, "INSCOB[\"X\",\"\",\"deportes\",\"" + LUNES + "\",\"15:00\",\"15:00\",\"Estadio\",\"\",\"\",\"\"]"),
                "Error: la cobertura debe terminar después de empezar");
        contiene(p.texto(JEFE_PRENSA, "INSCOB[\"X\",\"\",\"farandula\",\"" + LUNES + "\",\"15:00\",\"16:00\",\"Estadio\",\"\",\"\",\"\"]"),
                "Error: «tipo» debe ser conferencia, hecho_noticioso, deportes, acto_institucional o entrevista");
        contiene(p.texto(PERIODISTA, "INSCOB[\"X\",\"\",\"deportes\",\"" + LUNES + "\",\"15:00\",\"16:00\",\"Estadio\",\"\",\"\",\"\"]"),
                "Error: no tiene permiso para ejecutar INSCOB");
    }

    @Test
    void updcobCambiaSoloLoQueLlega() throws Exception {
        contiene(p.texto(JEFE_PRENSA, "UPDCOB[\"6\",\"\",\"\",\"\",\"\",\"COED, sala de prensa\",\"\"]"),
                "Éxito: cobertura modificada correctamente", "COED, sala de prensa", "15:30–17:00");
        contiene(p.texto(JEFE_PRENSA, "UPDCOB[\"6\",\"\",\"" + LUNES + "\",\"09:00\",\"\",\"\",\"\"]"), LUNES.toString(), "09:00–17:00");
    }

    @Test
    void updcobRevalidaAlEquipoAsignado() throws Exception {
        // Wilmer cubre «Plan de bacheo» de 17:00 a 18:30: el Clásico no puede moverse a esa hora
        contiene(p.texto(JEFE_PRENSA, "UPDCOB[\"4\",\"\",\"\",\"17:00\",\"18:00\",\"\",\"\"]"),
                "Error: con el horario nuevo hay conflictos del equipo asignado", "Wilmer Choque Mamani ya tiene asignada la cobertura «Plan de bacheo en el Plan 3000»");
        LocalDateTime inicio = p.uno("SELECT fecha_hora_inicio FROM cobertura WHERE id = 4").orElseThrow().fechaHora("fecha_hora_inicio");
        assertEquals(HOY.atTime(15, 0), inicio);
        contiene(p.texto(JEFE_PRENSA, "UPDCOB[\"1\",\"X\",\"\",\"\",\"\",\"\",\"\"]"), "Error: la cobertura 1 ya terminó (cumplida)");
    }

    @Test
    void delcobCancelaYDevuelveLoEntregado() throws Exception {
        contiene(p.texto(JEFE_PRENSA, "DELCOB[\"2\",\"Se levantó el bloqueo\"]"),
                "Éxito: cobertura cancelada correctamente", "cancelada", "Se levantó el bloqueo", "devolución de 4 recurso(s)");
        assertEquals(0, p.numero("SELECT count(*) FROM prestamo WHERE cobertura_id = 2 AND fecha_devolucion IS NULL"));
        assertEquals(0, p.numero("SELECT count(*) FROM v_ocupacion_recurso WHERE cobertura_id = 2"));
        contiene(p.texto(JEFE_PRENSA, "DELCOB[\"11\",\"\"]"), "Error: la cobertura 11 ya terminó (cancelada)");
    }


    // ------------------------------------------------------------------ personal (R6, L6)

    @Test
    void inscpeDentroYFueraDeTurno() throws Exception {
        long manana = nueva(LUNES, "09:00", "10:00");
        contiene(p.texto(JEFE_PRENSA, "INSCPE[\"" + manana + "\",\"1\",\"periodista\"]"),
                "Éxito: personal asignado correctamente", "Carla Rojas Suárez", "periodista");
        long noche = nueva(LUNES, "18:00", "19:00");
        contiene(p.texto(JEFE_PRENSA, "INSCPE[\"" + noche + "\",\"1\",\"periodista\"]"),
                "Éxito con advertencia: Carla Rojas Suárez está fuera de su turno (Mañana 06:00–14:00)");
        // L6: un presentador puede conducir y el Jefe de Prensa puede ir como periodista
        contiene(p.texto(JEFE_PRENSA, "INSCPE[\"" + manana + "\",\"10\",\"conductor\"]"), "Mariana Terceros Lara");
        contiene(p.texto(JEFE_PRENSA, "INSCPE[\"" + noche + "\",\"13\",\"periodista\"]"), "Roberto Arteaga");
    }

    @Test
    void inscpeChoquesYCargo() throws Exception {
        contiene(p.texto(JEFE_PRENSA, "INSCPE[\"6\",\"7\",\"camarografo\"]"),
                "Error: Wilmer Choque Mamani ya tiene asignada la cobertura «Clásico cruceño: conferencia previa» 15:00–16:30");
        long conPermiso = nueva(HOY.plusDays(2), "14:00", "15:00");  // Rubén tiene permiso ese día completo
        contiene(p.texto(JEFE_PRENSA, "INSCPE[\"" + conPermiso + "\",\"6\",\"camarografo\"]"),
                "Error: Rubén Cuéllar Antelo tiene permiso en ese horario", "00:00");
        contiene(p.texto(JEFE_PRENSA, "INSCPE[\"6\",\"8\",\"camarografo\"]"), "Error: Patricia Saucedo Melgar es editor: no puede ir como camarografo");
        contiene(p.texto(JEFE_PRENSA, "INSCPE[\"2\",\"2\",\"periodista\"]"), "Error: Marcelo Justiniano Égüez ya está en el equipo de esta cobertura");
        contiene(p.texto(JEFE_PRENSA, "INSCPE[\"1\",\"3\",\"periodista\"]"), "Error: la cobertura 1 ya terminó (cumplida)");
    }

    @Test
    void delcpe() throws Exception {
        contiene(p.texto(JEFE_PRENSA, "DELCPE[\"12\"]"), "Éxito: personal quitado de la cobertura correctamente", "Marcelo Justiniano Égüez");
        assertEquals(0, p.numero("SELECT count(*) FROM cobertura_personal WHERE id = 12"));
        contiene(p.texto(JEFE_PRENSA, "DELCPE[\"1\"]"), "Error: la cobertura 1 ya terminó (cumplida)");
    }

    // ------------------------------------------------------------------ recursos (R7)

    @Test
    void inscre() throws Exception {
        contiene(p.texto(JEFE_PRENSA, "INSCRE[\"6\",\"MOV-02\"]"), "Éxito: recurso reservado correctamente", "MOV-02", "15:30–17:00");
        contiene(p.texto(JEFE_PRENSA, "INSCRE[\"6\",\"MOV-01\"]"),
                "Error: MOV-01 ocupado 15:00–16:30 por «Clásico cruceño: conferencia previa»");
        contiene(p.texto(JEFE_PRENSA, "INSCRE[\"6\",\"CAM-04\"]"), "Error: CAM-04 está en mantenimiento");
        contiene(p.texto(JEFE_PRENSA, "INSCRE[\"6\",\"MOV-02\"]"), "Error: MOV-02 ya está reservado para esta cobertura");
        contiene(p.texto(JEFE_PRENSA, "INSCRE[\"6\",\"XYZ-1\"]"), "Error: el recurso XYZ-1 no existe");
    }

    @Test
    void delcre() throws Exception {
        contiene(p.texto(JEFE_PRENSA, "DELCRE[\"10\"]"), "Éxito: recurso liberado correctamente", "MOV-01");
        assertEquals(0, p.numero("SELECT count(*) FROM cobertura_recurso WHERE id = 10"));
        contiene(p.texto(JEFE_PRENSA, "DELCRE[\"1\"]"), "Error: la cobertura 1 ya terminó (cumplida)");
    }

    // ------------------------------------------------------------------ hoja y retorno (R4)

    @Test
    void hojcob() {
        contiene(p.texto(PERIODISTA, "HOJCOB[\"2\"]"), "Hoja de asignación", "4to anillo y Av. Banzer", "Equipo humano",
                "Freddy Montaño Vargas", "Equipos y unidad móvil", "3890-GHP");
        contiene(p.texto(AREA_TECNICA, "HOJCOB[\"6\"]"), "Todavía no hay personal asignado.");
        contiene(p.texto(AREA_TECNICA, "HOJCOB[\"99\"]"), "Error: el registro no existe");
    }

    @Test
    void retcobDelPeriodistaAsignadoLiberaYDevuelve() throws Exception {
        contiene(p.texto(OTRO_PERIODISTA, "RETCOB[\"2\",\"cancelada\",\"Se levantó el bloqueo\",\"Ninguna\"]"),
                "Éxito: retorno registrado correctamente", "cancelada", "devolución de 4 recurso(s)");
        assertEquals(0, p.numero("SELECT count(*) FROM v_ocupacion_personal WHERE cobertura_id = 2"));
        contiene(p.texto(JEFE_PRENSA, "RETCOB[\"2\",\"cumplida\",\"\",\"\"]"), "Error: la cobertura 2 ya tiene registrado su retorno (cancelada)");
    }

    @Test
    void retcobErrores() {
        contiene(p.texto(PERIODISTA, "RETCOB[\"2\",\"cumplida\",\"\",\"\"]"),
                "Error: solo el Jefe de Prensa o el personal asignado a la cobertura registra su retorno");
        contiene(p.texto(JEFE_PRENSA, "RETCOB[\"7\",\"cumplida\",\"\",\"\"]"), "Error: la cobertura todavía no empezó");
        contiene(p.texto(JEFE_PRENSA, "RETCOB[\"7\",\"suspendida\",\"\",\"\"]"), "Error: «resultado» debe ser cumplida, cancelada o reprogramada");
    }
}
