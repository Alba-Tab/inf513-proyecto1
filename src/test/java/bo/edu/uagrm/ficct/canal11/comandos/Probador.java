package bo.edu.uagrm.ficct.canal11.comandos;

import bo.edu.uagrm.ficct.canal11.datos.ConexionDePrueba;
import bo.edu.uagrm.ficct.canal11.datos.Fila;
import bo.edu.uagrm.ficct.canal11.datos.Repositorio;
import bo.edu.uagrm.ficct.canal11.comandos.catalogo.Catalogo;
import bo.edu.uagrm.ficct.canal11.pipeline.Pedido;
import bo.edu.uagrm.ficct.canal11.pipeline.Pipeline;
import bo.edu.uagrm.ficct.canal11.pipeline.RespuestaCorreo;

import java.sql.SQLException;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * Envía comandos como si llegaran por correo y lee la respuesta, sobre la base de pruebas y sin
 * dejar rastro (ver {@link ConexionDePrueba}). Pasa cada pedido por las etapas del comando del
 * pipeline (de Identificar a Registrar), sin MIME ni sockets. Lo usan las pruebas de cada caso de uso.
 */
public final class Probador implements AutoCloseable {

    // Usuarios de db/datos_prueba.sql, uno por rol
    public static final String DIRECTOR = "director@canal11.test";
    public static final String JEFE_PRENSA = "jefe.prensa@canal11.test";
    /** Carla Rojas Suárez: periodista, ficha 1, turno mañana. */
    public static final String PERIODISTA = "carla.rojas@canal11.test";
    /** Marcelo Justiniano Égüez: periodista, ficha 2. */
    public static final String OTRO_PERIODISTA = "marcelo.justiniano@canal11.test";
    /** Jorge Mendoza Ribera: camarógrafo, ficha 5. */
    public static final String CAMAROGRAFO = "jorge.mendoza@canal11.test";
    /** Patricia Saucedo Melgar: editora, ficha 8. */
    public static final String EDITOR = "patricia.saucedo@canal11.test";
    /** Mariana Terceros Lara: presentadora, ficha 10. */
    public static final String PRESENTADOR = "mariana.terceros@canal11.test";
    public static final String AREA_TECNICA = "area.tecnica@canal11.test";

    private final ConexionDePrueba conexion;
    private final Pipeline pipeline;
    private final Repositorio repositorio = new Repositorio(30);

    private Probador(ConexionDePrueba conexion, Catalogo catalogo) {
        this.conexion = conexion;
        this.pipeline = new Pipeline(Pipeline.delComando(conexion::conexion, repositorio, catalogo,
                Clock.system(ZoneId.of("America/La_Paz"))));
    }

    /** Con el catálogo completo. Salta la prueba si no hay base de pruebas. */
    public static Probador abrir() throws Exception {
        return abrir(Catalogo.completo());
    }

    public static Probador abrir(Catalogo catalogo) throws Exception {
        return new Probador(ConexionDePrueba.abrir(), catalogo);
    }

    /** Respuesta completa (asunto y HTML). */
    public RespuestaCorreo enviar(String remitente, String asunto, String cuerpo) {
        Pedido p = Pedido.de(remitente, asunto, cuerpo);
        pipeline.procesar(p);
        return p.respuesta();
    }

    /** El texto visible de la respuesta, sin etiquetas HTML. */
    public String texto(String remitente, String asunto) {
        return texto(remitente, asunto, "");
    }

    public String texto(String remitente, String asunto, String cuerpo) {
        return Probador.textoVisible(enviar(remitente, asunto, cuerpo).html());
    }

    public List<Fila> consultar(String sql, Object... parametros) throws SQLException {
        return repositorio.consultar(conexion.conexion(), sql, parametros);
    }

    public Optional<Fila> uno(String sql, Object... parametros) throws SQLException {
        return consultar(sql, parametros).stream().findFirst();
    }

    /** Un número de una consulta de una sola fila y columna ({@code SELECT count(*) …}). */
    public long numero(String sql, Object... parametros) throws SQLException {
        Fila f = consultar(sql, parametros).get(0);
        return ((Number) f.valores().get(0)).longValue();
    }

    public int ejecutar(String sql, Object... parametros) throws SQLException {
        return repositorio.ejecutar(conexion.conexion(), sql, parametros);
    }

    /** Lo que se ve de un HTML: sin etiquetas, con las entidades decodificadas y espacios simples. */
    public static String textoVisible(String html) {
        return html.replaceAll("<br>", "\n").replaceAll("<[^>]+>", " ")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&")
                .replaceAll("[ \\t]+", " ").replaceAll(" *\\n *", "\n").strip();
    }

    @Override
    public void close() throws SQLException {
        conexion.close();
    }
}
