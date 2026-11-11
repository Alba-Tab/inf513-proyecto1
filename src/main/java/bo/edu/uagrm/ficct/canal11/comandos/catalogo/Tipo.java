package bo.edu.uagrm.ficct.canal11.comandos.catalogo;

import bo.edu.uagrm.ficct.canal11.comandos.ErrorNegocio;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tipo de un parámetro: convierte el texto del asunto al valor que usa el comando y, si no puede,
 * explica qué está mal con el estilo de la Tabla 77 («Error: la fecha «2026-13-40» no es válida…»).
 *
 * <p>Los tipos son estrictos: una fecha imposible, letras donde va un número o una hora 25:00 se
 * rechazan antes de tocar la base, con un mensaje que nombra el parámetro (sección 4.2.4).</p>
 */
public final class Tipo {

    @FunctionalInterface
    interface Conversor {
        Object convertir(String valor, String parametro) throws ErrorNegocio;
    }

    private static final DateTimeFormatter FECHA_ESTRICTA = DateTimeFormatter.ofPattern("uuuu-MM-dd")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter HORA_CON_SEGUNDOS = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Pattern PATRON_FECHA = Pattern.compile("^(\\d{4})-(\\d{1,2})-(\\d{1,2})$");
    private static final Pattern PATRON_HORA = Pattern.compile("^(\\d{1,2}):(\\d{2})(?::(\\d{2}))?$");
    private static final Pattern PATRON_MOMENTO = Pattern.compile("^(\\d{4}-\\d{1,2}-\\d{1,2})(?:[ T]+(\\d{1,2}:\\d{2}(?::\\d{2})?))?$");
    private static final Pattern PATRON_DURACION = Pattern.compile("^(?:(\\d{1,2}):)?(\\d{1,3}):(\\d{2})$");
    private static final Pattern PATRON_CORREO = Pattern.compile("^[^@\\s\"<>(),;]+@[^@\\s\"<>(),;]+\\.[^@\\s\"<>(),;]+$");
    private static final Pattern PATRON_CODIGO = Pattern.compile("^[A-Z0-9][A-Z0-9_-]{0,19}$");
    private static final Pattern PATRON_COMANDO = Pattern.compile("^[A-Z]{6}$");
    private static final Pattern PATRON_CASO_USO = Pattern.compile("^(?:CU)?-?0?([1-8])$");
    private static final Pattern PATRON_DIAS = Pattern.compile("^[1-7](,[1-7])*$");

    /** Largo máximo de un texto libre (indicaciones, parte de retorno): un asunto no da para más. */
    private static final int TEXTO_LARGO = 4000;

    private final String descripcion;
    private final Conversor conversor;

    private Tipo(String descripcion, Conversor conversor) {
        this.descripcion = descripcion;
        this.conversor = conversor;
    }

    /** Cómo se escribe un valor de este tipo, para la ayuda («número», «fecha AAAA-MM-DD»…). */
    public String descripcion() {
        return descripcion;
    }

    /** Convierte un valor ya sin espacios a los costados y no vacío. */
    public Object convertir(String valor, String parametro) throws ErrorNegocio {
        return conversor.convertir(valor, parametro);
    }

    // ------------------------------------------------------------------ números

    /** Id de un registro: entero positivo. */
    public static final Tipo ID = entero(1, Integer.MAX_VALUE);

    /** Id de un registro o {@code "*"} (todos): el valor es {@code null} para «todos». */
    public static final Tipo ID_O_TODOS = new Tipo("número o *", (v, p) -> v.equals("*") ? null : ID.convertir(v, p));

    /** Entero entre {@code min} y {@code max}, ambos incluidos. */
    public static Tipo entero(int min, int max) {
        String desc = max == Integer.MAX_VALUE ? "número" : "número de " + min + " a " + max;
        return new Tipo(desc, (v, p) -> {
            String limpio = v.replace(" ", "");
            if (!limpio.matches("^[+-]?\\d{1,10}$")) {
                throw ErrorNegocio.de("«" + p + "» debe ser un número entero (llegó «" + v + "»)");
            }
            long n = Long.parseLong(limpio);
            if (n < min || n > max) {
                throw ErrorNegocio.de(max == Integer.MAX_VALUE
                        ? "«" + p + "» debe ser un número mayor o igual que " + min + " (llegó «" + v + "»)"
                        : "«" + p + "» debe ser un número de " + min + " a " + max + " (llegó «" + v + "»)");
            }
            return (int) n;
        });
    }

    // ------------------------------------------------------------------ textos

    /** Texto de hasta {@code maximo} caracteres. */
    public static Tipo texto(int maximo) {
        return new Tipo("texto", (v, p) -> {
            if (v.length() > maximo) {
                throw ErrorNegocio.de("«" + p + "» admite hasta " + maximo + " caracteres (llegaron " + v.length() + ")");
            }
            return v;
        });
    }

    /** Texto libre largo (indicaciones, parte del retorno). */
    public static final Tipo TEXTO_LARGO_LIBRE = texto(TEXTO_LARGO);

