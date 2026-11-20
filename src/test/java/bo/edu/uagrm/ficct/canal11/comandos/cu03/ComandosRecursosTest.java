package bo.edu.uagrm.ficct.canal11.comandos.cu03;

import bo.edu.uagrm.ficct.canal11.datos.Fila;
import bo.edu.uagrm.ficct.canal11.comandos.Probador;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static bo.edu.uagrm.ficct.canal11.comandos.Probador.AREA_TECNICA;
import static bo.edu.uagrm.ficct.canal11.comandos.Probador.CAMAROGRAFO;
import static bo.edu.uagrm.ficct.canal11.comandos.Probador.DIRECTOR;
import static bo.edu.uagrm.ficct.canal11.comandos.Probador.JEFE_PRENSA;
import static bo.edu.uagrm.ficct.canal11.comandos.Probador.PERIODISTA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CU-03: un caso de éxito y uno de error por comando, con los ejemplos del Anexo A. */
@Tag("bd")
class ComandosRecursosTest {

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

    private String estado(String codigo) throws Exception {
        return p.uno("SELECT estado FROM recurso WHERE codigo = ?", codigo).orElseThrow().texto("estado");
    }

    // ------------------------------------------------------------------ tipos




    // ------------------------------------------------------------------ inventario

    @Test
    void lisrec() {
        contiene(p.texto(CAMAROGRAFO, "LISREC[\"operativo\"]"), "15 registros.");
        contiene(p.texto(DIRECTOR, "LISREC[\"cámara\"]"), "5 registros.", "CAM-04", "mantenimiento");
        contiene(p.texto(JEFE_PRENSA, "LISREC[\"mov-02\"]"), "1 registro.", "Jorge Mendoza Ribera");
        contiene(p.texto(PERIODISTA, "LISREC[\"*\"]"), "Error: no tiene permiso para ejecutar LISREC");
    }

    @Test
    void insrec() {
        contiene(p.texto(AREA_TECNICA, "INSREC[\"CAM-06\",\"1\",\"Sony\",\"FX6\",\"\",\"Full frame 4K\"]"),
                "Éxito: recurso registrado correctamente", "CAM-06", "operativo");
        contiene(p.texto(AREA_TECNICA, "INSREC[\"mov-03\",\"5\",\"Toyota\",\"Hilux\",\"1234-abc\",\"\"]"), "MOV-03", "1234-ABC");
    }

    @Test
    void insrecErrores() {
        contiene(p.texto(AREA_TECNICA, "INSREC[\"CAM-01\",\"1\",\"Sony\",\"FX6\",\"\",\"\"]"), "Error: el código CAM-01 ya existe");
        contiene(p.texto(AREA_TECNICA, "INSREC[\"CAM-07\",\"1\",\"Sony\",\"FX6\",\"ABC-123\",\"\"]"), "Error: solo las unidades móviles llevan placa");
        contiene(p.texto(AREA_TECNICA, "INSREC[\"MOV-09\",\"5\",\"Toyota\",\"Hilux\",\"4521-KTD\",\"\"]"), "Error: esa placa ya está registrada");
        contiene(p.texto(AREA_TECNICA, "INSREC[\"CAM-07\",\"99\",\"Sony\",\"FX6\",\"\",\"\"]"), "Error: el tipo de recurso no existe");
    }

    @Test
    void updrec() {
        contiene(p.texto(AREA_TECNICA, "UPDREC[\"5\",\"Canon\",\"XF605\",\"\",\"4K liviana\",\"\"]"),
                "Éxito: recurso modificado correctamente", "XF605", "4K liviana");
        contiene(p.texto(AREA_TECNICA, "UPDREC[\"5\",\"\",\"\",\"ABC-1\",\"\",\"\"]"), "Error: solo las unidades móviles llevan placa");
        contiene(p.texto(AREA_TECNICA, "UPDREC[\"99\",\"X\",\"\",\"\",\"\",\"\"]"), "Error: el registro no existe");
    }

    @Test
    void delrec() throws Exception {
        contiene(p.texto(AREA_TECNICA, "DELREC[\"4\",\"Visor irreparable\"]"), "Éxito: recurso dado de baja correctamente", "baja");
        assertEquals("baja", estado("CAM-04"));
        assertEquals(1, p.numero("SELECT count(*) FROM mantenimiento WHERE recurso_id = 4 AND tipo = 'baja'"));
        contiene(p.texto(AREA_TECNICA, "DELREC[\"4\",\"otra vez\"]"), "Error: CAM-04 ya está dado de baja");
        contiene(p.texto(AREA_TECNICA, "DELREC[\"13\",\"Motor fundido\"]"), "Error: MOV-02 no fue devuelto");
    }

