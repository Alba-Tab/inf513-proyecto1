-- =====================================================================
-- Consultas de lógica de negocio - Canal 11 TVU - db_grupo14sa
-- Son las consultas que usa la capa de Negocio (Java). Aquí se escriben
-- con variables de psql (:variable) para poder probarlas; en Java cada
-- :variable es un "?" del PreparedStatement.
--   psql -d db_grupo14sa -f consultas_negocio.sql
-- =====================================================================

\set ON_ERROR_STOP on

-- =====================================================================
-- RNF-20  Autorización: ¿el remitente está registrado, activo y su rol
-- puede ejecutar el comando?  (AYUDA no pasa por aquí)
-- 0 filas -> "Error: no tiene permiso para ejecutar <comando>"
-- =====================================================================
\echo '--- [Permiso] ¿Carla (periodista) puede ejecutar APRNOT? (esperado: 0 filas)'
SELECT u.id, r.nombre AS rol
FROM usuario u
JOIN rol r          ON r.id = u.rol_id
JOIN rol_permiso rp ON rp.rol_id = r.id
JOIN permiso p      ON p.id = rp.permiso_id
WHERE u.correo = 'carla.rojas@canal11.test' AND u.estado = 'activo' AND p.comando = 'APRNOT';

\echo '--- [Permiso] ¿El Jefe de Prensa puede ejecutar APRNOT? (esperado: 1 fila)'
SELECT u.id, r.nombre AS rol
FROM usuario u
JOIN rol r          ON r.id = u.rol_id
JOIN rol_permiso rp ON rp.rol_id = r.id
JOIN permiso p      ON p.id = rp.permiso_id
WHERE u.correo = 'jefe.prensa@canal11.test' AND u.estado = 'activo' AND p.comando = 'APRNOT';

-- =====================================================================
-- CU-02  RF-02  Personal disponible en una fecha y hora
-- Comando: DISPER["2026-10-12","16:00"]
-- =====================================================================
SELECT (CURRENT_DATE + TIME '16:00') AS momento \gset

\echo '--- [DISPER] Personal disponible hoy a las 16:00'
SELECT u.nombre || ' ' || u.apellido AS persona, p.cargo, t.nombre AS turno
FROM personal p
JOIN usuario u ON u.id = p.usuario_id
JOIN turno t   ON t.id = p.turno_id
WHERE p.estado = 'activo'
  -- 1) el momento cae dentro de su turno (turno que empezó ese día o el anterior, por los nocturnos)
  AND EXISTS (SELECT 1
              FROM (VALUES (:'momento'::timestamp::date), (:'momento'::timestamp::date - 1)) AS d(dia)
              WHERE EXTRACT(ISODOW FROM d.dia)::int = ANY (string_to_array(t.dias, ',')::int[])
                AND :'momento'::timestamp >= d.dia + t.hora_inicio
                AND :'momento'::timestamp <  d.dia + t.hora_fin
                       + CASE WHEN t.hora_fin <= t.hora_inicio THEN INTERVAL '1 day' ELSE INTERVAL '0' END)
  -- 2) no tiene una ausencia que cubra ese momento
  AND NOT EXISTS (SELECT 1 FROM ausencia a
                  WHERE a.personal_id = p.id
                    AND :'momento'::timestamp >= a.fecha_hora_inicio
                    AND :'momento'::timestamp <  a.fecha_hora_fin)
  -- 3) no está ocupado en una cobertura activa a esa hora
  AND NOT EXISTS (SELECT 1 FROM v_ocupacion_personal o
                  WHERE o.personal_id = p.id
                    AND :'momento'::timestamp >= o.fecha_hora_inicio
                    AND :'momento'::timestamp <  o.fecha_hora_fin)
ORDER BY p.cargo, persona;

-- =====================================================================
-- CU-04  RF-06  Validar conflictos ANTES de asignar personal
-- Comando: INSCPE["id_cobertura","id_personal","funcion"]
-- Si alguna consulta devuelve filas -> "Error: <detalle>" y NO se inserta.
-- =====================================================================
\set cob 8
\set per 6
\echo '--- [INSCPE 8,6] a) ¿Tiene una ausencia que se cruza con la cobertura? (esperado: permiso)'
SELECT a.tipo, a.motivo, a.fecha_hora_inicio, a.fecha_hora_fin
FROM ausencia a JOIN cobertura c ON c.id = :cob
WHERE a.personal_id = :per
  AND a.fecha_hora_inicio < c.fecha_hora_fin AND c.fecha_hora_inicio < a.fecha_hora_fin;

