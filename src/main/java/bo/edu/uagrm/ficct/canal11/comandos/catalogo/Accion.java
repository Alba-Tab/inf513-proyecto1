package bo.edu.uagrm.ficct.canal11.comandos.catalogo;

import bo.edu.uagrm.ficct.canal11.comandos.Contexto;
import bo.edu.uagrm.ficct.canal11.comandos.ErrorNegocio;
import bo.edu.uagrm.ficct.canal11.comandos.Resultado;

import java.sql.SQLException;

/**
 * Lo que hace un comando una vez validados el permiso y los parámetros (el «handler»).
 *
 * <p>Corre dentro de la transacción que abre {@code CommandProcessor}: no hace commit ni rollback
 * (decisión D12). Si una regla del canal no se cumple, lanza {@link ErrorNegocio}; si falla la
 * base, deja pasar la {@link SQLException}, que se traduce con {@code TraductorErrores}.</p>
 */
@FunctionalInterface
public interface Accion {

    Resultado ejecutar(Contexto ctx) throws SQLException, ErrorNegocio;

    /** Acción de un comando del catálogo que todavía no se implementó. */
    Accion PENDIENTE = ctx -> Resultado.advertencia(ctx.codigo(),
            "El comando " + ctx.codigo() + " está en el catálogo, pero todavía no está habilitado en esta versión.");
}