    // ------------------------------------------------------------------ mantenimiento (R9)

    @Test
    void increcAvisaLasCoberturasQueLoUsan() throws Exception {
        contiene(p.texto(CAMAROGRAFO, "INCREC[\"MOV-01\",\"Falla de frenos\"]"),
                "Éxito: incidencia registrada correctamente", "mantenimiento", "reservado en coberturas pendientes",
                "Concejo trata la tarifa del micro");
        assertEquals("mantenimiento", estado("MOV-01"));
        Fila m = p.uno("SELECT tipo, usuario_id FROM mantenimiento WHERE recurso_id = 12").orElseThrow();
        assertEquals("incidencia", m.texto("tipo"));
        assertEquals(7, m.entero("usuario_id"));
    }

    @Test
    void increcErrores() {
        contiene(p.texto(AREA_TECNICA, "INCREC[\"CAM-04\",\"Otra falla\"]"), "Error: CAM-04 ya está en mantenimiento");
        contiene(p.texto(AREA_TECNICA, "INCREC[\"CAM-99\",\"x\"]"), "Error: el recurso CAM-99 no existe");
    }

    @Test
    void altrec() throws Exception {
        contiene(p.texto(AREA_TECNICA, "ALTREC[\"CAM-04\",\"Visor reemplazado\"]"), "Éxito: alta registrada correctamente", "operativo");
        assertEquals("operativo", estado("CAM-04"));
        contiene(p.texto(AREA_TECNICA, "ALTREC[\"CAM-01\",\"\"]"), "Error: CAM-01 no está en mantenimiento (está operativo)");
        contiene(p.texto(CAMAROGRAFO, "ALTREC[\"CAM-04\",\"\"]"), "Error: no tiene permiso para ejecutar ALTREC");
    }


    // ------------------------------------------------------------------ préstamos (R8)

    @Test
    void lispre() {
        contiene(p.texto(AREA_TECNICA, "LISPRE[\"abiertos\"]"), "4 registros.", "MOV-02", "abierto");
        contiene(p.texto(JEFE_PRENSA, "LISPRE[\"*\"]"), "7 registros.", "devuelto");
        contiene(p.texto(CAMAROGRAFO, "LISPRE[\"*\"]"), "Error: no tiene permiso para ejecutar LISPRE");
    }

    @Test
    void inspre() throws Exception {
        contiene(p.texto(AREA_TECNICA, "INSPRE[\"CAM-01\",\"6\",\"3\",\"Con batería extra\"]"),
                "Éxito: entrega registrada correctamente", "CAM-01", "Rubén Cuéllar Antelo", "abierto");
        contiene(p.texto(AREA_TECNICA, "INSPRE[\"CAM-03\",\"6\",\"3\",\"\"]"), "no está reservado para la cobertura 3");
    }

    @Test
    void inspreErrores() {
        contiene(p.texto(AREA_TECNICA, "INSPRE[\"MOV-02\",\"6\",\"\",\"\"]"), "Error: MOV-02 no fue devuelto");
        contiene(p.texto(AREA_TECNICA, "INSPRE[\"CAM-04\",\"6\",\"\",\"\"]"), "Error: CAM-04 está en mantenimiento");
        contiene(p.texto(AREA_TECNICA, "INSPRE[\"CAM-01\",\"99\",\"\",\"\"]"), "Error: el responsable indicado no existe");
        contiene(p.texto(AREA_TECNICA, "INSPRE[\"CAM-01\",\"6\",\"1\",\"\"]"), "Error: la cobertura 1 ya terminó (cumplida)");
    }

    @Test
    void devpre() throws Exception {
        contiene(p.texto(AREA_TECNICA, "DEVPRE[\"1\",\"Sin novedad\"]"), "Éxito: devolución registrada correctamente", "devuelto", "Sin novedad");
        contiene(p.texto(AREA_TECNICA, "DEVPRE[\"1\",\"\"]"), "Error: el préstamo ya fue devuelto");
        // devuelto, MOV-02 se puede volver a entregar (R8)
        contiene(p.texto(AREA_TECNICA, "INSPRE[\"MOV-02\",\"7\",\"\",\"\"]"), "Éxito: entrega registrada correctamente");
    }

}
