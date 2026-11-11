package bo.edu.uagrm.ficct.canal11.correo;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Conexión por socket a un servidor de texto (POP3 o SMTP), línea por línea.
 *
 * <p>Corrige dos detalles del ejemplo de la lección 3.0:</p>
 * <ul>
 *   <li>Cada línea enviada termina en {@code \r\n} y se escribe en UTF-8. Nunca se usa
 *       {@code DataOutputStream.writeBytes}, que descarta el byte alto de cada carácter y rompe
 *       las tildes y la ñ.</li>
 *   <li>Lo recibido se lee como bytes, sin convertirlo a texto, para que el lector MIME decodifique
 *       cada parte con su propio juego de caracteres.</li>
 * </ul>
 * <p>Todas las lecturas tienen tiempo máximo: un servidor que no responde no congela la aplicación.</p>
 */
final class ConexionTexto implements AutoCloseable {

    static final byte[] CRLF = {'\r', '\n'};

    /** Ninguna línea de POP3 o SMTP debería llegar a esto; protege la memoria ante un servidor que falla. */
    private static final int MAX_BYTES_LINEA = 1_000_000;

    private final Socket socket;
    private final InputStream entrada;
    private final OutputStream salida;

    private ConexionTexto(Socket socket) throws IOException {
        this.socket = socket;
        this.entrada = new BufferedInputStream(socket.getInputStream());
        this.salida = socket.getOutputStream();
    }

    /** Abre la conexión con tiempo máximo para conectar y para cada lectura. */
    static ConexionTexto abrir(String host, int puerto, int timeoutMs) throws IOException {
        Socket s = new Socket();
        try {
            s.connect(new InetSocketAddress(host, puerto), timeoutMs);
            s.setSoTimeout(timeoutMs);
            return new ConexionTexto(s);
        } catch (IOException e) {
            s.close();
            throw e;
        }
    }

    /** Envía una línea: el texto en UTF-8 más {@code \r\n}. */
    void enviarLinea(String linea) throws IOException {
        salida.write(linea.getBytes(StandardCharsets.UTF_8));
        salida.write(CRLF);
        salida.flush();
    }

    /** Envía bytes tal cual (por ejemplo, el cuerpo de DATA en SMTP). */
    void enviarBytes(byte[] bytes) throws IOException {
        salida.write(bytes);
        salida.flush();
    }

    /**
     * Lee una línea como bytes, sin el {@code \r\n} final.
     *
     * @throws EOFException si el servidor cerró la conexión
     */
    byte[] leerLineaBytes() throws IOException {
        ByteArrayOutputStream linea = new ByteArrayOutputStream(128);
        while (true) {
            int b = entrada.read();
            if (b == -1) {
                throw new EOFException("El servidor cerró la conexión");
            }
            if (b == '\n') {
                byte[] bytes = linea.toByteArray();
                int largo = bytes.length;
                if (largo > 0 && bytes[largo - 1] == '\r') {
                    largo--;
                }
                return largo == bytes.length ? bytes : Arrays.copyOf(bytes, largo);
            }
            linea.write(b);
            if (linea.size() > MAX_BYTES_LINEA) {
                throw new IOException("El servidor envió una línea de más de " + MAX_BYTES_LINEA + " bytes");
            }
        }
    }

    /** Lee una línea de respuesta del servidor (estado y texto, siempre ASCII en POP3 y SMTP). */
    String leerLinea() throws IOException {
        return new String(leerLineaBytes(), StandardCharsets.ISO_8859_1);
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
