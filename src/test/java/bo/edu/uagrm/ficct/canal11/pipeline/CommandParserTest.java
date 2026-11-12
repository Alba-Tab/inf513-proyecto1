package bo.edu.uagrm.ficct.canal11.pipeline;

import bo.edu.uagrm.ficct.canal11.pipeline.ComandoParseado.Ayuda;
import bo.edu.uagrm.ficct.canal11.pipeline.ComandoParseado.Comando;
import bo.edu.uagrm.ficct.canal11.pipeline.ComandoParseado.Invalido;
import bo.edu.uagrm.ficct.canal11.pipeline.ComandoParseado.Motivo;
import bo.edu.uagrm.ficct.canal11.correo.MimeParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandParserTest {

    private static Comando comando(String asunto) {
        return assertInstanceOf(Comando.class, CommandParser.analizar(asunto), asunto);
    }

    private static Invalido invalido(String asunto) {
        return assertInstanceOf(Invalido.class, CommandParser.analizar(asunto), asunto);
    }

    // ---------------------------------------------------------------- AYUDA

    @ParameterizedTest
    @ValueSource(strings = {"AYUDA", "ayuda", "  Ayuda  ", "AYUDA[]", "Re: AYUDA", "AYUDA por favor", "ayuda!"})
    void reconoceAyuda(String asunto) {
        assertInstanceOf(Ayuda.class, CommandParser.analizar(asunto));
    }

    // ---------------------------------------------------------------- comandos válidos

    /** Los parámetros esperados van separados por «|» (vacío = sin parámetros). */
    @ParameterizedTest
    @CsvSource(delimiter = ';', quoteCharacter = '\'', value = {
            "LISPER[\"*\"]                                      ; LISPER ; *",
            "LISCOB[\"2026-10-12\"]                             ; LISCOB ; 2026-10-12",
            "INSUSU[\"Carla\",\"Rojas Suárez\",\"3100003\",\"carla.rojas@gmail.com\",\"71234580\",\"clave123\",\"3\"] ; INSUSU ; Carla|Rojas Suárez|3100003|carla.rojas@gmail.com|71234580|clave123|3",
            "INSNOT[\"PARO\",\"Título\",\"vo_sot\",\"2026-10-12\",\"2\",\"\"] ; INSNOT ; PARO|Título|vo_sot|2026-10-12|2|",
            "DELUSU[3]                                          ; DELUSU ; 3",
            "LISCOB[*]                                          ; LISCOB ; *",
            "UPDNOT[\"4\", 5 ,\"x\"]                            ; UPDNOT ; 4|5|x",
            "liscob[\"2026-10-12\"]                             ; LISCOB ; 2026-10-12",
            "LISCOB [\"2026-10-12\"]                            ; LISCOB ; 2026-10-12",
            "INSCOB[\"a\", \"b\" ,  \"c\"]                      ; INSCOB ; a|b|c",
            "INSCOB[\"  con espacios  \"]                       ; INSCOB ; con espacios",
            "LISNOT[\"\",\"aprobada\"]                          ; LISNOT ; |aprobada",
            "LISROL[]                                           ; LISROL ; ",
            "APRNOT[\"3\"] aprobada, buen trabajo               ; APRNOT ; 3",
            "OBSNOT[\"6\",\"Falta la versión de la Alcaldía, y fotos\"] ; OBSNOT ; 6|Falta la versión de la Alcaldía, y fotos",
            "INSITE[\"1\",\"B\",\"nota\",\"7\",\"11\",\"\"]     ; INSITE ; 1|B|nota|7|11|",
            "Re: LISCOB[\"*\"]                                  ; LISCOB ; *",
            "RV: Re: Fwd: LISCOB[\"*\"]                         ; LISCOB ; *",
            "RE[2]: LISCOB[\"*\"]                               ; LISCOB ; *",
            "RE:LISCOB[\"*\"]                                   ; LISCOB ; *",
            "INSAUS[“6”,“permiso”,“2026-10-14 09:00”]           ; INSAUS ; 6|permiso|2026-10-14 09:00",
            "INSAUS[„6“,„permiso“]                              ; INSAUS ; 6|permiso"
    })
    void leeComandosConParametros(String asunto, String codigo, String parametros) {
        Comando c = comando(asunto);
        assertEquals(codigo, c.codigo());
        assertTrue(c.conCorchetes());
        List<String> esperados = parametros == null ? List.of() : List.of(parametros.split("\\|", -1));
        assertEquals(esperados, c.parametros());
    }

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {
            "LISPER         ; LISPER",
            "lisper         ; LISPER",
            "INSUSU         ; INSUSU",
            "Re: DELPER     ; DELPER",
            "LISCOB de hoy  ; LISCOB"
    })
    void soloElCodigoEsUnComandoSinCorchetes(String asunto, String codigo) {
        Comando c = comando(asunto);
        assertEquals(codigo, c.codigo());
        assertFalse(c.conCorchetes());
        assertTrue(c.parametros().isEmpty());
    }

    @Test
    void separaOperacionYTabla() {
        Comando c = comando("UPDCOB[\"4\"]");
        assertEquals("UPD", c.operacion());
        assertEquals("COB", c.tabla());
    }

    // ---------------------------------------------------------------- inválidos

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "Hola, ¿cómo están?", "LISCOBX[\"*\"]", "LIS[\"*\"]", "123456[\"*\"]",
            "LISCOB.", "[\"*\"]", "AYUDAS-urgente"})
    void asuntosQueNoSonComandos(String asunto) {
        Invalido i = invalido(asunto);
        assertEquals(Motivo.NO_RECONOCIDO, i.motivo());
        assertEquals("Error: comando no reconocido. Envíe AYUDA para ver los comandos disponibles", i.mensaje());
    }

    @Test
    void comillasEscapadasDentroDeUnParametro() {
        Comando c = assertInstanceOf(Comando.class, CommandParser.analizar("OBSNOT[\"6\",\"Dijo \\\"no\\\" al final\"]"));
        assertEquals(List.of("6", "Dijo \"no\" al final"), c.parametros());
        Comando barra = assertInstanceOf(Comando.class, CommandParser.analizar("INSMAT[\"3\",\"C:\\\\media\",\"C:\\ruta\"]"));
        assertEquals(List.of("3", "C:\\media", "C:\\ruta"), barra.parametros());  // \\ es una barra; una barra sola queda
        assertInstanceOf(Invalido.class, CommandParser.analizar("OBSNOT[\"6\",\"sin cerrar\\\"]"));
    }

    @Test
    void unAsuntoNuloNoRompeNada() {
        assertEquals(Motivo.NO_RECONOCIDO, invalido(null).motivo());
    }

    @ParameterizedTest
    @CsvSource(delimiter = ';', quoteCharacter = '\'', value = {
            "LISCOB[\"2026-10-12\"                 ; falta el corchete de cierre ]",
            "LISCOB[\"2026-10-12                   ; la comilla sin cerrar",
            "INSCOB[\"a\",                         ; falta el corchete de cierre ]",
            "INSCOB[\"a\",,\"b\"]                  ; falta un parámetro entre comas",
            "INSCOB[\"a\",]                        ; falta un parámetro entre comas",
            "INSCOB[,\"a\"]                        ; falta un parámetro entre comas",
            "OBSNOT[\"6\",\"Dijo \"no\" al final\"] ; escríbalas con barra invertida",
            "OBSNOT[\"6\",Dijo \"no\"]             ; escríbalas con barra invertida"
    })
    void formatoIncorrecto(String asunto, String detalle) {
        Invalido i = invalido(asunto);
        assertEquals(Motivo.FORMATO, i.motivo());
        assertEquals(asunto.substring(0, 6).toUpperCase(), i.codigo());
        assertTrue(i.mensaje().startsWith("Error: formato esperado CODIGO[\"param1\",\"param2\",...]"), i.mensaje());
        assertTrue(i.mensaje().contains(detalle), i.mensaje());
    }

    // ---------------------------------------------------------------- normalización

    @ParameterizedTest
    @CsvSource(delimiter = ';', quoteCharacter = '\'', value = {
            "'  Re: LISCOB  '         ; LISCOB",
            "'Fwd: RV: AYUDA'         ; AYUDA",
            "'LIS COB'           ; LIS COB",
            "'INSAUS[“6”]'            ; INSAUS[\"6\"]",
            "'línea\r\n uno'          ; línea uno",
            "'INSCOB[\"Estadio   Tahuichi\"]' ; INSCOB[\"Estadio Tahuichi\"]"
    })
    void normaliza(String asunto, String esperado) {
        assertEquals(esperado, CommandParser.normalizar(asunto));
    }

    // ---------------------------------------------------------------- con los correos de ejemplo

    private static String asuntoDe(String eml) throws IOException {
        try (InputStream in = CommandParserTest.class.getResourceAsStream("/eml/" + eml)) {
            return MimeParser.leer(in.readAllBytes()).asunto();
        }
    }

    @Test
    void asuntosDeCorreosReales() throws IOException {
        Comando gmail = comando(asuntoDe("gmail_web_inscob.eml"));
        assertEquals("INSCOB", gmail.codigo());
        assertEquals(10, gmail.parametros().size());
        assertEquals("Clásico cruceño", gmail.parametros().get(0));
        assertEquals("Conferencia de técnicos", gmail.parametros().get(9));

        Comando iphone = comando(asuntoDe("iphone_insaus.eml"));
        assertEquals(List.of("6", "permiso", "2026-10-14 09:00", "2026-10-14 11:00", "Trámite personal"), iphone.parametros());

        Comando outlook = comando(asuntoDe("outlook_aprnot.eml"));
        assertEquals("APRNOT", outlook.codigo());
        assertEquals(List.of("3"), outlook.parametros());

        Comando android = comando(asuntoDe("gmail_android_insnot.eml"));
        assertEquals("", android.parametros().get(5));

        Comando respuesta = comando(asuntoDe("respuesta_con_cita.eml"));
        assertEquals("GUINOT", respuesta.codigo());
        assertEquals(List.of("1"), respuesta.parametros());

        assertEquals(Motivo.NO_RECONOCIDO, invalido(asuntoDe("rebote_mailer_daemon.eml")).motivo());
    }
}
