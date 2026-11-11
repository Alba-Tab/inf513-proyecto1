package bo.edu.uagrm.ficct.canal11.correo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Cliente SMTP por socket (RFC 5321), a partir del ejemplo {@code ClienteSMTP} de la lección 3.0.
 *
 * <p>Las respuestas de varias líneas se leen como en el {@code getMultiline} SMTP del docente:
 * siguen mientras el cuarto carácter sea {@code '-'} y terminan en la línea donde es un espacio
 * ({@code 250-…} … {@code 250 …}).</p>
 *
 * <p>Uso típico:</p>
 * <pre>{@code
 * try (SmtpClient smtp = SmtpClient.conectar("localhost", 25, 30_000)) {
 *     smtp.ehlo("tecnoweb.org.bo");
 *     smtp.enviar(correo, reloj);
 *     smtp.quit();
 * }
 * }</pre>
 */
public final class SmtpClient implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(SmtpClient.class.getName());

    /** Una respuesta del servidor: el código y sus líneas (sin el código). */
    public record Respuesta(int codigo, List<String> lineas) {
        public String texto() {
            return String.join(" | ", lineas);
        }
    }

    private final ConexionTexto conexion;
    private final Respuesta saludo;
    private final Set<String> capacidades = new LinkedHashSet<>();

    private SmtpClient(ConexionTexto conexion, Respuesta saludo) {
        this.conexion = conexion;
        this.saludo = saludo;
    }

    /** Abre la conexión y lee el saludo ({@code 220 …}). */
    public static SmtpClient conectar(String host, int puerto, int timeoutMs) throws IOException {
        ConexionTexto c = ConexionTexto.abrir(host, puerto, timeoutMs);
        try {
            Respuesta saludo = leerRespuesta(c);
            if (saludo.codigo() != 220) {
                throw new SmtpException("(saludo)", saludo.codigo(), saludo.texto());
            }
            return new SmtpClient(c, saludo);
        } catch (IOException | RuntimeException e) {
            c.close();
            throw e;
        }
    }

    public Respuesta saludo() {
        return saludo;
    }

    /**
     * EHLO, y si el servidor no lo entiende, HELO. Guarda las capacidades anunciadas (8BITMIME,
     * SIZE, AUTH…).
     */
    public void ehlo(String dominio) throws IOException {
        Respuesta r = enviarComando("EHLO " + dominio);
        if (r.codigo() == 250) {
            capacidades.clear();
            for (int i = 1; i < r.lineas().size(); i++) {
                capacidades.add(r.lineas().get(i).trim().toUpperCase(Locale.ROOT));
            }
            return;
        }
        LOG.fine("SMTP: EHLO rechazado (" + r.texto() + "); se usa HELO");
        esperar("HELO " + dominio, 250);
    }

    /** Capacidades anunciadas por EHLO, en mayúsculas (por ejemplo {@code "AUTH CRAM-MD5"}). */
    public Set<String> capacidades() {
        return Collections.unmodifiableSet(capacidades);
    }

    /** {@code true} si el servidor anunció la capacidad (por ejemplo {@code "8BITMIME"} o {@code "AUTH"}). */
    public boolean admite(String capacidad) {
        String buscada = capacidad.toUpperCase(Locale.ROOT);
        return capacidades.stream().anyMatch(c -> c.equals(buscada) || c.startsWith(buscada + " "));
    }

    public void mailFrom(String direccion) throws IOException {
        esperar("MAIL FROM:<" + direccion + ">", 250);
    }

    public void rcptTo(String direccion) throws IOException {
        Respuesta r = enviarComando("RCPT TO:<" + direccion + ">");
        if (r.codigo() != 250 && r.codigo() != 251) {
            throw new SmtpException("RCPT TO:<" + direccion + ">", r.codigo(), r.texto());
        }
    }

    /**
     * DATA: envía el correo completo. Normaliza los finales de línea a {@code \r\n}, duplica el punto
     * de las líneas que empiezan con punto y termina con la línea {@code "."}.
     *
     * @return la respuesta final del servidor (250 con el identificador de la cola)
     */
    public Respuesta data(byte[] correo) throws IOException {
        esperar("DATA", 354);
        conexion.enviarBytes(conPuntosDuplicados(correo));
        conexion.enviarBytes(new byte[]{'.', '\r', '\n'});
        Respuesta r = leerRespuesta(conexion);
        LOG.fine("S: " + r.codigo() + " " + r.texto());
        if (r.codigo() != 250) {
            throw new SmtpException("DATA (fin)", r.codigo(), r.texto());
        }
        return r;
    }

    /** MAIL FROM, RCPT TO y DATA de un correo armado. Antes hay que llamar a {@link #ehlo(String)}. */
    public Respuesta enviar(CorreoSaliente correo, Clock reloj) throws IOException {
        mailFrom(correo.remitente());
        rcptTo(correo.destinatario());
        return data(correo.aBytes(reloj));
    }

    /** RSET: descarta la transacción en curso (por ejemplo, tras un RCPT rechazado). */
    public void rset() throws IOException {
        esperar("RSET", 250);
    }

    /** QUIT y cierra la conexión. No lanza excepciones: el correo ya se entregó con DATA. */
    public void quit() {
        try {
            enviarComando("QUIT");
        } catch (IOException e) {
            LOG.fine("SMTP: QUIT sin respuesta: " + e.getMessage());
        } finally {
            close();
        }
    }

    @Override
    public void close() {
        try {
            conexion.close();
        } catch (IOException e) {
            LOG.fine("SMTP: error al cerrar el socket: " + e.getMessage());
        }
    }

    private void esperar(String comando, int codigoEsperado) throws IOException {
        Respuesta r = enviarComando(comando);
        if (r.codigo() != codigoEsperado) {
            throw new SmtpException(comando, r.codigo(), r.texto());
        }
    }

    private Respuesta enviarComando(String comando) throws IOException {
        LOG.fine("C: " + comando);
        conexion.enviarLinea(comando);
        Respuesta r = leerRespuesta(conexion);
        LOG.fine("S: " + r.codigo() + " " + r.texto());
        return r;
    }

    /** Lee una respuesta completa: sigue mientras el cuarto carácter sea {@code '-'}. */
    private static Respuesta leerRespuesta(ConexionTexto c) throws IOException {
        List<String> lineas = new ArrayList<>();
        while (true) {
            String linea = c.leerLinea();
            if (linea.length() < 3) {
                throw new IOException("Respuesta SMTP inválida: «" + linea + "»");
            }
            int codigo;
            try {
                codigo = Integer.parseInt(linea.substring(0, 3));
            } catch (NumberFormatException e) {
                throw new IOException("Respuesta SMTP inválida: «" + linea + "»", e);
            }
            lineas.add(linea.length() > 4 ? linea.substring(4) : "");
            if (linea.length() == 3 || linea.charAt(3) != '-') {
                return new Respuesta(codigo, List.copyOf(lineas));
            }
        }
    }

    /**
     * Prepara el contenido de DATA: cada línea termina en {@code \r\n} (también la última) y las que
     * empiezan con punto llevan uno más (RFC 5321, §4.5.2).
     */
    static byte[] conPuntosDuplicados(byte[] correo) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream(correo.length + 64);
        boolean inicioDeLinea = true;
        for (int i = 0; i < correo.length; i++) {
            byte b = correo[i];
            if (inicioDeLinea && b == '.') {
                salida.write('.');
            }
            if (b == '\n') {
                salida.write('\r');
                salida.write('\n');
                inicioDeLinea = true;
            } else if (b == '\r') {
                if (i + 1 < correo.length && correo[i + 1] == '\n') {
                    continue; // el \n que sigue escribe el \r\n
                }
                salida.write('\r');
                salida.write('\n');
                inicioDeLinea = true;
            } else {
                salida.write(b);
                inicioDeLinea = false;
            }
        }
        if (!inicioDeLinea) {
            salida.write('\r');
            salida.write('\n');
        }
        return salida.toByteArray();
    }
}
