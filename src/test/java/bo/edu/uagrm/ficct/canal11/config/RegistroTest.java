package bo.edu.uagrm.ficct.canal11.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegistroTest {

    @TempDir
    Path carpeta;

    /** access.log solo lleva las líneas de los correos; error.log, solo los avisos y los errores. */
    @Test
    void separaAccesoYErrores() throws Exception {
        Logger raiz = Logger.getLogger("bo.edu.uagrm.ficct.canal11");
        boolean padres = raiz.getUseParentHandlers();
        Level nivel = raiz.getLevel();
        try {
            Registro.iniciar(carpeta, ZoneId.of("America/La_Paz"));
            Logger.getLogger(Registro.ACCESO).info("ana@gmail.com | LISPER | Éxito | «LISPER[\"*\"]» | 9 registros");
            Logger etapa = Logger.getLogger("bo.edu.uagrm.ficct.canal11.pipeline.Pipeline");
            etapa.info("lectura del buzón");
            etapa.log(Level.SEVERE, "Falló Ejecutar", new IllegalStateException("sin conexión"));
            for (Handler h : raiz.getHandlers()) {
                h.flush();
            }
            String acceso = Files.readString(carpeta.resolve("access.log"), StandardCharsets.UTF_8);
            String errores = Files.readString(carpeta.resolve("error.log"), StandardCharsets.UTF_8);
            assertTrue(acceso.contains("ana@gmail.com | LISPER | Éxito"), acceso);
            assertFalse(acceso.contains("Falló Ejecutar") || acceso.contains("lectura del buzón"), acceso);
            assertTrue(errores.contains("SEVERE") && errores.contains("Falló Ejecutar") && errores.contains("sin conexión"), errores);
            assertFalse(errores.contains("ana@gmail.com") || errores.contains("lectura del buzón"), errores);
            assertTrue(Files.readString(carpeta.resolve("canal11.0.log"), StandardCharsets.UTF_8).contains("lectura del buzón"));
        } finally {
            for (Handler h : raiz.getHandlers()) {
                raiz.removeHandler(h);
                h.close();
            }
            raiz.setUseParentHandlers(padres);
            raiz.setLevel(nivel);
        }
    }

    /** La consola muestra una etiqueta, el remitente, el comando y el resumen, sin las comillas «». */
    @Test
    void consolaOrdenada() {
        Registro.FormatoConsola f = new Registro.FormatoConsola(ZoneId.of("America/La_Paz"));
        java.util.logging.LogRecord ok = new java.util.logging.LogRecord(Level.INFO,
                "ana@gmail.com | LISUSU | Éxito | «lisusu» | 15 registros.");
        ok.setLoggerName(Registro.ACCESO);
        String linea = f.format(ok);
        assertTrue(linea.contains("OK") && linea.contains("ana@gmail.com") && linea.contains("LISUSU")
                && linea.contains("15 registros."), linea);
        assertFalse(linea.contains("«") || linea.contains("|"), linea);

        java.util.logging.LogRecord rec = new java.util.logging.LogRecord(Level.INFO, "Correo de ana@gmail.com: «lisusu»");
        rec.setLoggerName("bo.edu.uagrm.ficct.canal11.pipeline.etapas.Filtrar");
        assertTrue(f.format(rec).contains("RECIBIDO") && f.format(rec).contains("lisusu"), f.format(rec));

        java.util.logging.LogRecord env = new java.util.logging.LogRecord(Level.INFO, "Respuesta enviada a ana@gmail.com («Re: lisusu»)");
        env.setLoggerName("bo.edu.uagrm.ficct.canal11.pipeline.SendEmailThread");
        String e = f.format(env);
        assertTrue(e.contains("ENVIADO") && e.contains("ana@gmail.com") && e.contains("Re: lisusu"), e);
    }

    /** La consola no muestra tildes ni símbolos raros, para que se lea igual en cualquier terminal. */
    @Test
    void consolaSoloAscii() {
        assertEquals("Hola, Americo Claros: \"Exito\" - ok.", Registro.aAscii("Hola, Américo Claros: «Éxito» – ok…"));
    }
}