\set cob 6
\set per 7
\echo '--- [INSCPE 6,7] b) ¿Ya está en otra cobertura activa que se cruza? (esperado: Clásico cruceño; la de 17:00 no choca)'
SELECT o.cobertura_id, o.titulo, o.fecha_hora_inicio, o.fecha_hora_fin
FROM v_ocupacion_personal o JOIN cobertura c ON c.id = :cob
WHERE o.personal_id = :per AND o.cobertura_id <> c.id
  AND o.fecha_hora_inicio < c.fecha_hora_fin AND c.fecha_hora_inicio < o.fecha_hora_fin;

\set cob 6
\set per 1
\echo '--- [INSCPE 6,1] c) ¿La cobertura queda fuera de su turno? (Carla es turno mañana -> 1 fila = advertencia)'
SELECT u.nombre || ' ' || u.apellido AS persona, t.nombre AS turno, t.hora_inicio, t.hora_fin
FROM personal p
JOIN usuario u   ON u.id = p.usuario_id
JOIN turno t     ON t.id = p.turno_id
JOIN cobertura c ON c.id = :cob
WHERE p.id = :per
  AND NOT EXISTS (SELECT 1
                  FROM (VALUES (c.fecha_hora_inicio::date), (c.fecha_hora_inicio::date - 1)) AS d(dia)
                  WHERE EXTRACT(ISODOW FROM d.dia)::int = ANY (string_to_array(t.dias, ',')::int[])
                    AND c.fecha_hora_inicio >= d.dia + t.hora_inicio
                    AND c.fecha_hora_fin   <= d.dia + t.hora_fin
                           + CASE WHEN t.hora_fin <= t.hora_inicio THEN INTERVAL '1 day' ELSE INTERVAL '0' END);

-- =====================================================================
-- CU-04  RF-06  Validar un recurso antes de reservarlo
-- Comando: INSCRE["id_cobertura","codigo"]
-- =====================================================================
\set cob 6
\echo '--- [INSCRE 6,CAM-04] a) ¿El recurso no está operativo? (esperado: mantenimiento)'
SELECT r.codigo, r.estado, r.observacion FROM recurso r
WHERE r.codigo = 'CAM-04' AND r.estado <> 'operativo';

\echo '--- [INSCRE 6,MOV-01] b) ¿Está reservado en otra cobertura activa que se cruza? (esperado: Clásico cruceño)'
SELECT o.cobertura_id, o.titulo, o.fecha_hora_inicio, o.fecha_hora_fin
FROM recurso r
JOIN v_ocupacion_recurso o ON o.recurso_id = r.id
JOIN cobertura c           ON c.id = :cob
WHERE r.codigo = 'MOV-01' AND o.cobertura_id <> c.id
  AND o.fecha_hora_inicio < c.fecha_hora_fin AND c.fecha_hora_inicio < o.fecha_hora_fin;

-- =====================================================================
-- CU-04  RF-07  Hoja de asignación
-- Comando: HOJCOB["id_cobertura"]
-- =====================================================================
\set cob 2
\echo '--- [HOJCOB 2] Hoja de asignación'
SELECT c.titulo, c.fecha_hora_inicio::date AS fecha, c.fecha_hora_inicio::time AS hora_inicio,
       c.fecha_hora_fin::time AS hora_fin, c.lugar, c.prioridad, c.fuente, c.indicaciones,
       (SELECT string_agg(cp.funcion || ': ' || u.nombre || ' ' || u.apellido || ' (' || COALESCE(u.telefono,'-') || ')', ' | ')
          FROM cobertura_personal cp JOIN personal p ON p.id = cp.personal_id JOIN usuario u ON u.id = p.usuario_id
         WHERE cp.cobertura_id = c.id) AS equipo_humano,
       (SELECT string_agg(r.codigo || ' ' || r.marca || ' ' || r.modelo, ' | ' ORDER BY r.codigo)
          FROM cobertura_recurso cr JOIN recurso r ON r.id = cr.recurso_id
         WHERE cr.cobertura_id = c.id) AS recursos
