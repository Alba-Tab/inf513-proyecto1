-- =====================================================================
-- Sistema de Gestión de Operaciones y Noticias (NRCS) - Canal 11 TVU
-- Grupo 14 SA - Tecnología Web (INF513) - II/2026
-- Motor: PostgreSQL (probado en la 17)
-- Servidor: www.tecnoweb.org.bo  -  BD: db_grupo14sa
-- =====================================================================
-- Convención de valores de estado/tipo: minúsculas, sin tildes y con
-- guion bajo (ej. 'en_revision'), para que se escriban sin problemas en
-- el asunto de un correo.
-- Convención de rangos de tiempo: intervalo semiabierto [inicio, fin).
-- Dos rangos se solapan cuando inicio1 < fin2 AND inicio2 < fin1.
-- =====================================================================
-- CREATE DATABASE db_grupo14sa;   -- en tecnoweb la BD ya viene creada

DROP VIEW  IF EXISTS v_grilla_semanal, v_bloque_balance, v_escaleta_grilla, v_plantilla_control,
                     v_plantilla_bloque, v_nota_duracion, v_ocupacion_recurso, v_ocupacion_personal CASCADE;

DROP TABLE IF EXISTS bitacora_comando, item_escaleta, escaleta, plantilla_corte, plantilla, grilla, programa,
                     material_audiovisual, revision_nota, nota_version, nota_rotulo, nota,
                     cobertura_recurso, cobertura_personal, cobertura,
                     mantenimiento, prestamo, recurso, tipo_recurso,
                     ausencia, personal, turno, rol_permiso, permiso, usuario, rol CASCADE;

