package bo.edu.uagrm.ficct.canal11.comandos;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HTMLResponseBuilderTest {

    @Test
    void escapaLosCaracteresDeHtml() {
        assertEquals("&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt; &amp; &quot;ñ&quot;",
                HTMLResponseBuilder.escapar("<script>alert('x')</script> & \"ñ\""));
        assertEquals("", HTMLResponseBuilder.escapar(null));
    }

    @Test
    void unDatoDelUsuarioNoSeEjecuta() {
        String html = HTMLResponseBuilder.pagina("<b>título</b>",
                HTMLResponseBuilder.mensaje(HTMLResponseBuilder.Tipo.ERROR, "Error: <img src=x onerror=alert(1)>"));
        assertFalse(html.contains("<img"));
        assertFalse(html.contains("<b>título"));
        assertTrue(html.contains("&lt;img src=x onerror=alert(1)&gt;"));
    }

    @Test
    void laPaginaLlevaEncabezadoYRecordatorio() {
        String html = HTMLResponseBuilder.pagina("Ayuda", HTMLResponseBuilder.parrafo("Hola"));
        assertTrue(html.startsWith("<!DOCTYPE html>"));
        assertTrue(html.contains("<meta charset=\"UTF-8\">"));
        assertTrue(html.contains("Canal 11"));
        assertTrue(html.contains("Envíe AYUDA"));
        assertTrue(html.contains("<p style=\"margin:0 0 12px 0;\">Hola</p>"));
    }

    @Test
    void listaYCodigo() {
        String lista = HTMLResponseBuilder.lista(List.of(HTMLResponseBuilder.codigo("LISCOB[\"*\"]")));
        assertTrue(lista.contains("<li"));
        assertTrue(lista.contains("LISCOB[&quot;*&quot;]"));
    }

    @Test
    void errorEsUnaPaginaConMensajeRojo() {
        String html = HTMLResponseBuilder.error("Error: el registro no existe");
        assertTrue(html.contains("#b00020"));
        assertTrue(html.contains("Error: el registro no existe"));
    }

    @Test
    void tablaYFichaEscapanLosDatos() {
        java.util.Map<String, Object> valores = new java.util.LinkedHashMap<>();
        valores.put("Nombre", "<b>Ana</b>");
        valores.put("Fecha", java.time.LocalDate.of(2026, 10, 12));
        valores.put("Hora", java.time.LocalTime.of(20, 0));
        valores.put("Activo", Boolean.TRUE);
        valores.put("Guion", "línea 1\nlínea 2");
        valores.put("Nada", null);
        bo.edu.uagrm.ficct.canal11.datos.Fila f = new bo.edu.uagrm.ficct.canal11.datos.Fila(valores);
        String tabla = HTMLResponseBuilder.tabla(List.of(f));
        assertTrue(tabla.contains("&lt;b&gt;Ana&lt;/b&gt;"), tabla);
        assertTrue(tabla.contains(">2026-10-12<"), tabla);
        assertTrue(tabla.contains(">20:00<"), tabla);
        assertTrue(tabla.contains(">sí<"), tabla);
        assertTrue(tabla.contains("línea 1<br>línea 2"), tabla);
        assertTrue(tabla.contains(">—<"), tabla);
        String ficha = HTMLResponseBuilder.ficha(f);
        assertTrue(ficha.contains("<b>Nombre</b>"), ficha);
        assertEquals("", HTMLResponseBuilder.tabla(List.of()));
    }

    @Test
    void errorDeComandoTraeFormatoYEjemplo() {
        String html = HTMLResponseBuilder.errorDeComando("DELROL", "Error: el registro no existe", "", "DELROL[\"id\"]", "DELROL[\"8\"]");
        assertTrue(html.contains("Formato: <code"));
        assertTrue(html.contains("DELROL[&quot;8&quot;]"));
    }

    @Test
    void duracionesYHoras() {
        assertEquals("02:30", Formato.duracion(150));
        assertEquals("60:42", Formato.duracion(3642));
        assertEquals("-00:20", Formato.duracion(-20));
        assertEquals("20:00:20", Formato.hora(java.time.LocalTime.of(20, 0, 20)));
    }
}
