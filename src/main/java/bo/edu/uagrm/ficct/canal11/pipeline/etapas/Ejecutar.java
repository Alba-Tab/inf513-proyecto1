package bo.edu.uagrm.ficct.canal11.pipeline.etapas;

import bo.edu.uagrm.ficct.canal11.comandos.Contexto;
import bo.edu.uagrm.ficct.canal11.comandos.ErrorNegocio;
import bo.edu.uagrm.ficct.canal11.comandos.HTMLResponseBuilder;
import bo.edu.uagrm.ficct.canal11.comandos.Resultado;
import bo.edu.uagrm.ficct.canal11.comandos.catalogo.Catalogo;
import bo.edu.uagrm.ficct.canal11.comandos.catalogo.CommandSpec;
import bo.edu.uagrm.ficct.canal11.datos.Repositorio;
import bo.edu.uagrm.ficct.canal11.datos.TraductorErrores;
import bo.edu.uagrm.ficct.canal11.datos.TraductorErrores.Operacion;
import bo.edu.uagrm.ficct.canal11.pipeline.Etapa;
import bo.edu.uagrm.ficct.canal11.pipeline.Pedido;
import bo.edu.uagrm.ficct.canal11.pipeline.Pipeline;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Etapa 8: ejecuta la acción del comando en una transacción (D12). Si algo falla, se deshace
 * todo: nunca queda un cambio a medias. Los errores de la base se responden traducidos
 * ({@link TraductorErrores}), nunca con el texto de PostgreSQL.
 */
public final class Ejecutar implements Etapa {

    private static final Logger LOG = Logger.getLogger(Ejecutar.class.getName());

    private final Repositorio repositorio;
    private final Catalogo catalogo;
    private final Clock reloj;

    public Ejecutar(Repositorio repositorio, Catalogo catalogo, Clock reloj) {
        this.repositorio = repositorio;
        this.catalogo = catalogo;
        this.reloj = reloj;
    }

    @Override
    public void procesar(Pedido p) throws SQLException {
        CommandSpec spec = p.spec();
        Connection c = p.conexion();
        Contexto ctx = new Contexto(c, repositorio, p.remitente(), spec.codigo(), p.parametros(), p.cuerpo(), reloj, catalogo);
        try {
            c.setAutoCommit(false);
            Resultado r = spec.accion().ejecutar(ctx);
            try (Statement st = c.createStatement()) {
                // Las restricciones diferidas (el orden de la escaleta) se verifican aquí, con mensaje traducido
                st.execute("SET CONSTRAINTS ALL IMMEDIATE");
            }
            c.commit();
            p.responder(true, HTMLResponseBuilder.pagina(r.titulo(), r.contenidoHtml()), r.mensaje(), r.adjuntos());
        } catch (ErrorNegocio e) {
            deshacer(c);
            Respuestas.errorDeComando(p, e.getMessage(), e.detalleHtml());
        } catch (SQLException e) {
            deshacer(c);
            LOG.warning(spec.codigo() + " falló en la base de datos (" + e.getSQLState() + "): " + e.getMessage());
            Respuestas.errorDeComando(p, TraductorErrores.traducir(e, Operacion.delComando(spec.codigo())), "");
        } catch (RuntimeException e) {
            deshacer(c);
            LOG.log(Level.SEVERE, spec.codigo() + " falló por un error inesperado", e);
            Respuestas.error(p, Pipeline.ERROR_GENERAL);
        } catch (Error e) {
            // Sin esto, el setAutoCommit(true) del finally confirmaría los cambios a medias (JDBC)
            deshacer(c);
            throw e;
        } finally {
            try {
                c.setAutoCommit(true);
            } catch (SQLException e) {
                LOG.log(Level.FINE, "No se pudo volver a autocommit", e);
            }
        }
    }

    private static void deshacer(Connection c) {
        try {
            c.rollback();
        } catch (SQLException e) {
            LOG.log(Level.FINE, "No se pudo deshacer la transacción", e);
        }
    }
}
