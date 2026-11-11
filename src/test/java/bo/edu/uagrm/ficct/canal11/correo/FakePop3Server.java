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
 * Servidor POP3 falso para las pruebas: un buzón en memoria que habla el protocolo de verdad
 * (RFC 1939) por un {@code ServerSocket} local.
 *
 * <p>Atiende las conexiones de a una, como Dovecot con un buzón bloqueado. Puede simular fallas:
 * buzón en uso, un servidor que no responde y un corte a mitad de un RETR.</p>
 */
public final class FakePop3Server implements AutoCloseable {

    public enum Falla { NINGUNA, BUZON_EN_USO, SILENCIO_TRAS_SALUDO, CORTE_EN_RETR, QUIT_SIN_CONFIRMAR }

    public static final String USUARIO = "grupo14sa";
    public static final String CONTRASENA = "clave-ñ-123";

    private final ServerSocket servidor;
    private final Thread hilo;
    private final List<byte[]> mensajes = new ArrayList<>();
    private final List<Boolean> borrados = new ArrayList<>();
    private final List<String> comandos = Collections.synchronizedList(new ArrayList<>());
    private volatile Falla falla = Falla.NINGUNA;
    private volatile int lineasSinCrlf;
    private volatile boolean cerrado;

    public FakePop3Server() throws IOException {
        servidor = new ServerSocket(0, 10, InetAddress.getLoopbackAddress());
        hilo = new Thread(this::atender, "fake-pop3");
        hilo.setDaemon(true);
        hilo.start();
    }

    public int puerto() {
        return servidor.getLocalPort();
    }

    public synchronized void agregar(String mensaje) {
        agregar(mensaje.getBytes(StandardCharsets.UTF_8));
    }

    public synchronized void agregar(byte[] mensaje) {
        mensajes.add(mensaje);
        borrados.add(false);
    }

    /** Mensajes que siguen en el buzón (no borrados con DELE + QUIT). */
    public synchronized int mensajesEnBuzon() {
        return (int) borrados.stream().filter(b -> !b).count();
    }

    public void falla(Falla f) {
        this.falla = f;
    }

    /** Comandos recibidos, con la contraseña tal como llegó. */
    public List<String> comandos() {
        return new ArrayList<>(comandos);
    }

