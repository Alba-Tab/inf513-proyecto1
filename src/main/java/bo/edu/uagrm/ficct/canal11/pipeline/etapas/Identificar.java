package bo.edu.uagrm.ficct.canal11.pipeline.etapas;

import bo.edu.uagrm.ficct.canal11.comandos.Remitente;
import bo.edu.uagrm.ficct.canal11.datos.Fila;
import bo.edu.uagrm.ficct.canal11.datos.FuenteConexion;
import bo.edu.uagrm.ficct.canal11.datos.Repositorio;
import bo.edu.uagrm.ficct.canal11.pipeline.Etapa;
import bo.edu.uagrm.ficct.canal11.pipeline.Pedido;

import java.sql.SQLException;
import java.util.List;

/**
 * Etapa 3: abre la conexión a la base y busca al remitente (CU-01: no hay inicio de sesión, el
 * correo identifica al usuario). Un usuario no registrado o inactivo queda como {@code null}
 * (D16); qué se le responde lo deciden las etapas siguientes.
 *
 * <p>Si la base no responde, el {@code Pipeline} le contesta al remitente que intente más tarde.</p>
 */
public final class Identificar implements Etapa {

    private static final String REMITENTE = """
            SELECT u.id, u.nombre, u.apellido, u.correo, u.estado, u.rol_id, r.nombre AS rol,
                   p.id AS personal_id, p.cargo, p.estado AS estado_personal
            FROM usuario u
            JOIN rol r ON r.id = u.rol_id
            LEFT JOIN personal p ON p.usuario_id = u.id
            WHERE lower(u.correo) = lower(?)
            ORDER BY (u.correo = ?) DESC
            """;

    private final FuenteConexion fuente;
    private final Repositorio repositorio;

    public Identificar(FuenteConexion fuente, Repositorio repositorio) {
        this.fuente = fuente;
        this.repositorio = repositorio;
    }

    @Override
    public void procesar(Pedido p) throws SQLException {
        p.conexion(fuente.obtener());
        List<Fila> filas = repositorio.consultar(p.conexion(), REMITENTE, p.remitenteCorreo(), p.remitenteCorreo());
        if (filas.isEmpty() || !"activo".equals(filas.get(0).texto("estado"))) {
            return;
        }
        Fila f = filas.get(0);
        boolean fichaActiva = f.get("personal_id") != null && "activo".equals(f.texto("estado_personal"));
        p.remitente(new Remitente(f.entero("id"), f.texto("nombre"), f.texto("apellido"), f.texto("correo"),
                f.entero("rol_id"), f.texto("rol"), fichaActiva ? f.entero("personal_id") : null, fichaActiva ? f.texto("cargo") : null));
    }
}
