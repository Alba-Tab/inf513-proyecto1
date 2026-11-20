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
}
