package bo.edu.uagrm.ficct.canal11.pipeline;

import bo.edu.uagrm.ficct.canal11.comandos.Adjunto;
import bo.edu.uagrm.ficct.canal11.comandos.Remitente;
import bo.edu.uagrm.ficct.canal11.comandos.catalogo.CommandSpec;
import bo.edu.uagrm.ficct.canal11.comandos.catalogo.Parametros;

import java.sql.Connection;
import java.util.List;

/**
 * Un correo en viaje por el pipeline: lo que trae, lo que cada etapa le va agregando y la
 * respuesta que se le dará.
 *
 * <p>Cada etapa lee lo que dejaron las anteriores y completa su parte:</p>
 * <ol>
 *   <li>{@code Decodificar}: remitente, asunto, cuerpo y Message-ID;</li>
 *   <li>{@code Identificar}: la conexión a la base y el remitente registrado;</li>
 *   <li>{@code Interpretar}: el comando y su ficha del catálogo;</li>
 *   <li>{@code Validar}: los parámetros ya convertidos;</li>
 *   <li>{@code Ayuda}, {@code Autorizar}, {@code Validar} y {@code Ejecutar}: la respuesta.</li>
 * </ol>
 * <p>Un pedido termina de dos formas: con una respuesta ({@link #responder}), y entonces las
 * etapas que faltan se saltan salvo {@code Registrar} y {@code Responder}; o descartado
 * ({@link #descartar()}), y entonces no se registra ni se responde (correos automáticos).</p>
 */
public final class Pedido {

    // Lo que llega del buzón
    private final byte[] crudo;
    private final boolean demasiadoGrande;
    private final long tamano;

    // Decodificar
    private String remitenteCorreo = "";
    private String asunto = "";
    private String asuntoVisible = "";
    private String cuerpo = "";
    private String messageId = "";
    private boolean automatico;

    // Identificar
    private Connection conexion;
    private Remitente remitente;

    // Interpretar y Validar
    private ComandoParseado parseado;
    private CommandSpec spec;
    private String comando;
    private Parametros parametros;

    // Respuesta
    private boolean descartado;
    private boolean terminado;
    private boolean exito;
    private String html;
    private String resumen;
    private List<Adjunto> adjuntos = List.of();

    /** Un correo tal como salió del buzón ({@code contenido} son solo las cabeceras si era demasiado grande). */
    public Pedido(byte[] contenido, boolean demasiadoGrande, long tamano) {
        this.crudo = contenido;
        this.demasiadoGrande = demasiadoGrande;
        this.tamano = tamano;
    }

    /** Un pedido ya decodificado: lo usan las pruebas de los comandos, sin pasar por MIME. */
    public static Pedido de(String remitente, String asunto, String cuerpo) {
        Pedido p = new Pedido(null, false, 0);
        p.datosDelCorreo(remitente, asunto, cuerpo, "", false);
        return p;
    }

    // ------------------------------------------------------------------ escrituras de las etapas

    /** Lo que sale de {@code Decodificar}. El asunto visible se enmascara aquí, una sola vez. */
    public void datosDelCorreo(String remitente, String asunto, String cuerpo, String messageId, boolean automatico) {
        this.remitenteCorreo = remitente == null ? "" : remitente;
        this.asunto = asunto == null ? "" : asunto;
        this.asuntoVisible = Enmascarador.asunto(this.asunto);
        this.cuerpo = cuerpo == null ? "" : cuerpo;
        this.messageId = messageId == null ? "" : messageId;
        this.automatico = automatico;
    }

    public void conexion(Connection c) {
        this.conexion = c;
    }

    public void remitente(Remitente r) {
        this.remitente = r;
    }

    public void interpretado(ComandoParseado parseado, CommandSpec spec, String comando) {
        this.parseado = parseado;
        this.spec = spec;
        this.comando = comando;
    }

    public void parametros(Parametros p) {
        this.parametros = p;
    }

    /** El pedido ya tiene respuesta: las etapas que faltan se saltan, salvo Registrar y Responder. */
    public void responder(boolean exito, String html, String resumen, List<Adjunto> adjuntos) {
        this.terminado = true;
        this.exito = exito;
        this.html = html;
        this.resumen = resumen;
        this.adjuntos = adjuntos == null ? List.of() : List.copyOf(adjuntos);
    }

    public void error(String html, String resumen) {
        responder(false, html, resumen, List.of());
    }

    /** No se registra ni se responde (rebotes, respuestas automáticas, correos propios). */
    public void descartar() {
        this.descartado = true;
    }

    // ------------------------------------------------------------------ lecturas

    public byte[] crudo() {
        return crudo;
    }

    public boolean demasiadoGrande() {
        return demasiadoGrande;
    }

    public long tamano() {
        return tamano;
    }

    public String remitenteCorreo() {
        return remitenteCorreo;
    }

    public String asunto() {
        return asunto;
    }

    /** El asunto sin parámetros sensibles: el que va al log, a la bitácora y al «Re:». */
    public String asuntoVisible() {
        return asuntoVisible;
    }

    public String cuerpo() {
        return cuerpo;
    }

    public String messageId() {
        return messageId;
    }

    public boolean automatico() {
        return automatico;
    }

    public Connection conexion() {
        return conexion;
    }

    /** El remitente registrado y activo, o {@code null}. */
    public Remitente remitente() {
        return remitente;
    }

    public Integer usuarioId() {
        return remitente == null ? null : remitente.usuarioId();
    }

    public ComandoParseado parseado() {
        return parseado;
    }

    /** La ficha del comando, o {@code null} si el asunto no traía uno del catálogo. */
    public CommandSpec spec() {
        return spec;
    }

    /** Lo que se anota en la columna {@code comando} de la bitácora. */
    public String comando() {
        return comando;
    }

    public Parametros parametros() {
        return parametros;
    }

    public boolean descartado() {
        return descartado;
    }

    public boolean terminado() {
        return terminado;
    }

    public boolean exito() {
        return exito;
    }

    public String html() {
        return html;
    }

    public String resumen() {
        return resumen;
    }

    public List<Adjunto> adjuntos() {
        return adjuntos;
    }

    /** La respuesta lista para enviar, o {@code null} si el pedido no tiene respuesta. */
    public RespuestaCorreo respuesta() {
        if (!terminado) {
            return null;
        }
        return new RespuestaCorreo("Re: " + (asuntoVisible.isBlank() ? "(sin asunto)" : asuntoVisible), html, adjuntos);
    }
}
