package bo.edu.uagrm.ficct.canal11.comandos.cu05;

import bo.edu.uagrm.ficct.canal11.datos.Fila;
import bo.edu.uagrm.ficct.canal11.comandos.ErrorNegocio;
import bo.edu.uagrm.ficct.canal11.comandos.Probador;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;

import static bo.edu.uagrm.ficct.canal11.comandos.Probador.CAMAROGRAFO;
import static bo.edu.uagrm.ficct.canal11.comandos.Probador.DIRECTOR;
import static bo.edu.uagrm.ficct.canal11.comandos.Probador.EDITOR;
import static bo.edu.uagrm.ficct.canal11.comandos.Probador.JEFE_PRENSA;
import static bo.edu.uagrm.ficct.canal11.comandos.Probador.OTRO_PERIODISTA;
import static bo.edu.uagrm.ficct.canal11.comandos.Probador.PERIODISTA;
import static bo.edu.uagrm.ficct.canal11.comandos.Probador.PRESENTADOR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CU-05: un caso de éxito y uno de error por comando, con los ejemplos del Anexo A. */
@Tag("bd")
class ComandosNotasTest {

    private static final LocalDate HOY = LocalDate.now(ZoneId.of("America/La_Paz"));
    private static final String GUION = "Uno dos tres cuatro cinco seis siete ocho nueve diez";

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

    private String estado(int idNota) throws Exception {
        return p.uno("SELECT estado FROM nota WHERE id = ?", idNota).orElseThrow().texto("estado");
    }

    @Test
    void slugYPalabras() throws ErrorNegocio {
        assertEquals("PARO-TRANSPORTE", ComandosNotas.slug("Paro transporte"));
        assertEquals("CLASICO-CRUCENO", ComandosNotas.slug("clásico cruceño"));
        assertThrows(ErrorNegocio.class, () -> ComandosNotas.slug("¿qué?"));
        assertEquals(10, ComandosNotas.contarPalabras("  " + GUION + "\n"));
        assertEquals(0, ComandosNotas.contarPalabras("   "));
    }

    // ------------------------------------------------------------------ notas

    @Test
    void lisnot() {
        contiene(p.texto(DIRECTOR, "LISNOT[\"" + HOY + "\",\"aprobada\"]"), "5 registros.", "PARO-TRANSPORTE", "LISNOT[\"id\"]");
        contiene(p.texto(PERIODISTA, "LISNOT[\"\",\"aprobada\"]"), "5 registros.");
        contiene(p.texto(PERIODISTA, "LISNOT[\"en revisión\"]"), "1 registro.", "UAGRM-AUTORIDADES");
        contiene(p.texto(PERIODISTA, "LISNOT"), "11 registros.");
        contiene(p.texto(PERIODISTA, "LISNOT[\"ayer\"]"), "Error: indique \"*\", una fecha AAAA-MM-DD, el id de una nota o un estado");
    }

    @Test
    void lisnotConIdDevuelveElGuionCompleto() {
        contiene(p.texto(PRESENTADOR, "LISNOT[\"1\"]"), "GOBERNACION-PRESUPUESTO", "La Gobernación de Santa Cruz presentó esta mañana",
                "Rótulos", "Juan Carlos Rivero", "Material audiovisual", "Duración estimada");
        contiene(p.texto(PRESENTADOR, "LISNOT[\"99\"]"), "Error: el registro no existe");
    }

    @Test
    void insnotCuentaPalabrasYGuardaLaVersion1() throws Exception {
        LocalDate fecha = HOY.plusDays(1);
        contiene(p.texto(PERIODISTA, "INSNOT[\"Paro transporte\",\"Transportistas bloquean el 4to anillo\",\"vo_sot\",\"" + fecha + "\",\"2\",\"\"]", GUION),
                "Éxito: nota registrada correctamente", "PARO-TRANSPORTE", "borrador", "Carla Rojas Suárez", "Duración estimada: 00:04", "ENVNOT[\"");
        Fila n = p.uno("SELECT id, palabras, autor_id FROM nota WHERE slug = 'PARO-TRANSPORTE' AND fecha = ?", fecha).orElseThrow();
        assertEquals(10, n.entero("palabras"));
        assertEquals(1, n.entero("autor_id"));
        assertEquals(1, p.numero("SELECT count(*) FROM nota_version WHERE nota_id = ? AND numero = 1", n.entero("id")));
    }

