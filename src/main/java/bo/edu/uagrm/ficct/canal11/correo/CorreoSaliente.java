package bo.edu.uagrm.ficct.canal11.correo;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Un correo de respuesta del sistema, listo para el DATA de SMTP (RFC 5322 y MIME).
 *
 * <p>Decisión D8:</p>
 * <ul>
 *   <li>El cuerpo es HTML en UTF-8 codificado en <b>base64</b>. Así ninguna línea pasa los 998
 *       caracteres que permite SMTP (una tabla HTML larga) y ninguna empieza con punto.</li>
 *   <li>El asunto va codificado según RFC 2047 si tiene tildes.</li>
 *   <li>Lleva {@code Date}, {@code Message-ID}, {@code In-Reply-To} y {@code References}, para que
 *       Gmail lo muestre en el mismo hilo que el pedido, y {@code Auto-Submitted: auto-replied},
 *       para que otros sistemas no respondan a la respuesta.</li>
 *   <li>Opcionalmente lleva adjuntos (EXPESC): entonces el correo es {@code multipart/mixed}.</li>
 * </ul>
 * <p>Los datos que vienen del usuario (asunto, direcciones) se limpian de saltos de línea: nadie
 * puede agregar cabeceras a través de un comando.</p>
 */
public final class CorreoSaliente {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH);

    /** Un archivo adjunto. */
    public record Adjunto(String nombre, String tipoContenido, byte[] contenido) {
        public Adjunto {
            Objects.requireNonNull(nombre);
            Objects.requireNonNull(tipoContenido);
            Objects.requireNonNull(contenido);
        }
    }

    private final String remitente;
    private final String nombreRemitente;
    private final String destinatario;
    private final String asunto;
    private final String html;
    private final String enRespuestaA;
    private final List<Adjunto> adjuntos;

    private CorreoSaliente(Builder b) {
        this.remitente = direccion(b.remitente, "remitente");
        this.nombreRemitente = b.nombreRemitente == null ? null : CodificacionMime.sinSaltos(b.nombreRemitente);
        this.destinatario = direccion(b.destinatario, "destinatario");
        this.asunto = CodificacionMime.sinSaltos(Objects.requireNonNull(b.asunto, "asunto"));
        this.html = Objects.requireNonNull(b.html, "html");
        this.enRespuestaA = b.enRespuestaA == null || b.enRespuestaA.isBlank() ? null : CodificacionMime.sinSaltos(b.enRespuestaA);
        this.adjuntos = List.copyOf(b.adjuntos);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Dirección para MAIL FROM. */
    public String remitente() {
        return remitente;
    }

    /** Dirección para RCPT TO. */
    public String destinatario() {
        return destinatario;
    }

    public String asunto() {
        return asunto;
    }

    /**
     * El correo completo (cabeceras y cuerpo), con líneas terminadas en {@code \r\n}.
     *
     * @param reloj fecha de la cabecera {@code Date}, en su zona horaria
     */
    public byte[] aBytes(Clock reloj) {
        StringBuilder sb = new StringBuilder(html.length() * 2 + 1024);
        String dominio = remitente.substring(remitente.indexOf('@') + 1);
        cabecera(sb, "From", nombreRemitente == null
                ? remitente
                : CodificacionMime.codificarCabecera(nombreRemitente) + " <" + remitente + ">");
        cabecera(sb, "To", destinatario);
        cabecera(sb, "Subject", CodificacionMime.codificarCabecera(asunto));
        cabecera(sb, "Date", FECHA.format(ZonedDateTime.now(reloj)));
        cabecera(sb, "Message-ID", "<" + UUID.randomUUID() + "@" + dominio + ">");
        if (enRespuestaA != null) {
            cabecera(sb, "In-Reply-To", enRespuestaA);
            cabecera(sb, "References", enRespuestaA);
        }
        cabecera(sb, "Auto-Submitted", "auto-replied");
        cabecera(sb, "MIME-Version", "1.0");

        if (adjuntos.isEmpty()) {
            parteHtml(sb);
        } else {
            String limite = "=_canal11_" + UUID.randomUUID().toString().replace("-", "");
            cabecera(sb, "Content-Type", "multipart/mixed; boundary=\"" + limite + "\"");
            sb.append("\r\n");
            sb.append("--").append(limite).append("\r\n");
            parteHtml(sb);
            for (Adjunto a : adjuntos) {
                sb.append("--").append(limite).append("\r\n");
                String nombre = nombreArchivo(a.nombre());
                cabecera(sb, "Content-Type", a.tipoContenido() + "; name=\"" + nombre + "\"");
                cabecera(sb, "Content-Disposition", "attachment; filename=\"" + nombre + "\"");
                cabecera(sb, "Content-Transfer-Encoding", "base64");
                sb.append("\r\n");
                sb.append(CodificacionMime.base64EnLineas(a.contenido())).append("\r\n");
            }
            sb.append("--").append(limite).append("--\r\n");
        }
        return sb.toString().getBytes(StandardCharsets.US_ASCII);
    }

    private void parteHtml(StringBuilder sb) {
        cabecera(sb, "Content-Type", "text/html; charset=UTF-8");
        cabecera(sb, "Content-Transfer-Encoding", "base64");
        sb.append("\r\n");
        sb.append(CodificacionMime.base64EnLineas(html.getBytes(StandardCharsets.UTF_8))).append("\r\n");
    }

    private static void cabecera(StringBuilder sb, String nombre, String valor) {
        sb.append(nombre).append(": ").append(valor).append("\r\n");
    }

    /** El nombre de un adjunto va entre comillas: se deja en ASCII simple. */
    private static String nombreArchivo(String nombre) {
        String limpio = CodificacionMime.sinSaltos(nombre).replaceAll("[^A-Za-z0-9._-]", "_");
        return limpio.isEmpty() ? "adjunto" : limpio;
    }

    private static String direccion(String valor, String campo) {
        Objects.requireNonNull(valor, campo);
        String d = valor.strip();
        if (!d.matches("[^\\s<>@,;:\"]+@[^\\s<>@,;:\"]+")) {
            throw new IllegalArgumentException("Dirección de correo inválida para el " + campo + ": «" + CodificacionMime.sinSaltos(valor) + "»");
        }
        return d;
    }

    /** Arma un {@link CorreoSaliente}. Obligatorios: remitente, destinatario, asunto y html. */
    public static final class Builder {
        private String remitente;
        private String nombreRemitente;
        private String destinatario;
        private String asunto;
        private String html;
        private String enRespuestaA;
        private final List<Adjunto> adjuntos = new ArrayList<>();

        private Builder() {
        }

        public Builder de(String direccion) {
            this.remitente = direccion;
            return this;
        }

        public Builder de(String direccion, String nombre) {
            this.remitente = direccion;
            this.nombreRemitente = nombre;
            return this;
        }

        public Builder para(String direccion) {
            this.destinatario = direccion;
            return this;
        }

        public Builder asunto(String asunto) {
            this.asunto = asunto;
            return this;
        }

        public Builder html(String html) {
            this.html = html;
            return this;
        }

        /** El {@code Message-ID} del correo que se responde, con sus {@code <>}. */
        public Builder enRespuestaA(String messageId) {
            this.enRespuestaA = messageId;
            return this;
        }

        public Builder adjunto(String nombre, String tipoContenido, byte[] contenido) {
            this.adjuntos.add(new Adjunto(nombre, tipoContenido, contenido));
            return this;
        }

        public CorreoSaliente build() {
            return new CorreoSaliente(this);
        }
    }
}
