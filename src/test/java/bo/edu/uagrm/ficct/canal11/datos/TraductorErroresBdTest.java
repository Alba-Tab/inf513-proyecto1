package bo.edu.uagrm.ficct.canal11.datos;

import bo.edu.uagrm.ficct.canal11.datos.TraductorErrores.Operacion;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** El traductor contra el esquema real: cobertura de todas las restricciones y violaciones de verdad. */
@Tag("bd")
class TraductorErroresBdTest {

    private final Repositorio repo = new Repositorio(10);

    /** Restricciones del esquema (sin las claves primarias) e índices únicos que no son restricción. */
    private static final String RESTRICCIONES_DEL_ESQUEMA = """
            SELECT c.conname AS nombre
            FROM pg_constraint c
            JOIN pg_class t ON t.oid = c.conrelid
            JOIN pg_namespace n ON n.oid = t.relnamespace
            WHERE n.nspname = 'public' AND c.contype IN ('c', 'u', 'f', 'x')
            UNION
            SELECT i.relname
            FROM pg_index x
            JOIN pg_class i ON i.oid = x.indexrelid
            JOIN pg_namespace n ON n.oid = i.relnamespace
            WHERE n.nspname = 'public' AND x.indisunique
              AND NOT EXISTS (SELECT 1 FROM pg_constraint c WHERE c.conindid = x.indexrelid)
            """;

    @Test
    void todasLasRestriccionesDelEsquemaTienenMensaje() throws Exception {
        Set<String> esquema = new HashSet<>();
        for (Fila f : repo.consultar(BaseDatosPrueba.conexion().obtener(), RESTRICCIONES_DEL_ESQUEMA)) {
            esquema.add(f.texto("nombre"));
        }
        Set<String> sinMensaje = new TreeSet<>(esquema);
        sinMensaje.removeAll(TraductorErrores.restriccionesConocidas());
        assertTrue(sinMensaje.isEmpty(), "Restricciones sin mensaje en TraductorErrores: " + sinMensaje);

        Set<String> sobrantes = new TreeSet<>(TraductorErrores.restriccionesConocidas());
        sobrantes.removeAll(esquema);
        assertTrue(sobrantes.isEmpty(), "Mensajes de restricciones que ya no existen en el esquema: " + sobrantes);
    }

    /** Ejecuta una sentencia que debe fallar y devuelve el mensaje traducido. */
    private String mensajeDe(Operacion op, String sql, Object... parametros) throws Exception {
        return BaseDatosPrueba.enTransaccion(c -> {
            try {
                repo.ejecutar(c, sql, parametros);
            } catch (SQLException e) {
                return TraductorErrores.traducir(e, op);
            }
            fail("La sentencia debía fallar: " + sql);
            return null;
        });
    }

    private static final String NUEVO_USUARIO =
            "INSERT INTO usuario (nombre, apellido, ci, correo, password, rol_id) VALUES ('Ana', 'Paz', ?, ?, 'x', ?)";

    @Test
    void correoRepetido() throws Exception {
        assertEquals("Error: el correo ya está registrado",
                mensajeDe(Operacion.REGISTRAR, NUEVO_USUARIO, "9999999", "director@canal11.test", 1));
    }

    @Test
    void rolQueNoExiste() throws Exception {
        assertEquals("Error: el rol indicado no existe",
                mensajeDe(Operacion.REGISTRAR, NUEVO_USUARIO, "9999999", "ana.paz@canal11.test", 99));
    }

    @Test
    void rolConUsuariosNoSeElimina() throws Exception {
        assertEquals("Error: el rol tiene usuarios asignados",
                mensajeDe(Operacion.ELIMINAR, "DELETE FROM rol WHERE id = ?", 1));
    }

    @Test
    void recursoConHistorialNoSeBorra() throws Exception {
        assertEquals("Error: el recurso tiene historial de mantenimiento y no se puede borrar; para darlo de baja use DELREC",
                mensajeDe(Operacion.ELIMINAR, "DELETE FROM recurso WHERE codigo = ?", "CAM-04"));
    }

    @Test
    void ausenciaQueTerminaAntesDeEmpezar() throws Exception {
        assertEquals("Error: el fin debe ser posterior al inicio",
                mensajeDe(Operacion.REGISTRAR,
                        "INSERT INTO ausencia (personal_id, tipo, fecha_hora_inicio, fecha_hora_fin) "
                                + "VALUES (1, 'permiso', TIMESTAMP '2026-10-14 11:00', TIMESTAMP '2026-10-14 09:00')"));
    }

    @Test
    void recursoEntregadoSinDevolver() throws Exception {
        // MOV-02 (id 13) está entregado en los datos de prueba y no fue devuelto
        assertEquals("Error: el recurso ya está entregado y no fue devuelto",
                mensajeDe(Operacion.REGISTRAR,
                        "INSERT INTO prestamo (recurso_id, responsable_id) VALUES (?, ?)", 13, 6));
    }

    @Test
    void datoObligatorioFaltante() throws Exception {
        assertEquals("Error: falta el dato «nombre»",
                mensajeDe(Operacion.REGISTRAR, "INSERT INTO rol (nombre) VALUES (?)", (Object) null));
    }

    @Test
    void valorFueraDelDominio() throws Exception {
        assertEquals("Error: la prioridad debe ser alta, media o baja",
                mensajeDe(Operacion.MODIFICAR, "UPDATE cobertura SET prioridad = ? WHERE id = 1", "nula"));
    }

    @Test
    void textoMasLargoQueLaColumna() throws Exception {
        // prioridad es VARCHAR(5): PostgreSQL corta por largo antes de revisar el dominio
        assertEquals("Error: un texto supera el largo permitido",
                mensajeDe(Operacion.MODIFICAR, "UPDATE cobertura SET prioridad = ? WHERE id = 1", "urgente"));
    }
}
