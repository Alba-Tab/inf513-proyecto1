package bo.edu.uagrm.ficct.canal11.correo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Servidor SMTP falso para las pruebas: recibe correos por un {@code ServerSocket} local y los
 * guarda en memoria, tal como llegaron después de DATA (ya sin los puntos duplicados).
 *
 * <p>Imita a Sendmail de tecnoweb: saludo, EHLO con 8BITMIME y AUTH CRAM-MD5, y «Relaying denied»
 * para los dominios que no son locales si se activa {@link #rechazarReenvio}.</p>
 */
public final class FakeSmtpServer implements AutoCloseable {

    /** Un correo recibido. */
    public record Recibido(String remitente, List<String> destinatarios, byte[] datos) {
        public String texto() {
            return new String(datos, StandardCharsets.US_ASCII);
        }
    }

    public enum Falla { NINGUNA, SIN_EHLO, SILENCIO_TRAS_SALUDO }

    public static final String DOMINIO_LOCAL = "tecnoweb.org.bo";

    private final ServerSocket servidor;
    private final List<Recibido> recibidos = Collections.synchronizedList(new ArrayList<>());
    private final List<String> comandos = Collections.synchronizedList(new ArrayList<>());
    private volatile boolean rechazarReenvio;
    private volatile Falla falla = Falla.NINGUNA;
    private volatile int lineasSinCrlf;
    private volatile int lineaMasLarga;
    private volatile boolean cerrado;

    public FakeSmtpServer() throws IOException {
        servidor = new ServerSocket(0, 10, InetAddress.getLoopbackAddress());
        Thread hilo = new Thread(this::atender, "fake-smtp");
        hilo.setDaemon(true);
        hilo.start();
    }

    public int puerto() {
        return servidor.getLocalPort();
    }

    public void rechazarReenvio(boolean rechazar) {
        this.rechazarReenvio = rechazar;
    }

    public void falla(Falla f) {
        this.falla = f;
    }

    public List<Recibido> recibidos() {
        return new ArrayList<>(recibidos);
    }

    public List<String> comandos() {
        return new ArrayList<>(comandos);
    }

    public int lineasSinCrlf() {
        return lineasSinCrlf;
    }

    /** Largo (sin CRLF) de la línea más larga recibida, comandos y DATA incluidos. Debe ser ≤ 998. */
    public int lineaMasLarga() {
        return lineaMasLarga;
    }

    @Override
    public void close() throws IOException {
        cerrado = true;
        servidor.close();
    }

    private void atender() {
        while (!cerrado) {
            try (Socket s = servidor.accept()) {
                sesion(s.getInputStream(), s.getOutputStream());
            } catch (IOException e) {
                // conexión cortada o servidor cerrado
            }
        }
    }

    private void sesion(InputStream in, OutputStream out) throws IOException {
        escribir(out, "220-web.tecnoweb.org.bo ESMTP ; servidor de pruebas");
        escribir(out, "220 sin envío real");
        if (falla == Falla.SILENCIO_TRAS_SALUDO) {
            while (in.read() != -1) {
                // no responde
            }
            return;
        }
        String remitente = null;
        List<String> destinatarios = new ArrayList<>();
        String linea;
        while ((linea = leerLinea(in)) != null) {
            comandos.add(linea);
            String cmd = linea.length() >= 4 ? linea.substring(0, 4).toUpperCase() : linea.toUpperCase();
            switch (cmd) {
                case "EHLO" -> {
                    if (falla == Falla.SIN_EHLO) {
                        escribir(out, "502 5.5.1 Command not implemented");
                    } else {
                        escribir(out, "250-web.tecnoweb.org.bo Hello, pleased to meet you");
                        escribir(out, "250-ENHANCEDSTATUSCODES");
                        escribir(out, "250-8BITMIME");
                        escribir(out, "250-SIZE 209715200");
                        escribir(out, "250-AUTH CRAM-MD5");
                        escribir(out, "250 HELP");
                    }
                }
                case "HELO" -> escribir(out, "250 web.tecnoweb.org.bo Hello");
                case "MAIL" -> {
                    remitente = entreAngulos(linea);
                    destinatarios.clear();
                    escribir(out, "250 2.1.0 <" + remitente + ">... Sender ok");
                }
                case "RCPT" -> {
                    String d = entreAngulos(linea);
                    if (remitente == null) {
                        escribir(out, "503 5.0.0 Need MAIL before RCPT");
                    } else if (rechazarReenvio && !d.endsWith("@" + DOMINIO_LOCAL)) {
                        escribir(out, "550 5.7.1 <" + d + ">... Relaying denied. IP name possibly forged [127.0.0.1]");
                    } else {
                        destinatarios.add(d);
                        escribir(out, "250 2.1.5 <" + d + ">... Recipient ok");
                    }
                }
                case "DATA" -> {
                    if (destinatarios.isEmpty()) {
                        escribir(out, "503 5.0.0 Need RCPT (recipient)");
                    } else {
                        escribir(out, "354 Enter mail, end with \".\" on a line by itself");
                        recibidos.add(new Recibido(remitente, List.copyOf(destinatarios), leerDatos(in)));
                        escribir(out, "250 2.0.0 k9DAbc123 Message accepted for delivery");
                        remitente = null;
                        destinatarios.clear();
                    }
                }
                case "RSET" -> {
                    remitente = null;
                    destinatarios.clear();
                    escribir(out, "250 2.0.0 Reset state");
                }
                case "QUIT" -> {
                    escribir(out, "221 2.0.0 web.tecnoweb.org.bo closing connection");
                    return;
                }
                default -> escribir(out, "500 5.5.1 Command unrecognized");
            }
        }
    }

    /** Lee hasta la línea «.», quitando el punto duplicado al inicio de cada línea. */
    private byte[] leerDatos(InputStream in) throws IOException {
        ByteArrayOutputStream datos = new ByteArrayOutputStream();
        String linea;
        while ((linea = leerLineaLatin1(in)) != null) {
            if (linea.equals(".")) {
                break;
            }
            String sinPunto = linea.startsWith(".") ? linea.substring(1) : linea;
            datos.write(sinPunto.getBytes(StandardCharsets.ISO_8859_1));
            datos.write(ConexionTexto.CRLF);
        }
        return datos.toByteArray();
    }

    private static String entreAngulos(String linea) {
        int a = linea.indexOf('<');
        int b = linea.indexOf('>');
        return a >= 0 && b > a ? linea.substring(a + 1, b) : "";
    }

    private String leerLinea(InputStream in) throws IOException {
        String l = leerLineaLatin1(in);
        return l == null ? null : new String(l.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
    }

    private String leerLineaLatin1(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') {
                byte[] bytes = b.toByteArray();
                int largo = bytes.length;
                if (largo == 0 || bytes[largo - 1] != '\r') {
                    lineasSinCrlf++;
                } else {
                    largo--;
                }
                lineaMasLarga = Math.max(lineaMasLarga, largo);
                return new String(bytes, 0, largo, StandardCharsets.ISO_8859_1);
            }
            b.write(c);
        }
        return null;
    }

    private static void escribir(OutputStream out, String linea) throws IOException {
        out.write(linea.getBytes(StandardCharsets.US_ASCII));
        out.write(ConexionTexto.CRLF);
        out.flush();
    }
}
