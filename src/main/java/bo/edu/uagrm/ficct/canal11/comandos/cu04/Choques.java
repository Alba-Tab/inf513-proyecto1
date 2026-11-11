package bo.edu.uagrm.ficct.canal11.comandos.cu04;

import bo.edu.uagrm.ficct.canal11.datos.Fila;
import bo.edu.uagrm.ficct.canal11.comandos.Contexto;
import bo.edu.uagrm.ficct.canal11.comandos.Sql;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Validación de conflictos de una cobertura (RF-06, RNF-19), con las consultas de
 * {@code db/consultas_negocio.sql}. Los rangos son semiabiertos: dos se cruzan cuando
 * {@code inicio1 < fin2 AND inicio2 < fin1}.
 *
 * <ul>
 *   <li>R3: solo las coberturas {@code planificada} y {@code en_curso} ocupan (vistas
 *       {@code v_ocupacion_*}).</li>
 *   <li>R6: una ausencia o una ocupación que se cruza es un error; quedar fuera del turno es solo
 *       una advertencia.</li>
 *   <li>R7: un recurso que no está operativo o que tiene otra reserva que se cruza es un error.</li>
 * </ul>
 *
 * <p>Cada método devuelve el mensaje del problema (sin «Error: »), o vacío si no hay.</p>
 */
final class Choques {

    private static final String AUSENCIA = """
            SELECT a.tipo, %s AS periodo
            FROM ausencia a JOIN cobertura c ON c.id = ?
            WHERE a.personal_id = ?
              AND a.fecha_hora_inicio < c.fecha_hora_fin AND c.fecha_hora_inicio < a.fecha_hora_fin
            ORDER BY a.fecha_hora_inicio LIMIT 1
            """.formatted(Sql.rango("a.fecha_hora_inicio", "a.fecha_hora_fin"));

    private static final String OCUPACION_PERSONAL = """
            SELECT o.titulo, %s AS horario
            FROM v_ocupacion_personal o JOIN cobertura c ON c.id = ?
            WHERE o.personal_id = ? AND o.cobertura_id <> c.id
              AND o.fecha_hora_inicio < c.fecha_hora_fin AND c.fecha_hora_inicio < o.fecha_hora_fin
            ORDER BY o.fecha_hora_inicio LIMIT 1
            """.formatted(Sql.rango("o.fecha_hora_inicio", "o.fecha_hora_fin"));

    /** Fila si la cobertura NO cae completa dentro del turno de la persona (turno que empieza ese día o el anterior). */
    private static final String FUERA_DE_TURNO = """
            SELECT t.nombre, to_char(t.hora_inicio, 'HH24:MI') || '–' || to_char(t.hora_fin, 'HH24:MI') AS horario
            FROM personal p
            JOIN turno t     ON t.id = p.turno_id
            JOIN cobertura c ON c.id = ?
            WHERE p.id = ?
              AND NOT EXISTS (SELECT 1
                              FROM (VALUES (c.fecha_hora_inicio::date), (c.fecha_hora_inicio::date - 1)) AS d(dia)
                              WHERE EXTRACT(ISODOW FROM d.dia)::int = ANY (string_to_array(t.dias, ',')::int[])
                                AND c.fecha_hora_inicio >= d.dia + t.hora_inicio
                                AND c.fecha_hora_fin   <= d.dia + t.hora_fin
                                       + CASE WHEN t.hora_fin <= t.hora_inicio THEN INTERVAL '1 day' ELSE INTERVAL '0' END)
            """;

    private static final String OCUPACION_RECURSO = """
            SELECT o.titulo, %s AS horario
            FROM v_ocupacion_recurso o JOIN cobertura c ON c.id = ?
            WHERE o.recurso_id = ? AND o.cobertura_id <> c.id
              AND o.fecha_hora_inicio < c.fecha_hora_fin AND c.fecha_hora_inicio < o.fecha_hora_fin
            ORDER BY o.fecha_hora_inicio LIMIT 1
            """.formatted(Sql.rango("o.fecha_hora_inicio", "o.fecha_hora_fin"));

    private Choques() {
    }

    /** R6: una ausencia u otra cobertura que se cruza (error). */
    static Optional<String> dePersona(Contexto ctx, int idCobertura, int idPersonal, String nombre) throws SQLException {
        Optional<Fila> ausencia = ctx.uno(AUSENCIA, idCobertura, idPersonal);
        if (ausencia.isPresent()) {
            return Optional.of(nombre + " tiene " + ausencia.get().texto("tipo").replace('_', ' ') + " en ese horario ("
                    + ausencia.get().texto("periodo") + ")");
        }
        Optional<Fila> otra = ctx.uno(OCUPACION_PERSONAL, idCobertura, idPersonal);
        return otra.map(f -> nombre + " ya tiene asignada la cobertura «" + f.texto("titulo") + "» " + f.texto("horario"));
    }

    /** R6: la cobertura queda fuera del turno de la persona (advertencia). */
    static Optional<String> fueraDeTurno(Contexto ctx, int idCobertura, int idPersonal, String nombre) throws SQLException {
        return ctx.uno(FUERA_DE_TURNO, idCobertura, idPersonal)
                .map(f -> nombre + " está fuera de su turno (" + f.texto("nombre") + " " + f.texto("horario") + ")");
    }

    /** R7: el recurso no está operativo o ya está reservado en otra cobertura que se cruza (error). */
    static Optional<String> deRecurso(Contexto ctx, int idCobertura, int idRecurso, String codigo, String estado) throws SQLException {
        if (!"operativo".equals(estado)) {
            return Optional.of(codigo + " está " + ("baja".equals(estado) ? "dado de baja" : "en mantenimiento"));
        }
        return ctx.uno(OCUPACION_RECURSO, idCobertura, idRecurso)
                .map(f -> codigo + " ocupado " + f.texto("horario") + " por «" + f.texto("titulo") + "»");
    }

    /** Todos los conflictos del equipo ya asignado (UPDCOB los revalida después de cambiar el horario). */
    static List<String> delEquipo(Contexto ctx, int idCobertura, List<String> advertencias) throws SQLException {
        List<String> errores = new ArrayList<>();
        for (Fila f : ctx.consultar("""
                SELECT cp.personal_id, u.nombre || ' ' || u.apellido AS nombre
                FROM cobertura_personal cp JOIN personal p ON p.id = cp.personal_id JOIN usuario u ON u.id = p.usuario_id
                WHERE cp.cobertura_id = ? ORDER BY cp.id
                """, idCobertura)) {
            dePersona(ctx, idCobertura, f.entero("personal_id"), f.texto("nombre")).ifPresent(errores::add);
            fueraDeTurno(ctx, idCobertura, f.entero("personal_id"), f.texto("nombre")).ifPresent(advertencias::add);
        }
        for (Fila f : ctx.consultar("""
                SELECT r.id, r.codigo, r.estado FROM cobertura_recurso cr JOIN recurso r ON r.id = cr.recurso_id
                WHERE cr.cobertura_id = ? ORDER BY r.codigo
                """, idCobertura)) {
            deRecurso(ctx, idCobertura, f.entero("id"), f.texto("codigo"), f.texto("estado")).ifPresent(errores::add);
        }
        return errores;
    }
}
