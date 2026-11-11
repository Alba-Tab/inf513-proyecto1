package bo.edu.uagrm.ficct.canal11.pipeline;

import bo.edu.uagrm.ficct.canal11.comandos.HTMLResponseBuilder;
import bo.edu.uagrm.ficct.canal11.comandos.catalogo.Catalogo;
import bo.edu.uagrm.ficct.canal11.config.Config;
import bo.edu.uagrm.ficct.canal11.correo.CorreoSaliente;
import bo.edu.uagrm.ficct.canal11.datos.FuenteConexion;
import bo.edu.uagrm.ficct.canal11.datos.Repositorio;
import bo.edu.uagrm.ficct.canal11.datos.TraductorErrores;
import bo.edu.uagrm.ficct.canal11.datos.TraductorErrores.Operacion;
import bo.edu.uagrm.ficct.canal11.pipeline.etapas.Autorizar;
import bo.edu.uagrm.ficct.canal11.pipeline.etapas.Ayuda;
import bo.edu.uagrm.ficct.canal11.pipeline.etapas.Decodificar;
import bo.edu.uagrm.ficct.canal11.pipeline.etapas.Ejecutar;
import bo.edu.uagrm.ficct.canal11.pipeline.etapas.Filtrar;
import bo.edu.uagrm.ficct.canal11.pipeline.etapas.Identificar;
import bo.edu.uagrm.ficct.canal11.pipeline.etapas.Interpretar;
import bo.edu.uagrm.ficct.canal11.pipeline.etapas.Registrar;
import bo.edu.uagrm.ficct.canal11.pipeline.etapas.Responder;
import bo.edu.uagrm.ficct.canal11.pipeline.etapas.Validar;

import java.sql.SQLException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * La tubería (pipes and filters): pasa cada {@link Pedido} por las etapas, en orden. Es el flujo
 * de atención de un correo (sección 4.2.1 del documento), un paso por clase:
 *
 * <pre>
 * Decodificar → Filtrar → Identificar → Interpretar → Ayuda → Autorizar → Validar → Ejecutar → Registrar → Responder
 * </pre>
 *
 * <p>Nunca lanza una excepción por un correo: si una etapa falla, el pedido recibe una respuesta de
 * error y se siguen las etapas que corren siempre. Solo un {@link Error} de Java (memoria, pila)
 * sale de aquí, después de que {@code Ejecutar} deshizo la transacción.</p>
 */
public final class Pipeline implements Consumer<Pedido> {

    private static final Logger LOG = Logger.getLogger(Pipeline.class.getName());

    public static final String ERROR_GENERAL = "Error: no se pudo completar el comando; intente de nuevo más tarde";

    private final List<Etapa> etapas;

    public Pipeline(List<Etapa> etapas) {
        this.etapas = List.copyOf(etapas);
    }

    /** La tubería de la aplicación, del correo crudo a la respuesta encolada. */
    public static Pipeline completo(Config config, FuenteConexion fuente, Repositorio repositorio, Catalogo catalogo,
                                    Clock reloj, Consumer<CorreoSaliente> envio) {
        List<Etapa> e = new ArrayList<>();
        e.add(new Decodificar());
        e.add(new Filtrar(config));
        e.addAll(delComando(fuente, repositorio, catalogo, reloj));
        e.add(new Responder(config.correoSistema(), envio));
        return new Pipeline(e);
    }

    /**
     * Las etapas que atienden el comando, de un pedido ya decodificado a su registro en la
     * bitácora. Las pruebas de los comandos las usan sin el correo.
     */
    public static List<Etapa> delComando(FuenteConexion fuente, Repositorio repositorio, Catalogo catalogo, Clock reloj) {
        return List.of(new Identificar(fuente, repositorio), new Interpretar(catalogo), new Ayuda(repositorio, catalogo),
                new Autorizar(repositorio), new Validar(), new Ejecutar(repositorio, catalogo, reloj), new Registrar(repositorio));
    }

    @Override
    public void accept(Pedido p) {
        procesar(p);
    }

    public void procesar(Pedido p) {
        for (Etapa etapa : etapas) {
            if (p.descartado()) {
                return;
            }
            if (p.terminado() && !etapa.siempre()) {
                continue;
            }
            try {
                etapa.procesar(p);
            } catch (SQLException e) {
                LOG.warning("Base de datos no disponible en " + nombre(etapa) + " al atender a " + p.remitenteCorreo()
                        + ": " + e.getMessage());
                if (!p.terminado()) {
                    String m = TraductorErrores.traducir(e, Operacion.CONSULTAR);
                    p.error(HTMLResponseBuilder.error(m), m);
                }
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "Falló " + nombre(etapa) + " con el correo de " + p.remitenteCorreo()
                        + " («" + p.asuntoVisible() + "»)", e);
                if (!p.terminado()) {
                    p.error(HTMLResponseBuilder.error(ERROR_GENERAL), ERROR_GENERAL);
                }
            }
        }
    }

    private static String nombre(Etapa e) {
        return e.getClass().getSimpleName();
    }
}
