package bo.edu.uagrm.ficct.canal11.comandos;

import bo.edu.uagrm.ficct.canal11.datos.Repositorio;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Decisión D4: una cobertura {@code planificada} cuya hora de inicio ya llegó pasa sola a
 * {@code en_curso}. Se ejecuta en cada vuelta del bucle, antes de leer el buzón.
 *
 * <p>Para los choques no cambia nada (las dos ocupan personal y recursos, R3); sirve para saber qué
 * equipos están en la calle. Ningún comando pone este estado.</p>
 */
public final class CoberturasEnCurso {

    private static final String ACTUALIZAR = """
            UPDATE cobertura
               SET estado = 'en_curso', actualizado_en = CURRENT_TIMESTAMP
             WHERE estado = 'planificada'
               AND fecha_hora_inicio <= LOCALTIMESTAMP
            """;

    private final Repositorio repositorio;

    public CoberturasEnCurso(Repositorio repositorio) {
        this.repositorio = repositorio;
    }

    /** Pasa a {@code en_curso} las coberturas que ya empezaron; devuelve cuántas cambiaron. */
    public int actualizar(Connection c) throws SQLException {
        return repositorio.ejecutar(c, ACTUALIZAR);
    }
}
