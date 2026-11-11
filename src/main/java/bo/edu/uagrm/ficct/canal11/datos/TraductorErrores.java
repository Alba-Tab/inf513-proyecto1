package bo.edu.uagrm.ficct.canal11.datos;

import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Traduce un error de PostgreSQL a un mensaje para el usuario, con el estilo de la Tabla 77
 * («Error: …»). Decisión D13.
 *
 * <p>Busca primero por el nombre de la restricción violada (todas las del esquema tienen su
 * mensaje; una prueba lo verifica contra {@code pg_constraint}). Si no hay restricción, usa el
 * código SQLSTATE. <b>Nunca</b> devuelve el texto original de PostgreSQL: ese va al log.</p>
 */
public final class TraductorErrores {

    /** Qué intentaba hacer el comando. Decide el mensaje de una clave foránea violada. */
    public enum Operacion {
        CONSULTAR, REGISTRAR, MODIFICAR, ELIMINAR;

        /** Operación según las tres primeras letras del código del comando (LIS, INS, UPD, DEL…). */
        public static Operacion delComando(String codigo) {
            String op = codigo == null || codigo.length() < 3 ? "" : codigo.substring(0, 3).toUpperCase();
            return switch (op) {
                case "INS", "DUP" -> REGISTRAR;
                case "DEL" -> ELIMINAR;
                case "LIS", "DIS", "HOJ", "EXP", "REP", "ANA" -> CONSULTAR;
                default -> MODIFICAR;
            };
        }
    }

    /** Mensajes de una clave foránea: al registrar con una referencia inexistente y al borrar algo en uso. */
    private record ClaveForanea(String noExiste, String enUso) {
    }

    private static final String PREFIJO = "Error: ";
    private static final String TIENE_DATOS_ASOCIADOS = "el registro tiene datos asociados y no se puede eliminar";

    private static final Map<String, String> RESTRICCIONES = new HashMap<>();
    private static final Map<String, ClaveForanea> CLAVES_FORANEAS = new HashMap<>();

