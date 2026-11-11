package bo.edu.uagrm.ficct.canal11.config;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

/**
 * Garantiza que corra una sola copia de la aplicación (decisión D9): dos copias se pelearían por el
 * buzón y podrían procesar el mismo correo.
 *
 * <p>Usa un bloqueo del sistema operativo sobre un archivo ({@link FileChannel#tryLock()}). Si el
 * proceso muere, el sistema libera el bloqueo solo; por eso un {@code cron} puede intentar
 * arrancarla cada tanto sin riesgo de duplicarla.</p>
 */
public final class BloqueoInstancia implements AutoCloseable {

    private final FileChannel canal;
    private final FileLock bloqueo;

    private BloqueoInstancia(FileChannel canal, FileLock bloqueo) {
        this.canal = canal;
        this.bloqueo = bloqueo;
    }

    /**
     * Intenta tomar el bloqueo.
     *
     * @return el bloqueo, o vacío si ya hay otra copia corriendo
     */
    public static Optional<BloqueoInstancia> tomar(Path archivo) throws IOException {
        FileChannel canal = FileChannel.open(archivo, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            FileLock bloqueo = canal.tryLock();
            if (bloqueo == null) {
                canal.close();
                return Optional.empty();
            }
            canal.truncate(0);
            canal.write(ByteBuffer.wrap((ProcessHandle.current().pid() + "\n").getBytes(StandardCharsets.US_ASCII)));
            canal.force(false);
            return Optional.of(new BloqueoInstancia(canal, bloqueo));
        } catch (OverlappingFileLockException e) {
            // ya tomado dentro de esta misma JVM
            canal.close();
            return Optional.empty();
        } catch (IOException | RuntimeException e) {
            canal.close();
            throw e;
        }
    }

    @Override
    public void close() throws IOException {
        try {
            bloqueo.release();
        } finally {
            canal.close();
        }
    }
}
