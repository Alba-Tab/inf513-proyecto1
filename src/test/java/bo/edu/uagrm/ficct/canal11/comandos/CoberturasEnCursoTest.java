package bo.edu.uagrm.ficct.canal11.comandos;

import bo.edu.uagrm.ficct.canal11.datos.BaseDatosPrueba;
import bo.edu.uagrm.ficct.canal11.datos.Repositorio;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("bd")
class CoberturasEnCursoTest {

    private final Repositorio repo = new Repositorio(10);

    private static final String NUEVA = """
            INSERT INTO cobertura (titulo, tipo, fecha_hora_inicio, fecha_hora_fin, lugar, creado_por)
            VALUES (?, 'conferencia', LOCALTIMESTAMP + make_interval(mins => ?), LOCALTIMESTAMP + make_interval(mins => ?), 'Prueba', 2)
            RETURNING id
            """;

    @Test
    void pasaAEnCursoSoloLasPlanificadasQueYaEmpezaron() throws Exception {
        BaseDatosPrueba.enTransaccion(c -> {
            int empezo = repo.ejecutarDevolviendo(c, NUEVA, "Empezó hace una hora", -60, 30).get(0).entero("id");
            int manana = repo.ejecutarDevolviendo(c, NUEVA, "Empieza mañana", 24 * 60, 25 * 60).get(0).entero("id");
            int cumplida = repo.ejecutarDevolviendo(c, NUEVA, "Ya volvió", -120, -60).get(0).entero("id");
            repo.ejecutar(c, "UPDATE cobertura SET estado = 'cumplida' WHERE id = ?", cumplida);

            int cambiadas = new CoberturasEnCurso(repo).actualizar(c);
            assertTrue(cambiadas >= 1);
            assertEquals("en_curso", estado(c, empezo));
            assertEquals("planificada", estado(c, manana));
            assertEquals("cumplida", estado(c, cumplida), "un resultado del retorno no se toca");
            assertEquals(0, new CoberturasEnCurso(repo).actualizar(c), "una segunda vuelta no cambia nada");
            return null;
        });
    }

    private String estado(Connection c, int id) throws Exception {
        return repo.consultarUno(c, "SELECT estado FROM cobertura WHERE id = ?", id).orElseThrow().texto("estado");
    }
}
