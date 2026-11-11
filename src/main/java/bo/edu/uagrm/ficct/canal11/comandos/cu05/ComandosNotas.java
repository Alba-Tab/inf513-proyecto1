package bo.edu.uagrm.ficct.canal11.comandos.cu05;

import bo.edu.uagrm.ficct.canal11.datos.Fila;
import bo.edu.uagrm.ficct.canal11.datos.TablaCrud;
import bo.edu.uagrm.ficct.canal11.comandos.Contexto;
import bo.edu.uagrm.ficct.canal11.comandos.ErrorNegocio;
import bo.edu.uagrm.ficct.canal11.comandos.Formato;
import bo.edu.uagrm.ficct.canal11.comandos.Remitente;
import bo.edu.uagrm.ficct.canal11.comandos.Resultado;
import bo.edu.uagrm.ficct.canal11.comandos.catalogo.CommandSpec;
import bo.edu.uagrm.ficct.canal11.comandos.catalogo.Parametros;
import bo.edu.uagrm.ficct.canal11.comandos.catalogo.Tipo;

import java.sql.SQLException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * CU-05 Gestión de Redactar Notas (NRCS), Gestionar Material Audiovisual y Flujo de Aprobación
 * (Tabla 83 del Anexo A).
 *
 * <ul>
 *   <li>INSNOT y GUINOT toman el guion del cuerpo del correo; el sistema cuenta las palabras y
 *       guarda cada versión (R14). Si cambia el guion de una nota aprobada, vuelve a revisión.</li>
 *   <li>R13: el slug no se repite en la misma fecha.</li>
 *   <li>R15: ENVNOT pasa a revisión; APRNOT y OBSNOT solo sobre una nota en revisión, y observar
 *       exige el motivo. Cada paso queda en {@code revision_nota}.</li>
 *   <li>A5: solo el autor o el Jefe de Prensa modifican una nota, su guion y sus rótulos.</li>
 *   <li>R25: la respuesta da la duración estimada (palabras ÷ ritmo + video/SOT).</li>
 *   <li>M6: {@code LISNOT["id"]} devuelve la nota completa con su guion (así la lee el
 *       Presentador). L5: {@code LISNOT["","aprobada"]} filtra solo por estado.</li>
 * </ul>
 */
public final class ComandosNotas {

    private static final String CU = "CU-05";
    private static final List<String> ESTADOS = List.of("borrador", "en_revision", "observada", "aprobada", "emitida", "baja");
    private static final List<String> ESTADOS_MATERIAL = List.of("pendiente", "ingestado", "en_edicion", "listo");
    static final Tipo TIPO_PIEZA = Tipo.opcion("lectura", "vo", "vo_sot", "nota_completa", "enlace_vivo", "entrevista_estudio");
    static final Tipo ESTADO_NOTA = Tipo.opcion(ESTADOS.toArray(String[]::new));
    static final Tipo TIPO_MATERIAL = Tipo.opcion("video", "sot", "grafico", "foto", "audio");
    static final Tipo ESTADO_MATERIAL = Tipo.opcion(ESTADOS_MATERIAL.toArray(String[]::new));
    static final Tipo RITMO = Tipo.entero(80, 250);

    private ComandosNotas() {
    }