FROM cobertura c WHERE c.id = :cob;

-- =====================================================================
-- CU-04  RF-08  Registrar retorno y liberar recursos
-- Comando: RETCOB["id_cobertura","cumplida|cancelada|reprogramada","parte","incidencias"]
-- Cualquier resultado saca a la cobertura de v_ocupacion_*: eso libera al personal
-- y a los recursos. Si fue 'reprogramada', la nueva fecha se crea con DUPCOB.
-- =====================================================================
\echo '--- [RETCOB 2] Retorno de la cobertura 2 (con ROLLBACK)'
BEGIN;
UPDATE cobertura
   SET estado = 'cumplida', parte_retorno = 'Imágenes del bloqueo y 3 testimonios',
       incidencias_retorno = 'Ninguna', fecha_hora_retorno = GREATEST(LOCALTIMESTAMP(0), fecha_hora_inicio),
       actualizado_en = CURRENT_TIMESTAMP
 WHERE id = 2 AND estado IN ('planificada','en_curso');
UPDATE prestamo
   SET fecha_devolucion = GREATEST(CURRENT_TIMESTAMP, fecha_entrega), observacion_devolucion = 'Retorno de cobertura', actualizado_en = CURRENT_TIMESTAMP
 WHERE cobertura_id = 2 AND fecha_devolucion IS NULL;
SELECT r.codigo, p.fecha_devolucion IS NOT NULL AS devuelto
FROM prestamo p JOIN recurso r ON r.id = p.recurso_id WHERE p.cobertura_id = 2 ORDER BY r.codigo;
ROLLBACK;

\echo '--- [RETCOB 4 reprogramada] MOV-01 queda libre para la cobertura 6 (esperado: 0 filas) (con ROLLBACK)'
BEGIN;
UPDATE cobertura SET estado = 'reprogramada', parte_retorno = 'Se postergó la conferencia'
 WHERE id = 4 AND estado IN ('planificada','en_curso');
SELECT o.cobertura_id, o.titulo
FROM recurso r JOIN v_ocupacion_recurso o ON o.recurso_id = r.id JOIN cobertura c ON c.id = 6
WHERE r.codigo = 'MOV-01' AND o.cobertura_id <> c.id
  AND o.fecha_hora_inicio < c.fecha_hora_fin AND c.fecha_hora_inicio < o.fecha_hora_fin;
ROLLBACK;

-- =====================================================================
-- CU-03  RF-04  Reportar daño
-- Comando: INCREC["codigo","descripcion"]
-- =====================================================================
\echo '--- [INCREC MOV-01] Incidencia + coberturas futuras afectadas (con ROLLBACK)'
BEGIN;
UPDATE recurso SET estado = 'mantenimiento', observacion = 'Pinchazo y falla de frenos', actualizado_en = CURRENT_TIMESTAMP
 WHERE codigo = 'MOV-01' AND estado = 'operativo';
INSERT INTO mantenimiento (recurso_id, tipo, descripcion, usuario_id)
SELECT id, 'incidencia', 'Pinchazo y falla de frenos', 15 FROM recurso WHERE codigo = 'MOV-01';
SELECT o.cobertura_id, o.titulo, o.fecha_hora_inicio
FROM v_ocupacion_recurso o JOIN recurso r ON r.id = o.recurso_id
WHERE r.codigo = 'MOV-01' AND o.fecha_hora_fin >= LOCALTIMESTAMP
ORDER BY o.fecha_hora_inicio;
ROLLBACK;

-- =====================================================================
-- CU-05  RF-09 / RF-11  Guion, versiones y aprobación
-- =====================================================================
\echo '--- [GUINOT 1] Cambiar el guion de una nota APROBADA: nueva versión y vuelve a revisión (con ROLLBACK)'
-- Comando: GUINOT["id_nota"]  -> el guion nuevo va en el CUERPO del correo
BEGIN;
UPDATE nota
   SET guion = 'Texto corregido del guion enviado en el cuerpo del correo...',
       palabras = 9,
       estado = CASE WHEN estado = 'aprobada' THEN 'en_revision' ELSE estado END,
       actualizado_en = CURRENT_TIMESTAMP
 WHERE id = 1 AND estado IN ('borrador','observada','en_revision','aprobada');
