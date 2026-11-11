package bo.edu.uagrm.ficct.canal11.comandos;

/**
 * Un comando que no se puede ejecutar por una regla del canal o por un dato inválido: el registro
 * no existe, la persona ya está asignada, la nota no está en revisión…
 *
 * <p>El mensaje ya viene listo para el usuario, con el estilo de la Tabla 77 («Error: …»).
 * {@code CommandProcessor} deshace la transacción y lo responde tal cual.</p>
 */
public final class ErrorNegocio extends Exception {

    private static final String PREFIJO = "Error: ";

    /** HTML adicional para la respuesta (por ejemplo, la lista de choques); puede ser vacío. */
    private final String detalleHtml;

    private ErrorNegocio(String mensaje, String detalleHtml) {
        super(mensaje, null, false, false);
        this.detalleHtml = detalleHtml == null ? "" : detalleHtml;
    }

    /** {@code de("el registro no existe")} → «Error: el registro no existe». */
    public static ErrorNegocio de(String motivo) {
        return new ErrorNegocio(PREFIJO + motivo, "");
    }

    /** Igual que {@link #de(String)}, con HTML adicional debajo del mensaje (ya escapado). */
    public static ErrorNegocio conDetalle(String motivo, String detalleHtml) {
        return new ErrorNegocio(PREFIJO + motivo, detalleHtml);
    }

    /** «Error: el registro no existe» (Tabla 77, flujo alternativo AG5). */
    public static ErrorNegocio noExiste() {
        return de("el registro no existe");
    }

    public String detalleHtml() {
        return detalleHtml;
    }
}
