package bo.edu.uagrm.ficct.canal11.pipeline;

import bo.edu.uagrm.ficct.canal11.comandos.ErrorNegocio;
import bo.edu.uagrm.ficct.canal11.comandos.Probador;
import bo.edu.uagrm.ficct.canal11.comandos.Resultado;
import bo.edu.uagrm.ficct.canal11.datos.Fila;
import bo.edu.uagrm.ficct.canal11.comandos.catalogo.Catalogo;
import bo.edu.uagrm.ficct.canal11.comandos.catalogo.CommandSpec;
import bo.edu.uagrm.ficct.canal11.comandos.catalogo.Tipo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static bo.edu.uagrm.ficct.canal11.comandos.Probador.DIRECTOR;
import static bo.edu.uagrm.ficct.canal11.comandos.Probador.JEFE_PRENSA;
import static bo.edu.uagrm.ficct.canal11.comandos.Probador.PERIODISTA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** El flujo común a todos los comandos (sección 4.2.1), etapa por etapa: permiso, formato, transacción y bitácora. */
@Tag("bd")
class PipelineTest {

    private Probador probador;

    @BeforeEach
    void abrir() throws Exception {
        probador = Probador.abrir();
    }

    @AfterEach
    void cerrar() throws Exception {
        if (probador != null) {
            probador.close();
        }
    }

    private Fila ultimaBitacora() throws Exception {
        return probador.uno("SELECT * FROM bitacora_comando ORDER BY id DESC LIMIT 1").orElseThrow();
    }

    // ------------------------------------------------------------------ AYUDA (P1)

    @Test
    void ayudaMuestraLosComandosDelRolConSuEjemplo() throws Exception {
        String t = probador.texto(PERIODISTA, "AYUDA");
        assertTrue(t.contains("Hola, Carla Rojas Suárez (Periodista)"), t);
        assertTrue(t.contains("LISNOT[\"2026-10-12\",\"aprobada\"]"), t);
        assertTrue(t.contains("ENVNOT[\"5\"]"), t);
        assertTrue(t.contains("el guion va en el cuerpo del correo"), t);
        assertFalse(t.contains("APRNOT"), "un periodista no aprueba notas");
        assertFalse(t.contains("INSUSU"), t);
        assertEquals("AYUDA", ultimaBitacora().texto("comando"));
        assertEquals("exito", ultimaBitacora().texto("resultado"));
    }

    @Test
    void laAyudaCambiaConLosPermisos() throws Exception {
        probador.ejecutar("DELETE FROM rol_permiso WHERE rol_id = 3 AND permiso_id = (SELECT id FROM permiso WHERE comando = 'ENVNOT')");
        assertFalse(probador.texto(PERIODISTA, "AYUDA").contains("ENVNOT"));
    }

    @Test
    void ayudaDeUnRemitenteNoRegistrado() throws Exception {
        String t = probador.texto("desconocido@gmail.com", "ayuda");
        assertTrue(t.contains("No está registrado en el sistema. Contacte al Director del canal."), t);
        Fila b = ultimaBitacora();
        assertEquals("desconocido@gmail.com", b.texto("remitente"));
        assertNull(b.get("usuario_id"));
        assertEquals("error", b.texto("resultado"));
    }

    // ------------------------------------------------------------------ R1 y D16

    @Test
    void unComandoQueNoExiste() throws Exception {
        String t = probador.texto(DIRECTOR, "HOLAAA[\"1\"]");
        assertTrue(t.contains("Error: comando no reconocido. Envíe AYUDA para ver los comandos disponibles"), t);
        assertNull(ultimaBitacora().get("comando"));
    }

    @Test
    void sinPermisoNoSeEjecuta() throws Exception {
        long antes = probador.numero("SELECT count(*) FROM rol_permiso");
        String t = probador.texto(PERIODISTA, "INSRPM[\"3\",\"LISBIT\"]");
        assertTrue(t.contains("Error: no tiene permiso para ejecutar INSRPM"), t);
        assertEquals(antes, probador.numero("SELECT count(*) FROM rol_permiso"));
        assertEquals("INSRPM", ultimaBitacora().texto("comando"));
    }

