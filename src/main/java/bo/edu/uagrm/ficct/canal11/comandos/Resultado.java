package bo.edu.uagrm.ficct.canal11.comandos;

import bo.edu.uagrm.ficct.canal11.datos.Fila;
import bo.edu.uagrm.ficct.canal11.comandos.HTMLResponseBuilder.Tipo;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Lo que devuelve un comando que se ejecutó bien: el mensaje principal (Tabla 77) y el contenido de
 * la respuesta (fichas, tablas, avisos), que {@code CommandProcessor} envuelve en la página.
 *
 * <p>El mensaje principal es también lo que queda en la columna {@code respuesta} de la bitácora.</p>
 */
public final class Resultado {

    private Tipo tipo;
    private final String titulo;
    private String mensaje;
    private final StringBuilder cuerpo = new StringBuilder();
    private final List<Adjunto> adjuntos = new ArrayList<>();

    private Resultado(Tipo tipo, String titulo, String mensaje) {
        this.tipo = tipo;
        this.titulo = titulo;
        this.mensaje = mensaje;
    }

    /** «Éxito: {operacion} correctamente», por ejemplo {@code exito("Usuarios", "usuario registrado")}. */
    public static Resultado exito(String titulo, String operacion) {
        return new Resultado(Tipo.EXITO, titulo, "Éxito: " + operacion + " correctamente");
    }

    /** Éxito con un mensaje armado a mano (ya con «Éxito: …»). */
    public static Resultado exitoConMensaje(String titulo, String mensaje) {
        return new Resultado(Tipo.EXITO, titulo, mensaje);
    }

    /** «Éxito con advertencia: {advertencia}» (Tabla 77: la persona está fuera de su turno). */
    public static Resultado exitoConAdvertencia(String titulo, String advertencia) {
        return new Resultado(Tipo.ADVERTENCIA, titulo, "Éxito con advertencia: " + advertencia);
    }

    /** Aviso que no es un error (por ejemplo, un comando todavía no habilitado). */
    public static Resultado advertencia(String titulo, String mensaje) {
        return new Resultado(Tipo.ADVERTENCIA, titulo, mensaje);
    }

    /** Mensaje informativo («No hay personal disponible en ese horario»). */
    public static Resultado informacion(String titulo, String mensaje) {
        return new Resultado(Tipo.INFORMACION, titulo, mensaje);
    }

    /**
     * Listado (plantilla P2): tabla con encabezado y una fila por registro. Si no hay filas, responde
     * {@code siVacio}.
     */
    public static Resultado listado(String titulo, List<Fila> filas, String siVacio) {
        if (filas.isEmpty()) {
            return informacion(titulo, siVacio);
        }
        Resultado r = informacion(titulo, filas.size() == 1 ? "1 registro." : filas.size() + " registros.");
        return r.tabla(filas);
    }

    /** Agrega un párrafo de texto. */
    public Resultado parrafo(String texto) {
        cuerpo.append(HTMLResponseBuilder.parrafo(texto));
        return this;
    }

    /** Agrega un subtítulo. */
    public Resultado subtitulo(String texto) {
        cuerpo.append(HTMLResponseBuilder.subtitulo(texto));
        return this;
    }

    /** Agrega los datos de un registro en dos columnas (campo y valor). */
    public Resultado ficha(Fila fila) {
        cuerpo.append(HTMLResponseBuilder.ficha(fila));
        return this;
    }

    /** Agrega una tabla con las filas (las columnas son las de la consulta). */
    public Resultado tabla(List<Fila> filas) {
        cuerpo.append(HTMLResponseBuilder.tabla(filas));
        return this;
    }

    /** Agrega un recuadro de advertencia y marca la respuesta como advertencia (plantilla P4). */
    public Resultado aviso(String texto) {
        cuerpo.append(HTMLResponseBuilder.mensaje(Tipo.ADVERTENCIA, texto));
        if (tipo == Tipo.EXITO) {
            tipo = Tipo.ADVERTENCIA;
        }
        return this;
    }

    /** Agrega HTML ya armado con {@link HTMLResponseBuilder} (todo dato ya escapado). */
    public Resultado html(String html) {
        cuerpo.append(html);
        return this;
    }

    /** Agrega un texto al mensaje principal. */
    public Resultado agregarAlMensaje(String texto) {
        mensaje = mensaje + texto;
        return this;
    }

    /** Adjunta una página HTML (EXPESC). */
    public Resultado adjuntoHtml(String nombre, String html) {
        adjuntos.add(new Adjunto(nombre, "text/html; charset=UTF-8", html.getBytes(StandardCharsets.UTF_8)));
        return this;
    }

    public List<Adjunto> adjuntos() {
        return List.copyOf(adjuntos);
    }

    public Tipo tipo() {
        return tipo;
    }

    public String titulo() {
        return titulo;
    }

    public String mensaje() {
        return mensaje;
    }

    /** Contenido de la página: el mensaje principal y lo agregado. */
    public String contenidoHtml() {
        return HTMLResponseBuilder.mensaje(tipo, mensaje) + cuerpo;
    }
}
