package bo.edu.uagrm.ficct.canal11.pipeline;

import bo.edu.uagrm.ficct.canal11.pipeline.ComandoParseado.Comando;
import bo.edu.uagrm.ficct.canal11.pipeline.ComandoParseado.Invalido;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Oculta los parámetros sensibles de un comando (decisión D6) antes de escribirlo en el log, en la
 * bitácora o en el asunto de la respuesta.
 *
 * <p>Hoy el único es la contraseña de INSUSU (sexto parámetro). Si el asunto de un comando sensible
 * viene mal formado, o con otra cantidad de parámetros (entonces la contraseña puede estar en otra
 * posición), se ocultan todos: así nunca se filtra una contraseña.</p>
 */
public final class Enmascarador {

    public static final String MASCARA = "******";

    /** Cantidad de parámetros del comando y posiciones (desde 0) de los sensibles. */
    private record Sensible(int cantidad, Set<Integer> posiciones) {
    }

    private static final Map<String, Sensible> SENSIBLES = Map.of(
            "INSUSU", new Sensible(7, Set.of(5)));

    private Enmascarador() {
    }

    /** {@code true} si el comando tiene parámetros que no se pueden mostrar. */
    public static boolean esSensible(String codigo) {
        return codigo != null && SENSIBLES.containsKey(codigo);
    }

    /** El asunto listo para mostrar o registrar: igual que llegó, salvo los parámetros sensibles. */
    public static String asunto(String asunto) {
        if (asunto == null) {
            return "";
        }
        ComandoParseado p = CommandParser.analizar(asunto);
        if (p instanceof Comando c && esSensible(c.codigo())) {
            if (!c.conCorchetes()) {
                return c.codigo();
            }
            return c.codigo() + parametros(c).stream().map(Enmascarador::entreComillas).collect(Collectors.joining(",", "[", "]"));
        }
        if (p instanceof Invalido i && esSensible(i.codigo())) {
            return i.codigo() + "[" + MASCARA + "]";
        }
        return asunto.strip();
    }

    /** El valor como se escribe en el asunto: entre comillas, con {@code "} y {@code \\} escapadas. */
    private static String entreComillas(String v) {
        return "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /** Los parámetros del comando con los sensibles reemplazados por {@link #MASCARA}. */
    public static List<String> parametros(Comando c) {
        Sensible sensible = SENSIBLES.get(c.codigo());
        List<String> resultado = new ArrayList<>(c.parametros());
        if (sensible == null) {
            return resultado;
        }
        if (resultado.size() != sensible.cantidad()) {
            // Con otra cantidad, la contraseña puede estar en cualquier posición
            return resultado.stream().map(v -> MASCARA).toList();
        }
        sensible.posiciones().forEach(i -> resultado.set(i, MASCARA));
        return resultado;
    }
}
