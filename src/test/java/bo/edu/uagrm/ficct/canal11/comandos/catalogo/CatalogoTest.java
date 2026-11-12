package bo.edu.uagrm.ficct.canal11.comandos.catalogo;

import bo.edu.uagrm.ficct.canal11.datos.BaseDatosPrueba;
import bo.edu.uagrm.ficct.canal11.datos.Fila;
import bo.edu.uagrm.ficct.canal11.datos.Repositorio;
import bo.edu.uagrm.ficct.canal11.pipeline.ComandoParseado;
import bo.edu.uagrm.ficct.canal11.pipeline.CommandParser;
import bo.edu.uagrm.ficct.canal11.comandos.ErrorNegocio;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogoTest {

    private final Catalogo catalogo = Catalogo.completo();

    @Test
    void tieneLosComandosDeLosCasosDeUsoAprobados() {
        assertEquals(65, catalogo.cantidad());
        Map<String, Long> porCu = catalogo.todos().stream()
                .collect(Collectors.groupingBy(CommandSpec::casoUso, TreeMap::new, Collectors.counting()));
        assertEquals(Map.of("CU-01", 8L, "CU-02", 9L, "CU-03", 9L, "CU-04", 10L, "CU-05", 11L,
                "CU-06", 13L, "CU-07", 2L, "CU-08", 3L), porCu);
    }

    /** Cada ejemplo del Anexo A se lee con su código y pasa la validación de su ficha. */
    @Test
    void cadaEjemploEsValido() throws ErrorNegocio {
        for (CommandSpec s : catalogo.todos()) {
            ComandoParseado p = CommandParser.analizar(s.ejemplo());
            ComandoParseado.Comando c = assertInstanceOf(ComandoParseado.Comando.class, p, s.codigo());
            assertEquals(s.codigo(), c.codigo());
            s.validar(c.parametros());
        }
    }

    @Test
    void todosLosComandosTienenSuAccion() {
        assertEquals(List.of(), catalogo.todos().stream().filter(CommandSpec::pendiente).map(CommandSpec::codigo).toList());
    }

    @Test
    void validaLaCantidadDeParametros() {
        CommandSpec delusu = catalogo.buscar("DELUSU").orElseThrow();
        assertEquals("Error: se esperaba 1 parámetro (id) y no llegó ninguno",
                assertThrows(ErrorNegocio.class, () -> delusu.validar(List.of())).getMessage());
        CommandSpec disper = catalogo.buscar("DISPER").orElseThrow();
        assertEquals("Error: se esperaban entre 2 y 3 parámetros (fecha, hora, cargo) y llegaron 4",
                assertThrows(ErrorNegocio.class, () -> disper.validar(List.of("2026-10-12", "16:00", "camarografo", "x"))).getMessage());
        CommandSpec insrpm = catalogo.buscar("INSRPM").orElseThrow();
        assertEquals("Error: falta «id_rol»: es obligatorio",
                assertThrows(ErrorNegocio.class, () -> insrpm.validar(List.of("", "LISCOB"))).getMessage());
        assertEquals("INSRPM[\"id_rol\",\"comando\"]", insrpm.formato());
    }

    @Test
    void losOmitiblesPuedenFaltar() throws ErrorNegocio {
        Parametros p = catalogo.buscar("DISPER").orElseThrow().validar(List.of("2026-10-12", "16:00"));
        assertTrue(!p.tiene("cargo"));
        Parametros vacios = catalogo.buscar("UPDUSU").orElseThrow().validar(List.of("3", "", "Rojas Paz", "", "", ""));
        assertTrue(!vacios.tiene("nombre"));
        assertTrue(!vacios.tiene("estado"), "estado es omitible y no llegó");
        assertEquals("Rojas Paz", vacios.texto("apellido"));
    }

    /** El catálogo y la tabla permiso tienen los mismos comandos, con la misma descripción y caso de uso. */
    @Test
    @Tag("bd")
    void coincideConLaTablaPermiso() throws Exception {
        List<Fila> permisos = new Repositorio(10).consultar(BaseDatosPrueba.conexion().obtener(),
                "SELECT comando, descripcion, caso_uso FROM permiso ORDER BY id");
        assertEquals(catalogo.cantidad(), permisos.size());
        int i = 0;
        for (CommandSpec s : catalogo.todos()) {
            Fila f = permisos.get(i++);
            assertEquals(s.codigo(), f.texto("comando"), "orden del Anexo A");
            assertEquals(s.descripcion(), f.texto("descripcion"), s.codigo());
            assertEquals(s.casoUso(), f.texto("caso_uso"), s.codigo());
        }
    }
}