    public static List<CommandSpec> comandos() {
        return List.of(
                CommandSpec.de(CU, "LISNOT", "Listar notas")
                        .opcional("fecha", Tipo.FILTRO).omitible("estado", ESTADO_NOTA)
                        .ejemplo("LISNOT[\"2026-10-12\",\"aprobada\"]").accion(ComandosNotas::lisnot).build(),
                CommandSpec.de(CU, "INSNOT", "Redactar nota (el guion va en el cuerpo del correo)")
                        .param("slug", Tipo.texto(40)).param("titulo", Tipo.texto(200)).param("tipo_pieza", TIPO_PIEZA)
                        .param("fecha", Tipo.FECHA).opcional("id_cobertura", Tipo.ID).opcional("palabras_por_minuto", RITMO)
                        .guionEnCuerpo()
                        .ejemplo("INSNOT[\"PARO-TRANSPORTE\",\"Transportistas bloquean el 4to anillo\",\"vo_sot\",\"2026-10-12\",\"2\",\"\"]")
                        .accion(ComandosNotas::insnot).build(),
                CommandSpec.de(CU, "UPDNOT", "Modificar datos de la nota")
                        .param("id", Tipo.ID).opcional("slug", Tipo.texto(40)).opcional("titulo", Tipo.texto(200))
                        .opcional("tipo_pieza", TIPO_PIEZA).opcional("fecha", Tipo.FECHA).opcional("id_cobertura", Tipo.ID)
                        .opcional("palabras_por_minuto", RITMO)
                        .ejemplo("UPDNOT[\"4\",\"INCENDIOS-CHIQUITANIA\",\"COED reporta 14 focos de calor\",\"lectura\",\"2026-10-12\",\"6\",\"130\"]")
                        .accion(ComandosNotas::updnot).build(),
                CommandSpec.de(CU, "DELNOT", "Dar de baja la nota")
                        .param("id", Tipo.ID)
                        .ejemplo("DELNOT[\"5\"]").accion(ComandosNotas::delnot).build(),
                CommandSpec.de(CU, "GUINOT", "Reemplazar el guion (cuerpo del correo; crea una versión)")
                        .param("id", Tipo.ID)
                        .guionEnCuerpo()
                        .ejemplo("GUINOT[\"1\"]").accion(ComandosNotas::guinot).build(),
                CommandSpec.de(CU, "ENVNOT", "Enviar la nota a revisión")
                        .param("id", Tipo.ID)
                        .ejemplo("ENVNOT[\"5\"]").accion(ComandosNotas::envnot).build(),
                CommandSpec.de(CU, "APRNOT", "Aprobar la nota")
                        .param("id", Tipo.ID)
                        .ejemplo("APRNOT[\"3\"]").accion(ComandosNotas::aprnot).build(),
                CommandSpec.de(CU, "OBSNOT", "Observar la nota con motivo")
                        .param("id", Tipo.ID).opcional("motivo", Tipo.texto(255))
                        .ejemplo("OBSNOT[\"6\",\"Falta la versión de la Alcaldía\"]").accion(ComandosNotas::obsnot).build());
    }

    // =================================================================== notas

    private static final String NOTAS = """
            SELECT n.id AS "Id", n.slug AS "Slug", n.titulo AS "Título", n.tipo_pieza AS "Tipo", n.fecha AS "Fecha",
                   n.estado AS "Estado", u.nombre || ' ' || u.apellido AS "Autor", n.palabras AS "Palabras",
                   %s AS "Duración", n.cobertura_id AS "Cobertura"
            FROM nota n
            JOIN personal pe ON pe.id = n.autor_id
            JOIN usuario u ON u.id = pe.usuario_id
            JOIN v_nota_duracion d ON d.nota_id = n.id
            """.formatted(Formato.mmss("d.duracion_total"));

    private static final String NOTA_COMPLETA = """
            SELECT n.id AS "Id", n.slug AS "Slug", n.titulo AS "Título", n.tipo_pieza AS "Tipo", n.fecha AS "Fecha",
                   n.estado AS "Estado", u.nombre || ' ' || u.apellido AS "Autor", n.cobertura_id AS "Cobertura",
                   n.palabras AS "Palabras", COALESCE(n.palabras_por_minuto::text, 'el del noticiero') AS "Ritmo (palabras/min)",
                   %s AS "Lectura", %s AS "Video y SOT", %s AS "Duración estimada",
                   n.guion AS "Guion"
            FROM nota n
            JOIN personal pe ON pe.id = n.autor_id
            JOIN usuario u ON u.id = pe.usuario_id
            JOIN v_nota_duracion d ON d.nota_id = n.id
            WHERE n.id = ?
            """.formatted(Formato.mmss("d.duracion_lectura"), Formato.mmss("d.duracion_video"), Formato.mmss("d.duracion_total"));