INSERT INTO nota_version (nota_id, numero, guion, palabras, usuario_id)
SELECT 1, COALESCE(MAX(numero), 0) + 1, 'Texto corregido del guion enviado en el cuerpo del correo...', 9, 3
FROM nota_version WHERE nota_id = 1;
SELECT n.slug, n.estado, v.numero, v.palabras FROM nota n JOIN nota_version v ON v.nota_id = n.id WHERE n.id = 1 ORDER BY v.numero;
ROLLBACK;

\echo '--- [APRNOT 3] Aprobar: solo el Jefe de Prensa y solo si está en_revision (con ROLLBACK)'
-- Comando: APRNOT["id_nota"]   /  OBSNOT["id_nota","motivo"]
BEGIN;
UPDATE nota SET estado = 'aprobada', actualizado_en = CURRENT_TIMESTAMP
 WHERE id = 3 AND estado = 'en_revision'
   AND EXISTS (SELECT 1 FROM usuario u JOIN rol r ON r.id = u.rol_id
               WHERE u.correo = 'jefe.prensa@canal11.test' AND r.nombre = 'Jefe de Prensa');
INSERT INTO revision_nota (nota_id, usuario_id, accion) VALUES (3, 2, 'aprobacion');
SELECT id, slug, estado FROM nota WHERE id = 3;
ROLLBACK;

-- =====================================================================
-- CU-06  Grilla de programas (cuándo sale cada uno)
-- Comando: LISGRI["2026-10-12","2026-10-18"]
-- =====================================================================
\echo '--- [LISGRI] Grilla de los próximos 7 días (programa, horario y estado de su escaleta)'
SELECT f.fecha::date, g.dia, g.hora_inicio, g.hora_fin, g.programa, g.tipo,
       COALESCE(e.estado, 'sin escaleta') AS escaleta
FROM generate_series(CURRENT_DATE, CURRENT_DATE + 6, INTERVAL '1 day') AS f(fecha)
JOIN v_grilla_semanal g ON g.dia_semana = EXTRACT(ISODOW FROM f.fecha)
                       AND f.fecha::date >= g.vigente_desde
                       AND (g.vigente_hasta IS NULL OR f.fecha::date <= g.vigente_hasta)
LEFT JOIN escaleta e ON e.grilla_id = g.grilla_id AND e.fecha = f.fecha::date
ORDER BY f.fecha, g.hora_inicio;

\echo '--- [INSGRI] lunes 20:30-21:00 desde hoy, sin fecha de fin (esperado: choca con el Central)'
SELECT g.programa, g.dia, g.hora_inicio, g.hora_fin, g.vigente_desde, g.vigente_hasta
FROM v_grilla_semanal g
WHERE g.dia_semana = 1
  AND TIME '20:30' < g.hora_fin AND g.hora_inicio < TIME '21:00'
  AND g.vigente_desde <= COALESCE(NULL::date, 'infinity')                 -- vigente_hasta nuevo (NULL = sin fin)
  AND COALESCE(g.vigente_hasta, 'infinity') >= CURRENT_DATE;               -- vigente_desde nuevo

-- =====================================================================
-- CU-06  RF-12  Plantilla
-- =====================================================================
\echo '--- [LISPLA] Control de plantillas (todas deben estar ok)'
SELECT * FROM v_plantilla_control ORDER BY plantilla_id;

\echo '--- [LISPLA 1] Bloques y tiempo neto'
SELECT b.bloque, b.inicio_bloque, b.fin_bloque, b.tiempo_disponible FROM v_plantilla_bloque b WHERE b.plantilla_id = 1 ORDER BY b.numero_bloque;
SELECT p.nombre, p.duracion_total, COALESCE(SUM(c.duracion), 0) AS tiempo_comercial,
       p.duracion_total - COALESCE(SUM(c.duracion), 0) AS tiempo_neto
FROM plantilla p LEFT JOIN plantilla_corte c ON c.plantilla_id = p.id
WHERE p.id = 1 GROUP BY p.id;

\echo '--- [INSCOR/UPDCOR/DELCOR/UPDPLA] Rechazar si la plantilla tiene escaletas en borrador (esperado: 1)'
SELECT COUNT(*) AS escaletas_en_borrador FROM escaleta WHERE plantilla_id = 1 AND estado = 'borrador';

