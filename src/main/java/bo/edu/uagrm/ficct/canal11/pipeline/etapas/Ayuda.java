package bo.edu.uagrm.ficct.canal11.pipeline.etapas;

import bo.edu.uagrm.ficct.canal11.comandos.HTMLResponseBuilder;
import bo.edu.uagrm.ficct.canal11.comandos.MenuAyuda;
import bo.edu.uagrm.ficct.canal11.comandos.Resultado;
import bo.edu.uagrm.ficct.canal11.comandos.catalogo.Catalogo;
import bo.edu.uagrm.ficct.canal11.datos.Repositorio;
import bo.edu.uagrm.ficct.canal11.pipeline.ComandoParseado;
import bo.edu.uagrm.ficct.canal11.pipeline.Etapa;
import bo.edu.uagrm.ficct.canal11.pipeline.Pedido;

import java.sql.SQLException;

/**
 * Etapa 5: si el asunto es AYUDA, responde los comandos que puede usar el rol del remitente, con
 * un ejemplo de cada uno (plantilla P1). Sale de {@code rol_permiso}: si cambian los permisos, la
 * ayuda cambia sola.
 */
public final class Ayuda implements Etapa {

    private final MenuAyuda menu;

    public Ayuda(Repositorio repositorio, Catalogo catalogo) {
        this.menu = new MenuAyuda(repositorio, catalogo);
    }

    @Override
    public void procesar(Pedido p) throws SQLException {
        if (!(p.parseado() instanceof ComandoParseado.Ayuda)) {
            return;
        }
        if (p.remitente() == null) {
            Respuestas.error(p, Respuestas.NO_REGISTRADO);
            return;
        }
        Resultado r = menu.armar(p.conexion(), p.remitente());
        p.responder(true, HTMLResponseBuilder.pagina(r.titulo(), r.contenidoHtml()), r.mensaje(), r.adjuntos());
    }
}
