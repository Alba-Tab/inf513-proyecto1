package bo.edu.uagrm.ficct.canal11.correo;

import java.io.IOException;

/** El servidor POP3 respondió {@code -ERR} a un comando. */
public class Pop3Exception extends IOException {

    private final String comando;
    private final String respuesta;

    public Pop3Exception(String comando, String respuesta) {
        super("POP3 " + comando + ": " + respuesta);
        this.comando = comando;
        this.respuesta = respuesta;
    }

    /** El comando que falló (sin la contraseña). */
    public String comando() {
        return comando;
    }

    /** La línea {@code -ERR …} completa del servidor. */
    public String respuesta() {
        return respuesta;
    }

    /** Dovecot avisa {@code -ERR [IN-USE]} cuando otra sesión tiene el buzón abierto. */
    public boolean buzonEnUso() {
        return respuesta != null && respuesta.contains("[IN-USE]");
    }

    /** {@code -ERR [AUTH]}: usuario o contraseña incorrectos. */
    public boolean credencialesInvalidas() {
        return respuesta != null && respuesta.contains("[AUTH]");
    }
}
