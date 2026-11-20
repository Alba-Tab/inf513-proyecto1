-- =====================================================================
-- Pruebas de integridad - Canal 11 TVU - db_grupo14sa
-- Ejecutar después de script_db_grupo14sa.sql y datos_prueba.sql.
-- Cada prueba dice OK o FALLA; todo se deshace al final (ROLLBACK).
-- Los códigos C1..C10 y A1 son los cambios de bd_formal_v2.md, sección 1.
--   psql -d db_grupo14sa -f pruebas_integridad.sql
-- =====================================================================
\set ON_ERROR_STOP on
\pset footer off
BEGIN;
SET CONSTRAINTS ALL IMMEDIATE;

CREATE FUNCTION pg_temp.rechaza(nombre text, sentencia text) RETURNS text AS $$
BEGIN
  EXECUTE sentencia;
  RETURN 'FALLA  ' || nombre || '  (se aceptó y debía rechazarse)';
EXCEPTION WHEN others THEN
  RETURN 'OK     ' || nombre || '  -> ' || SQLERRM;
END $$ LANGUAGE plpgsql;

CREATE FUNCTION pg_temp.acepta(nombre text, sentencia text) RETURNS text AS $$
BEGIN
  EXECUTE sentencia;
  RETURN 'OK     ' || nombre;
EXCEPTION WHEN others THEN
  RETURN 'FALLA  ' || nombre || '  -> ' || SQLERRM;
END $$ LANGUAGE plpgsql;

CREATE FUNCTION pg_temp.verifica(nombre text, condicion boolean) RETURNS text AS $$
  SELECT CASE WHEN condicion THEN 'OK     ' ELSE 'FALLA  ' END || nombre;
$$ LANGUAGE sql;

