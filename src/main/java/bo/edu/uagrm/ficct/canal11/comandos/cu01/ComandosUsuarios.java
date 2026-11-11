package bo.edu.uagrm.ficct.canal11.comandos.cu01;

import at.favre.lib.crypto.bcrypt.BCrypt;
import bo.edu.uagrm.ficct.canal11.datos.Fila;
import bo.edu.uagrm.ficct.canal11.datos.TablaCrud;
import bo.edu.uagrm.ficct.canal11.comandos.Contexto;
import bo.edu.uagrm.ficct.canal11.comandos.ErrorNegocio;
import bo.edu.uagrm.ficct.canal11.comandos.Remitente;
import bo.edu.uagrm.ficct.canal11.comandos.Resultado;
import bo.edu.uagrm.ficct.canal11.comandos.Sql;
import bo.edu.uagrm.ficct.canal11.comandos.catalogo.CommandSpec;
import bo.edu.uagrm.ficct.canal11.comandos.catalogo.Parametros;
import bo.edu.uagrm.ficct.canal11.comandos.catalogo.Tipo;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CU-01 Gestión de Usuarios: usuarios, roles y permisos (Tabla 79 del Anexo A).
 *
 * <p>Reglas propias, además de las de la base:</p>
 * <ul>
 *   <li>LISUSU: solo el Director y el Jefe de Prensa ven a todos; los demás roles reciben sus
 *       propios datos (flujo principal, paso 7).</li>
 *   <li>La contraseña se guarda con bcrypt {@code $2y$} costo 10 (D5) y nunca se muestra.</li>
 *   <li>Siempre queda al menos un Director activo, y nadie se desactiva a sí mismo: si no, nadie
 *       podría volver a administrar el sistema por correo.</li>
 *   <li>Los roles Director y Jefe de Prensa no se renombran ni se eliminan (el sistema los usa por
 *       su nombre), y el Director no pierde los comandos que administran los permisos.</li>
 *   <li>Los permisos son los comandos del catálogo: vienen cargados en la base y se otorgan o quitan
 *       con INSRPM y DELRPM.</li>
 * </ul>
 */
public final class ComandosUsuarios {

    private static final String CU = "CU-01";
    private static final Tipo ESTADO = Tipo.opcion("activo", "inactivo");
    private static final int COSTO_BCRYPT = 10;

    /** Roles que ven a todos los usuarios con LISUSU. */
    private static final Set<String> VEN_TODOS = Set.of(Remitente.DIRECTOR, Remitente.JEFE_PRENSA);
    /** Sin estos permisos, el Director no podría volver a otorgar ninguno. */
    private static final Set<String> ADMINISTRACION = Set.of("INSRPM", "DELRPM", "LISRPM");
    /** Roles que trabajan en prensa: después de registrarlos, el Jefe de Prensa les hace la ficha. */
    private static final Set<String> ROLES_DE_PRENSA = Set.of("Jefe de Prensa", "Periodista", "Camarografo", "Editor de video", "Presentador");

    private ComandosUsuarios() {
    }

    public static List<CommandSpec> comandos() {
        return List.of(
                CommandSpec.de(CU, "LISUSU", "Listar usuarios (Periodista, Camarógrafo, Editor y Presentador solo reciben sus propios datos)")
                        .param("rol", Tipo.FILTRO)
                        .ejemplo("LISUSU[\"*\"]").accion(ComandosUsuarios::lisusu).build(),
                CommandSpec.de(CU, "INSUSU", "Registrar usuario")
                        .param("nombre", Tipo.texto(100)).param("apellido", Tipo.texto(100)).param("ci", Tipo.texto(20))
                        .param("correo", Tipo.CORREO).opcional("telefono", Tipo.texto(20)).param("password", Tipo.CONTRASENA)
                        .param("id_rol", Tipo.ID)
                        .ejemplo("INSUSU[\"Carla\",\"Rojas Suárez\",\"3100003\",\"carla.rojas@gmail.com\",\"71234580\",\"clave123\",\"3\"]")
                        .accion(ComandosUsuarios::insusu).build(),
                CommandSpec.de(CU, "UPDUSU", "Modificar usuario")
                        .param("id", Tipo.ID).opcional("nombre", Tipo.texto(100)).opcional("apellido", Tipo.texto(100))
                        .opcional("correo", Tipo.CORREO).opcional("telefono", Tipo.texto(20)).opcional("id_rol", Tipo.ID)
                        .omitible("estado", ESTADO)
                        .ejemplo("UPDUSU[\"3\",\"Carla\",\"Rojas Suárez\",\"carla.rojas@gmail.com\",\"71234580\",\"3\"]")
                        .accion(ComandosUsuarios::updusu).build(),
                CommandSpec.de(CU, "DELUSU", "Desactivar usuario")
                        .param("id", Tipo.ID)
                        .ejemplo("DELUSU[\"3\"]").accion(ComandosUsuarios::delusu).build(),
                CommandSpec.de(CU, "LISROL", "Listar roles")
                        .param("filtro", Tipo.FILTRO)
                        .ejemplo("LISROL[\"*\"]").accion(ComandosUsuarios::lisrol).build(),
                CommandSpec.de(CU, "LISRPM", "Listar los permisos de un rol")
                        .param("id_rol", Tipo.ID_O_TODOS)
                        .ejemplo("LISRPM[\"6\"]").accion(ComandosUsuarios::lisrpm).build(),
                CommandSpec.de(CU, "INSRPM", "Otorgar un comando a un rol")
                        .param("id_rol", Tipo.ID).param("comando", Tipo.COMANDO)
                        .ejemplo("INSRPM[\"6\",\"HOJCOB\"]").accion(ComandosUsuarios::insrpm).build(),
                CommandSpec.de(CU, "DELRPM", "Quitar un comando a un rol")
                        .param("id_rol", Tipo.ID).param("comando", Tipo.COMANDO)
                        .ejemplo("DELRPM[\"6\",\"HOJCOB\"]").accion(ComandosUsuarios::delrpm).build());
    }