-- =====================================================================
-- CU-06  RF-13  Escaleta
-- =====================================================================
\echo '--- [INSESC] Generar la escaleta de mañana del Central con la plantilla 1 (con ROLLBACK)'
-- Comando: INSESC["id_grilla","fecha","id_plantilla"]
SELECT g.id AS grilla_manana FROM grilla g
WHERE g.programa_id = 1 AND g.dia_semana = EXTRACT(ISODOW FROM CURRENT_DATE + 1) \gset
BEGIN;
-- 1) la escaleta: 0 filas = error (otro programa, otro día, grilla no vigente o plantilla inconsistente)
INSERT INTO escaleta (grilla_id, plantilla_id, fecha, hora_inicio, duracion_total, responsable_id)
SELECT g.id, p.id, CURRENT_DATE + 1, g.hora_inicio, p.duracion_total, 2
FROM grilla g
JOIN plantilla p            ON p.programa_id = g.programa_id                       -- mismo programa
JOIN v_plantilla_control pc ON pc.plantilla_id = p.id AND pc.observacion = 'ok'    -- plantilla consistente
WHERE g.id = :grilla_manana AND p.id = 1 AND g.estado = 'vigente'
  AND g.dia_semana = EXTRACT(ISODOW FROM CURRENT_DATE + 1)                         -- el día coincide
  AND CURRENT_DATE + 1 >= g.vigente_desde AND (g.vigente_hasta IS NULL OR CURRENT_DATE + 1 <= g.vigente_hasta)
RETURNING id AS esc_nueva \gset
-- 2) sus cortes, copiados de la plantilla
INSERT INTO item_escaleta (escaleta_id, orden, bloque, tipo, titulo, duracion_estimada)
SELECT :esc_nueva, 999, CHR(64 + c.numero), 'corte', 'Corte comercial ' || c.numero, c.duracion
FROM escaleta e JOIN plantilla_corte c ON c.plantilla_id = e.plantilla_id
WHERE e.id = :esc_nueva;
SELECT pagina, tipo, titulo, duracion, hora_salida FROM v_escaleta_grilla WHERE escaleta_id = :esc_nueva ORDER BY posicion;
ROLLBACK;

\echo '--- [INSESC] Grilla del Central con la plantilla del Matinal (esperado: 0 filas = error)'
SELECT g.id FROM grilla g JOIN plantilla p ON p.programa_id = g.programa_id
WHERE g.id = :grilla_manana AND p.id = 2;

\set esc 1
\echo '--- [INSITE] a) ¿El bloque existe y la escaleta está en borrador? (bloque E -> 0 filas = error)'
-- Comando: INSITE["id_escaleta","bloque","tipo","id_nota o titulo","id_presentador","duracion"]
SELECT b.bloque FROM escaleta e JOIN v_plantilla_bloque b ON b.plantilla_id = e.plantilla_id
WHERE e.id = :esc AND e.estado = 'borrador' AND b.bloque = 'E';

\echo '--- [INSITE] b) Solo notas APROBADAS (nota 3 está en revisión -> 0 filas = error)'
SELECT id, slug FROM nota WHERE id = 3 AND estado = 'aprobada';

\echo '--- [INSITE] c) Insertar al final del bloque C, antes del corte (con ROLLBACK)'
BEGIN;
INSERT INTO item_escaleta (escaleta_id, orden, bloque, tipo, titulo, presentador_id, duracion_estimada)
SELECT :esc, COALESCE(MAX(orden) FILTER (WHERE tipo <> 'corte'), 0) + 1, 'C', 'entrevista', 'Dirigente vecinal en estudio', 11, 300
FROM item_escaleta WHERE escaleta_id = :esc AND bloque = 'C';
SELECT pagina, tipo, titulo, duracion FROM v_escaleta_grilla WHERE escaleta_id = :esc AND bloque = 'C' ORDER BY posicion;
ROLLBACK;

\echo '--- [UPDITE] Reordenar: intercambiar los dos primeros ítems del bloque A (con ROLLBACK)'
BEGIN;
UPDATE item_escaleta SET orden = CASE orden WHEN 1 THEN 2 ELSE 1 END
WHERE escaleta_id = :esc AND bloque = 'A' AND orden IN (1, 2);   -- funciona por el UNIQUE DEFERRABLE
SELECT pagina, titulo FROM v_escaleta_grilla WHERE escaleta_id = :esc AND bloque = 'A' ORDER BY posicion LIMIT 2;
ROLLBACK;