    @Test
    void elJefeDePrensaTambienRedacta() {
        contiene(p.texto(JEFE_PRENSA, "INSNOT[\"EDITORIAL\",\"Editorial del día\",\"lectura\",\"" + HOY + "\",\"\",\"130\"]", GUION),
                "Éxito: nota registrada correctamente", "Roberto Arteaga");
    }

    @Test
    void insnotErrores() throws Exception {
        String asunto = "INSNOT[\"NUEVA\",\"Título\",\"vo\",\"" + HOY + "\",\"\",\"\"]";
        contiene(p.texto(PERIODISTA, asunto, "   "), "Error: el guion va en el cuerpo del correo");
        contiene(p.texto(PERIODISTA, "INSNOT[\"PARO-TRANSPORTE\",\"Otro\",\"vo\",\"" + HOY + "\",\"\",\"\"]", GUION),
                "Error: ya existe la nota PARO-TRANSPORTE para esa fecha");
        contiene(p.texto(PERIODISTA, "INSNOT[\"NUEVA\",\"Título\",\"vo\",\"" + HOY + "\",\"99\",\"\"]", GUION), "Error: la cobertura indicada no existe");
        contiene(p.texto(DIRECTOR, asunto, GUION), "Error: no tiene permiso para ejecutar INSNOT");
        p.ejecutar("UPDATE personal SET estado = 'inactivo' WHERE id = 1");
        contiene(p.texto(PERIODISTA, asunto, GUION), "Error: para redactar notas necesita una ficha de personal activa");
    }

    @Test
    void updnot() {
        contiene(p.texto(JEFE_PRENSA, "UPDNOT[\"4\",\"INCENDIOS-CHIQUITANIA\",\"COED reporta 14 focos de calor\",\"lectura\",\"" + HOY + "\",\"6\",\"130\"]"),
                "Éxito: nota modificada correctamente", "COED reporta 14 focos de calor");
        contiene(p.texto(PERIODISTA, "UPDNOT[\"3\",\"\",\"Nuevas autoridades en la UAGRM\",\"\",\"\",\"\",\"\"]"), "Nuevas autoridades en la UAGRM");
    }

    @Test
    void updnotErrores() {
        contiene(p.texto(PERIODISTA, "UPDNOT[\"2\",\"\",\"Otro título\",\"\",\"\",\"\",\"\"]"), "Error: solo el autor o el Jefe de Prensa pueden modificarla");
        contiene(p.texto(JEFE_PRENSA, "UPDNOT[\"9\",\"\",\"Otro título\",\"\",\"\",\"\",\"\"]"), "Error: la nota está emitida: ya no se modifica");
        contiene(p.texto(PERIODISTA, "UPDNOT[\"3\",\"PARO-TRANSPORTE\",\"\",\"\",\"\",\"\",\"\"]"), "Error: ya existe la nota PARO-TRANSPORTE para esa fecha");
        contiene(p.texto(PERIODISTA, "UPDNOT[\"3\",\"\",\"\",\"\",\"\",\"\",\"300\"]"), "Error: «palabras_por_minuto» debe ser un número de 80 a 250");
    }

    @Test
    void guinotSobreUnaNotaAprobadaVuelveARevision() throws Exception {
        contiene(p.texto(PERIODISTA, "GUINOT[\"1\"]", GUION + " once doce"),
                "Éxito: guion reemplazado correctamente", "versión 2", "vuelve a revisión", "está en la escaleta 1");
        assertEquals("en_revision", estado(1));
        assertEquals(12, p.uno("SELECT palabras FROM nota WHERE id = 1").orElseThrow().entero("palabras"));
        assertEquals(2, p.numero("SELECT count(*) FROM nota_version WHERE nota_id = 1"));
    }

    @Test
    void guinotErrores() {
        contiene(p.texto(PERIODISTA, "GUINOT[\"1\"]", ""), "Error: el guion va en el cuerpo del correo");
        contiene(p.texto(PERIODISTA, "GUINOT[\"2\"]", GUION), "Error: solo el autor o el Jefe de Prensa pueden modificarla");
    }