    @Test
    void unRemitenteInactivoEsComoNoRegistrado() throws Exception {
        probador.ejecutar("UPDATE usuario SET estado = 'inactivo' WHERE correo = ?", PERIODISTA);
        String t = probador.texto(PERIODISTA, "LISNOT[\"*\"]");
        assertTrue(t.contains("Error: no tiene permiso para ejecutar LISNOT. No está registrado en el sistema"), t);
    }

    @Test
    void elCorreoSeComparaSinMayusculas() {
        assertTrue(probador.texto("Director@Canal11.TEST", "AYUDA").contains("Hola, Américo Claros (Director)"));
    }

    // ------------------------------------------------------------------ formato (4.2.4)

    @Test
    void insSinCorchetesPideElFormatoConUnEjemplo() {
        String t = probador.texto(DIRECTOR, "DELUSU");
        assertTrue(t.contains("Error: formato esperado DELUSU[\"id\"]"), t);
        assertTrue(t.contains("Ejemplo: DELUSU[\"3\"]"), t);
    }

    @Test
    void cantidadYTipoDeLosParametros() {
        assertTrue(probador.texto(DIRECTOR, "DELUSU[\"8\",\"9\"]").contains("Error: se esperaba 1 parámetro (id) y llegaron 2"));
        assertTrue(probador.texto(DIRECTOR, "DELUSU[\"ocho\"]").contains("Error: «id» debe ser un número entero (llegó «ocho»)"));
    }

    @Test
    void unAsuntoMalFormadoMuestraElEjemploDelComando() {
        String t = probador.texto(DIRECTOR, "DELUSU[\"3\"");
        assertTrue(t.contains("Error: formato esperado"), t);
        assertTrue(t.contains("Ejemplo: DELUSU["), t);
    }

    // ------------------------------------------------------------------ transacción (D12) y bitácora (R2)

    /**
     * Un catálogo de prueba: INSROL inserta un rol y después falla de distintas formas. INSROL ya
     * no es un comando del sistema: su permiso se agrega solo dentro de la transacción de la prueba.
     */
    private Probador conAccion(String modo) throws Exception {
        probador.close();
        CommandSpec spec = CommandSpec.de("CU-01", "INSROL", "Registrar rol")
                .param("nombre", Tipo.texto(50)).opcional("descripcion", Tipo.texto(150))
                .ejemplo("INSROL[\"Productor\",\"x\"]")
                .accion(ctx -> {
                    ctx.ejecutar("INSERT INTO rol (nombre) VALUES (?)", ctx.p().texto("nombre"));
                    switch (modo) {
                        case "negocio" -> throw ErrorNegocio.de("una regla no se cumple");
                        case "sql" -> ctx.ejecutar("INSERT INTO rol (nombre) VALUES ('Director')");
                        case "inesperado" -> throw new IllegalStateException("bug");
                        case "error" -> throw new StackOverflowError("recursión sin fin");
                        case "diferida" -> ctx.ejecutar("UPDATE item_escaleta SET orden = 1 WHERE escaleta_id = 1 AND bloque = 'A' AND orden = 2");
                        default -> {
                        }
                    }
                    return Resultado.exito("Roles", "rol registrado");
                }).build();
        probador = Probador.abrir(new Catalogo(List.of(spec)));
        probador.ejecutar("INSERT INTO permiso (comando, descripcion, caso_uso) VALUES ('INSROL', 'Registrar rol', 'CU-01')");
        probador.ejecutar("INSERT INTO rol_permiso (rol_id, permiso_id) SELECT r.id, p.id FROM rol r, permiso p "
                + "WHERE r.nombre = 'Director' AND p.comando = 'INSROL'");
        return probador;
    }

    @Test
    void unErrorDeNegocioDeshaceTodo() throws Exception {
        String t = conAccion("negocio").texto(DIRECTOR, "INSROL[\"Productor\",\"\"]");
        assertTrue(t.contains("Error: una regla no se cumple"), t);
        assertEquals(0, probador.numero("SELECT count(*) FROM rol WHERE nombre = 'Productor'"));
        Fila b = ultimaBitacora();
        assertEquals("error", b.texto("resultado"));
        assertEquals("Error: una regla no se cumple", b.texto("respuesta"));
    }