\echo '--- [LISESC 1] Escaleta de hoy como grilla'
SELECT pagina, tipo, slug, titulo, presentador, duracion, acumulado_seg, hora_salida, estado_nota
FROM v_escaleta_grilla WHERE escaleta_id = 1 ORDER BY posicion;

\echo '--- [LISESC 1] Control de tiempos por bloque'
SELECT bloque, tiempo_disponible, tiempo_usado, diferencia, alerta FROM v_bloque_balance WHERE escaleta_id = 1 ORDER BY bloque;

-- =====================================================================
-- CU-08  RF-14  Reportes por período
-- Comandos: REPCOB / REPREC / REPMAN / REPNOT ["fecha_desde","fecha_hasta"]
-- =====================================================================
SELECT CURRENT_DATE - 13 AS desde, CURRENT_DATE AS hasta \gset

\echo '--- [REPCOB] Coberturas por periodista'
SELECT u.nombre || ' ' || u.apellido AS periodista,
       COUNT(*) FILTER (WHERE c.estado = 'cumplida')     AS cumplidas,
       COUNT(*) FILTER (WHERE c.estado = 'cancelada')    AS canceladas,
       COUNT(*) FILTER (WHERE c.estado = 'reprogramada') AS reprogramadas,
       COUNT(*) FILTER (WHERE c.estado IN ('planificada','en_curso')) AS pendientes,
       COUNT(*) AS total
FROM cobertura c
JOIN cobertura_personal cp ON cp.cobertura_id = c.id AND cp.funcion = 'periodista'
JOIN personal p ON p.id = cp.personal_id JOIN usuario u ON u.id = p.usuario_id
WHERE c.fecha_hora_inicio::date BETWEEN :'desde' AND :'hasta'
GROUP BY periodista ORDER BY total DESC;

\echo '--- [REPREC] Uso de equipos y unidades móviles (horas)'
SELECT r.codigo, tr.nombre AS tipo, COUNT(c.id) AS coberturas,
       ROUND(COALESCE(SUM(EXTRACT(EPOCH FROM (c.fecha_hora_fin - c.fecha_hora_inicio)) / 3600), 0)::numeric, 1) AS horas
FROM recurso r
JOIN tipo_recurso tr ON tr.id = r.tipo_recurso_id
LEFT JOIN cobertura_recurso cr ON cr.recurso_id = r.id
LEFT JOIN cobertura c ON c.id = cr.cobertura_id AND c.estado <> 'cancelada'
                     AND c.fecha_hora_inicio::date BETWEEN :'desde' AND :'hasta'
GROUP BY r.codigo, tr.nombre ORDER BY horas DESC, r.codigo;

\echo '--- [REPMAN] Incidencias del período, su alta y días fuera de servicio'
SELECT r.codigo, tr.nombre AS tipo, i.fecha_hora AS incidencia, i.descripcion,
       a.tipo AS cierre, a.fecha_hora AS cerrada_en,
       ROUND((EXTRACT(EPOCH FROM (COALESCE(a.fecha_hora, LOCALTIMESTAMP) - i.fecha_hora)) / 86400)::numeric, 1) AS dias_fuera,
       r.estado AS estado_actual
FROM mantenimiento i
JOIN recurso r       ON r.id = i.recurso_id
JOIN tipo_recurso tr ON tr.id = r.tipo_recurso_id
LEFT JOIN LATERAL (SELECT m.tipo, m.fecha_hora FROM mantenimiento m
                   WHERE m.recurso_id = i.recurso_id AND m.tipo IN ('alta','baja') AND m.fecha_hora > i.fecha_hora
                   ORDER BY m.fecha_hora LIMIT 1) a ON TRUE
WHERE i.tipo = 'incidencia' AND i.fecha_hora::date BETWEEN :'desde' AND :'hasta'
ORDER BY i.fecha_hora;