    /** Filtro de un listado: {@code "*"} (todo, valor {@code null}) o un texto. */
    public static final Tipo FILTRO = new Tipo("* o un valor", (v, p) -> v.equals("*") ? null : texto(150).convertir(v, p));

    /** Contraseña de INSUSU: de 6 caracteres a 72 bytes (límite de bcrypt). */
    public static final Tipo CONTRASENA = new Tipo("texto", (v, p) -> {
        if (v.length() < 6) {
            throw ErrorNegocio.de("la contraseña debe tener al menos 6 caracteres");
        }
        if (v.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw ErrorNegocio.de("la contraseña admite hasta 72 bytes (unos 72 caracteres sin tildes)");
        }
        return v;
    });

    public static final Tipo CORREO = new Tipo("correo", (v, p) -> {
        String c = v.toLowerCase(Locale.ROOT);
        if (c.length() > 150 || !PATRON_CORREO.matcher(c).matches()) {
            throw ErrorNegocio.de("«" + v + "» no es un correo válido");
        }
        return c;
    });

    /** Código de un recurso (CAM-01, MOV-02…), en mayúsculas. */
    public static final Tipo CODIGO_RECURSO = new Tipo("código", (v, p) -> {
        String c = v.toUpperCase(Locale.ROOT).replace(' ', '-');
        if (!PATRON_CODIGO.matcher(c).matches()) {
            throw ErrorNegocio.de("el código «" + v + "» no es válido; use letras, números y guiones, por ejemplo CAM-01");
        }
        return c;
    });

    /** Código de un comando del sistema (seis letras), en mayúsculas. */
    public static final Tipo COMANDO = new Tipo("seis letras", (v, p) -> {
        String c = v.toUpperCase(Locale.ROOT);
        if (!PATRON_COMANDO.matcher(c).matches()) {
            throw ErrorNegocio.de("«" + v + "» no es un código de comando: son seis letras, por ejemplo LISCOB");
        }
        return c;
    });

    /** Caso de uso: CU-01 a CU-08 (acepta {@code cu-5}, {@code CU05} o {@code 5}). */
    public static final Tipo CASO_USO = new Tipo("CU-01 a CU-08", (v, p) -> {
        Matcher m = PATRON_CASO_USO.matcher(v.toUpperCase(Locale.ROOT).replace(" ", ""));
        if (!m.matches()) {
            throw ErrorNegocio.de("el caso de uso debe ser de CU-01 a CU-08 (llegó «" + v + "»)");
        }
        return "CU-0" + m.group(1);
    });

    /** Días de la semana de un turno: {@code 1,2,3,4,5} (1 = lunes … 7 = domingo); se ordenan. */
    public static final Tipo DIAS = new Tipo("días 1 a 7 separados por comas", (v, p) -> {
        String limpio = v.replace(" ", "");
        if (!PATRON_DIAS.matcher(limpio).matches()) {
            throw ErrorNegocio.de("los días van de 1 (lunes) a 7 (domingo), separados por comas; por ejemplo 1,2,3,4,5 (llegó «" + v + "»)");
        }
        TreeSet<String> dias = new TreeSet<>(Arrays.asList(limpio.split(",")));
        return String.join(",", dias);
    });

    /** Bloque de una escaleta: una letra de la A a la J. */
    public static final Tipo BLOQUE = new Tipo("letra A a J", (v, p) -> {
        String b = v.toUpperCase(Locale.ROOT);
        if (!b.matches("^[A-J]$")) {
            throw ErrorNegocio.de("el bloque es una letra de la A a la J (llegó «" + v + "»)");
        }
        return b;
    });

    /**
     * Uno de varios valores fijos (estados, tipos, cargos). Se compara sin mayúsculas ni tildes, y
     * un espacio o un guion cuentan como guion bajo: «Baja médica» se lee {@code baja_medica}.
     */
    public static Tipo opcion(String... valores) {
        List<String> lista = List.of(valores);
        return new Tipo(String.join(" | ", lista), (v, p) -> {
            String normal = normalizarOpcion(v);
            if (!lista.contains(normal)) {
                throw ErrorNegocio.de("«" + p + "» debe ser " + enumerar(lista) + " (llegó «" + v + "»)");
            }
            return normal;
        });
    }

