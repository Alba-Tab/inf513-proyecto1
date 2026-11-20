package bo.edu.uagrm.ficct.canal11.correo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * Cliente POP3 por socket (RFC 1939), a partir del ejemplo {@code ClientePOP} de la lección 3.0.
 *
 * <p>Cada método envía un comando y valida que la respuesta empiece con {@code +OK}; si empieza
 * con {@code -ERR}, lanza {@link Pop3Exception}. Las respuestas de varias líneas (LIST, UIDL, RETR
 * y TOP) se leen con la lógica del {@code getMultiline} del docente: terminan en una línea que es
 * solo un punto, y si una línea empieza con punto se le quita uno (RFC 1939, §3).</p>
 *
 * <p>Las marcas de DELE se aplican recién al responder {@code +OK} a QUIT. Si la conexión se
 * cierra sin QUIT (por ejemplo con {@link #close()} tras un error), el servidor no borra nada.
 * Por eso el bucle procesa un correo solo después de que {@link #quit()} devuelve {@code true}
 * (decisión D3).</p>
 *
 * <p>Uso típico:</p>
 * <pre>{@code
 * try (Pop3Client pop = Pop3Client.conectar("localhost", 110, 30_000)) {
 *     pop.login("grupo14sa", contrasena);
 *     if (pop.stat().mensajes() > 0) {
 *         byte[] correo = pop.retr(1);
 *         pop.dele(1);
 *         if (pop.quit()) { procesar(correo); }
 *     }
 * }
 * }</pre>
 */
public final class Pop3Client implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(Pop3Client.class.getName());

    /** Respuesta a STAT: cantidad de mensajes y tamaño total del buzón. */
    public record Estado(int mensajes, long octetos) {
    }

    private final ConexionTexto conexion;
    private final String saludo;

    private Pop3Client(ConexionTexto conexion, String saludo) {
        this.conexion = conexion;
        this.saludo = saludo;
    }

    /**
     * Abre la conexión y lee el saludo del servidor ({@code +OK Dovecot ready.}).
     *
     * @param timeoutMs tiempo máximo para conectar y para cada respuesta
     */
    public static Pop3Client conectar(String host, int puerto, int timeoutMs) throws IOException {
        ConexionTexto c = ConexionTexto.abrir(host, puerto, timeoutMs);
        try {
            String saludo = c.leerLinea();
            LOG.fine("S: " + saludo);
            if (!saludo.startsWith("+OK")) {
                throw new Pop3Exception("(saludo)", saludo);
            }
            return new Pop3Client(c, saludo);
        } catch (IOException | RuntimeException e) {
            c.close();
            throw e;
        }
    }

    /** El saludo con el que respondió el servidor al conectar. */
    public String saludo() {
        return saludo;
    }

    /** USER y PASS. La contraseña nunca va al log. */
    public void login(String usuario, String contrasena) throws IOException {
        comando("USER " + usuario, "USER " + usuario);
        comando("PASS " + contrasena, "PASS ********");
    }

    /** STAT: cantidad de mensajes y octetos del buzón. */
    public Estado stat() throws IOException {
        String[] partes = comando("STAT", "STAT").split("\\s+");
        try {
            return new Estado(Integer.parseInt(partes[1]), Long.parseLong(partes[2]));
        } catch (RuntimeException e) {
            throw new IOException("Respuesta inesperada a STAT: " + String.join(" ", partes), e);
        }
    }

    /** LIST n: tamaño en octetos del mensaje n (sirve para descartar correos grandes antes de RETR). */
    public long list(int numero) throws IOException {
        String[] partes = comando("LIST " + numero, "LIST " + numero).split("\\s+");
        try {
            return Long.parseLong(partes[2]);
        } catch (RuntimeException e) {
            throw new IOException("Respuesta inesperada a LIST " + numero + ": " + String.join(" ", partes), e);
        }
    }

    /** UIDL n: identificador único y permanente del mensaje n en el servidor. */
    public String uidl(int numero) throws IOException {
        String[] partes = comando("UIDL " + numero, "UIDL " + numero).split("\\s+");
        if (partes.length < 3) {
            throw new IOException("Respuesta inesperada a UIDL " + numero + ": " + String.join(" ", partes));
        }
        return partes[2];
    }

    /** RETR n: el mensaje completo (cabeceras y cuerpo) tal como está en el servidor, en bytes. */
    public byte[] retr(int numero) throws IOException {
        comando("RETR " + numero, "RETR " + numero);
        return leerMultilinea();
    }

    /** TOP n k: las cabeceras del mensaje n y sus primeras k líneas de cuerpo. */
    public byte[] top(int numero, int lineas) throws IOException {
        comando("TOP " + numero + " " + lineas, "TOP " + numero + " " + lineas);
        return leerMultilinea();
    }

    /** DELE n: marca el mensaje para borrarlo. Se borra recién con QUIT. */
    public void dele(int numero) throws IOException {
        comando("DELE " + numero, "DELE " + numero);
    }

    /** NOOP: no hace nada; sirve para comprobar que la sesión sigue viva. */
    public void noop() throws IOException {
        comando("NOOP", "NOOP");
    }

    /** RSET: desmarca los mensajes marcados con DELE en esta sesión. */
    public void rset() throws IOException {
        comando("RSET", "RSET");
    }

    /**
     * QUIT: cierra la sesión y el servidor aplica los DELE.
     *
     * @return {@code true} si el servidor confirmó con {@code +OK}; solo entonces los mensajes
     *         marcados quedaron borrados
     */
    public boolean quit() {
        try {
            conexion.enviarLinea("QUIT");
            LOG.fine("C: QUIT");
            String respuesta = conexion.leerLinea();
            LOG.fine("S: " + respuesta);
            return respuesta.startsWith("+OK");
        } catch (IOException e) {
            LOG.warning("POP3: QUIT sin confirmación (" + e.getMessage() + "); el servidor no borró los mensajes marcados");
            return false;
        } finally {
            cerrarSilencioso();
        }
    }

    /** Cierra el socket sin QUIT: el servidor descarta los DELE de la sesión. */
    @Override
    public void close() {
        cerrarSilencioso();
    }

    private void cerrarSilencioso() {
        try {
            conexion.close();
        } catch (IOException e) {
            LOG.fine("POP3: error al cerrar el socket: " + e.getMessage());
        }
    }

    /** Envía un comando y devuelve la línea {@code +OK …}; si es {@code -ERR}, lanza {@link Pop3Exception}. */
    private String comando(String linea, String paraLog) throws IOException {
        LOG.fine("C: " + paraLog);
        conexion.enviarLinea(linea);
        String respuesta = conexion.leerLinea();
        LOG.fine("S: " + respuesta);
        if (respuesta.startsWith("+OK")) {
            return respuesta;
        }
        throw new Pop3Exception(paraLog, respuesta);
    }

    /**
     * Lee una respuesta de varias líneas, como el {@code getMultiline} de la lección 3.0: termina en
     * la línea {@code "."} y a las líneas que empiezan con punto se les quita uno. Devuelve las
     * líneas unidas con {@code \r\n}, como en el mensaje original.
     */
    private byte[] leerMultilinea() throws IOException {
        ByteArrayOutputStream mensaje = new ByteArrayOutputStream(8192);
        while (true) {
            byte[] linea = conexion.leerLineaBytes();
            if (linea.length == 1 && linea[0] == '.') {
                return mensaje.toByteArray();
            }
            int desde = linea.length > 0 && linea[0] == '.' ? 1 : 0;
            mensaje.write(linea, desde, linea.length - desde);
            mensaje.write(ConexionTexto.CRLF);
        }
    }

    @Override
    public String toString() {
        return "Pop3Client[" + saludo + "]";
    }
}
