package bo.edu.uagrm.ficct.canal11.comandos;

/**
 * Quién envió el comando: el usuario registrado con ese correo, su rol y, si la tiene, su ficha de
 * personal. No hay inicio de sesión: el correo identifica al usuario (CU-01).
 *
 * @param usuarioId  id del usuario
 * @param personalId id de su ficha de personal, o {@code null} si no tiene (Director, Área Técnica)
 * @param cargo      cargo de la ficha, o {@code null}
 */
public record Remitente(int usuarioId, String nombre, String apellido, String correo, int rolId, String rol,
                        Integer personalId, String cargo) {

    public static final String DIRECTOR = "Director";
    public static final String JEFE_PRENSA = "Jefe de Prensa";

    public String nombreCompleto() {
        return nombre + " " + apellido;
    }

    public boolean esDirector() {
        return DIRECTOR.equals(rol);
    }

    public boolean esJefePrensa() {
        return JEFE_PRENSA.equals(rol);
    }

    /** {@code true} si tiene ficha de personal activa (para redactar notas, registrar retornos…). */
    public boolean tieneFicha() {
        return personalId != null;
    }
}
