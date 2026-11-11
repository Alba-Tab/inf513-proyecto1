package bo.edu.uagrm.ficct.canal11.correo;

import java.io.IOException;

/** El servidor SMTP respondió con un código de error (4xx o 5xx) o con uno inesperado. */
public class SmtpException extends IOException {

    private final String comando;
    private final int codigo;
    private final String respuesta;

    public SmtpException(String comando, int codigo, String respuesta) {
        super("SMTP " + comando + ": " + respuesta);
        this.comando = comando;
        this.codigo = codigo;
        this.respuesta = respuesta;
    }

    public String comando() {
        return comando;
    }

    /** Código de tres dígitos de la respuesta (por ejemplo 550). */
    public int codigo() {
        return codigo;
    }

    /** Texto completo de la respuesta del servidor. */
    public String respuesta() {
        return respuesta;
    }

    /** 5xx: el servidor no lo aceptará aunque se reintente (por ejemplo, «Relaying denied»). */
    public boolean esPermanente() {
        return codigo >= 500 && codigo < 600;
    }

    /** 4xx: falla temporal; se puede reintentar más tarde. */
    public boolean esTemporal() {
        return codigo >= 400 && codigo < 500;
    }

    /** {@code 550 5.7.1 … Relaying denied}: el servidor no reenvía a ese dominio desde esta conexión. */
    public boolean reenvioDenegado() {
        return respuesta != null && respuesta.toLowerCase().contains("relaying denied");
    }
}
