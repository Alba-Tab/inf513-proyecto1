package bo.edu.uagrm.ficct.canal11.pipeline;

/**
 * Un filtro del pipeline: hace un solo trabajo sobre el {@link Pedido} y lo deja para la etapa
 * siguiente.
 *
 * <p>Si encuentra un problema, le pone la respuesta de error al pedido ({@link Pedido#error}); las
 * etapas que faltan se saltan, salvo las que corren {@link #siempre()}.</p>
 */
@FunctionalInterface
public interface Etapa {

    void procesar(Pedido pedido) throws Exception;

    /** {@code true} si corre aunque el pedido ya tenga respuesta (Registrar y Responder). */
    default boolean siempre() {
        return false;
    }
}