    // =================================================================== usuarios

    private static final String USUARIOS = """
            SELECT u.id AS "Id", u.nombre || ' ' || u.apellido AS "Nombre", u.ci AS "CI", u.correo AS "Correo",
                   u.telefono AS "Teléfono", r.nombre AS "Rol", u.estado AS "Estado"
            FROM usuario u JOIN rol r ON r.id = u.rol_id
            """;

    private static Resultado lisusu(Contexto ctx) throws SQLException {
        Remitente r = ctx.remitente();
        if (!VEN_TODOS.contains(r.rol())) {
            List<Fila> propios = ctx.consultar(USUARIOS + " WHERE u.id = ?", r.usuarioId());
            return Resultado.listado("Sus datos", propios, "No se encontraron sus datos.")
                    .parrafo("Su rol solo puede ver sus propios datos.");
        }
        String filtro = ctx.p().texto("rol");
        List<Fila> filas;
        if (filtro == null) {
            filas = ctx.consultar(USUARIOS + " ORDER BY u.id");
        } else if (filtro.matches("\\d+")) {
            filas = ctx.consultar(USUARIOS + " WHERE r.id = ? ORDER BY u.id", Integer.parseInt(filtro));
        } else if (Sql.normal(filtro).equals("activo") || Sql.normal(filtro).equals("inactivo")) {
            filas = ctx.consultar(USUARIOS + " WHERE u.estado = ? ORDER BY u.id", Sql.normal(filtro));
        } else {
            filas = ctx.consultar(USUARIOS + " WHERE " + Sql.sinTildes("r.nombre") + " LIKE ? ORDER BY u.id", Sql.contiene(filtro));
        }
        return Resultado.listado("Usuarios", filas, "No hay usuarios con ese filtro. Use \"*\", el nombre o el id de un rol, o activo/inactivo.");
    }

    private static Resultado insusu(Contexto ctx) throws SQLException, ErrorNegocio {
        Parametros p = ctx.p();
        correoLibre(ctx, p.texto("correo"), 0);
        Fila rol = ctx.uno("SELECT id, nombre FROM rol WHERE id = ?", p.entero("id_rol"))
                .orElseThrow(() -> ErrorNegocio.de("el rol indicado no existe"));
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("nombre", p.texto("nombre"));
        v.put("apellido", p.texto("apellido"));
        v.put("ci", p.texto("ci"));
        v.put("correo", p.texto("correo"));
        v.put("telefono", p.texto("telefono"));
        v.put("password", hash(p.texto("password")));
        v.put("rol_id", rol.entero("id"));
        int id = TablaCrud.insertar(ctx.repositorio(), ctx.conexion(), "usuario", v);
        Resultado r = Resultado.exito("Usuarios", "usuario registrado").ficha(usuario(ctx, id));
        if (ROLES_DE_PRENSA.contains(rol.texto("nombre"))) {
            r.parrafo("Siguiente paso: el Jefe de Prensa registra su ficha de personal con INSPER[\"" + id
                    + "\",\"cargo\",\"condicion_laboral\",\"id_turno\",\"\",\"fecha_ingreso\"].");
        }
        return r;
    }

