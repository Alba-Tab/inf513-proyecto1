package bo.edu.uagrm.ficct.canal11.correo;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Un correo recibido, ya decodificado por {@link MimeParser}: lo que el sistema necesita para
 * ejecutar un comando y responder.
 *
 * @param remitente       dirección del remitente en minúsculas (sin nombre ni {@code <>}); vacía si no se pudo leer
 * @param nombreRemitente nombre que acompaña a la dirección, decodificado; vacío si no tiene
 * @param asunto          asunto decodificado (tildes incluidas) y desplegado en una sola línea
 * @param cuerpo          texto del correo con saltos {@code \n} (la parte text/plain, o el HTML convertido a texto)
 * @param messageId       {@code Message-ID} con sus {@code <>}, para responder en el mismo hilo; vacío si no tiene
 * @param tamanoBytes     tamaño del correo tal como llegó
 * @param cabeceras       todas las cabeceras desplegadas, con el nombre en minúsculas
 */
public record MensajeCorreo(String remitente, String nombreRemitente, String asunto, String cuerpo,
                            String messageId, int tamanoBytes, Map<String, List<String>> cabeceras) {

    /** Línea que separa la firma: «-- » (RFC 3676, §4.3). */
    private static final Pattern FIRMA = Pattern.compile("^-- ?$");
    /** Encabezados de una cita al responder: «El lun, 12 oct 2026 a las 10:00, X escribió:» o «On … wrote:». */
    private static final Pattern ENCABEZADO_CITA = Pattern.compile(
            "^(El |On ).*(escribió|wrote)\\s*:\\s*$|^-{3,}\\s*(Mensaje original|Original Message)\\s*-{3,}\\s*$|^_{10,}\\s*$",
            Pattern.CASE_INSENSITIVE);

    public MensajeCorreo {
        cabeceras = Collections.unmodifiableMap(cabeceras);
    }

    /** Primer valor de una cabecera (sin distinguir mayúsculas), o {@code null}. */
    public String cabecera(String nombre) {
        List<String> valores = cabeceras.get(nombre.toLowerCase(Locale.ROOT));
        return valores == null || valores.isEmpty() ? null : valores.get(0);
    }

    /**
     * {@code true} si el correo lo generó una máquina: un rebote ({@code MAILER-DAEMON}, remitente
     * vacío), una respuesta automática ({@code Auto-Submitted}, {@code X-Autoreply}) o una lista de
     * correo ({@code Precedence: bulk}). A esos no se les responde, para no entrar en un bucle.
     */
    public boolean esAutomatico() {
        String r = remitente.toLowerCase(Locale.ROOT);
        if (r.isEmpty() || r.startsWith("mailer-daemon@") || r.startsWith("postmaster@")) {
            return true;
        }
        String auto = cabecera("auto-submitted");
        if (auto != null && !auto.trim().toLowerCase(Locale.ROOT).startsWith("no")) {
            return true;
        }
        String precedencia = cabecera("precedence");
        if (precedencia != null && precedencia.trim().toLowerCase(Locale.ROOT).matches("bulk|junk|list|auto_reply")) {
            return true;
        }
        if (cabecera("x-autoreply") != null || cabecera("x-autorespond") != null || cabecera("x-failed-recipients") != null) {
            return true;
        }
        String returnPath = cabecera("return-path");
        return returnPath != null && returnPath.trim().equals("<>");
    }

    /**
     * El cuerpo sin la firma ni lo citado al responder: lo que de verdad escribió el remitente.
     * Sirve para el guion de INSNOT y GUINOT cuando el periodista responde a un correo del sistema.
     */
    public String cuerpoSinCitas() {
        StringBuilder sb = new StringBuilder();
        for (String linea : cuerpo.split("\n", -1)) {
            if (FIRMA.matcher(linea).matches() || ENCABEZADO_CITA.matcher(linea.trim()).matches()) {
                break;
            }
            if (linea.startsWith(">")) {
                continue;
            }
            sb.append(linea).append('\n');
        }
        return sb.toString().strip();
    }
}