    static {
        // ---- CU-01 Usuarios, roles y permisos
        r("usuario_correo_key", "el correo ya está registrado");
        r("usuario_ci_key", "ese CI ya está registrado");
        r("usuario_estado_check", "el estado debe ser activo o inactivo");
        fk("usuario_rol_id_fkey", "el rol indicado no existe", "el rol tiene usuarios asignados");
        r("rol_nombre_key", "ya existe un rol con ese nombre");
        r("permiso_comando_key", "ese comando ya está registrado como permiso");
        r("permiso_caso_uso_check", "el caso de uso debe ser de CU-01 a CU-08");
        r("rol_permiso_rol_id_permiso_id_key", "el rol ya tiene ese permiso");
        fk("rol_permiso_rol_id_fkey", "el rol indicado no existe", null);
        fk("rol_permiso_permiso_id_fkey", "el permiso indicado no existe", null);

        // ---- CU-02 Personal, turnos y ausencias
        r("turno_nombre_key", "ya existe un turno con ese nombre");
        r("turno_check", "la hora de inicio y la de fin del turno no pueden ser iguales");
        r("turno_dias_check", "los días van de 1 (lunes) a 7 (domingo), separados por comas; por ejemplo 1,2,3,4,5");
        r("personal_usuario_id_key", "el usuario ya está registrado como personal");
        r("personal_cargo_check", "el cargo debe ser periodista, camarografo, editor, presentador, conductor o jefe_prensa");
        r("personal_condicion_laboral_check", "la condición laboral debe ser planta, contrato o practicante");
        r("personal_estado_check", "el estado debe ser activo o inactivo");
        fk("personal_usuario_id_fkey", "el usuario indicado no existe", "el usuario tiene una ficha de personal");
        fk("personal_turno_id_fkey", "el turno no existe", "el turno tiene personal asignado");
        r("ausencia_tipo_check", "el tipo de ausencia debe ser permiso, vacacion, baja_medica o comision");
        r("ck_ausencia_rango", "el fin debe ser posterior al inicio");
        fk("ausencia_personal_id_fkey", "la persona indicada no existe", null);

        // ---- CU-03 Recursos, préstamos y mantenimiento
        r("tipo_recurso_nombre_key", "ya existe un tipo de recurso con ese nombre");
        r("recurso_codigo_key", "ese código de recurso ya existe");
        r("recurso_placa_key", "esa placa ya está registrada");
        r("recurso_estado_check", "el estado del recurso debe ser operativo, mantenimiento o baja");
        fk("recurso_tipo_recurso_id_fkey", "el tipo de recurso no existe", "el tipo tiene recursos registrados");
        r("prestamo_check", "la devolución no puede ser anterior a la entrega");
        r("ux_prestamo_abierto", "el recurso ya está entregado y no fue devuelto");
        fk("prestamo_recurso_id_fkey", "el recurso indicado no existe", "el recurso tiene préstamos registrados");
        fk("prestamo_responsable_id_fkey", "el responsable indicado no existe", "la persona tiene préstamos registrados");
        fk("prestamo_cobertura_id_fkey", "la cobertura indicada no existe", null);
        r("mantenimiento_tipo_check", "el tipo de evento debe ser incidencia, alta o baja");
        fk("mantenimiento_recurso_id_fkey", "el recurso indicado no existe",
                "el recurso tiene historial de mantenimiento y no se puede borrar; para darlo de baja use DELREC");
        fk("mantenimiento_usuario_id_fkey", "el usuario indicado no existe", null);
        fk("mantenimiento_cobertura_id_fkey", "la cobertura indicada no existe", null);

        // ---- CU-04 Coberturas
        r("ck_cobertura_rango", "la cobertura debe terminar después de empezar");
        r("ck_cobertura_retorno", "el retorno no puede ser anterior al inicio de la cobertura");
        r("cobertura_estado_check", "el estado de la cobertura debe ser planificada, en_curso, cumplida, cancelada o reprogramada");
        r("cobertura_prioridad_check", "la prioridad debe ser alta, media o baja");
        r("cobertura_tipo_check", "el tipo de cobertura debe ser conferencia, hecho_noticioso, deportes, acto_institucional o entrevista");
        fk("cobertura_cobertura_origen_id_fkey", "la cobertura de origen no existe", null);
        fk("cobertura_creado_por_fkey", "el usuario que planifica no existe", "el usuario planificó coberturas");
        r("cobertura_personal_cobertura_id_personal_id_key", "esa persona ya está asignada a la cobertura");
        r("cobertura_personal_funcion_check", "la función debe ser periodista, camarografo o conductor");
        fk("cobertura_personal_cobertura_id_fkey", "la cobertura indicada no existe", null);
        fk("cobertura_personal_personal_id_fkey", "la persona indicada no existe", "la persona tiene coberturas asignadas");
        r("cobertura_recurso_cobertura_id_recurso_id_key", "ese recurso ya está reservado para la cobertura");
        fk("cobertura_recurso_cobertura_id_fkey", "la cobertura indicada no existe", null);
        fk("cobertura_recurso_recurso_id_fkey", "el recurso indicado no existe", "el recurso tiene reservas en coberturas");

        // ---- CU-05 Notas
        r("nota_slug_fecha_key", "ya existe una nota con ese slug para esa fecha");
        r("nota_estado_check", "el estado de la nota debe ser borrador, en_revision, observada, aprobada, emitida o baja");
        r("nota_tipo_pieza_check", "el tipo de pieza debe ser lectura, vo, vo_sot, nota_completa, enlace_vivo o entrevista_estudio");
        r("nota_palabras_check", "la cantidad de palabras no puede ser negativa");
        r("nota_palabras_por_minuto_check", "el ritmo de lectura va de 80 a 250 palabras por minuto");
        fk("nota_autor_id_fkey", "el autor indicado no existe", "la persona es autora de notas");
        fk("nota_cobertura_id_fkey", "la cobertura indicada no existe", null);
        r("uq_rotulo_orden", "la nota ya tiene un rótulo en esa posición");
        fk("nota_rotulo_nota_id_fkey", "la nota indicada no existe", null);
        r("nota_version_nota_id_numero_key", "esa versión del guion ya existe");
        fk("nota_version_nota_id_fkey", "la nota indicada no existe", null);
        fk("nota_version_usuario_id_fkey", "el usuario indicado no existe", "el usuario tiene versiones de guion registradas");
        r("revision_nota_accion_check", "la acción debe ser envio, aprobacion u observacion");
        r("revision_nota_check", "indique el motivo de la observación");
        fk("revision_nota_nota_id_fkey", "la nota indicada no existe", null);
        fk("revision_nota_usuario_id_fkey", "el usuario indicado no existe", "el usuario tiene revisiones registradas");
        r("material_audiovisual_estado_check", "el estado del material debe ser pendiente, ingestado, en_edicion o listo");
        r("material_audiovisual_tipo_check", "el tipo de material debe ser video, sot, grafico, foto o audio");
        r("material_audiovisual_duracion_check", "la duración no puede ser negativa");
        fk("material_audiovisual_nota_id_fkey", "la nota indicada no existe", null);
        fk("material_audiovisual_editor_id_fkey", "el editor indicado no existe", null);

        // ---- CU-06 Programas, grilla, plantillas y escaleta
        r("programa_nombre_key", "ya existe un programa con ese nombre");
        r("programa_tipo_check", "el tipo de programa debe ser noticiero, especial u otro");
        r("programa_estado_check", "el estado del programa debe ser activo o inactivo");
        r("ck_grilla_horas", "la hora de fin debe ser posterior a la de inicio: un horario no cruza la medianoche (como máximo hasta 24:00)");
        r("grilla_check", "la vigencia termina antes de empezar");
        r("grilla_dia_semana_check", "el día de la semana va de 1 (lunes) a 7 (domingo)");
        r("grilla_estado_check", "el estado del horario debe ser vigente o inactiva");
        fk("grilla_programa_id_fkey", "el programa indicado no existe", "el programa tiene horarios en la grilla");
        r("plantilla_nombre_key", "ya existe una plantilla con ese nombre");
        r("plantilla_cantidad_bloques_check", "la cantidad de bloques va de 1 a 10");
        r("plantilla_duracion_total_check", "la duración debe ser mayor que cero");
        r("plantilla_estado_check", "el estado de la plantilla debe ser vigente o inactiva");
        r("plantilla_palabras_por_minuto_check", "el ritmo de lectura va de 80 a 250 palabras por minuto");
        fk("plantilla_programa_id_fkey", "el programa indicado no existe", "el programa tiene plantillas");
        r("plantilla_corte_plantilla_id_numero_key", "la plantilla ya tiene un corte con ese número");
        r("plantilla_corte_numero_check", "el número de corte debe ser mayor que cero");
        r("plantilla_corte_minuto_inicio_check", "el minuto de inicio del corte debe ser mayor que cero");
        r("plantilla_corte_duracion_check", "la duración del corte debe ser mayor que cero");
        fk("plantilla_corte_plantilla_id_fkey", "la plantilla indicada no existe", null);
        r("escaleta_grilla_id_fecha_key", "ya existe una escaleta para ese horario y esa fecha");
        r("escaleta_estado_check", "el estado de la escaleta debe ser borrador, cerrada o emitida");
        r("escaleta_duracion_total_check", "la duración de la escaleta debe ser mayor que cero");
        fk("escaleta_grilla_id_fkey", "el horario de grilla indicado no existe", "el horario de grilla tiene escaletas");
        fk("escaleta_plantilla_id_fkey", "la plantilla indicada no existe", "la plantilla tiene escaletas");
        fk("escaleta_responsable_id_fkey", "el responsable indicado no existe", "el usuario es responsable de escaletas");
        r("uq_item_bloque_orden", "ya hay un ítem en esa posición del bloque");
        r("ux_item_corte", "ese bloque ya tiene su corte comercial");
        r("ux_item_nota", "esa nota ya está en la escaleta");
        r("ck_item_orden", "el orden va de 1 a 998 (el 999 es del corte comercial)");
        r("ck_item_corte_vacio", "un corte comercial no lleva nota ni presentador");
        r("item_escaleta_check", "un ítem de tipo nota debe indicar la nota");
        r("item_escaleta_check1", "el ítem necesita una nota o un título");
        r("item_escaleta_bloque_check", "el bloque va de A a J");
        r("item_escaleta_tipo_check", "el tipo de ítem debe ser apertura, titulares, nota, enlace_vivo, entrevista, corte, despedida u otro");
        r("item_escaleta_estado_check", "el estado del ítem debe ser programado, flotado, baja o emitido");
        r("item_escaleta_duracion_estimada_check", "la duración no puede ser negativa");
        r("item_escaleta_duracion_real_check", "la duración real no puede ser negativa");
        fk("item_escaleta_escaleta_id_fkey", "la escaleta indicada no existe", null);
        fk("item_escaleta_nota_id_fkey", "la nota indicada no existe", "la nota está en una escaleta");
        fk("item_escaleta_presentador_id_fkey", "el presentador indicado no existe", null);

        // ---- RNF-24 Bitácora
        r("bitacora_comando_resultado_check", "el resultado de la bitácora debe ser exito o error");
        fk("bitacora_comando_usuario_id_fkey", "el usuario indicado no existe", null);
    }