    private static Resultado updusu(Contexto ctx) throws SQLException, ErrorNegocio {
        Parametros p = ctx.p();
        p.requerirAlguno("nombre", "apellido", "correo", "telefono", "id_rol", "estado");
        int id = p.entero("id");
        Fila actual = ctx.requerir("SELECT u.id, u.estado, r.nombre AS rol FROM usuario u JOIN rol r ON r.id = u.rol_id WHERE u.id = ?", id);
        if (p.tiene("correo")) {
            correoLibre(ctx, p.texto("correo"), id);
        }
        if (p.tiene("id_rol") && !ctx.existe("SELECT 1 FROM rol WHERE id = ?", p.entero("id_rol"))) {
            throw ErrorNegocio.de("el rol indicado no existe");
        }
        boolean desactiva = "inactivo".equals(p.texto("estado"));
        boolean dejaDeSerDirector = Remitente.DIRECTOR.equals(actual.texto("rol")) && p.tiene("id_rol")
                && !ctx.existe("SELECT 1 FROM rol WHERE id = ? AND nombre = ?", p.entero("id_rol"), Remitente.DIRECTOR);
        if (id == ctx.remitente().usuarioId() && (desactiva || dejaDeSerDirector)) {
            throw ErrorNegocio.de("no puede desactivarse ni quitarse el rol a sí mismo; pídaselo a otro Director");
        }
        if (Remitente.DIRECTOR.equals(actual.texto("rol")) && (desactiva || dejaDeSerDirector)) {
            quedaOtroDirector(ctx, id);
        }
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("nombre", p.texto("nombre"));
        v.put("apellido", p.texto("apellido"));
        v.put("correo", p.texto("correo"));
        v.put("telefono", p.texto("telefono"));
        v.put("rol_id", p.entero("id_rol"));
        v.put("estado", p.texto("estado"));
        TablaCrud.actualizar(ctx.repositorio(), ctx.conexion(), "usuario", id, v);
        return Resultado.exito("Usuarios", "usuario modificado").ficha(usuario(ctx, id));
    }

    private static Resultado delusu(Contexto ctx) throws SQLException, ErrorNegocio {
        int id = ctx.p().entero("id");
        Fila actual = ctx.requerir("SELECT u.estado, r.nombre AS rol FROM usuario u JOIN rol r ON r.id = u.rol_id WHERE u.id = ?", id);
        if (id == ctx.remitente().usuarioId()) {
            throw ErrorNegocio.de("no puede desactivarse a sí mismo; pídaselo a otro Director");
        }
        if ("inactivo".equals(actual.texto("estado"))) {
            throw ErrorNegocio.de("el usuario ya está inactivo");
        }
        if (Remitente.DIRECTOR.equals(actual.texto("rol"))) {
            quedaOtroDirector(ctx, id);
        }
        TablaCrud.cambiarEstado(ctx.repositorio(), ctx.conexion(), "usuario", id, "inactivo");
        return Resultado.exito("Usuarios", "usuario desactivado").ficha(usuario(ctx, id))
                .parrafo("El usuario no se elimina, para conservar su historial. Desde ahora sus correos se rechazan. "
                        + "Para reactivarlo: UPDUSU[\"" + id + "\",\"\",\"\",\"\",\"\",\"\",\"activo\"].");
    }

    private static Fila usuario(Contexto ctx, int id) throws SQLException, ErrorNegocio {
        return ctx.requerir(USUARIOS + " WHERE u.id = ?", id);
    }

    /** El correo no puede repetirse, tampoco con otras mayúsculas (A2). */
    private static void correoLibre(Contexto ctx, String correo, int excepto) throws SQLException, ErrorNegocio {
        if (ctx.existe("SELECT 1 FROM usuario WHERE lower(correo) = lower(?) AND id <> ?", correo, excepto)) {
            throw ErrorNegocio.de("el correo ya está registrado");
        }
    }

    private static void quedaOtroDirector(Contexto ctx, int sinEste) throws SQLException, ErrorNegocio {
        if (!ctx.existe("SELECT 1 FROM usuario u JOIN rol r ON r.id = u.rol_id WHERE r.nombre = ? AND u.estado = 'activo' AND u.id <> ?",
                Remitente.DIRECTOR, sinEste)) {
            throw ErrorNegocio.de("debe quedar al menos un Director activo para administrar el sistema");
        }
    }

    /** bcrypt {@code $2y$}, costo 10 (D5): compatible con PHP/Laravel del hito web. */
    static String hash(String contrasena) {
        return BCrypt.with(BCrypt.Version.VERSION_2Y).hashToString(COSTO_BCRYPT, contrasena.toCharArray());
    }

    // =================================================================== roles

    private static final String ROLES = """
            SELECT r.id AS "Id", r.nombre AS "Rol", r.descripcion AS "Descripción",
                   (SELECT count(*) FROM usuario u WHERE u.rol_id = r.id AND u.estado = 'activo') AS "Usuarios activos",
                   (SELECT count(*) FROM rol_permiso rp WHERE rp.rol_id = r.id) AS "Comandos"
            FROM rol r
            """;

