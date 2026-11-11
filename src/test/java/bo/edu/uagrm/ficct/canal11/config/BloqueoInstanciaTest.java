package bo.edu.uagrm.ficct.canal11.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BloqueoInstanciaTest {

    @Test
    void unaSegundaCopiaNoTomaElBloqueo(@TempDir Path dir) throws Exception {
        Path archivo = dir.resolve("canal11.lock");
        Optional<BloqueoInstancia> primera = BloqueoInstancia.tomar(archivo);
        assertTrue(primera.isPresent());
        try {
            assertTrue(BloqueoInstancia.tomar(archivo).isEmpty());
            assertEquals(String.valueOf(ProcessHandle.current().pid()),
                    Files.readString(archivo, StandardCharsets.US_ASCII).strip());
        } finally {
            primera.get().close();
        }
        // liberado, se puede volver a tomar
        Optional<BloqueoInstancia> otra = BloqueoInstancia.tomar(archivo);
        assertTrue(otra.isPresent());
        otra.get().close();
    }
}