    private static Resultado lisnot(Contexto ctx) throws SQLException, ErrorNegocio {
        Parametros p = ctx.p();
        String filtro = p.texto("fecha");
        String estado = p.texto("estado");
        if (filtro != null && filtro.matches("\\d+")) {
            return notaCompleta(ctx, Integer.parseInt(filtro));  // M6
        }
        if (filtro != null && estado == null && ESTADOS.contains(Tipo.normalizarOpcion(filtro))) {
            estado = Tipo.normalizarOpcion(filtro);  // L5: LISNOT["aprobada"]
            filtro = null;
        }
        LocalDate fecha = null;
        if (filtro != null) {
            String f = Tipo.normalizarOpcion(filtro);
            if (f.equals("hoy")) {
                fecha = ctx.hoy();
            } else if (f.equals("manana")) {
                fecha = ctx.hoy().plusDays(1);
            } else {
                try {
                    fecha = (LocalDate) Tipo.FECHA.convertir(filtro, "fecha");
                } catch (ErrorNegocio e) {
                    throw ErrorNegocio.de("indique \"*\", una fecha AAAA-MM-DD, el id de una nota o un estado (llegó «" + filtro + "»)");
                }
            }
        }
        List<Fila> filas;
        String titulo = "Notas" + (fecha == null ? "" : " del " + fecha) + (estado == null ? "" : " (" + mostrar(estado) + ")");
        if (fecha != null && estado != null) {
            filas = ctx.consultar(NOTAS + " WHERE n.fecha = ? AND n.estado = ? ORDER BY n.slug", fecha, estado);
        } else if (fecha != null) {
            filas = ctx.consultar(NOTAS + " WHERE n.fecha = ? ORDER BY n.slug", fecha);
        } else if (estado != null) {
            filas = ctx.consultar(NOTAS + " WHERE n.estado = ? ORDER BY n.fecha DESC, n.slug", estado);
        } else {
            filas = ctx.consultar(NOTAS + " ORDER BY n.fecha DESC, n.slug");
        }
        return Resultado.listado(titulo, filas, "No hay notas con ese filtro.")
                .parrafo("Para leer una nota completa, con su guion: LISNOT[\"id\"].");
    }

    /** M6: la nota con su guion, rótulos y material. */
    private static Resultado notaCompleta(Contexto ctx, int id) throws SQLException, ErrorNegocio {
        Fila n = ctx.requerir(NOTA_COMPLETA, id);
        Resultado r = Resultado.informacion("Nota " + n.texto("Slug"), "Nota " + id + ": «" + n.texto("Título") + "».").ficha(n);
        List<Fila> rotulos = ctx.consultar(ROTULOS + " WHERE r.nota_id = ? ORDER BY r.orden", id);
        if (!rotulos.isEmpty()) {
            r.subtitulo("Rótulos").tabla(rotulos);
        }
        List<Fila> material = ctx.consultar(MATERIAL + " WHERE m.nota_id = ? ORDER BY m.id", id);
        if (!material.isEmpty()) {
            r.subtitulo("Material audiovisual").tabla(material);
        }
        return r;
    }

    private static Resultado insnot(Contexto ctx) throws SQLException, ErrorNegocio {
        Parametros p = ctx.p();
        Remitente yo = ctx.remitente();
        if (!yo.tieneFicha()) {
            throw ErrorNegocio.de("para redactar notas necesita una ficha de personal activa (INSPER)");
        }
        String guion = guion(ctx);
        String slug = slug(p.texto("slug"));
        LocalDate fecha = p.fecha("fecha");
        slugLibre(ctx, slug, fecha, 0);
        coberturaExistente(ctx, p.entero("id_cobertura"));
        int palabras = contarPalabras(guion);
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("slug", slug);
        v.put("titulo", p.texto("titulo"));
        v.put("tipo_pieza", p.texto("tipo_pieza"));
        v.put("guion", guion);
        v.put("palabras", palabras);
        v.put("palabras_por_minuto", p.entero("palabras_por_minuto"));
        v.put("fecha", fecha);
        v.put("cobertura_id", p.entero("id_cobertura"));
        v.put("autor_id", yo.personalId());
        int id = TablaCrud.insertar(ctx.repositorio(), ctx.conexion(), "nota", v);
        guardarVersion(ctx, id, guion, palabras);
        Fila n = ctx.requerir(NOTA_COMPLETA, id);
        return Resultado.exito("Notas", "nota registrada").ficha(n)
                .parrafo("Duración estimada: " + n.texto("Duración estimada") + ". La nota queda en borrador: cuando esté lista, envíela con ENVNOT[\""
                        + id + "\"].");
    }