    /** Cuántas líneas llegaron terminadas solo en LF, sin el CR. Debe ser 0. */
    public int lineasSinCrlf() {
        return lineasSinCrlf;
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
                // conexión cortada o servidor cerrado: se atiende la siguiente
            }
        }
    }

    private void sesion(InputStream in, OutputStream out) throws IOException {
        // Como en Dovecot, en cada sesión los mensajes que quedan se numeran desde 1 (RFC 1939, §5)
        List<Integer> vista = new ArrayList<>();
        synchronized (this) {
            for (int i = 0; i < mensajes.size(); i++) {
                if (!borrados.get(i)) {
                    vista.add(i);
                }
            }
        }
        List<Boolean> marcas = new ArrayList<>(Collections.nCopies(vista.size(), false));
        escribir(out, "+OK Dovecot ready.");
        if (falla == Falla.SILENCIO_TRAS_SALUDO) {
            while (in.read() != -1) {
                // no responde nada: el cliente debe cortar por tiempo
            }
            return;
        }
        boolean autenticado = false;
        String usuario = null;
        String linea;
        while ((linea = leerLinea(in)) != null) {
            comandos.add(linea);
            String[] p = linea.split(" ", 3);
            String cmd = p[0].toUpperCase();
            switch (cmd) {
                case "USER" -> {
                    usuario = p.length > 1 ? p[1] : "";
                    escribir(out, "+OK");
                }
                case "PASS" -> {
                    String pass = linea.length() > 5 ? linea.substring(5) : "";
                    if (falla == Falla.BUZON_EN_USO) {
                        escribir(out, "-ERR [IN-USE] Mailbox is locked by another POP3 session.");
                    } else if (USUARIO.equals(usuario) && CONTRASENA.equals(pass)) {
                        autenticado = true;
                        escribir(out, "+OK Logged in.");
                    } else {
                        escribir(out, "-ERR [AUTH] Authentication failed.");
                    }
                }
                case "QUIT" -> {
                    if (falla == Falla.QUIT_SIN_CONFIRMAR) {
                        // el servidor falla al aplicar los DELE: no borra nada
                        escribir(out, "-ERR [SYS/TEMP] Internal error occurred.");
                        return;
                    }
                    synchronized (this) {
                        for (int n = 0; n < marcas.size(); n++) {
                            if (marcas.get(n)) {
                                borrados.set(vista.get(n), true);
                            }
                        }
                    }
                    escribir(out, "+OK Logging out.");
                    return;
                }
                default -> {
                    if (!autenticado) {
                        escribir(out, "-ERR Unknown command.");
                    } else {
                        comandoAutenticado(cmd, p, vista, marcas, out);
                    }
                }
            }
        }
        // Se cortó sin QUIT: las marcas de DELE se descartan
    }

    private synchronized void comandoAutenticado(String cmd, String[] p, List<Integer> vista, List<Boolean> marcas,
                                                 OutputStream out) throws IOException {
        switch (cmd) {
            case "STAT" -> {
                int n = 0;
                long octetos = 0;
                for (int i = 0; i < vista.size(); i++) {
                    if (!marcas.get(i)) {
                        n++;
                        octetos += mensajes.get(vista.get(i)).length;
                    }
                }
                escribir(out, "+OK " + n + " " + octetos);
            }
            case "LIST", "UIDL", "RETR", "TOP", "DELE" -> {
                int n = numero(p) - 1;
                if (n < 0 || n >= vista.size() || marcas.get(n)) {
                    escribir(out, "-ERR No such message");
                    return;
                }
                byte[] mensaje = mensajes.get(vista.get(n));
                switch (cmd) {
                    case "LIST" -> escribir(out, "+OK " + (n + 1) + " " + mensaje.length);
                    case "UIDL" -> escribir(out, "+OK " + (n + 1) + " uid-" + (vista.get(n) + 1));
                    case "DELE" -> {
                        marcas.set(n, true);
                        escribir(out, "+OK Marked to be deleted.");
                    }
                    case "RETR" -> enviarMensaje(out, mensaje, Integer.MAX_VALUE, falla == Falla.CORTE_EN_RETR);
                    default -> enviarMensaje(out, mensaje, Integer.parseInt(p[2].trim()), false);
                }
            }
            case "NOOP" -> escribir(out, "+OK");
            case "RSET" -> {
                Collections.fill(marcas, false);
                escribir(out, "+OK");
            }
            default -> escribir(out, "-ERR Unknown command.");
        }
    }

    private static int numero(String[] p) {
        try {
            return Integer.parseInt(p[1].trim());
        } catch (RuntimeException e) {
            return -1;
        }
    }

    /**
     * Envía un mensaje como respuesta multilínea: duplica el punto inicial de cada línea y termina
     * con «.». Con {@code lineasCuerpo} limita el cuerpo (TOP). Con {@code cortar} cierra a la mitad.
     */
    private static void enviarMensaje(OutputStream out, byte[] mensaje, int lineasCuerpo, boolean cortar)
            throws IOException {
        escribir(out, "+OK " + mensaje.length + " octets");
        List<byte[]> lineas = partirLineas(mensaje);
        boolean enCuerpo = false;
        int cuerpo = 0;
        for (int n = 0; n < lineas.size(); n++) {
            if (cortar && n == lineas.size() / 2) {
                out.flush();
                throw new IOException("corte simulado");
            }
            byte[] l = lineas.get(n);
            if (enCuerpo && cuerpo++ >= lineasCuerpo) {
                break;
            }
            if (l.length > 0 && l[0] == '.') {
                out.write('.');
            }
            out.write(l);
            out.write(ConexionTexto.CRLF);
            if (!enCuerpo && l.length == 0) {
                enCuerpo = true;
            }
        }
        escribir(out, ".");
    }

    private static List<byte[]> partirLineas(byte[] mensaje) {
        List<byte[]> lineas = new ArrayList<>();
        ByteArrayOutputStream actual = new ByteArrayOutputStream();
        for (int i = 0; i < mensaje.length; i++) {
            if (mensaje[i] == '\r' && i + 1 < mensaje.length && mensaje[i + 1] == '\n') {
                lineas.add(actual.toByteArray());
                actual.reset();
                i++;
            } else {
                actual.write(mensaje[i]);
            }
        }
        if (actual.size() > 0) {
            lineas.add(actual.toByteArray());
        }
        return lineas;
    }

    private String leerLinea(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') {
                byte[] bytes = b.toByteArray();
                if (bytes.length == 0 || bytes[bytes.length - 1] != '\r') {
                    lineasSinCrlf++;
                    return new String(bytes, StandardCharsets.UTF_8);
                }
                return new String(bytes, 0, bytes.length - 1, StandardCharsets.UTF_8);
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
