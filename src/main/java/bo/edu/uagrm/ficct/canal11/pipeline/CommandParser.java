package bo.edu.uagrm.ficct.canal11.pipeline;

import bo.edu.uagrm.ficct.canal11.pipeline.ComandoParseado.Ayuda;
import bo.edu.uagrm.ficct.canal11.pipeline.ComandoParseado.Comando;
import bo.edu.uagrm.ficct.canal11.pipeline.ComandoParseado.Invalido;
import bo.edu.uagrm.ficct.canal11.pipeline.ComandoParseado.Motivo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Analizador sintáctico del asunto («CommandParser» del documento): separa el código de seis letras
 * de sus parámetros, con el formato {@code CODIGO["param1","param2",...]} (sección 4.2.2).
 *
 * <p>Acepta lo que mandan los clientes de correo de verdad:</p>
 * <ul>
 *   <li>prefijos de respuesta o reenvío al inicio ({@code Re:}, {@code RV:}, {@code Fwd:}, {@code RE[2]:}…);</li>
 *   <li>comillas tipográficas {@code “ ” „}, que el celular pone solo, como comillas rectas;</li>
 *   <li>el código en minúsculas y espacios antes del corchete o entre parámetros (un asunto largo
 *       se parte en varias líneas y al unirlas queda un espacio);</li>
 *   <li>parámetros sin comillas cuando no tienen comas ({@code DELUSU[3]}, {@code LISCOB[*]});</li>
 *   <li>texto después del corchete de cierre, que se ignora ({@code APRNOT["3"] aprobada}).</li>
 * </ul>
 * <p>No acepta comillas dentro de un parámetro (decisión L8): eso es un error de formato.</p>
 */
public final class CommandParser {

    public static final String NO_RECONOCIDO = "Error: comando no reconocido. Envíe AYUDA para ver los comandos disponibles";
    static final String FORMATO = "Error: formato esperado CODIGO[\"param1\",\"param2\",...]";
    private static final String COMILLAS = "para poner comillas dentro de un parámetro escríbalas con barra invertida: \\\"";

    /** Prefijos de respuesta y reenvío en español e inglés, con número opcional ({@code RE[2]:}). */
    private static final Pattern PREFIJO = Pattern.compile(
            "^(re|rv|r|fw|fwd|enc|reenv|resp|aw|tr|wg)(\\[\\d+])?\\s*:\\s*", Pattern.CASE_INSENSITIVE);
    private static final Pattern AYUDA = Pattern.compile("^ayuda(\\s*\\[\\s*])?(\\W.*)?$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern CODIGO = Pattern.compile("^([A-Za-z]{6})(?=$|[\\s\\[])");

    private CommandParser() {
    }

    /** Interpreta un asunto ya decodificado. Nunca lanza excepciones. */
    public static ComandoParseado analizar(String asunto) {
        String texto = normalizar(asunto);
        if (AYUDA.matcher(texto).matches()) {
            return new Ayuda();
        }
        Matcher m = CODIGO.matcher(texto);
        if (!m.find()) {
            return new Invalido(Motivo.NO_RECONOCIDO, "", NO_RECONOCIDO);
        }
        String codigo = m.group(1).toUpperCase(Locale.ROOT);
        String resto = texto.substring(m.end()).stripLeading();
        if (!resto.startsWith("[")) {
            // Solo el código (o el código y texto suelto): sin corchetes
            return new Comando(codigo, List.of(), false);
        }
        return parametros(codigo, resto);
    }

    /**
     * El asunto tal como lo interpreta el sistema: sin espacios a los costados, sin prefijos de
     * respuesta ni reenvío, con comillas rectas y espacios simples.
     */
    public static String normalizar(String asunto) {
        if (asunto == null) {
            return "";
        }
        String t = asunto
                .replace('“', '"').replace('”', '"')   // “ ”
                .replace('„', '"').replace('‟', '"')   // „ ‟
                .replace('″', '"').replace('＂', '"')   // ″ ＂
                .replace(' ', ' ').replace('\t', ' ')
                .replace('\r', ' ').replace('\n', ' ')
                .replaceAll(" {2,}", " ")
                .strip();
        Matcher m = PREFIJO.matcher(t);
        while (m.find()) {
            t = t.substring(m.end()).stripLeading();
            m = PREFIJO.matcher(t);
        }
        return t;
    }

    /**
     * Lee {@code [ "a" , "b" , c ]} a partir del corchete de apertura. Dentro de un parámetro entre
     * comillas, {@code \"} es una comilla y {@code \\} una barra invertida.
     */
    private static ComandoParseado parametros(String codigo, String texto) {
        List<String> valores = new ArrayList<>();
        int i = 1; // después del "["
        int n = texto.length();
        i = saltarEspacios(texto, i);
        if (i < n && texto.charAt(i) == ']') {
            return new Comando(codigo, valores, true);
        }
        while (true) {
            i = saltarEspacios(texto, i);
            if (i >= n) {
                return formato(codigo, "falta el corchete de cierre ]");
            }
            char c = texto.charAt(i);
            String valor;
            if (c == '"') {
                StringBuilder sb = new StringBuilder();
                int k = i + 1;
                while (k < n && texto.charAt(k) != '"') {
                    char x = texto.charAt(k);
                    if (x == '\\' && k + 1 < n && (texto.charAt(k + 1) == '"' || texto.charAt(k + 1) == '\\')) {
                        x = texto.charAt(++k);  // \" es una comilla y \\ una barra dentro del parámetro
                    }
                    sb.append(x);
                    k++;
                }
                if (k >= n) {
                    return formato(codigo, "un parámetro tiene la comilla sin cerrar");
                }
                valor = sb.toString();
                i = saltarEspacios(texto, k + 1);
                if (i < n && texto.charAt(i) != ',' && texto.charAt(i) != ']') {
                    return formato(codigo, COMILLAS);
                }
            } else if (c == ',' || c == ']') {
                return formato(codigo, "falta un parámetro entre comas; para dejarlo vacío escriba \"\"");
            } else {
                int fin = i;
                while (fin < n && texto.charAt(fin) != ',' && texto.charAt(fin) != ']') {
                    if (texto.charAt(fin) == '"') {
                        return formato(codigo, COMILLAS);
                    }
                    fin++;
                }
                valor = texto.substring(i, fin);
                i = fin;
            }
            valores.add(valor.strip());
            if (i >= n) {
                return formato(codigo, "falta el corchete de cierre ]");
            }
            if (texto.charAt(i) == ']') {
                return new Comando(codigo, valores, true);
            }
            i++; // la coma
        }
    }

    private static int saltarEspacios(String texto, int i) {
        while (i < texto.length() && texto.charAt(i) == ' ') {
            i++;
        }
        return i;
    }

    private static Invalido formato(String codigo, String detalle) {
        return new Invalido(Motivo.FORMATO, codigo, FORMATO + " (" + detalle + ")");
    }
}