    @Test
    void unErrorDeLaBaseSeTraduceYDeshaceTodo() throws Exception {
        String t = conAccion("sql").texto(DIRECTOR, "INSROL[\"Productor\",\"\"]");
        assertTrue(t.contains("Error: ya existe un rol con ese nombre"), t);
        assertFalse(t.contains("duplicate key"), "nunca el texto crudo de PostgreSQL");
        assertEquals(0, probador.numero("SELECT count(*) FROM rol WHERE nombre = 'Productor'"));
    }

    @Test
    void unaRestriccionDiferidaTambienSeTraduce() throws Exception {
        String t = conAccion("diferida").texto(DIRECTOR, "INSROL[\"Productor\",\"\"]");
        assertTrue(t.contains("Error: ya hay un ítem en esa posición del bloque"), t);
        assertEquals(0, probador.numero("SELECT count(*) FROM rol WHERE nombre = 'Productor'"));
    }

    @Test
    void unErrorInesperadoNoTumbaNada() throws Exception {
        String t = conAccion("inesperado").texto(DIRECTOR, "INSROL[\"Productor\",\"\"]");
        assertTrue(t.contains(Pipeline.ERROR_GENERAL), t);
        assertEquals(0, probador.numero("SELECT count(*) FROM rol WHERE nombre = 'Productor'"));
        // el siguiente comando funciona
        assertTrue(conAccion("ok").texto(DIRECTOR, "INSROL[\"Productor\",\"\"]").contains("Éxito: rol registrado correctamente"));
        assertEquals(1, probador.numero("SELECT count(*) FROM rol WHERE nombre = 'Productor'"));
    }

    @Test
    void unErrorDeJavaTambienDeshaceTodo() throws Exception {
        // setAutoCommit(true) confirma lo pendiente: sin el rollback, el rol quedaría guardado
        assertThrows(StackOverflowError.class, () -> conAccion("error").texto(DIRECTOR, "INSROL[\"Productor\",\"\"]"));
        assertEquals(0, probador.numero("SELECT count(*) FROM rol WHERE nombre = 'Productor'"));
    }

    @Test
    void laBitacoraNoGuardaLaContrasena() throws Exception {
        RespuestaCorreo r = probador.enviar(DIRECTOR,
                "INSUSU[\"Ana\",\"Paz\",\"9100001\",\"ana.paz@gmail.com\",\"7\",\"clave-de-ana\",\"3\"]", "");
        assertFalse(r.asunto().contains("clave-de-ana"));
        assertFalse(r.html().contains("clave-de-ana"));
        Fila b = ultimaBitacora();
        assertTrue(b.texto("asunto").contains(Enmascarador.MASCARA), b.texto("asunto"));
        assertFalse(b.texto("asunto").contains("clave-de-ana"));
    }

    @Test
    void elAsuntoDeLaRespuesta() {
        assertEquals("Re: LISROL[\"*\"]", probador.enviar(DIRECTOR, "LISROL[\"*\"]", "").asunto());
        assertEquals("Re: (sin asunto)", probador.enviar(DIRECTOR, "", "").asunto());
    }

    @Test
    void unComandoTodaviaNoHabilitadoAvisa() throws Exception {
        probador.close();
        CommandSpec sinAccion = CommandSpec.de("CU-07", "ANAPER", "Producción por periodista")
                .param("fecha_desde", Tipo.FECHA).param("fecha_hasta", Tipo.FECHA)
                .ejemplo("ANAPER[\"2026-10-01\",\"2026-10-31\"]").build();
        probador = Probador.abrir(new Catalogo(List.of(sinAccion)));
        String t = probador.texto(JEFE_PRENSA, "ANAPER[\"2026-10-01\",\"2026-10-31\"]");
        assertTrue(t.contains("todavía no está habilitado"), t);
        assertTrue(probador.texto(JEFE_PRENSA, "AYUDA").contains("(próximamente)"));
    }
}
