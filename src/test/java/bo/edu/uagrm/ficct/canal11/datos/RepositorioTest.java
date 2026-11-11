package bo.edu.uagrm.ficct.canal11.datos;

import bo.edu.uagrm.ficct.canal11.datos.TraductorErrores.Operacion;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("bd")
class RepositorioTest {

    private final Repositorio repo = new Repositorio(10);

    @Test
    void consultaFilasConLasColumnasEnOrden() throws Exception {
        List<Fila> filas = repo.consultar(BaseDatosPrueba.conexion().obtener(),
                "SELECT id, correo, rol_id FROM usuario ORDER BY id");
        assertEquals(15, filas.size());
        assertEquals(List.of("id", "correo", "rol_id"), filas.get(0).columnas());
        assertEquals("director@canal11.test", filas.get(0).texto("correo"));
        assertEquals(1, filas.get(0).entero("rol_id"));
    }

    @Test
    void lasFechasYHorasLleganComoJavaTime() throws Exception {
        Fila f = repo.consultarUno(BaseDatosPrueba.conexion().obtener(),
                "SELECT DATE '2026-10-12' AS d, TIME '15:00' AS h, TIMESTAMP '2026-10-12 15:00' AS dh").orElseThrow();
        assertEquals(LocalDate.of(2026, 10, 12), f.fecha("d"));
        assertEquals(LocalTime.of(15, 0), f.hora("h"));
        assertEquals(LocalDateTime.of(2026, 10, 12, 15, 0), f.fechaHora("dh"));
    }

    @Test
    void recibeParametrosDeVariosTipos() throws Exception {
        LocalDate hoy = LocalDate.now(BaseDatosPrueba.ZONA);
        Fila f = repo.consultarUno(BaseDatosPrueba.conexion().obtener(),
                "SELECT count(*) AS n FROM cobertura WHERE fecha_hora_inicio::date = ? AND prioridad = ?",
                hoy, "alta").orElseThrow();
        // Coberturas de hoy con prioridad alta en los datos de prueba: Presupuesto, Paro de transporte e Incendios
        assertEquals(3, f.entero("n"));

        Fila nulo = repo.consultarUno(BaseDatosPrueba.conexion().obtener(),
                "SELECT ?::text IS NULL AS es_nulo", (Object) null).orElseThrow();
        assertTrue(nulo.logico("es_nulo"));
    }

    @Test
    void elTextoDelCorreoNuncaSeEjecutaComoSql() throws Exception {
        String malicioso = "x'; DROP TABLE rol; --";
        List<Fila> filas = repo.consultar(BaseDatosPrueba.conexion().obtener(),
                "SELECT id FROM rol WHERE nombre = ?", malicioso);
        assertTrue(filas.isEmpty());
        assertEquals(7, repo.consultar(BaseDatosPrueba.conexion().obtener(), "SELECT id FROM rol").size());
    }

    @Test
    void registraYDevuelveLaFilaDentroDeUnaTransaccion() throws Exception {
        List<Fila> creadas = BaseDatosPrueba.enTransaccion(c ->
                repo.ejecutarDevolviendo(c, "INSERT INTO rol (nombre, descripcion) VALUES (?, ?) RETURNING id, nombre",
                        "Productor", "Apoya el armado de la escaleta"));
        assertEquals(1, creadas.size());
        assertEquals("Productor", creadas.get(0).texto("nombre"));
        // La transacción se deshizo: el rol no quedó
        Optional<Fila> despues = repo.consultarUno(BaseDatosPrueba.conexion().obtener(),
                "SELECT id FROM rol WHERE nombre = ?", "Productor");
        assertTrue(despues.isEmpty());
    }

    @Test
    void ejecutarDevuelveLasFilasAfectadas() throws Exception {
        int afectadas = BaseDatosPrueba.enTransaccion(c ->
                repo.ejecutar(c, "UPDATE recurso SET observacion = ? WHERE tipo_recurso_id = ?", "revisado", 2));
        assertEquals(4, afectadas);
    }

    @Test
    void consultarUnoFallaSiHayVariasFilas() throws Exception {
        Connection c = BaseDatosPrueba.conexion().obtener();
        assertThrows(IllegalStateException.class, () -> repo.consultarUno(c, "SELECT id FROM rol"));
    }

    @Test
    void unaConsultaLentaSeCortaPorTiempo() throws Exception {
        Connection c = BaseDatosPrueba.conexion().obtener();
        Repositorio apurado = new Repositorio(1);
        SQLException e = assertThrows(SQLException.class, () -> apurado.consultar(c, "SELECT pg_sleep(5)"));
        assertEquals("Error: la operación tardó demasiado; intente de nuevo",
                TraductorErrores.traducir(e, Operacion.CONSULTAR));
    }
}