    // ------------------------------------------------------------------ revisión (R15)

    @Test
    void envnotErrores() {
        contiene(p.texto(PERIODISTA, "ENVNOT[\"3\"]"), "Error: la nota está en revisión: solo se envían a revisión las notas en borrador u observadas");
        contiene(p.texto(PERIODISTA, "ENVNOT[\"6\"]"), "Error: solo el autor o el Jefe de Prensa pueden modificarla");
    }

    @Test
    void aprnotYObsnotErrores() {
        contiene(p.texto(JEFE_PRENSA, "APRNOT[\"5\"]"), "Error: la nota no está en revisión");
        contiene(p.texto(PERIODISTA, "APRNOT[\"3\"]"), "Error: no tiene permiso para ejecutar APRNOT");
        contiene(p.texto(JEFE_PRENSA, "OBSNOT[\"3\",\"\"]"), "Error: indique el motivo de la observación");
        contiene(p.texto(JEFE_PRENSA, "OBSNOT[\"5\",\"x\"]"), "Error: la nota no está en revisión");
    }

    @Test
    void obsnotConComillasEscapadasEnElMotivo() throws Exception {
        contiene(p.texto(JEFE_PRENSA, "OBSNOT[\"3\",\"El vecino dijo \\\"no\\\" y falta su nombre\"]"), "Éxito: nota observada correctamente");
        assertEquals("El vecino dijo \"no\" y falta su nombre",
                p.consultar("SELECT motivo FROM revision_nota WHERE nota_id = 3 ORDER BY id DESC LIMIT 1").get(0).texto("motivo"));
    }


    // ------------------------------------------------------------------ rótulos



    // ------------------------------------------------------------------ material

    @Test
    void delnot() throws Exception {
        contiene(p.texto(JEFE_PRENSA, "DELNOT[\"5\"]"), "Éxito: nota dada de baja correctamente", "baja");
        assertEquals("baja", estado(5));
        contiene(p.texto(JEFE_PRENSA, "DELNOT[\"1\"]"), "Error: la nota está en la escaleta 1: quítela antes con DELITE");
        contiene(p.texto(PERIODISTA, "DELNOT[\"7\"]"), "Error: solo el autor o el Jefe de Prensa pueden modificarla");
    }

    // ------------------------------------------------------------------ material

    @Test
    void lismat() {
        contiene(p.texto(EDITOR, "LISMAT[\"pendiente\"]"), "2 registros.", "Imágenes del acto");
        contiene(p.texto(CAMAROGRAFO, "LISMAT[\"2\"]"), "Material de PARO-TRANSPORTE", "1 registro.", "00:55");
        contiene(p.texto(EDITOR, "LISMAT[\"perdido\"]"), "Error: indique \"*\", el id de una nota o un estado");
    }

    @Test
    void insmatSumaALaDuracionDeLaNota() {
        contiene(p.texto(CAMAROGRAFO, "INSMAT[\"3\",\"Imágenes del acto\",\"video\",\"00:40\",\"/media/2026/autoridades.mxf\"]"),
                "Éxito: material registrado correctamente", "pendiente", "Duración estimada de la nota");
        contiene(p.texto(CAMAROGRAFO, "INSMAT[\"9\",\"Más imágenes\",\"video\",\"00:40\",\"\"]"), "Error: la nota está emitida: ya no se modifica");
        contiene(p.texto(CAMAROGRAFO, "INSMAT[\"3\",\"Imágenes\",\"pelicula\",\"00:40\",\"\"]"), "Error: «tipo» debe ser video, sot, grafico, foto o audio");
    }

    @Test
    void updmat() throws Exception {
        contiene(p.texto(EDITOR, "UPDMAT[\"2\",\"listo\",\"00:55\",\"/media/2026/paro_final.mxf\"]"),
                "Éxito: material actualizado correctamente", "listo", "/media/2026/paro_final.mxf", "Patricia Saucedo Melgar");
        contiene(p.texto(PERIODISTA, "UPDMAT[\"2\",\"listo\",\"\",\"\"]"), "Error: no tiene permiso para ejecutar UPDMAT");
        contiene(p.texto(EDITOR, "UPDMAT[\"99\",\"listo\",\"\",\"\"]"), "Error: el registro no existe");
    }
}