-- =====================================================================
-- CU-01 GESTIÓN DE USUARIOS
-- =====================================================================
-- Tabla: rol
CREATE TABLE rol (
    id              SERIAL PRIMARY KEY,
    nombre          VARCHAR(50)  NOT NULL UNIQUE,
    descripcion     VARCHAR(150),
    creado_en       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Tabla: usuario  (cuenta que puede enviar comandos por correo)
CREATE TABLE usuario (
    id              SERIAL PRIMARY KEY,
    nombre          VARCHAR(100) NOT NULL,
    apellido        VARCHAR(100) NOT NULL,
    ci              VARCHAR(20)  NOT NULL UNIQUE,
    correo          VARCHAR(150) NOT NULL UNIQUE,
    telefono        VARCHAR(20),
    password        VARCHAR(255) NOT NULL,  -- hash bcrypt ($2y$, costo 10)
    estado          VARCHAR(10)  NOT NULL DEFAULT 'activo'
                    CHECK (estado IN ('activo','inactivo')),
    rol_id          INTEGER      NOT NULL REFERENCES rol(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    creado_en       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Tabla: permiso  (cada comando del sistema que requiere autorización)
CREATE TABLE permiso (
    id              SERIAL PRIMARY KEY,
    comando         VARCHAR(10)  NOT NULL UNIQUE,  -- ej. APRNOT
    descripcion     VARCHAR(150) NOT NULL,
    caso_uso        VARCHAR(5)   NOT NULL CHECK (caso_uso ~ '^CU-0[1-8]$'),
    creado_en       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Tabla: rol_permiso  (qué comandos puede ejecutar cada rol)
CREATE TABLE rol_permiso (
    id              SERIAL PRIMARY KEY,
    rol_id          INTEGER   NOT NULL REFERENCES rol(id)     ON UPDATE CASCADE ON DELETE CASCADE,
    permiso_id      INTEGER   NOT NULL REFERENCES permiso(id) ON UPDATE CASCADE ON DELETE CASCADE,
    creado_en       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (rol_id, permiso_id)
);

-- =====================================================================
-- CU-02 GESTIÓN DE PERSONAL DE PRENSA, TURNOS Y DISPONIBILIDAD
-- =====================================================================
-- Tabla: turno  (catálogo de turnos de trabajo)
CREATE TABLE turno (
    id              SERIAL PRIMARY KEY,
    nombre          VARCHAR(50)  NOT NULL UNIQUE,
    hora_inicio     TIME         NOT NULL,
    hora_fin        TIME         NOT NULL,  -- si es menor que hora_inicio, cruza la medianoche
    dias            VARCHAR(13)  NOT NULL DEFAULT '1,2,3,4,5',  -- 1=lunes ... 7=domingo (se recibe así en INSTUR)
    descripcion     VARCHAR(150),
    creado_en       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (hora_inicio <> hora_fin),
    CHECK (dias ~ '^[1-7](,[1-7])*$')
);

-- Tabla: personal  (ficha laboral; cada miembro del personal es un usuario)
CREATE TABLE personal (
    id                   SERIAL PRIMARY KEY,
    usuario_id           INTEGER     NOT NULL UNIQUE REFERENCES usuario(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    cargo                VARCHAR(20) NOT NULL  -- conductor = conductor de TV (no chofer); jefe_prensa: también redacta notas
                         CHECK (cargo IN ('periodista','camarografo','editor','presentador','conductor','jefe_prensa')),
    condicion_laboral    VARCHAR(15) NOT NULL
                         CHECK (condicion_laboral IN ('planta','contrato','practicante')),
    unidad_academica     VARCHAR(150),  -- solo practicantes
    turno_id             INTEGER     NOT NULL REFERENCES turno(id) ON UPDATE CASCADE ON DELETE RESTRICT,  -- turno fijo oficial (RF-02)
    fecha_ingreso        DATE,
    estado               VARCHAR(10) NOT NULL DEFAULT 'activo'
                         CHECK (estado IN ('activo','inactivo')),
    creado_en            TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Tabla: ausencia  (permisos, vacaciones, bajas médicas, comisiones)
-- Fecha Y hora: un permiso de 09:00 a 11:00 ya no bloquea el día completo (RF-02 "fecha y hora").
-- Un día completo se guarda de 00:00 a 00:00 del día siguiente (fin exclusivo).
CREATE TABLE ausencia (
    id                 SERIAL PRIMARY KEY,
    personal_id        INTEGER     NOT NULL REFERENCES personal(id) ON UPDATE CASCADE ON DELETE CASCADE,
    tipo               VARCHAR(15) NOT NULL
                       CHECK (tipo IN ('permiso','vacacion','baja_medica','comision')),
    fecha_hora_inicio  TIMESTAMP   NOT NULL,
    fecha_hora_fin     TIMESTAMP   NOT NULL,
    motivo             VARCHAR(255),
    creado_en          TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_ausencia_rango CHECK (fecha_hora_fin > fecha_hora_inicio)
);

-- =====================================================================
-- CU-03 GESTIÓN DE INVENTARIO, ASIGNACIÓN Y MANTENIMIENTO
-- DE EQUIPOS TÉCNICOS Y UNIDADES MÓVILES  (ambos son "recursos")
-- =====================================================================
-- Tabla: tipo_recurso
CREATE TABLE tipo_recurso (
    id               SERIAL PRIMARY KEY,
    nombre           VARCHAR(50) NOT NULL UNIQUE,
    es_unidad_movil  BOOLEAN     NOT NULL DEFAULT FALSE,
    descripcion      VARCHAR(150),
    creado_en        TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Tabla: recurso  (equipo técnico o unidad móvil)
CREATE TABLE recurso (
    id               SERIAL PRIMARY KEY,
    codigo           VARCHAR(20)  NOT NULL UNIQUE,  -- ej. CAM-01, MOV-01
    tipo_recurso_id  INTEGER      NOT NULL REFERENCES tipo_recurso(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    marca            VARCHAR(50)  NOT NULL,
    modelo           VARCHAR(80)  NOT NULL,
    placa            VARCHAR(15)  UNIQUE,  -- solo unidades móviles (lo valida INSREC/UPDREC)
    caracteristicas  VARCHAR(255),
    estado           VARCHAR(15)  NOT NULL DEFAULT 'operativo'
                     CHECK (estado IN ('operativo','mantenimiento','baja')),
    observacion      VARCHAR(255),
    creado_en        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Tabla: cobertura  (se define aquí porque prestamo y mantenimiento la referencian)
-- CU-04 GESTIÓN DE PLANIFICAR COBERTURAS, VALIDAR CONFLICTOS Y LIBERAR RECURSOS
-- Inicio y fin con fecha y hora: una cobertura de 22:00 a 02:00 ya se puede registrar.
-- El correo no cambia: INSCOB sigue recibiendo fecha, hora_inicio y hora_fin; Java arma
-- inicio = fecha + hora_inicio y fin = fecha + hora_fin (+1 día si hora_fin <= hora_inicio).
CREATE TABLE cobertura (
    id                   SERIAL PRIMARY KEY,
    titulo               VARCHAR(200) NOT NULL,
    tema                 VARCHAR(255),
    tipo                 VARCHAR(20)  NOT NULL
                         CHECK (tipo IN ('conferencia','hecho_noticioso','deportes','acto_institucional','entrevista')),
    fecha_hora_inicio    TIMESTAMP    NOT NULL,
    fecha_hora_fin       TIMESTAMP    NOT NULL,
    lugar                VARCHAR(200) NOT NULL,
    prioridad            VARCHAR(5)   NOT NULL DEFAULT 'media'
                         CHECK (prioridad IN ('alta','media','baja')),
    fuente               VARCHAR(200),  -- contacto / fuente informativa
    indicaciones         TEXT,
-- Solo 'planificada' y 'en_curso' ocupan personal y recursos (ver v_ocupacion_*).
-- 'cumplida', 'cancelada' y 'reprogramada' son resultados del retorno (RF-08) y liberan.
    estado               VARCHAR(15)  NOT NULL DEFAULT 'planificada'
                         CHECK (estado IN ('planificada','en_curso','cumplida','cancelada','reprogramada')),
    parte_retorno        TEXT,  -- RF-08 resultado de la cobertura
    incidencias_retorno  TEXT,
    fecha_hora_retorno   TIMESTAMP,
    cobertura_origen_id  INTEGER      REFERENCES cobertura(id) ON UPDATE CASCADE ON DELETE SET NULL,  -- duplicada / reprogramada
    creado_por           INTEGER      NOT NULL REFERENCES usuario(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    creado_en            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_cobertura_rango   CHECK (fecha_hora_fin > fecha_hora_inicio),
    CONSTRAINT ck_cobertura_retorno CHECK (fecha_hora_retorno IS NULL OR fecha_hora_retorno >= fecha_hora_inicio)
);

-- Tabla: prestamo  (entrega y devolución de un recurso con su responsable)
CREATE TABLE prestamo (
    id                      SERIAL PRIMARY KEY,
    recurso_id              INTEGER   NOT NULL REFERENCES recurso(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    responsable_id          INTEGER   NOT NULL REFERENCES personal(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    cobertura_id            INTEGER   REFERENCES cobertura(id) ON UPDATE CASCADE ON DELETE SET NULL,
    fecha_entrega           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_devolucion        TIMESTAMP,  -- NULL = todavía no se devolvió
    observacion_entrega     VARCHAR(255),
    observacion_devolucion  VARCHAR(255),
    creado_en               TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (fecha_devolucion IS NULL OR fecha_devolucion >= fecha_entrega)
);

-- un recurso no puede estar entregado dos veces al mismo tiempo
CREATE UNIQUE INDEX ux_prestamo_abierto ON prestamo(recurso_id) WHERE fecha_devolucion IS NULL;

-- Tabla: mantenimiento  (bitácora: incidencia -> pasa a mantenimiento; alta -> vuelve a operativo; baja)
CREATE TABLE mantenimiento (
    id            SERIAL PRIMARY KEY,
    recurso_id    INTEGER      NOT NULL REFERENCES recurso(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    tipo          VARCHAR(12)  NOT NULL CHECK (tipo IN ('incidencia','alta','baja')),
    descripcion   VARCHAR(255) NOT NULL,
    cobertura_id  INTEGER      REFERENCES cobertura(id) ON UPDATE CASCADE ON DELETE SET NULL,
    usuario_id    INTEGER      REFERENCES usuario(id) ON UPDATE CASCADE ON DELETE SET NULL,
    fecha_hora    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Tabla: cobertura_personal  (periodista, camarógrafo y, si va, el conductor de TV que conduce desde el lugar)
CREATE TABLE cobertura_personal (
    id              SERIAL PRIMARY KEY,
    cobertura_id    INTEGER     NOT NULL REFERENCES cobertura(id) ON UPDATE CASCADE ON DELETE CASCADE,
    personal_id     INTEGER     NOT NULL REFERENCES personal(id)  ON UPDATE CASCADE ON DELETE RESTRICT,
    funcion         VARCHAR(15) NOT NULL CHECK (funcion IN ('periodista','camarografo','conductor')),
    creado_en       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (cobertura_id, personal_id)
);

-- Tabla: cobertura_recurso  (equipos y unidad móvil reservados para la cobertura)
CREATE TABLE cobertura_recurso (
    id              SERIAL PRIMARY KEY,
    cobertura_id    INTEGER   NOT NULL REFERENCES cobertura(id) ON UPDATE CASCADE ON DELETE CASCADE,
    recurso_id      INTEGER   NOT NULL REFERENCES recurso(id)   ON UPDATE CASCADE ON DELETE RESTRICT,
    creado_en       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (cobertura_id, recurso_id)
);

-- =====================================================================
-- CU-05 GESTIÓN DE REDACTAR NOTAS (NRCS), MATERIAL AUDIOVISUAL
-- Y FLUJO DE APROBACIÓN
-- =====================================================================
-- Tabla: nota  (pieza periodística con su guion)
CREATE TABLE nota (
    id                SERIAL PRIMARY KEY,
    slug              VARCHAR(40)  NOT NULL,  -- identificador corto, ej. PARO-TRANSPORTE
    titulo            VARCHAR(200) NOT NULL,
    tipo_pieza        VARCHAR(20)  NOT NULL
                      CHECK (tipo_pieza IN ('lectura','vo','vo_sot','nota_completa','enlace_vivo','entrevista_estudio')),
    guion             TEXT         NOT NULL DEFAULT '',  -- llega en el CUERPO del correo
    palabras          INTEGER      NOT NULL DEFAULT 0 CHECK (palabras >= 0),  -- lo calcula Java al guardar el guion
    palabras_por_minuto SMALLINT   CHECK (palabras_por_minuto BETWEEN 80 AND 250),  -- ritmo propio (ej. noticia grave = más lento); NULL = el del noticiero
    fecha             DATE         NOT NULL,  -- emisión para la que se escribe
    estado            VARCHAR(12)  NOT NULL DEFAULT 'borrador'
                      CHECK (estado IN ('borrador','en_revision','observada','aprobada','emitida','baja')),
    cobertura_id      INTEGER      REFERENCES cobertura(id) ON UPDATE CASCADE ON DELETE SET NULL,
    autor_id          INTEGER      NOT NULL REFERENCES personal(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    creado_en         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (slug, fecha)
);

-- Tabla: nota_rotulo  (rótulos / generador de caracteres: nombre y cargo del entrevistado)
CREATE TABLE nota_rotulo (
    id              SERIAL PRIMARY KEY,
    nota_id         INTEGER      NOT NULL REFERENCES nota(id) ON UPDATE CASCADE ON DELETE CASCADE,
    orden           SMALLINT     NOT NULL DEFAULT 1,
    nombre          VARCHAR(100) NOT NULL,
    cargo           VARCHAR(150),
    creado_en       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_rotulo_orden UNIQUE (nota_id, orden)  -- dos rótulos no pueden ocupar la misma posición
);

-- Tabla: nota_version  (cada cambio del guion queda guardado: trazabilidad)
CREATE TABLE nota_version (
    id          SERIAL PRIMARY KEY,
    nota_id     INTEGER   NOT NULL REFERENCES nota(id) ON UPDATE CASCADE ON DELETE CASCADE,
    numero      INTEGER   NOT NULL,
    guion       TEXT      NOT NULL,
    palabras    INTEGER   NOT NULL DEFAULT 0,
    usuario_id  INTEGER   NOT NULL REFERENCES usuario(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    fecha_hora  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (nota_id, numero)
);

-- Tabla: revision_nota  (historial del flujo de aprobación)
CREATE TABLE revision_nota (
    id          SERIAL PRIMARY KEY,
    nota_id     INTEGER      NOT NULL REFERENCES nota(id) ON UPDATE CASCADE ON DELETE CASCADE,
    usuario_id  INTEGER      NOT NULL REFERENCES usuario(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    accion      VARCHAR(12)  NOT NULL CHECK (accion IN ('envio','aprobacion','observacion')),
    motivo      VARCHAR(255),  -- obligatorio al observar
    fecha_hora  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (accion <> 'observacion' OR motivo IS NOT NULL)
);

-- Tabla: material_audiovisual  (videos, SOT, gráficos asociados a la nota)
CREATE TABLE material_audiovisual (
    id              SERIAL PRIMARY KEY,
    nota_id         INTEGER      NOT NULL REFERENCES nota(id) ON UPDATE CASCADE ON DELETE CASCADE,
    descripcion     VARCHAR(200) NOT NULL,
    tipo            VARCHAR(10)  NOT NULL CHECK (tipo IN ('video','sot','grafico','foto','audio')),
    duracion        INTEGER      NOT NULL DEFAULT 0 CHECK (duracion >= 0),  -- segundos
    ubicacion       VARCHAR(255),  -- ruta del archivo en el servidor de edición
    estado          VARCHAR(12)  NOT NULL DEFAULT 'pendiente'
                    CHECK (estado IN ('pendiente','ingestado','en_edicion','listo')),
    editor_id       INTEGER      REFERENCES personal(id) ON UPDATE CASCADE ON DELETE SET NULL,
    creado_en       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =====================================================================
-- CU-06 GESTIÓN DE CONFIGURAR PLANTILLAS Y CONFECCIONAR ESCALETA
-- DEL NOTICIERO CON CONTROL DE TIEMPOS
-- =====================================================================
-- Tabla: programa  (programas del canal; solo los noticieros y especiales tienen escaleta)
CREATE TABLE programa (
    id              SERIAL PRIMARY KEY,
    nombre          VARCHAR(100) NOT NULL UNIQUE,  -- ej. Noticiero Central
    tipo            VARCHAR(10)  NOT NULL CHECK (tipo IN ('noticiero','especial','otro')),  -- otro = solo referencia en la grilla
    descripcion     VARCHAR(255),
    estado          VARCHAR(10)  NOT NULL DEFAULT 'activo' CHECK (estado IN ('activo','inactivo')),
    creado_en       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Tabla: grilla  (cuándo sale cada programa: una fila por día de la semana)
-- Un horario no cruza la medianoche: termina como máximo a las 24:00 (PostgreSQL acepta TIME '24:00').
-- Un programa de 23:30 a 00:30 se registra como dos filas: 23:30-24:00 y 00:00-00:30 del día siguiente.
CREATE TABLE grilla (
    id              SERIAL PRIMARY KEY,
    programa_id     INTEGER     NOT NULL REFERENCES programa(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    dia_semana      SMALLINT    NOT NULL CHECK (dia_semana BETWEEN 1 AND 7),  -- 1=lunes ... 7=domingo
    hora_inicio     TIME        NOT NULL,
    hora_fin        TIME        NOT NULL,
    vigente_desde   DATE        NOT NULL DEFAULT CURRENT_DATE,
    vigente_hasta   DATE,  -- NULL = sin fecha de fin; = vigente_desde para un especial de un día
    estado          VARCHAR(10) NOT NULL DEFAULT 'vigente' CHECK (estado IN ('vigente','inactiva')),
    creado_en       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_grilla_horas CHECK (hora_fin > hora_inicio),
    CHECK (vigente_hasta IS NULL OR vigente_hasta >= vigente_desde)
);

-- Tabla: plantilla  (estructura del noticiero: duración, bloques y ritmo de lectura)
-- cantidad_bloques se mantiene (lo pide RF-12) y debe cumplir: cortes = cantidad_bloques - 1.
-- Lo controla v_plantilla_control; INSESC no genera escaletas de una plantilla inconsistente.
CREATE TABLE plantilla (
    id                   SERIAL PRIMARY KEY,
    programa_id          INTEGER      NOT NULL REFERENCES programa(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    nombre               VARCHAR(100) NOT NULL UNIQUE,  -- ej. Noticiero Central
    duracion_total       INTEGER      NOT NULL CHECK (duracion_total > 0),  -- segundos
    cantidad_bloques     SMALLINT     NOT NULL CHECK (cantidad_bloques BETWEEN 1 AND 10),
    palabras_por_minuto  SMALLINT     NOT NULL DEFAULT 150 CHECK (palabras_por_minuto BETWEEN 80 AND 250),  -- ritmo estándar del noticiero
    estado               VARCHAR(10)  NOT NULL DEFAULT 'vigente' CHECK (estado IN ('vigente','inactiva')),
    creado_en            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Tabla: plantilla_corte  (ubicación y duración de cada corte comercial;

-- el contenido del corte lo define Programación/Tráfico)
-- El corte número n cierra el bloque n (corte 1 cierra el bloque A).
CREATE TABLE plantilla_corte (
    id              SERIAL PRIMARY KEY,
    plantilla_id    INTEGER   NOT NULL REFERENCES plantilla(id) ON UPDATE CASCADE ON DELETE CASCADE,
    numero          SMALLINT  NOT NULL CHECK (numero > 0),
    minuto_inicio   SMALLINT  NOT NULL CHECK (minuto_inicio > 0),  -- minuto del programa en que empieza el corte
    duracion        INTEGER   NOT NULL CHECK (duracion > 0),  -- segundos
    creado_en       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (plantilla_id, numero)
);

-- Tabla: escaleta  (una emisión concreta de un noticiero en una fecha)
-- INSESC exige que la grilla y la plantilla sean del mismo programa y que el día de la
-- fecha coincida con el de la grilla (la consulta de INSESC no inserta nada si no se cumple).
CREATE TABLE escaleta (
    id                SERIAL PRIMARY KEY,
    grilla_id         INTEGER     NOT NULL REFERENCES grilla(id)    ON UPDATE CASCADE ON DELETE RESTRICT,  -- horario que se emite
    plantilla_id      INTEGER     NOT NULL REFERENCES plantilla(id) ON UPDATE CASCADE ON DELETE RESTRICT,  -- estructura que se usa
    fecha             DATE        NOT NULL,
    hora_inicio       TIME        NOT NULL,  -- se copia de la grilla al crearla
    duracion_total    INTEGER     NOT NULL CHECK (duracion_total > 0),  -- se copia de la plantilla
    estado            VARCHAR(10) NOT NULL DEFAULT 'borrador'
                      CHECK (estado IN ('borrador','cerrada','emitida')),
    hora_inicio_real  TIME,
    duracion_real     INTEGER,  -- segundos, al registrarla como emitida
    responsable_id    INTEGER     NOT NULL REFERENCES usuario(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    creado_en         TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (grilla_id, fecha)
);

-- Tabla: item_escaleta  (cada fila de la escaleta: nota, enlace, entrevista, corte...)
-- Los cortes los genera INSESC desde plantilla_corte (uno por bloque, con orden 999 = siempre
-- al final de su bloque). INSITE no acepta tipo 'corte'.
-- El orden es dentro del bloque: la escaleta se lee por (bloque, orden).
CREATE TABLE item_escaleta (
    id                 SERIAL PRIMARY KEY,
    escaleta_id        INTEGER      NOT NULL REFERENCES escaleta(id) ON UPDATE CASCADE ON DELETE CASCADE,
    orden              SMALLINT     NOT NULL,  -- posición dentro del bloque
    bloque             CHAR(1)      NOT NULL CHECK (bloque BETWEEN 'A' AND 'J'),
    tipo               VARCHAR(12)  NOT NULL
                       CHECK (tipo IN ('apertura','titulares','nota','enlace_vivo','entrevista','corte','despedida','otro')),
    nota_id            INTEGER      REFERENCES nota(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    titulo             VARCHAR(200),  -- para ítems que no son nota
    presentador_id     INTEGER      REFERENCES personal(id) ON UPDATE CASCADE ON DELETE SET NULL,
    duracion_estimada  INTEGER      NOT NULL DEFAULT 0 CHECK (duracion_estimada >= 0),  -- seg; en notas se calcula
    duracion_real      INTEGER      CHECK (duracion_real >= 0),
    estado             VARCHAR(10)  NOT NULL DEFAULT 'programado'
                       CHECK (estado IN ('programado','flotado','baja','emitido')),
    ultima_hora        BOOLEAN      NOT NULL DEFAULT FALSE,
    creado_en          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (tipo <> 'nota' OR nota_id IS NOT NULL),
    CHECK (nota_id IS NOT NULL OR titulo IS NOT NULL),
    CONSTRAINT ck_item_orden       CHECK (CASE WHEN tipo = 'corte' THEN orden = 999 ELSE orden BETWEEN 1 AND 998 END),
    CONSTRAINT ck_item_corte_vacio CHECK (tipo <> 'corte' OR (nota_id IS NULL AND presentador_id IS NULL)),
    CONSTRAINT uq_item_bloque_orden UNIQUE (escaleta_id, bloque, orden)
               DEFERRABLE INITIALLY DEFERRED  -- permite reordenar en una transacción
);

-- un solo corte por bloque
CREATE UNIQUE INDEX ux_item_corte ON item_escaleta(escaleta_id, bloque) WHERE tipo = 'corte';

-- la misma nota no puede salir dos veces en la misma emisión (sí puede quedar flotada y volver)
CREATE UNIQUE INDEX ux_item_nota ON item_escaleta(escaleta_id, nota_id)
       WHERE nota_id IS NOT NULL AND estado IN ('programado','emitido');

-- =====================================================================
-- TRANSVERSAL: RNF-24 TRAZABILIDAD DE COMANDOS
-- =====================================================================
-- Tabla: bitacora_comando  (cada correo recibido y su respuesta)
CREATE TABLE bitacora_comando (
    id          SERIAL PRIMARY KEY,
    remitente   VARCHAR(150) NOT NULL,
    usuario_id  INTEGER      REFERENCES usuario(id) ON UPDATE CASCADE ON DELETE SET NULL,  -- NULL si no está registrado
    asunto      TEXT         NOT NULL,
    comando     VARCHAR(10),  -- NULL si el asunto no tiene un comando reconocible
    resultado   VARCHAR(6)   NOT NULL CHECK (resultado IN ('exito','error')),
    respuesta   TEXT,
    fecha_hora  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =====================================================================
-- ÍNDICES PARA LAS VALIDACIONES DE CONFLICTO Y LOS REPORTES
-- =====================================================================

CREATE INDEX ix_cobertura_inicio       ON cobertura(fecha_hora_inicio, estado);

CREATE INDEX ix_cob_personal_personal  ON cobertura_personal(personal_id);

CREATE INDEX ix_cob_recurso_recurso    ON cobertura_recurso(recurso_id);

CREATE INDEX ix_ausencia_personal      ON ausencia(personal_id, fecha_hora_inicio, fecha_hora_fin);

CREATE INDEX ix_mantenimiento_fecha    ON mantenimiento(fecha_hora);  -- REPMAN por período
CREATE INDEX ix_nota_fecha_estado      ON nota(fecha, estado);

CREATE INDEX ix_grilla_dia             ON grilla(dia_semana, hora_inicio);

CREATE INDEX ix_bitacora_fecha         ON bitacora_comando(fecha_hora);

-- (ix_item_escaleta_orden ya no hace falta: lo cubre uq_item_bloque_orden)
-- =====================================================================
-- VISTAS DE OCUPACIÓN
-- Única definición de "qué cobertura ocupa a alguien": planificada o en_curso.
-- DISPER, INSCPE, INSCRE, INCREC y los reportes la leen de aquí.
-- =====================================================================

CREATE VIEW v_ocupacion_personal AS
SELECT cp.personal_id, cp.funcion, c.id AS cobertura_id, c.titulo,
       c.fecha_hora_inicio, c.fecha_hora_fin, c.estado
FROM cobertura_personal cp
JOIN cobertura c ON c.id = cp.cobertura_id
WHERE c.estado IN ('planificada','en_curso');

CREATE VIEW v_ocupacion_recurso AS
SELECT cr.recurso_id, c.id AS cobertura_id, c.titulo,
       c.fecha_hora_inicio, c.fecha_hora_fin, c.estado
FROM cobertura_recurso cr
JOIN cobertura c ON c.id = cr.cobertura_id
WHERE c.estado IN ('planificada','en_curso');

-- =====================================================================
-- VISTAS DE CÁLCULO (control de tiempos del noticiero)
-- =====================================================================
-- Duración de cada nota: lectura del guion + video (si la pieza lleva video)
-- (fuera de una escaleta se estima con el ritmo propio de la nota o 150 palabras/min)
CREATE VIEW v_nota_duracion AS
WITH b AS (
    SELECT n.id, n.slug, n.tipo_pieza, n.palabras,
           CEIL(n.palabras * 60.0 / COALESCE(n.palabras_por_minuto, 150))::INT AS duracion_lectura,
           CASE WHEN n.tipo_pieza = 'lectura' THEN 0
                ELSE COALESCE((SELECT SUM(m.duracion) FROM material_audiovisual m
                               WHERE m.nota_id = n.id AND m.tipo IN ('video','sot')), 0)::INT
           END AS duracion_video
    FROM nota n)
SELECT id AS nota_id, slug, tipo_pieza, palabras, duracion_lectura, duracion_video,
       duracion_lectura + duracion_video AS duracion_total
FROM b;

-- Tiempo disponible de cada bloque. Los bloques salen de cantidad_bloques (no de los cortes):
-- el bloque n empieza cuando termina el corte n-1 y termina cuando empieza el corte n
-- (el último bloque termina con el programa). Si falta un corte, el tiempo sale NULL
-- en vez de que el bloque desaparezca.
CREATE VIEW v_plantilla_bloque AS
WITH lim AS (
    SELECT p.id AS plantilla_id, b.n AS numero_bloque,
           CASE WHEN b.n = 1 THEN 0 ELSE ca.minuto_inicio * 60 + ca.duracion END AS inicio_bloque,
           CASE WHEN b.n = p.cantidad_bloques THEN p.duracion_total ELSE cs.minuto_inicio * 60 END AS fin_bloque
    FROM plantilla p
    CROSS JOIN LATERAL generate_series(1, p.cantidad_bloques) AS b(n)
    LEFT JOIN plantilla_corte ca ON ca.plantilla_id = p.id AND ca.numero = b.n - 1  -- corte que abre el bloque
    LEFT JOIN plantilla_corte cs ON cs.plantilla_id = p.id AND cs.numero = b.n  -- corte que lo cierra
)
SELECT plantilla_id,
       numero_bloque,
       CHR(64 + numero_bloque) AS bloque,
       inicio_bloque,
       fin_bloque,
       fin_bloque - inicio_bloque AS tiempo_disponible
FROM lim;

-- Control de la plantilla: 'ok' solo si tiene cantidad_bloques - 1 cortes, bien numerados,
-- sin solaparse y dentro de la duración. INSESC rechaza las plantillas que no estén en 'ok'.
CREATE VIEW v_plantilla_control AS
SELECT p.id AS plantilla_id,
       p.nombre,
       p.cantidad_bloques,
       (SELECT COUNT(*) FROM plantilla_corte c WHERE c.plantilla_id = p.id) AS cortes,
       CASE
         WHEN (SELECT COUNT(*) FROM plantilla_corte c WHERE c.plantilla_id = p.id) <> p.cantidad_bloques - 1
              THEN 'debe tener ' || (p.cantidad_bloques - 1) || ' cortes'
         WHEN EXISTS (SELECT 1 FROM v_plantilla_bloque b
                      WHERE b.plantilla_id = p.id AND (b.tiempo_disponible IS NULL OR b.tiempo_disponible <= 0))
              THEN 'cortes salteados, solapados o fuera de la duracion'
         ELSE 'ok'
       END AS observacion
FROM plantilla p;

-- La escaleta como grilla: página, duración, acumulado y hora estimada de salida.
-- Lectura de cada nota = palabras / ritmo (el de la nota si lo tiene; si no, el estándar del noticiero).
-- Se ordena por (bloque, orden); el corte (orden 999) queda siempre al final de su bloque.
CREATE VIEW v_escaleta_grilla AS
WITH it AS (
    SELECT i.*,
           CASE WHEN i.nota_id IS NOT NULL THEN
                CEIL(n.palabras * 60.0 / COALESCE(n.palabras_por_minuto, pl.palabras_por_minuto))::INT + d.duracion_video
                ELSE i.duracion_estimada END AS duracion_calc
    FROM item_escaleta i
    JOIN escaleta es            ON es.id = i.escaleta_id
    JOIN plantilla pl           ON pl.id = es.plantilla_id
    LEFT JOIN nota n            ON n.id = i.nota_id
    LEFT JOIN v_nota_duracion d ON d.nota_id = i.nota_id
    WHERE i.estado IN ('programado','emitido')  -- flotados y bajas no suman tiempo
)
SELECT it.escaleta_id,
       it.id AS item_id,
       ROW_NUMBER() OVER w AS posicion,
       it.orden,
       it.bloque,
       CASE WHEN it.tipo = 'corte' THEN 'CORTE ' || (ASCII(it.bloque) - 64)
            ELSE it.bloque || ROW_NUMBER() OVER (PARTITION BY it.escaleta_id, it.bloque, (it.tipo = 'corte') ORDER BY it.orden)
       END AS pagina,
       it.tipo,
       COALESCE(n.slug, '') AS slug,
       COALESCE(n.titulo, it.titulo) AS titulo,
       n.estado AS estado_nota,
       u.nombre || ' ' || u.apellido AS presentador,
       it.duracion_calc AS duracion,
       SUM(it.duracion_calc) OVER w - it.duracion_calc AS inicio_seg,
       SUM(it.duracion_calc) OVER w AS acumulado_seg,
       (e.hora_inicio + ((SUM(it.duracion_calc) OVER w - it.duracion_calc) * INTERVAL '1 second'))::TIME AS hora_salida,
       it.duracion_real,
       it.ultima_hora,
       it.estado
FROM it
JOIN escaleta e       ON e.id = it.escaleta_id
LEFT JOIN nota n      ON n.id = it.nota_id
LEFT JOIN personal pr ON pr.id = it.presentador_id
LEFT JOIN usuario u   ON u.id = pr.usuario_id
WINDOW w AS (PARTITION BY it.escaleta_id ORDER BY it.bloque, it.orden ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW);

-- Control de tiempos por bloque: usado vs disponible (alerta si se pasa o falta más de 15 s)
CREATE VIEW v_bloque_balance AS
SELECT e.id AS escaleta_id,
       b.bloque,
       b.tiempo_disponible,
       COALESCE(SUM(g.duracion) FILTER (WHERE g.tipo <> 'corte'), 0) AS tiempo_usado,
       COALESCE(SUM(g.duracion) FILTER (WHERE g.tipo <> 'corte'), 0) - b.tiempo_disponible AS diferencia,
       CASE WHEN COALESCE(SUM(g.duracion) FILTER (WHERE g.tipo <> 'corte'), 0) - b.tiempo_disponible > 15  THEN 'exceso'
            WHEN COALESCE(SUM(g.duracion) FILTER (WHERE g.tipo <> 'corte'), 0) - b.tiempo_disponible < -15 THEN 'deficit'
            ELSE 'ok' END AS alerta
FROM escaleta e
JOIN v_plantilla_bloque b    ON b.plantilla_id = e.plantilla_id
LEFT JOIN v_escaleta_grilla g ON g.escaleta_id = e.id AND g.bloque = b.bloque
GROUP BY e.id, b.bloque, b.tiempo_disponible;

-- Grilla semanal legible (con el nombre del día y del programa)
CREATE VIEW v_grilla_semanal AS
SELECT g.id AS grilla_id,
       g.dia_semana,
       (ARRAY['Lunes','Martes','Miércoles','Jueves','Viernes','Sábado','Domingo'])[g.dia_semana] AS dia,
       g.hora_inicio,
       g.hora_fin,
       pr.id AS programa_id,
       pr.nombre AS programa,
       pr.tipo,
       g.vigente_desde,
       g.vigente_hasta
FROM grilla g
JOIN programa pr ON pr.id = g.programa_id
WHERE g.estado = 'vigente' AND pr.estado = 'activo';