    private static Resultado updnot(Contexto ctx) throws SQLException, ErrorNegocio {
        Parametros p = ctx.p();
        p.requerirAlguno("slug", "titulo", "tipo_pieza", "fecha", "id_cobertura", "palabras_por_minuto");
        int id = p.entero("id");
        Fila n = editable(ctx, id);
        String slug = p.tiene("slug") ? slug(p.texto("slug")) : n.texto("slug");
        LocalDate fecha = p.tiene("fecha") ? p.fecha("fecha") : n.fecha("fecha");
        slugLibre(ctx, slug, fecha, id);
        coberturaExistente(ctx, p.entero("id_cobertura"));
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("slug", p.tiene("slug") ? slug : null);
        v.put("titulo", p.texto("titulo"));
        v.put("tipo_pieza", p.texto("tipo_pieza"));
        v.put("fecha", p.fecha("fecha"));
        v.put("cobertura_id", p.entero("id_cobertura"));
        v.put("palabras_por_minuto", p.entero("palabras_por_minuto"));
        TablaCrud.actualizar(ctx.repositorio(), ctx.conexion(), "nota", id, v);
        return Resultado.exito("Notas", "nota modificada").ficha(ctx.requerir(NOTAS + " WHERE n.id = ?", id));
    }

    private static Resultado delnot(Contexto ctx) throws SQLException, ErrorNegocio {
        int id = ctx.p().entero("id");
        editable(ctx, id);
        List<Fila> enEscaleta = escaletasDe(ctx, id);
        if (!enEscaleta.isEmpty()) {
            throw ErrorNegocio.de("la nota está en la escaleta " + enEscaleta.get(0).entero("escaleta_id")
                    + ": quítela antes con DELITE");
        }
        ctx.ejecutar("UPDATE nota SET estado = 'baja', actualizado_en = CURRENT_TIMESTAMP WHERE id = ?", id);
        return Resultado.exito("Notas", "nota dada de baja").ficha(ctx.requerir(NOTAS + " WHERE n.id = ?", id))
                .parrafo("La nota y su historial se conservan.");
    }

    private static Resultado guinot(Contexto ctx) throws SQLException, ErrorNegocio {
        int id = ctx.p().entero("id");
        Fila n = editable(ctx, id);
        String guion = guion(ctx);
        int palabras = contarPalabras(guion);
        boolean estabaAprobada = "aprobada".equals(n.texto("estado"));
        ctx.ejecutar("""
                UPDATE nota SET guion = ?, palabras = ?,
                                estado = CASE WHEN estado = 'aprobada' THEN 'en_revision' ELSE estado END,
                                actualizado_en = CURRENT_TIMESTAMP
                WHERE id = ?
                """, guion, palabras, id);
        int version = guardarVersion(ctx, id, guion, palabras);
        Fila completa = ctx.requerir(NOTA_COMPLETA, id);
        Resultado r = Resultado.exito("Notas", "guion reemplazado").ficha(completa)
                .parrafo("Se guardó la versión " + version + " del guion. Duración estimada: " + completa.texto("Duración estimada") + ".");
        if (estabaAprobada) {
            r.aviso("La nota estaba aprobada: vuelve a revisión hasta que el Jefe de Prensa la apruebe otra vez (R14).");
            List<Fila> enEscaleta = escaletasDe(ctx, id);
            if (!enEscaleta.isEmpty()) {
                r.aviso("La nota está en la escaleta " + enEscaleta.get(0).entero("escaleta_id")
                        + ": LISESC la marca con una alerta mientras no esté aprobada.");  // M3
            }
        }
        return r;
    }

