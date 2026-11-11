package bo.edu.uagrm.ficct.canal11;

import bo.edu.uagrm.ficct.canal11.config.Config;
import bo.edu.uagrm.ficct.canal11.datos.DBConnection;
import bo.edu.uagrm.ficct.canal11.datos.Fila;
import bo.edu.uagrm.ficct.canal11.datos.Repositorio;
import bo.edu.uagrm.ficct.canal11.correo.Pop3Client;
import bo.edu.uagrm.ficct.canal11.correo.SmtpClient;
import bo.edu.uagrm.ficct.canal11.correo.SmtpException;

import java.io.PrintStream;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.sql.SQLException;

/**
 * Paso 0 del despliegue, desde la propia aplicación:
 * {@code java -jar canal11-mail.jar --verificar config.properties su.correo@gmail.com}.
 *
 * <p>Prueba, con las credenciales de {@code config.properties} y los mismos clientes que usa la
 * aplicación:</p>
 * <ol>
 *   <li>la base de datos: conexión, versión, zona horaria y si ya tiene las tablas;</li>
 *   <li>el buzón POP3: inicio de sesión y cantidad de correos (no borra nada);</li>
 *   <li>el SMTP: EHLO, un destinatario local y uno externo (no envía nada: corta antes de DATA).</li>
 * </ol>
 */
final class Diagnostico {

    private final Config config;
    private final PrintStream out;
    private int fallas;

    Diagnostico(Config config, PrintStream out) {
        this.config = config;
        this.out = out;
    }

    /** Ejecuta las tres pruebas y devuelve cuántas fallaron. */
    int ejecutar(String correoExterno) {
        out.println("Verificando con " + config.resumen());
        baseDeDatos();
        buzon();
        envio(correoExterno);
        out.println();
        out.println(fallas == 0
                ? "Resumen: todo listo. La aplicación puede leer el buzón, usar la base y responder a " + correoExterno + "."
                : "Resumen: " + fallas + " punto(s) para resolver (marcados con [FALLA]).");
        return fallas;
    }

    private void baseDeDatos() {
        titulo("Base de datos");
        try (DBConnection db = new DBConnection(config)) {
            Repositorio repo = new Repositorio(config.timeoutSegundos());
            Fila f = repo.consultarUno(db.obtener(),
                    "SELECT current_setting('server_version') AS version, current_setting('TimeZone') AS zona,"
                            + " current_database() AS base, to_regclass('public.usuario') IS NOT NULL AS tiene_tablas").orElseThrow();
            ok("PostgreSQL " + f.texto("version") + ", base " + f.texto("base") + ", zona de la sesión " + f.texto("zona"));
            if (Boolean.TRUE.equals(f.logico("tiene_tablas"))) {
                Fila n = repo.consultarUno(db.obtener(),
                        "SELECT (SELECT count(*) FROM usuario) AS usuarios, (SELECT count(*) FROM permiso) AS permisos").orElseThrow();
                ok("tablas cargadas: " + n.texto("usuarios") + " usuarios, " + n.texto("permisos") + " permisos");
            } else {
                aviso("la base todavía no tiene las tablas: correr scripts/cargar-bd.sh");
            }
        } catch (SQLException | RuntimeException e) {
            falla("no se pudo usar la base: " + e.getMessage());
        }
    }

    private void buzon() {
        titulo("Buzón POP3 (no se borra nada)");
        try (Pop3Client pop = Pop3Client.conectar(config.pop3Host(), config.pop3Puerto(), config.timeoutSegundos() * 1000)) {
            ok(pop.saludo());
            pop.login(config.pop3Usuario(), config.pop3Contrasena());
            Pop3Client.Estado estado = pop.stat();
            ok("sesión iniciada como " + config.pop3Usuario() + ": " + estado.mensajes() + " correo(s) en el buzón");
            if (estado.mensajes() > 0) {
                aviso("al arrancar, la aplicación va a procesar (y borrar) esos " + estado.mensajes() + " correo(s)");
            }
            pop.quit();
        } catch (Exception e) {
            falla("no se pudo leer el buzón: " + describir(e, config.pop3Host()));
        }
    }

    /** Explica un error de red en palabras: DNS, conexión rechazada, tiempo agotado… */
    static String describir(Exception e, String host) {
        if (e instanceof UnknownHostException) {
            return "no se encontró el servidor «" + host + "» (el DNS no lo resuelve; pruebe con su IP)";
        }
        if (e instanceof ConnectException) {
            return "el servidor «" + host + "» rechazó la conexión (¿el puerto es correcto?)";
        }
        if (e instanceof SocketTimeoutException) {
            return "el servidor «" + host + "» no respondió a tiempo";
        }
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    private void envio(String correoExterno) {
        titulo("Envío SMTP (no se envía nada: se corta antes de DATA)");
        String dominio = config.correoSistema().substring(config.correoSistema().indexOf('@') + 1);
        try (SmtpClient smtp = SmtpClient.conectar(config.smtpHost(), config.smtpPuerto(), config.timeoutSegundos() * 1000)) {
            smtp.ehlo(dominio);
            ok("EHLO aceptado; capacidades: " + smtp.capacidades());
            smtp.mailFrom(config.correoSistema());
            smtp.rcptTo(config.correoSistema());
            ok("acepta enviar a una cuenta local (" + config.correoSistema() + ")");
            try {
                smtp.rcptTo(correoExterno);
                ok("acepta enviar a " + correoExterno + ": la aplicación podrá responder a Gmail");
            } catch (SmtpException e) {
                if (e.reenvioDenegado()) {
                    falla("rechaza enviar a " + correoExterno + " (Relaying denied). Desde este equipo no se puede responder"
                            + " a otros dominios: correr la aplicación en el servidor tecnoweb, o agregar autenticación"
                            + " (rama feat/smtp-cram-md5)");
                } else {
                    falla("rechaza enviar a " + correoExterno + ": " + e.respuesta());
                }
            }
            smtp.rset();
            smtp.quit();
        } catch (Exception e) {
            falla("no se pudo usar el SMTP: " + describir(e, config.smtpHost()));
        }
    }

    private void titulo(String t) {
        out.println();
        out.println("=== " + t);
    }

    private void ok(String m) {
        out.println("  [OK]    " + m);
    }

    private void aviso(String m) {
        out.println("  [AVISO] " + m);
    }

    private void falla(String m) {
        fallas++;
        out.println("  [FALLA] " + m);
    }
}