SELECT resultado FROM (VALUES
 -- [C1]
 (pg_temp.verifica('C1  la bitácora guarda un asunto de más de 255 caracteres',
    (SELECT MAX(length(asunto)) FROM bitacora_comando) > 255)),
 (pg_temp.verifica('C1  la bitácora acepta comando NULL (asunto sin comando)',
    EXISTS (SELECT 1 FROM bitacora_comando WHERE comando IS NULL))),
 -- [C2]
 (pg_temp.verifica('C2  una cobertura reprogramada no ocupa (v_ocupacion_recurso solo ve planificada/en_curso)',
    NOT EXISTS (SELECT 1 FROM v_ocupacion_recurso WHERE estado NOT IN ('planificada','en_curso')))),
 -- [C3]
 (pg_temp.verifica('C3  todas las plantillas de prueba están ok',
    NOT EXISTS (SELECT 1 FROM v_plantilla_control WHERE observacion <> 'ok'))),
 (pg_temp.verifica('C3  la escaleta de ayer ahora tiene sus 3 cortes (faltaba el Corte 3)',
    (SELECT COUNT(*) FROM item_escaleta WHERE escaleta_id = 2 AND tipo = 'corte') = 3)),
 (pg_temp.rechaza ('C3  un corte con orden distinto de 999',
    $q$INSERT INTO item_escaleta (escaleta_id, orden, bloque, tipo, titulo, duracion_estimada) VALUES (1, 7, 'A', 'corte', 'x', 60)$q$)),
 (pg_temp.rechaza ('C3  un ítem normal con orden 999',
    $q$INSERT INTO item_escaleta (escaleta_id, orden, bloque, tipo, titulo, duracion_estimada) VALUES (1, 999, 'C', 'otro', 'x', 60)$q$)),
 (pg_temp.rechaza ('C3  un segundo corte en el mismo bloque',
    $q$UPDATE item_escaleta SET bloque = 'A' WHERE escaleta_id = 1 AND tipo = 'corte' AND bloque = 'B'$q$)),
 (pg_temp.rechaza ('C3  un corte con nota o presentador',
    $q$UPDATE item_escaleta SET presentador_id = 10 WHERE escaleta_id = 1 AND tipo = 'corte' AND bloque = 'A'$q$)),
 -- [C4]
 (pg_temp.rechaza ('C4  borrar un recurso con historial de mantenimiento (CAM-04)',
    $q$DELETE FROM recurso WHERE codigo = 'CAM-04'$q$)),
 -- [C5]
 (pg_temp.rechaza ('C5  grilla de 22:30 a 00:30 (cruza la medianoche)',
    $q$INSERT INTO grilla (programa_id, dia_semana, hora_inicio, hora_fin) VALUES (4, 6, '22:30', '00:30')$q$)),
 (pg_temp.acepta  ('C5  grilla de 23:00 a 24:00',
    $q$INSERT INTO grilla (programa_id, dia_semana, hora_inicio, hora_fin) VALUES (4, 6, '23:00', '24:00')$q$)),
 -- [C6]
 (pg_temp.rechaza ('C6  dos ítems con el mismo orden en el mismo bloque',
    $q$INSERT INTO item_escaleta (escaleta_id, orden, bloque, tipo, titulo, duracion_estimada) VALUES (1, 1, 'A', 'otro', 'x', 10)$q$)),
 (pg_temp.rechaza ('C6  la misma nota dos veces programada en la emisión',
    $q$INSERT INTO item_escaleta (escaleta_id, orden, bloque, tipo, nota_id, duracion_estimada) VALUES (1, 2, 'C', 'nota', 1, 0)$q$)),
 (pg_temp.acepta  ('C6  una nota flotada puede volver a programarse en otro bloque',
    $q$INSERT INTO item_escaleta (escaleta_id, orden, bloque, tipo, nota_id, duracion_estimada) VALUES (1, 2, 'D', 'nota', 8, 0)$q$)),
 -- [C8]
 (pg_temp.rechaza ('C8  ausencia que termina antes de empezar',
    $q$INSERT INTO ausencia (personal_id, tipo, fecha_hora_inicio, fecha_hora_fin) VALUES (1, 'permiso', '2026-10-14 11:00', '2026-10-14 09:00')$q$)),
 (pg_temp.verifica('C8  un permiso de 09:00 a 11:00 bloquea las 10:00 y no las 12:00',
    EXISTS (SELECT 1 FROM ausencia WHERE personal_id = 1 AND CURRENT_DATE + 1 + TIME '10:00' >= fecha_hora_inicio AND CURRENT_DATE + 1 + TIME '10:00' < fecha_hora_fin)
    AND NOT EXISTS (SELECT 1 FROM ausencia WHERE personal_id = 1 AND CURRENT_DATE + 1 + TIME '12:00' >= fecha_hora_inicio AND CURRENT_DATE + 1 + TIME '12:00' < fecha_hora_fin))),
 -- [C9]
 (pg_temp.verifica('C9  existe una cobertura de 22:00 a 02:00 del día siguiente',
    EXISTS (SELECT 1 FROM cobertura WHERE fecha_hora_fin::date > fecha_hora_inicio::date))),
 (pg_temp.verifica('C9  una reserva a la 01:00 choca con la cobertura que empezó a las 22:00 del día anterior',
    EXISTS (SELECT 1 FROM v_ocupacion_recurso o JOIN recurso r ON r.id = o.recurso_id
            WHERE r.codigo = 'MOV-02' AND o.fecha_hora_inicio < CURRENT_DATE + 2 + TIME '03:00'
              AND CURRENT_DATE + 2 + TIME '01:00' < o.fecha_hora_fin))),
 (pg_temp.rechaza ('C9  cobertura que termina antes de empezar',
    $q$INSERT INTO cobertura (titulo, tipo, fecha_hora_inicio, fecha_hora_fin, lugar, creado_por) VALUES ('x', 'conferencia', '2026-10-14 10:00', '2026-10-14 09:00', 'x', 2)$q$)),
 -- [A1]
 (pg_temp.rechaza ('A1  dos rótulos con el mismo orden en una nota',
    $q$INSERT INTO nota_rotulo (nota_id, orden, nombre) VALUES (2, 1, 'x')$q$))
) AS t(resultado);

\echo '--- [C3] Si se borra el corte 2 de la plantilla 1, el bloque B no desaparece: su tiempo sale NULL y el control lo marca'
DELETE FROM plantilla_corte WHERE plantilla_id = 1 AND numero = 2;
SELECT bloque, inicio_bloque, fin_bloque, tiempo_disponible FROM v_plantilla_bloque WHERE plantilla_id = 1 ORDER BY numero_bloque;
SELECT plantilla_id, cortes, observacion FROM v_plantilla_control WHERE plantilla_id = 1;

ROLLBACK;
