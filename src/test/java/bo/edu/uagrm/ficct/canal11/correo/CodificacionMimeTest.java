package bo.edu.uagrm.ficct.canal11.correo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Decodificación de cabeceras (RFC 2047), quoted-printable, base64 y juegos de caracteres. */
class CodificacionMimeTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "=?UTF-8?Q?Cl=C3=A1sico_cruce=C3=B1o?=                       | Clásico cruceño",
            "=?utf-8?q?Cl=C3=A1sico?=                                     | Clásico",
            "=?UTF-8?B?w4k=?= =?UTF-8?B?eGl0bw==?=                        | Éxito",
            "Re: =?UTF-8?Q?=C3=89xito?= ok                                | Re: Éxito ok",
            "=?UTF-8?Q?a=C3?=  =?UTF-8?Q?=B1b?=                           | añb",
            "=?iso-8859-1?Q?aprobaci=F3n?=                                | aprobación",
            "=?UTF-8*es?Q?hola?=                                          | hola",
            "=?UTF-8?Q?uno?= y =?UTF-8?Q?dos?=                            | uno y dos",
            "=?ISO-8859-1?Q?A=F1ez?= =?UTF-8?Q?=C3=89g=C3=BCez?=          | AñezÉgüez",
            "LISCOB[\"*\"]                                                | LISCOB[\"*\"]",
            "=?UTF-8?X?no_es_una_codificacion?=                           | =?UTF-8?X?no_es_una_codificacion?="
    })
    void decodificaCabeceras(String codificado, String esperado) {
        assertEquals(esperado, CodificacionMime.decodificarCabecera(codificado));
    }

    @Test
    void unaPalabraConBase64InvalidoNoRompeNada() {
        assertEquals("Re: ", CodificacionMime.decodificarCabecera("Re: =?UTF-8?B?***?="));
        assertEquals(null, CodificacionMime.decodificarCabecera(null));
    }

    @Test
    void codificarYDecodificarEsLaIdentidad() {
        String asunto = "Éxito: cobertura «Clásico cruceño» en el Estadio Ramón «Tahuichi» Aguilera — 15:00 a 16:30 😀";
        assertEquals(asunto, CodificacionMime.decodificarCabecera(
                CodificacionMime.codificarCabecera(asunto).replace("\r\n", "")));
    }

    @Test
    void quotedPrintable() {
        assertEquals("Hola mundoseguidoñ", qp("Hola=20mundo=\r\nseguido=C3=B1"));
        assertEquals("linea unida", qp("linea =  \nunida"));
        assertEquals("=ZZ queda igual", qp("=ZZ queda igual"));
        assertEquals("fin", qp("fin="));
        assertEquals("minúsculas ñ", qp("min=c3=bascul=61s =C3=b1"));
    }

    private static String qp(String s) {
        return new String(CodificacionMime.decodificarQuotedPrintable(s.getBytes(StandardCharsets.ISO_8859_1)), StandardCharsets.UTF_8);
    }

    @Test
    void base64ConSaltosYRelleno() {
        assertEquals("Clásico cruceño", new String(CodificacionMime.decodificarBase64(
                "Q2zDoXNp\r\nY28gY3J1\r\nY2XDsW8=\r\n".getBytes(StandardCharsets.US_ASCII)), StandardCharsets.UTF_8));
    }

    @Test
    void juegosDeCaracteres() {
        byte[] comillasWindows = {(byte) 0x93, 'B', 'u', 'e', 'n', (byte) 0x94};
        assertEquals("“Buen”", CodificacionMime.texto(comillasWindows, "iso-8859-1"));
        assertEquals("“Buen”", CodificacionMime.texto(comillasWindows, null), "sin charset y no es UTF-8: Windows-1252");
        assertEquals("Añez", CodificacionMime.texto("Añez".getBytes(StandardCharsets.UTF_8), "us-ascii"),
                "dice ASCII pero manda UTF-8");
        assertEquals("Añez", CodificacionMime.texto("Añez".getBytes(StandardCharsets.UTF_8), "x-inventado"));
        assertEquals("Añez", CodificacionMime.texto("Añez".getBytes(StandardCharsets.UTF_8), "\"UTF-8\""));
    }
}