    private TraductorErrores() {
    }

    private static void r(String restriccion, String mensaje) {
        RESTRICCIONES.put(restriccion, mensaje);
    }

    private static void fk(String restriccion, String noExiste, String enUso) {
        CLAVES_FORANEAS.put(restriccion, new ClaveForanea(noExiste, enUso));
    }

    /** Mensaje para el usuario, siempre con el prefijo «Error: ». */
    public static String traducir(SQLException e, Operacion operacion) {
        String restriccion = null;
        String columna = null;
        if (e instanceof PSQLException p && p.getServerErrorMessage() != null) {
            ServerErrorMessage sem = p.getServerErrorMessage();
            restriccion = sem.getConstraint();
            columna = sem.getColumn();
        }
        if (restriccion != null) {
            ClaveForanea fk = CLAVES_FORANEAS.get(restriccion);
            if (fk != null) {
                if (operacion == Operacion.ELIMINAR) {
                    return PREFIJO + (fk.enUso() != null ? fk.enUso() : TIENE_DATOS_ASOCIADOS);
                }
                return PREFIJO + fk.noExiste();
            }
            String mensaje = RESTRICCIONES.get(restriccion);
            if (mensaje != null) {
                return PREFIJO + mensaje;
            }
            if (restriccion.endsWith("_pkey")) {
                return PREFIJO + "el registro ya existe";
            }
        }
        return PREFIJO + porEstado(e, operacion, columna);
    }