    private static Resultado lisrol(Contexto ctx) throws SQLException {
        String filtro = ctx.p().texto("filtro");
        List<Fila> filas = filtro == null
                ? ctx.consultar(ROLES + " ORDER BY r.id")
                : ctx.consultar(ROLES + " WHERE " + Sql.sinTildes("r.nombre") + " LIKE ? ORDER BY r.id", Sql.contiene(filtro));
        return Resultado.listado("Roles", filas, "No hay roles con ese nombre.");
    }

    // =================================================================== permisos

    private static final String PERMISOS_DEL_ROL = """
            SELECT p.comando AS "Comando", p.descripcion AS "Descripción", p.caso_uso AS "Caso de uso"
            FROM rol_permiso rp JOIN permiso p ON p.id = rp.permiso_id
            WHERE rp.rol_id = ?
            ORDER BY p.id
            """;

    private static Resultado lisrpm(Contexto ctx) throws SQLException, ErrorNegocio {
        Integer idRol = ctx.p().entero("id_rol");
        if (idRol == null) {
            List<Fila> resumen = ctx.consultar("""
                    SELECT r.id AS "Id", r.nombre AS "Rol", count(rp.id) AS "Cantidad",
                           string_agg(p.comando, ', ' ORDER BY p.id) AS "Comandos"
                    FROM rol r
                    LEFT JOIN rol_permiso rp ON rp.rol_id = r.id
                    LEFT JOIN permiso p ON p.id = rp.permiso_id
                    GROUP BY r.id, r.nombre ORDER BY r.id
                    """);
            return Resultado.listado("Permisos por rol", resumen, "No hay roles.");
        }
        Fila rol = ctx.uno("SELECT nombre FROM rol WHERE id = ?", idRol).orElseThrow(() -> ErrorNegocio.de("el rol indicado no existe"));
        return Resultado.listado("Permisos del rol " + rol.texto("nombre"), ctx.consultar(PERMISOS_DEL_ROL, idRol),
                "El rol " + rol.texto("nombre") + " no tiene comandos asignados.");
    }

    private static Resultado insrpm(Contexto ctx) throws SQLException, ErrorNegocio {
        Fila rol = rolExistente(ctx);
        String comando = ctx.p().texto("comando");
        Fila permiso = ctx.uno("SELECT id, descripcion FROM permiso WHERE comando = ?", comando)
                .orElseThrow(() -> ErrorNegocio.de("el comando " + comando + " no existe en el catálogo del sistema"));
        if (ctx.existe("SELECT 1 FROM rol_permiso WHERE rol_id = ? AND permiso_id = ?", rol.entero("id"), permiso.entero("id"))) {
            throw ErrorNegocio.de("el rol " + rol.texto("nombre") + " ya tiene el permiso de " + comando);
        }
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("rol_id", rol.entero("id"));
        v.put("permiso_id", permiso.entero("id"));
        TablaCrud.insertar(ctx.repositorio(), ctx.conexion(), "rol_permiso", v);
        return Resultado.exito("Permisos", "permiso otorgado")
                .parrafo("El rol " + rol.texto("nombre") + " ya puede usar " + comando + " (" + permiso.texto("descripcion")
                        + ") y lo ve en su AYUDA.");
    }

    private static Resultado delrpm(Contexto ctx) throws SQLException, ErrorNegocio {
        Fila rol = rolExistente(ctx);
        String comando = ctx.p().texto("comando");
        if (Remitente.DIRECTOR.equals(rol.texto("nombre")) && ADMINISTRACION.contains(comando)) {
            throw ErrorNegocio.de("al Director no se le puede quitar " + comando + ": sin él no se podrían restaurar los permisos");
        }
        int borrados = ctx.ejecutar("""
                DELETE FROM rol_permiso
                WHERE rol_id = ? AND permiso_id = (SELECT id FROM permiso WHERE comando = ?)
                """, rol.entero("id"), comando);
        if (borrados == 0) {
            throw ErrorNegocio.de("el rol " + rol.texto("nombre") + " no tiene el permiso de " + comando);
        }
        return Resultado.exito("Permisos", "permiso quitado")
                .parrafo("El rol " + rol.texto("nombre") + " ya no puede usar " + comando + ".");
    }

    private static Fila rolExistente(Contexto ctx) throws SQLException, ErrorNegocio {
        return ctx.uno("SELECT id, nombre FROM rol WHERE id = ?", ctx.p().entero("id_rol"))
                .orElseThrow(() -> ErrorNegocio.de("el rol indicado no existe"));
    }
}