    // =================================================================== revisión (R15)

    private static Resultado envnot(Contexto ctx) throws SQLException, ErrorNegocio {
        int id = ctx.p().entero("id");
        Fila n = editable(ctx, id);
        String estado = n.texto("estado");
        if (!estado.equals("borrador") && !estado.equals("observada")) {
            throw ErrorNegocio.de("la nota está " + mostrar(estado) + ": solo se envían a revisión las notas en borrador u observadas");
        }
        if (n.entero("palabras") == 0) {
            throw ErrorNegocio.de("la nota no tiene guion: cárguelo con GUINOT");
        }
        cambiarEstado(ctx, id, "en_revision", "envio", null);
        return Resultado.exito("Notas", "nota enviada a revisión").ficha(ctx.requerir(NOTAS + " WHERE n.id = ?", id));
    }

    private static Resultado aprnot(Contexto ctx) throws SQLException, ErrorNegocio {
        int id = ctx.p().entero("id");
        enRevision(ctx, id);
        cambiarEstado(ctx, id, "aprobada", "aprobacion", null);
        return Resultado.exito("Notas", "nota aprobada").ficha(ctx.requerir(NOTAS + " WHERE n.id = ?", id))
                .parrafo("Ya puede entrar a la escaleta (INSITE).");
    }

    private static Resultado obsnot(Contexto ctx) throws SQLException, ErrorNegocio {
        int id = ctx.p().entero("id");
        String motivo = ctx.p().texto("motivo");
        if (motivo == null) {
            throw ErrorNegocio.de("indique el motivo de la observación");  // A4
        }
        enRevision(ctx, id);
        cambiarEstado(ctx, id, "observada", "observacion", motivo);
        return Resultado.exito("Notas", "nota observada").ficha(ctx.requerir(NOTAS + " WHERE n.id = ?", id))
                .parrafo("Motivo: " + motivo + ". El autor corrige con GUINOT o UPDNOT y la vuelve a enviar con ENVNOT.");
    }

    private static void enRevision(Contexto ctx, int id) throws SQLException, ErrorNegocio {
        Fila n = ctx.requerir("SELECT estado FROM nota WHERE id = ?", id);
        if (!"en_revision".equals(n.texto("estado"))) {
            throw ErrorNegocio.de("la nota no está en revisión");  // A3
        }
    }

    private static void cambiarEstado(Contexto ctx, int id, String estado, String accion, String motivo) throws SQLException {
        ctx.ejecutar("UPDATE nota SET estado = ?, actualizado_en = CURRENT_TIMESTAMP WHERE id = ?", estado, id);
        ctx.ejecutar("INSERT INTO revision_nota (nota_id, usuario_id, accion, motivo, fecha_hora) VALUES (?, ?, ?, ?, LOCALTIMESTAMP(0))",
                id, ctx.remitente().usuarioId(), accion, motivo);
    }

    // =================================================================== rótulos

    private static final String ROTULOS = """
            SELECT r.id AS "Id", r.orden AS "Orden", r.nombre AS "Nombre", r.cargo AS "Cargo"
            FROM nota_rotulo r
            """;

    // =================================================================== material audiovisual

    private static final String MATERIAL = """
            SELECT m.id AS "Id", n.id AS "Nota", n.slug AS "Slug", m.descripcion AS "Descripción", m.tipo AS "Tipo",
                   %s AS "Duración", m.ubicacion AS "Ubicación", m.estado AS "Estado",
                   u.nombre || ' ' || u.apellido AS "Editor"
            FROM material_audiovisual m
            JOIN nota n ON n.id = m.nota_id
            LEFT JOIN personal pe ON pe.id = m.editor_id
            LEFT JOIN usuario u ON u.id = pe.usuario_id
            """.formatted(Formato.mmss("m.duracion"));

    // =================================================================== reglas comunes