\echo '--- [REPNOT] Notas emitidas, observadas y flotadas'
SELECT
  (SELECT COUNT(*) FROM nota WHERE estado = 'emitida' AND fecha BETWEEN :'desde' AND :'hasta') AS emitidas,
  (SELECT COUNT(DISTINCT nota_id) FROM revision_nota WHERE accion = 'observacion' AND fecha_hora::date BETWEEN :'desde' AND :'hasta') AS observadas,
  (SELECT COUNT(*) FROM item_escaleta i JOIN escaleta e ON e.id = i.escaleta_id
    WHERE i.estado = 'flotado' AND e.fecha BETWEEN :'desde' AND :'hasta') AS flotadas;

-- =====================================================================
-- CU-07  Analítica periodística (indicadores, no solo listados)
-- Comandos: ANAPER / ANAEMI / ANACOB / ANANOT ["fecha_desde","fecha_hasta"]
-- =====================================================================
\echo '--- [ANAPER] Producción por periodista: notas, % aprobadas a la primera, tiempo medio de aprobación'
WITH rev AS (
  SELECT nota_id,
         MIN(fecha_hora) FILTER (WHERE accion = 'envio')       AS primer_envio,
         MAX(fecha_hora) FILTER (WHERE accion = 'aprobacion')  AS aprobada_en,
         COUNT(*)        FILTER (WHERE accion = 'observacion') AS observaciones
  FROM revision_nota GROUP BY nota_id)
SELECT u.nombre || ' ' || u.apellido AS periodista,
       COUNT(n.id) AS notas,
       COUNT(*) FILTER (WHERE n.estado IN ('aprobada','emitida')) AS aprobadas_o_emitidas,
       ROUND(100.0 * COUNT(*) FILTER (WHERE r.aprobada_en IS NOT NULL AND r.observaciones = 0)
             / NULLIF(COUNT(*) FILTER (WHERE r.aprobada_en IS NOT NULL), 0), 0) AS pct_aprobadas_a_la_primera,
       ROUND(AVG(EXTRACT(EPOCH FROM (r.aprobada_en - r.primer_envio)) / 60)::numeric, 0) AS min_promedio_aprobacion
FROM nota n
JOIN personal p ON p.id = n.autor_id JOIN usuario u ON u.id = p.usuario_id
LEFT JOIN rev r ON r.nota_id = n.id
WHERE n.fecha BETWEEN :'desde' AND :'hasta'
GROUP BY periodista ORDER BY notas DESC;

\echo '--- [ANAEMI] Duración estimada (suma de la escaleta) vs real'
SELECT e.fecha,
       e.duracion_total AS plantilla_seg,
       est.estimada_seg,
       e.duracion_real AS real_seg,
       e.duracion_real - est.estimada_seg AS desviacion_seg,
       (SELECT COUNT(*) FROM item_escaleta i WHERE i.escaleta_id = e.id AND i.ultima_hora) AS items_ultima_hora,
       (SELECT ROUND(AVG(g.duracion_real - g.duracion)::numeric, 1) FROM v_escaleta_grilla g
         WHERE g.escaleta_id = e.id AND g.duracion_real IS NOT NULL AND g.tipo <> 'corte') AS desviacion_media_por_item
FROM escaleta e
CROSS JOIN LATERAL (SELECT COALESCE(SUM(g.duracion), 0) AS estimada_seg
                    FROM v_escaleta_grilla g WHERE g.escaleta_id = e.id) est
WHERE e.estado = 'emitida' AND e.fecha BETWEEN :'desde' AND :'hasta'
ORDER BY e.fecha;

\echo '--- [ANACOB] Cumplimiento de coberturas por tipo'
SELECT tipo, COUNT(*) AS total,
       ROUND(100.0 * COUNT(*) FILTER (WHERE estado = 'cumplida')
             / NULLIF(COUNT(*) FILTER (WHERE estado IN ('cumplida','cancelada','reprogramada')), 0), 0) AS pct_cumplidas
FROM cobertura WHERE fecha_hora_inicio::date BETWEEN :'desde' AND :'hasta'
GROUP BY tipo ORDER BY total DESC;

\echo '--- [ANANOT] Duración media por tipo de pieza (segundos)'
SELECT d.tipo_pieza, COUNT(*) AS notas, ROUND(AVG(d.duracion_total), 0) AS duracion_media
FROM v_nota_duracion d JOIN nota n ON n.id = d.nota_id
WHERE n.fecha BETWEEN :'desde' AND :'hasta'
GROUP BY d.tipo_pieza ORDER BY duracion_media DESC;
