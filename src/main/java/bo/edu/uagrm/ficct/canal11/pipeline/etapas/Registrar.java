package bo.edu.uagrm.ficct.canal11.pipeline.etapas;

import bo.edu.uagrm.ficct.canal11.datos.Bitacora;
import bo.edu.uagrm.ficct.canal11.datos.Repositorio;
import bo.edu.uagrm.ficct.canal11.pipeline.Etapa;
import bo.edu.uagrm.ficct.canal11.pipeline.Pedido;

import java.util.logging.Logger;

/**
 * Etapa 9: anota el correo en {@code bitacora_comando} (R2, RNF-24), también si fue rechazado.
 * Corre siempre, fuera de la transacción del comando: si el comando se deshizo, su registro queda.
 * El asunto va enmascarado (D6). Sin conexión a la base (la base no respondió) solo queda el log.
 */
public final class Registrar implements Etapa {

    private static final Logger LOG = Logger.getLogger(Registrar.class.getName());

    private final Bitacora bitacora;

    public Registrar(Repositorio repositorio) {
        this.bitacora = new Bitacora(repositorio);
    }

    @Override
    public boolean siempre() {
        return true;
    }

    @Override
    public void procesar(Pedido p) {
        if (p.conexion() != null && p.terminado()) {
            try {
                bitacora.registrar(p.conexion(), p.remitenteCorreo(), p.usuarioId(), p.asuntoVisible(), p.comando(),
                        p.exito(), p.resumen());
            } catch (Exception e) {
                LOG.warning("No se pudo escribir la bitácora del correo de " + p.remitenteCorreo() + ": " + e.getMessage());
            }
        }
        LOG.info((p.exito() ? "Atendido" : "Rechazado") + " «" + p.asuntoVisible() + "» de " + p.remitenteCorreo()
                + ": " + p.resumen());
    }
}