    /**
     * La nota existe, todavía se puede cambiar (no está emitida ni de baja) y el remitente es su
     * autor o el Jefe de Prensa (A5).
     */
    private static Fila editable(Contexto ctx, int id) throws SQLException, ErrorNegocio {
        Fila n = ctx.requerir("SELECT id, slug, estado, autor_id, fecha, palabras FROM nota WHERE id = ?", id);
        Remitente yo = ctx.remitente();
        if (!yo.esJefePrensa() && !n.entero("autor_id").equals(yo.personalId())) {
            throw ErrorNegocio.de("solo el autor o el Jefe de Prensa pueden modificarla");  // A5
        }
        noCerrada(n.texto("estado"));
        return n;
    }

    private static void noCerrada(String estado) throws ErrorNegocio {
        if (estado.equals("emitida") || estado.equals("baja")) {
            throw ErrorNegocio.de("la nota está " + estado + ": ya no se modifica");
        }
    }

    /** El estado como se lee: {@code en_revision} → «en revisión». */
    private static String mostrar(String estado) {
        return estado.equals("en_revision") ? "en revisión" : estado.replace('_', ' ');
    }

    /** A2: el guion va en el cuerpo del correo. */
    private static String guion(Contexto ctx) throws ErrorNegocio {
        String g = ctx.cuerpo().strip();
        if (g.isEmpty()) {
            throw ErrorNegocio.de("el guion va en el cuerpo del correo");
        }
        return g;
    }

    /** Palabras del guion, igual que la base: secuencias separadas por espacios. */
    static int contarPalabras(String guion) {
        String t = guion.strip();
        return t.isEmpty() ? 0 : t.split("\\s+").length;
    }

    /** El slug en mayúsculas, sin tildes y con guiones: «Paro transporte» → {@code PARO-TRANSPORTE}. */
    static String slug(String texto) throws ErrorNegocio {
        String s = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT).strip().replaceAll("[\\s_]+", "-");
        if (!s.matches("^[A-Z0-9][A-Z0-9-]*$")) {
            throw ErrorNegocio.de("el slug lleva letras, números y guiones, por ejemplo PARO-TRANSPORTE (llegó «" + texto + "»)");
        }
        return s;
    }

    private static void slugLibre(Contexto ctx, String slug, LocalDate fecha, int excepto) throws SQLException, ErrorNegocio {
        if (ctx.existe("SELECT 1 FROM nota WHERE slug = ? AND fecha = ? AND id <> ?", slug, fecha, excepto)) {
            throw ErrorNegocio.de("ya existe la nota " + slug + " para esa fecha");  // A1, R13
        }
    }

    private static void coberturaExistente(Contexto ctx, Integer idCobertura) throws SQLException, ErrorNegocio {
        if (idCobertura != null && !ctx.existe("SELECT 1 FROM cobertura WHERE id = ?", idCobertura)) {
            throw ErrorNegocio.de("la cobertura indicada no existe");  // punto de extensión «Vincular cobertura»
        }
    }

    private static int guardarVersion(Contexto ctx, int idNota, String guion, int palabras) throws SQLException, ErrorNegocio {
        return ctx.devolviendo("""
                INSERT INTO nota_version (nota_id, numero, guion, palabras, usuario_id, fecha_hora)
                SELECT ?, COALESCE(max(numero), 0) + 1, ?, ?, ?, LOCALTIMESTAMP(0) FROM nota_version WHERE nota_id = ?
                RETURNING numero
                """, idNota, guion, palabras, ctx.remitente().usuarioId(), idNota).entero("numero");
    }

    /** Escaletas en borrador o cerradas donde la nota está programada. */
    private static List<Fila> escaletasDe(Contexto ctx, int idNota) throws SQLException {
        return ctx.consultar("""
                SELECT i.escaleta_id FROM item_escaleta i JOIN escaleta e ON e.id = i.escaleta_id
                WHERE i.nota_id = ? AND i.estado = 'programado' AND e.estado IN ('borrador', 'cerrada')
                ORDER BY e.fecha
                """, idNota);
    }
}