    private static String porEstado(SQLException e, Operacion operacion, String columna) {
        String estado = e.getSQLState() == null ? "" : e.getSQLState();
        if (estado.startsWith("08") || estado.equals("57P01") || estado.equals("57P02") || estado.equals("57P03")
                || (estado.isEmpty() && e.getCause() instanceof IOException)) {
            return "el sistema no puede acceder a la base de datos en este momento; intente más tarde";
        }
        return switch (estado) {
            case "23505" -> "el registro ya existe";
            case "23503" -> operacion == Operacion.ELIMINAR
                    ? TIENE_DATOS_ASOCIADOS
                    : "un dato hace referencia a un registro que no existe";
            case "23514" -> "un dato no cumple las reglas del sistema";
            case "23502" -> columna == null ? "falta un dato obligatorio" : "falta el dato «" + columna.replace('_', ' ') + "»";
            case "22001" -> "un texto supera el largo permitido";
            case "22003" -> "un número está fuera del rango permitido";
            case "22007", "22008" -> "una fecha u hora no es válida";
            case "22P02" -> "un dato tiene un formato inválido";
            case "57014" -> "la operación tardó demasiado; intente de nuevo";
            case "40001", "40P01" -> "hubo un conflicto con otra operación; intente de nuevo";
            default -> "no se pudo completar la operación";
        };
    }

    /** Nombres de todas las restricciones que tienen mensaje propio (para la prueba contra el esquema). */
    static Set<String> restriccionesConocidas() {
        Set<String> todas = new HashSet<>(RESTRICCIONES.keySet());
        todas.addAll(CLAVES_FORANEAS.keySet());
        return Collections.unmodifiableSet(todas);
    }
}