    /** Sin mayúsculas, sin tildes y con guion bajo en lugar de espacios y guiones. */
    public static String normalizarOpcion(String v) {
        String sinTildes = Normalizer.normalize(v, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.toLowerCase(Locale.ROOT).strip().replaceAll("[\\s-]+", "_");
    }

    /** «a, b o c» (y «u» antes de una palabra que empieza con o: «especial u otro»). */
    public static String enumerar(List<String> valores) {
        if (valores.size() == 1) {
            return valores.get(0);
        }
        String ultimo = valores.get(valores.size() - 1);
        String conjuncion = normalizarOpcion(ultimo).matches("^h?o.*") ? " u " : " o ";
        return String.join(", ", valores.subList(0, valores.size() - 1)) + conjuncion + ultimo;
    }

    // ------------------------------------------------------------------ verdadero o falso

    public static final Tipo BOOLEANO = new Tipo("true o false", (v, p) -> {
        switch (normalizarOpcion(v)) {
            case "true", "verdadero", "si", "s", "1", "yes" -> {
                return Boolean.TRUE;
            }
            case "false", "falso", "no", "n", "0" -> {
                return Boolean.FALSE;
            }
            default -> throw ErrorNegocio.de("«" + p + "» debe ser true o false (llegó «" + v + "»)");
        }
    });

    // ------------------------------------------------------------------ fechas y horas

    /** Fecha {@code AAAA-MM-DD}; rechaza fechas imposibles como 2026-02-30. */
    public static final Tipo FECHA = new Tipo("fecha AAAA-MM-DD", Tipo::fecha);

    /** Fecha o {@code "*"} (valor {@code null}). */
    public static final Tipo FECHA_O_TODOS = new Tipo("fecha AAAA-MM-DD o *", (v, p) -> v.equals("*") ? null : fecha(v, p));

    /** Hora {@code HH:MM} (o {@code HH:MM:SS}) de 00:00 a 23:59. */
    public static final Tipo HORA = new Tipo("hora HH:MM", Tipo::hora);

    /**
     * Hora de la grilla: como {@link #HORA}, y además {@code 24:00} para un programa que termina a
     * medianoche (R16). El valor es el texto {@code HH:MM:SS}, que la consulta convierte con
     * {@code CAST(? AS time)}.
     */
    public static final Tipo HORA_GRILLA = new Tipo("hora HH:MM (hasta 24:00)", (v, p) -> {
        String limpio = v.replace(" ", "");
        if (limpio.equals("24:00") || limpio.equals("24:00:00")) {
            return "24:00:00";
        }
        return hora(limpio, p).format(HORA_CON_SEGUNDOS);
    });

    /** Fecha sola o fecha con hora: {@code 2026-10-14} o {@code 2026-10-14 09:00} (ver {@link Momento}). */
    public static final Tipo MOMENTO = new Tipo("fecha AAAA-MM-DD o AAAA-MM-DD HH:MM", (v, p) -> {
        Matcher m = PATRON_MOMENTO.matcher(v.strip());
        if (!m.matches()) {
            throw ErrorNegocio.de("«" + v + "» no es una fecha válida para «" + p + "»; use AAAA-MM-DD o AAAA-MM-DD HH:MM, por ejemplo 2026-10-14 09:00");
        }
        LocalDate dia = fecha(m.group(1), p);
        if (m.group(2) == null) {
            return new Momento(dia.atStartOfDay(), true);
        }
        return new Momento(LocalDateTime.of(dia, hora(m.group(2), p)), false);
    });

    /** Duración {@code mm:ss} (también {@code hh:mm:ss}); el valor son segundos. */
    public static final Tipo DURACION = new Tipo("duración mm:ss", (v, p) -> {
        Matcher m = PATRON_DURACION.matcher(v.replace(" ", ""));
        if (!m.matches() || Integer.parseInt(m.group(3)) > 59 || (m.group(1) != null && Integer.parseInt(m.group(2)) > 59)) {
            throw ErrorNegocio.de("la duración «" + v + "» de «" + p + "» no es válida; use mm:ss, por ejemplo 02:30");
        }
        int horas = m.group(1) == null ? 0 : Integer.parseInt(m.group(1));
        return horas * 3600 + Integer.parseInt(m.group(2)) * 60 + Integer.parseInt(m.group(3));
    });

    private static LocalDate fecha(String v, String p) throws ErrorNegocio {
        Matcher m = PATRON_FECHA.matcher(v.replace(" ", "").replace('/', '-'));
        if (m.matches()) {
            String normal = m.group(1) + "-" + dosDigitos(m.group(2)) + "-" + dosDigitos(m.group(3));
            try {
                return LocalDate.parse(normal, FECHA_ESTRICTA);
            } catch (DateTimeParseException e) {
                // cae al mensaje de abajo
            }
        }
        throw ErrorNegocio.de("la fecha «" + v + "» de «" + p + "» no es válida; use AAAA-MM-DD, por ejemplo 2026-10-12");
    }

    private static LocalTime hora(String v, String p) throws ErrorNegocio {
        Matcher m = PATRON_HORA.matcher(v.replace(" ", "").replace('.', ':').replace('h', ':'));
        if (m.matches()) {
            int h = Integer.parseInt(m.group(1));
            int min = Integer.parseInt(m.group(2));
            int s = m.group(3) == null ? 0 : Integer.parseInt(m.group(3));
            if (h <= 23 && min <= 59 && s <= 59) {
                return LocalTime.of(h, min, s);
            }
        }
        throw ErrorNegocio.de("la hora «" + v + "» de «" + p + "» no es válida; use HH:MM, por ejemplo 15:30");
    }

    private static String dosDigitos(String s) {
        return s.length() == 1 ? "0" + s : s;
    }
}
