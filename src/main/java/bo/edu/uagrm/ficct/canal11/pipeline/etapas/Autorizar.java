package bo.edu.uagrm.ficct.canal11.pipeline.etapas;

import bo.edu.uagrm.ficct.canal11.datos.Repositorio;
import bo.edu.uagrm.ficct.canal11.pipeline.Etapa;
import bo.edu.uagrm.ficct.canal11.pipeline.Pedido;

import java.sql.SQLException;

/**
 * Etapa 6: el remitente tiene que estar registrado y activo, y su rol tiene que tener el permiso
 * del comando (R1). Un no registrado y un inactivo reciben lo mismo (D16).
 */
public final class Autorizar implements Etapa {

    private static final String PERMISO = """
            SELECT 1 FROM rol_permiso rp JOIN permiso p ON p.id = rp.permiso_id
            WHERE rp.rol_id = ? AND p.comando = ?
            """;

    private final Repositorio repositorio;

    public Autorizar(Repositorio repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    public void procesar(Pedido p) throws SQLException {
        if (p.spec() == null) {
            return; // AYUDA ya respondió
        }
        String codigo = p.spec().codigo();
        if (p.remitente() == null) {
            Respuestas.error(p, "Error: no tiene permiso para ejecutar " + codigo + ". " + Respuestas.NO_REGISTRADO);
            return;
        }
        if (repositorio.consultar(p.conexion(), PERMISO, p.remitente().rolId(), codigo).isEmpty()) {
            Respuestas.error(p, "Error: no tiene permiso para ejecutar " + codigo);
        }
    }
}
