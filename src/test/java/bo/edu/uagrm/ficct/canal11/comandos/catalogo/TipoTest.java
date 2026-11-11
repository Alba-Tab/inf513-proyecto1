package bo.edu.uagrm.ficct.canal11.comandos.catalogo;

import bo.edu.uagrm.ficct.canal11.comandos.ErrorNegocio;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TipoTest {

    private static String error(Tipo t, String valor) {
        return assertThrows(ErrorNegocio.class, () -> t.convertir(valor, "p")).getMessage();
    }

    @Test
    void numeros() throws ErrorNegocio {
        assertEquals(3, Tipo.ID.convertir("3", "id"));
        assertEquals(3, Tipo.ID.convertir("+3", "id"));
        assertEquals("Error: «p» debe ser un número entero (llegó «tres»)", error(Tipo.ID, "tres"));
        assertEquals("Error: «p» debe ser un número entero (llegó «1.5»)", error(Tipo.ID, "1.5"));
        assertTrue(error(Tipo.ID, "0").contains("mayor o igual que 1"));
        assertTrue(error(Tipo.ID, "-5").contains("mayor o igual que 1"));
        assertEquals("Error: «p» debe ser un número de 80 a 250 (llegó «300»)", error(Tipo.entero(80, 250), "300"));
        assertNull(Tipo.ID_O_TODOS.convertir("*", "id"));
        assertEquals(7, Tipo.ID_O_TODOS.convertir("7", "id"));
    }

    @Test
    void textos() throws ErrorNegocio {
        assertEquals("hola", Tipo.texto(5).convertir("hola", "p"));
        assertEquals("Error: «p» admite hasta 3 caracteres (llegaron 4)", error(Tipo.texto(3), "hola"));
        assertNull(Tipo.FILTRO.convertir("*", "p"));
        assertEquals("operativo", Tipo.FILTRO.convertir("operativo", "p"));
    }

    @Test
    void contrasena() throws ErrorNegocio {
        assertEquals("clave123", Tipo.CONTRASENA.convertir("clave123", "password"));
        assertTrue(error(Tipo.CONTRASENA, "corta").contains("al menos 6"));
        assertTrue(error(Tipo.CONTRASENA, "ñ".repeat(37)).contains("72 bytes"));
    }

    @ParameterizedTest
    @CsvSource({
            "2026-10-12, 2026-10-12",
            "2026-1-5, 2026-01-05",
            "2026/10/12, 2026-10-12",
            "2028-02-29, 2028-02-29"})
    void fechasValidas(String entrada, String esperada) throws ErrorNegocio {
        assertEquals(LocalDate.parse(esperada), Tipo.FECHA.convertir(entrada, "fecha"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-13-40", "2026-02-30", "2027-02-29", "12-10-2026", "hoy", "2026-10"})
    void fechasInvalidas(String entrada) {
        assertEquals("Error: la fecha «" + entrada + "» de «p» no es válida; use AAAA-MM-DD, por ejemplo 2026-10-12",
                error(Tipo.FECHA, entrada));
    }

    @Test
    void horas() throws ErrorNegocio {
        assertEquals(LocalTime.of(15, 30), Tipo.HORA.convertir("15:30", "h"));
        assertEquals(LocalTime.of(9, 5), Tipo.HORA.convertir("9:05", "h"));
        assertEquals(LocalTime.of(20, 0, 20), Tipo.HORA.convertir("20:00:20", "h"));
        assertTrue(error(Tipo.HORA, "25:00").contains("no es válida; use HH:MM"));
        assertTrue(error(Tipo.HORA, "24:00").contains("no es válida"));
        assertEquals("24:00:00", Tipo.HORA_GRILLA.convertir("24:00", "h"));
        assertEquals("06:30:00", Tipo.HORA_GRILLA.convertir("6:30", "h"));
    }

    @Test
    void momentos() throws ErrorNegocio {
        Momento dia = (Momento) Tipo.MOMENTO.convertir("2026-10-14", "inicio");
        assertTrue(dia.soloFecha());
        assertEquals(LocalDateTime.of(2026, 10, 14, 0, 0), dia.comoInicio());
        assertEquals(LocalDateTime.of(2026, 10, 15, 0, 0), dia.comoFin());
        Momento conHora = (Momento) Tipo.MOMENTO.convertir("2026-10-14 09:00", "inicio");
        assertEquals(LocalDateTime.of(2026, 10, 14, 9, 0), conHora.comoFin());
        assertTrue(error(Tipo.MOMENTO, "2026-10-14 9").contains("AAAA-MM-DD HH:MM"));
        assertTrue(error(Tipo.MOMENTO, "2026-10-32").contains("no es válida"));
    }

    @Test
    void duraciones() throws ErrorNegocio {
        assertEquals(120, Tipo.DURACION.convertir("02:00", "d"));
        assertEquals(40, Tipo.DURACION.convertir("0:40", "d"));
        assertEquals(3642, Tipo.DURACION.convertir("60:42", "d"));
        assertEquals(3642, Tipo.DURACION.convertir("1:00:42", "d"));
        assertTrue(error(Tipo.DURACION, "2:75").contains("use mm:ss"));
        assertTrue(error(Tipo.DURACION, "dos minutos").contains("use mm:ss"));
    }

    @Test
    void opcionesSinMayusculasNiTildes() throws ErrorNegocio {
        Tipo t = Tipo.opcion("permiso", "vacacion", "baja_medica");
        assertEquals("vacacion", t.convertir("Vacación", "tipo"));
        assertEquals("baja_medica", t.convertir("Baja médica", "tipo"));
        assertEquals("baja_medica", t.convertir("baja-medica", "tipo"));
        assertEquals("Error: «p» debe ser permiso, vacacion o baja_medica (llegó «feriado»)", error(t, "feriado"));
        assertEquals("noticiero, especial u otro", Tipo.enumerar(java.util.List.of("noticiero", "especial", "otro")));
    }

    @Test
    void otros() throws ErrorNegocio {
        assertEquals(Boolean.TRUE, Tipo.BOOLEANO.convertir("Sí", "b"));
        assertEquals(Boolean.FALSE, Tipo.BOOLEANO.convertir("false", "b"));
        assertTrue(error(Tipo.BOOLEANO, "quizás").contains("true o false"));
        assertEquals("carla@gmail.com", Tipo.CORREO.convertir("Carla@Gmail.com", "c"));
        assertTrue(error(Tipo.CORREO, "carla.gmail.com").contains("no es un correo válido"));
        assertEquals("CAM-01", Tipo.CODIGO_RECURSO.convertir("cam-01", "c"));
        assertEquals("EXPESC", Tipo.COMANDO.convertir("expesc", "c"));
        assertTrue(error(Tipo.COMANDO, "EXP").contains("seis letras"));
        assertEquals("CU-05", Tipo.CASO_USO.convertir("cu-5", "c"));
        assertEquals("CU-05", Tipo.CASO_USO.convertir("5", "c"));
        assertTrue(error(Tipo.CASO_USO, "CU-09").contains("CU-01 a CU-08"));
        assertEquals("1,3,5", Tipo.DIAS.convertir("5, 1,3", "d"));
        assertTrue(error(Tipo.DIAS, "1,8").contains("de 1 (lunes) a 7 (domingo)"));
        assertEquals("B", Tipo.BLOQUE.convertir("b", "bloque"));
        assertTrue(error(Tipo.BLOQUE, "K").contains("de la A a la J"));
    }
}
