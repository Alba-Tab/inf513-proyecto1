package bo.edu.uagrm.ficct.canal11.datos;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Bitácora de comandos ({@code bitacora_comando}, RNF-24 y R2): cada correo procesado queda
 * registrado con su remitente, el asunto, el comando y el resultado, aunque no traiga un comando.
 *
 * <p>Se escribe fuera de la transacción del comando (decisión D12): si el comando falla y se
 * deshace, su registro en la bitácora queda igual. El asunto llega ya enmascarado (D6).</p>
 */
public final class Bitacora {

    private static final String INSERTAR = """
            INSERT INTO bitacora_comando (remitente, usuario_id, asunto, comando, resultado, respuesta)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

    private final Repositorio repositorio;

    public Bitacora(Repositorio repositorio) {
        this.repositorio = repositorio;
    }

    /**
     * @param usuarioId {@code null} si el remitente no está registrado
     * @param comando   el código, o {@code null} si el asunto no traía un comando del sistema
     * @param respuesta el mensaje principal de la respuesta
     */
    public void registrar(Connection c, String remitente, Integer usuarioId, String asunto, String comando,
                          boolean exito, String respuesta) throws SQLException {
        repositorio.ejecutar(c, INSERTAR, recortar(remitente, 150), usuarioId, asunto == null ? "" : asunto,
                recortar(comando, 10), exito ? "exito" : "error", respuesta);
    }

    private static String recortar(String s, int maximo) {
        return s == null || s.length() <= maximo ? s : s.substring(0, maximo);
    }
}
