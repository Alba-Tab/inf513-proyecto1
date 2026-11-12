-- =====================================================================
-- Datos de prueba - Canal 11 TVU - db_grupo14sa
-- Ejecutar DESPUÉS de script_db_grupo14sa.sql (BD recién creada: los id empiezan en 1).
-- Corresponde al esquema v2: ausencias y coberturas con fecha y hora, cortes generados desde la plantilla.
-- Las fechas son relativas a CURRENT_DATE para que la demo funcione cualquier día.
-- Los correos @canal11.test son de ejemplo: reemplazarlos por los correos
-- reales con los que el grupo va a enviar los comandos.
-- Contraseña de todas las cuentas de prueba: canal11. Se guarda con bcrypt
-- ($2y$, costo 10), cada una con su propia sal; los hashes se generaron con
-- la misma librería que usa la aplicación (at.favre.lib:bcrypt).
-- =====================================================================

-- ---------- CU-01 ----------
-- Roles según el organigrama (Jefatura de Prensa y Área Técnica y de Producción)
INSERT INTO rol (nombre, descripcion) VALUES
 ('Director',             'Máxima autoridad del canal. Administra usuarios, roles y permisos; consulta reportes y analítica'), -- 1
 ('Jefe de Prensa',       'Planifica coberturas, asigna personal y recursos, aprueba notas, gestiona turnos, plantillas y escaleta'), -- 2
 ('Periodista',           'Redacta notas y guiones, adjunta material y consulta sus coberturas'),                -- 3
 ('Camarografo',          'Consulta sus asignaciones, recibe y devuelve equipos y reporta incidencias'),         -- 4
 ('Editor de video',      'Edita el material audiovisual y actualiza su estado'),                                -- 5
 ('Presentador',          'Presentadores y conductores de TV: consultan la escaleta, los guiones y sus turnos'), -- 6
 ('Jefe de Area Tecnica', 'Área Técnica y de Producción: inventario, entrega/devolución y mantenimiento de equipos y unidades móviles'); -- 7

INSERT INTO usuario (nombre, apellido, ci, correo, telefono, password, rol_id) VALUES
 ('Américo',       'Claros',            '3100001', 'director@canal11.test',          NULL,       '$2y$10$bgTQ8LSddyhQ1N7KoaCnIOjM/oGDh26g.HsEPXQ8u8Z.VJFAnkDUy', 1), -- 1
 ('Roberto',       'Arteaga',           '3100002', 'jefe.prensa@canal11.test',       '70010002', '$2y$10$P6b1x3H4wMRyUCWXiHFLneTcNe8bqHnkWVICGbA/dmbUazPVF8dCy', 2), -- 2
 ('Carla',         'Rojas Suárez',      '3100003', 'carla.rojas@canal11.test',       '71234580', '$2y$10$gJJXaRxXt3QXilqEZNrjk.ffGik4fnHj6mPl7A2rNY5gEH4wOroaa', 3), -- 3
 ('Marcelo',       'Justiniano Égüez',  '3100004', 'marcelo.justiniano@canal11.test','72145566', '$2y$10$5N/WcTlET.tIT/PeBh4kCuo/yO/xYJVBY.OOxibAKKPv54QVsMau.', 3), -- 4
 ('Daniela',       'Vaca Pinto',        '3100005', 'daniela.vaca@canal11.test',      '76301122', '$2y$10$zNK4gjRAQiVKrbvEqvn6QenBYgCXGcCw15F2iMij2fgzBwJb9UBYy', 3), -- 5
 ('Luis Fernando', 'Añez Rivero',       '3100006', 'luis.anez@canal11.test',         '69012345', '$2y$10$1YzyRdv40c6wbvnwE56Ek.vPBjTXdltO/08s1J.8M/ye9fcWm8Dii', 3), -- 6
 ('Jorge',         'Mendoza Ribera',    '3100007', 'jorge.mendoza@canal11.test',     '70088912', '$2y$10$syEfiRkIye2e5uW3vUoUgepNNrciuWfZ0/2qnKHEBYHhkFgDDvXwG', 4), -- 7
 ('Rubén',         'Cuéllar Antelo',    '3100008', 'ruben.cuellar@canal11.test',     '77654321', '$2y$10$kVxNd6Cka9X14w6ZwWeNTOM9AkeC3CzsIcYsfgjbhTKEas8PgnCTq', 4), -- 8
 ('Wilmer',        'Choque Mamani',     '3100009', 'wilmer.choque@canal11.test',     '71987654', '$2y$10$4/x0RQSnDfQmWvsNZV2hrehgeemnocmWHPoztP8O6zy7g1mMlsZQW', 4), -- 9
 ('Patricia',      'Saucedo Melgar',    '3100010', 'patricia.saucedo@canal11.test',  '72233445', '$2y$10$i0Vc.SmWAmgta78pwpqGuOaVh1rrcunrdMzYDVD3jXUEBs/lGzKNu', 5), -- 10
 ('Andrés',        'Parada Rocha',      '3100011', 'andres.parada@canal11.test',     '60123987', '$2y$10$2kWNWreig1pBg6p3DLNBQuc3zHPQEdpKEf.oQYyGvhGpvXKGpu/K.', 5), -- 11
 ('Mariana',       'Terceros Lara',     '3100012', 'mariana.terceros@canal11.test',  '70456789', '$2y$10$..vs1tiHW.fQOKFQ5yY3JeQY1ipRFiw/SKlWsQNzNSrLPWahltub.', 6), -- 12
 ('Gonzalo',       'Salvatierra Roca',  '3100013', 'gonzalo.salvatierra@canal11.test','75551234','$2y$10$C.bCPHqNpEn8nibi6R381emi7hyY8pvo2u346qsPrY5PdnRwgk9lm', 6), -- 13
 ('Freddy',        'Montaño Vargas',    '3100014', 'freddy.montano@canal11.test',    '71112233', '$2y$10$VfC/Wq45UunMeY/i0hYwn..8SkXUN.UlB2NzS8GNNRreRW4jk1eTO', 6), -- 14 conductor de TV
 ('Hugo',          'Flores Arias',      '3100015', 'area.tecnica@canal11.test',      '70010015', '$2y$10$q4xW/EO5dRqkIiEJpmDZauqNQQXvbS7FqttMPdar/QBe2qaMChRUm', 7); -- 15

-- <<PERMISOS: generado desde el catálogo de comandos>>
-- Cada comando del sistema y los roles que pueden ejecutarlo. AYUDA no necesita permiso.
INSERT INTO permiso (comando, descripcion, caso_uso) VALUES
 ('LISUSU', 'Listar usuarios (Periodista, Camarógrafo, Editor y Presentador solo reciben sus propios datos)', 'CU-01'),
 ('INSUSU', 'Registrar usuario', 'CU-01'),
 ('UPDUSU', 'Modificar usuario', 'CU-01'),
 ('DELUSU', 'Desactivar usuario', 'CU-01'),
 ('LISROL', 'Listar roles', 'CU-01'),
 ('LISRPM', 'Listar los permisos de un rol', 'CU-01'),
 ('INSRPM', 'Otorgar un comando a un rol', 'CU-01'),
 ('DELRPM', 'Quitar un comando a un rol', 'CU-01'),
 ('LISPER', 'Listar personal', 'CU-02'),
 ('INSPER', 'Registrar ficha del personal', 'CU-02'),
 ('UPDPER', 'Modificar ficha del personal', 'CU-02'),
 ('DELPER', 'Desactivar personal', 'CU-02'),
 ('LISTUR', 'Listar turnos', 'CU-02'),
 ('INSTUR', 'Registrar turno', 'CU-02'),
 ('LISAUS', 'Listar ausencias', 'CU-02'),
 ('INSAUS', 'Registrar permiso, vacación o baja (días completos o con hora)', 'CU-02'),
 ('DISPER', 'Personal disponible en una fecha y hora', 'CU-02'),
 ('LISREC', 'Listar recursos', 'CU-03'),
 ('INSREC', 'Registrar recurso', 'CU-03'),
 ('UPDREC', 'Modificar recurso', 'CU-03'),
 ('DELREC', 'Dar de baja un recurso', 'CU-03'),
 ('INCREC', 'Reportar daño o incidencia (pasa a mantenimiento)', 'CU-03'),
 ('ALTREC', 'Dar de alta tras la reparación', 'CU-03'),
 ('LISPRE', 'Listar préstamos', 'CU-03'),
 ('INSPRE', 'Entregar recurso (préstamo)', 'CU-03'),
 ('DEVPRE', 'Registrar devolución', 'CU-03'),
 ('LISCOB', 'Agenda de coberturas', 'CU-04'),
 ('INSCOB', 'Planificar cobertura (si la hora de fin es menor, termina al día siguiente)', 'CU-04'),
 ('UPDCOB', 'Modificar o reprogramar cobertura (revalida conflictos)', 'CU-04'),
 ('DELCOB', 'Cancelar cobertura (libera recursos)', 'CU-04'),
 ('INSCPE', 'Asignar personal (valida turno, ausencias y choques)', 'CU-04'),
 ('DELCPE', 'Quitar personal de la cobertura', 'CU-04'),
 ('INSCRE', 'Reservar recurso (valida estado y choques)', 'CU-04'),
 ('DELCRE', 'Liberar un recurso de la cobertura', 'CU-04'),
 ('HOJCOB', 'Hoja de asignación de la cobertura', 'CU-04'),
 ('RETCOB', 'Registrar retorno y liberar personal y recursos', 'CU-04'),
 ('LISNOT', 'Listar notas', 'CU-05'),
 ('INSNOT', 'Redactar nota (el guion va en el cuerpo del correo)', 'CU-05'),
 ('UPDNOT', 'Modificar datos de la nota', 'CU-05'),
 ('DELNOT', 'Dar de baja la nota', 'CU-05'),
 ('GUINOT', 'Reemplazar el guion (cuerpo del correo; crea una versión)', 'CU-05'),
 ('ENVNOT', 'Enviar la nota a revisión', 'CU-05'),
 ('APRNOT', 'Aprobar la nota', 'CU-05'),
 ('OBSNOT', 'Observar la nota con motivo', 'CU-05'),
 ('LISPRG', 'Listar programas', 'CU-06'),
 ('INSPRG', 'Registrar programa', 'CU-06'),
 ('LISGRI', 'Ver la grilla de un rango de fechas', 'CU-06'),
 ('INSGRI', 'Agregar horario a la grilla (valida choques)', 'CU-06'),
 ('LISESC', 'Escaleta con tiempos y alertas por bloque', 'CU-06'),
 ('INSESC', 'Generar la escaleta del día y copiar los cortes de la plantilla', 'CU-06'),
 ('UPDESC', 'Cerrar, reabrir o registrar como emitida', 'CU-06'),
 ('INSITE', 'Agregar ítem al final del bloque (solo notas aprobadas; no acepta cortes)', 'CU-06'),
 ('DELITE', 'Dar de baja un ítem', 'CU-06'),
 ('EXPESC', 'Exportar la escaleta (adjunto HTML)', 'CU-06'),
 ('ANAPER', 'Producción por periodista: notas, % aprobadas a la primera y tiempo medio de aprobación', 'CU-07'),
 ('ANACOB', 'Cumplimiento de coberturas por tipo', 'CU-07'),
 ('REPCOB', 'Coberturas por periodista', 'CU-08'),
 ('REPREC', 'Uso de equipos y unidades móviles (horas)', 'CU-08'),
 ('LISBIT', 'Bitácora de comandos recibidos (RNF-24)', 'CU-08');

INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT r.id, p.id FROM (VALUES
 ('Director', 'LISUSU'),
 ('Jefe de Prensa', 'LISUSU'),
 ('Periodista', 'LISUSU'),
 ('Camarografo', 'LISUSU'),
 ('Editor de video', 'LISUSU'),
 ('Presentador', 'LISUSU'),
 ('Director', 'INSUSU'),
 ('Director', 'UPDUSU'),
 ('Director', 'DELUSU'),
 ('Director', 'LISROL'),
 ('Director', 'LISRPM'),
 ('Director', 'INSRPM'),
 ('Director', 'DELRPM'),
 ('Director', 'LISPER'),
 ('Jefe de Prensa', 'LISPER'),
 ('Jefe de Area Tecnica', 'LISPER'),
 ('Jefe de Prensa', 'INSPER'),
 ('Jefe de Prensa', 'UPDPER'),
 ('Jefe de Prensa', 'DELPER'),
 ('Director', 'LISTUR'),
 ('Jefe de Prensa', 'LISTUR'),
 ('Jefe de Area Tecnica', 'LISTUR'),
 ('Periodista', 'LISTUR'),
 ('Camarografo', 'LISTUR'),
 ('Editor de video', 'LISTUR'),
 ('Presentador', 'LISTUR'),
 ('Jefe de Prensa', 'INSTUR'),
 ('Director', 'LISAUS'),
 ('Jefe de Prensa', 'LISAUS'),
 ('Jefe de Prensa', 'INSAUS'),
 ('Director', 'DISPER'),
 ('Jefe de Prensa', 'DISPER'),
 ('Jefe de Area Tecnica', 'DISPER'),
 ('Director', 'LISREC'),
 ('Jefe de Prensa', 'LISREC'),
 ('Jefe de Area Tecnica', 'LISREC'),
 ('Camarografo', 'LISREC'),
 ('Jefe de Area Tecnica', 'INSREC'),
 ('Jefe de Area Tecnica', 'UPDREC'),
 ('Jefe de Area Tecnica', 'DELREC'),
 ('Jefe de Area Tecnica', 'INCREC'),
 ('Camarografo', 'INCREC'),
 ('Jefe de Area Tecnica', 'ALTREC'),
 ('Jefe de Prensa', 'LISPRE'),
 ('Jefe de Area Tecnica', 'LISPRE'),
 ('Jefe de Area Tecnica', 'INSPRE'),
 ('Jefe de Area Tecnica', 'DEVPRE'),
 ('Director', 'LISCOB'),
 ('Jefe de Prensa', 'LISCOB'),
 ('Jefe de Area Tecnica', 'LISCOB'),
 ('Periodista', 'LISCOB'),
 ('Camarografo', 'LISCOB'),
 ('Editor de video', 'LISCOB'),
 ('Presentador', 'LISCOB'),
 ('Jefe de Prensa', 'INSCOB'),
 ('Jefe de Prensa', 'UPDCOB'),
 ('Jefe de Prensa', 'DELCOB'),
 ('Jefe de Prensa', 'INSCPE'),
 ('Jefe de Prensa', 'DELCPE'),
 ('Jefe de Prensa', 'INSCRE'),
 ('Jefe de Prensa', 'DELCRE'),
 ('Jefe de Prensa', 'HOJCOB'),
 ('Jefe de Area Tecnica', 'HOJCOB'),
 ('Periodista', 'HOJCOB'),
 ('Camarografo', 'HOJCOB'),
 ('Presentador', 'HOJCOB'),
 ('Jefe de Prensa', 'RETCOB'),
 ('Periodista', 'RETCOB'),
 ('Director', 'LISNOT'),
 ('Jefe de Prensa', 'LISNOT'),
 ('Periodista', 'LISNOT'),
 ('Editor de video', 'LISNOT'),
 ('Presentador', 'LISNOT'),
 ('Jefe de Prensa', 'INSNOT'),
 ('Periodista', 'INSNOT'),
 ('Jefe de Prensa', 'UPDNOT'),
 ('Periodista', 'UPDNOT'),
 ('Jefe de Prensa', 'DELNOT'),
 ('Periodista', 'DELNOT'),
 ('Jefe de Prensa', 'GUINOT'),
 ('Periodista', 'GUINOT'),
 ('Jefe de Prensa', 'ENVNOT'),
 ('Periodista', 'ENVNOT'),
 ('Jefe de Prensa', 'APRNOT'),
 ('Jefe de Prensa', 'OBSNOT'),
 ('Director', 'LISPRG'),
 ('Jefe de Prensa', 'LISPRG'),
 ('Jefe de Area Tecnica', 'LISPRG'),
 ('Periodista', 'LISPRG'),
 ('Camarografo', 'LISPRG'),
 ('Editor de video', 'LISPRG'),
 ('Presentador', 'LISPRG'),
 ('Jefe de Prensa', 'INSPRG'),
 ('Director', 'LISGRI'),
 ('Jefe de Prensa', 'LISGRI'),
 ('Jefe de Area Tecnica', 'LISGRI'),
 ('Periodista', 'LISGRI'),
 ('Camarografo', 'LISGRI'),
 ('Editor de video', 'LISGRI'),
 ('Presentador', 'LISGRI'),
 ('Jefe de Prensa', 'INSGRI'),
 ('Director', 'LISESC'),
 ('Jefe de Prensa', 'LISESC'),
 ('Periodista', 'LISESC'),
 ('Editor de video', 'LISESC'),
 ('Presentador', 'LISESC'),
 ('Jefe de Prensa', 'INSESC'),
 ('Jefe de Prensa', 'UPDESC'),
 ('Jefe de Prensa', 'INSITE'),
 ('Jefe de Prensa', 'DELITE'),
 ('Jefe de Prensa', 'EXPESC'),
 ('Editor de video', 'EXPESC'),
 ('Presentador', 'EXPESC'),
 ('Director', 'ANAPER'),
 ('Jefe de Prensa', 'ANAPER'),
 ('Director', 'ANACOB'),
 ('Jefe de Prensa', 'ANACOB'),
 ('Director', 'REPCOB'),
 ('Jefe de Prensa', 'REPCOB'),
 ('Director', 'REPREC'),
 ('Jefe de Prensa', 'REPREC'),
 ('Director', 'LISBIT')
) AS x(rol, comando)
JOIN rol r ON r.nombre = x.rol
JOIN permiso p ON p.comando = x.comando;
-- <</PERMISOS>>

-- ---------- CU-02 ----------
INSERT INTO turno (nombre, hora_inicio, hora_fin, dias, descripcion) VALUES
 ('Mañana',          '06:00', '14:00', '1,2,3,4,5', 'Turno mañana de lunes a viernes'),        -- 1
 ('Tarde',           '14:00', '22:00', '1,2,3,4,5', 'Turno tarde de lunes a viernes'),         -- 2
 ('Noche',           '22:00', '06:00', '1,2,3,4,5', 'Cruza la medianoche'),                    -- 3
 ('Fin de semana',   '08:00', '20:00', '6,7',       'Sábados y domingos'),                     -- 4
 ('Parcial 13-19',   '13:00', '19:00', '1,2,3,4,5', 'Practicantes de Comunicación Social'),    -- 5
 ('Parcial 14-18',   '14:00', '18:00', '1,2,3,4,5', 'Practicantes de Comunicación Social');    -- 6

INSERT INTO personal (usuario_id, cargo, condicion_laboral, unidad_academica, turno_id, fecha_ingreso) VALUES
 ( 3, 'periodista',  'planta',      NULL, 1, '2019-02-01'), -- 1  Carla Rojas
 ( 4, 'periodista',  'planta',      NULL, 1, '2017-03-15'), -- 2  Marcelo Justiniano
 ( 5, 'periodista',  'contrato',    NULL, 2, '2024-01-10'), -- 3  Daniela Vaca
 ( 6, 'periodista',  'practicante', 'Comunicación Social - Fac. de Humanidades', 5, '2026-08-01'), -- 4 Luis F. Añez
 ( 7, 'camarografo', 'planta',      NULL, 1, '2015-06-01'), -- 5  Jorge Mendoza
 ( 8, 'camarografo', 'contrato',    NULL, 1, '2023-04-03'), -- 6  Rubén Cuéllar
 ( 9, 'camarografo', 'planta',      NULL, 2, '2018-09-17'), -- 7  Wilmer Choque
 (10, 'editor',      'planta',      NULL, 2, '2016-11-02'), -- 8  Patricia Saucedo
 (11, 'editor',      'practicante', 'Comunicación Social - Fac. de Humanidades', 6, '2026-08-01'), -- 9 Andrés Parada
 (12, 'presentador', 'planta',      NULL, 2, '2014-05-20'), -- 10 Mariana Terceros
 (13, 'presentador', 'contrato',    NULL, 2, '2022-02-14'), -- 11 Gonzalo Salvatierra
 (14, 'conductor',   'planta',      NULL, 1, '2012-07-09'), -- 12 Freddy Montaño (conductor de TV)
 ( 2, 'jefe_prensa', 'planta',      NULL, 1, '2010-03-01'); -- 13 Roberto Arteaga: el Jefe de Prensa también redacta notas

-- Rangos con fin exclusivo: un día completo va de 00:00 a 00:00 del día siguiente (R11)
INSERT INTO ausencia (personal_id, tipo, fecha_hora_inicio, fecha_hora_fin, motivo) VALUES
 (6, 'permiso',  CURRENT_DATE + 2,                 CURRENT_DATE + 3,                 'Trámite personal'),
 (8, 'vacacion', CURRENT_DATE + 7,                 CURRENT_DATE + 15,                'Vacación anual'),
 (1, 'permiso',  CURRENT_DATE + 1 + TIME '09:00', CURRENT_DATE + 1 + TIME '11:00', 'Cita médica'); -- 2 horas: no bloquea el resto del día

-- ---------- CU-03 ----------
INSERT INTO tipo_recurso (nombre, es_unidad_movil, descripcion) VALUES
 ('Camara',          FALSE, 'Cámaras de video ENG'),        -- 1
 ('Microfono',       FALSE, 'Micrófonos inalámbricos y de cañón'), -- 2
 ('Tripode',         FALSE, 'Trípodes y soportes'),         -- 3
 ('Luces',           FALSE, 'Iluminación portátil'),        -- 4
 ('Unidad movil',    TRUE,  'Vehículos para coberturas'),   -- 5
 ('Isla de edicion', FALSE, 'Estaciones de edición de video'); -- 6

INSERT INTO recurso (codigo, tipo_recurso_id, marca, modelo, placa, caracteristicas, estado, observacion) VALUES
 ('CAM-01', 1, 'Sony',       'PXW-Z190',   NULL, '4K, 3 sensores 1/3"', 'operativo', NULL),                 -- 1
 ('CAM-02', 1, 'Sony',       'PXW-Z190',   NULL, '4K, 3 sensores 1/3"', 'operativo', NULL),                 -- 2
 ('CAM-03', 1, 'Panasonic',  'AG-CX350',   NULL, '4K, streaming',       'operativo', NULL),                 -- 3
 ('CAM-04', 1, 'Panasonic',  'AG-CX350',   NULL, '4K, streaming',       'mantenimiento', 'Falla en el visor LCD; enviada a servicio técnico'), -- 4
 ('CAM-05', 1, 'Canon',      'XF605',      NULL, '4K liviana',          'operativo', NULL),                 -- 5
 ('MIC-01', 2, 'Sennheiser', 'EW 112P G4', NULL, 'Corbatero inalámbrico','operativo', NULL),                -- 6
 ('MIC-02', 2, 'Sennheiser', 'EW 112P G4', NULL, 'Corbatero inalámbrico','operativo', NULL),                -- 7
 ('MIC-03', 2, 'Rode',       'NTG4+',      NULL, 'Cañón',               'operativo', NULL),                 -- 8
 ('MIC-04', 2, 'Shure',      'SM63',       NULL, 'De mano',             'operativo', NULL),                 -- 9
 ('TRI-01', 3, 'Manfrotto',  '504X',       NULL, 'Cabezal fluido',      'operativo', NULL),                 -- 10
 ('TRI-02', 3, 'Sachtler',   'Ace M',      NULL, 'Cabezal fluido',      'operativo', NULL),                 -- 11
 ('MOV-01', 5, 'Toyota',     'Hilux',      '4521-KTD', 'Doble cabina 4x4', 'operativo', NULL),              -- 12
 ('MOV-02', 5, 'Nissan',     'Frontier',   '3890-GHP', 'Doble cabina 4x4', 'operativo', NULL),              -- 13
 ('ISL-01', 6, 'Apple',      'Mac Studio', NULL, 'Adobe Premiere',      'operativo', NULL),                 -- 14
 ('ISL-02', 6, 'HP',         'Z4',         NULL, 'DaVinci Resolve',     'operativo', NULL),                 -- 15
 ('LUZ-01', 4, 'Aputure',    'Amaran 200d',NULL, 'LED 200 W',           'operativo', NULL);                 -- 16

-- ---------- CU-04 ----------
INSERT INTO cobertura (titulo, tema, tipo, fecha_hora_inicio, fecha_hora_fin, lugar, prioridad, fuente, indicaciones, estado, creado_por) VALUES
 -- hoy
 ('Presupuesto departamental 2027', 'Gobernación presenta el anteproyecto de presupuesto', 'conferencia', CURRENT_DATE + TIME '09:00', CURRENT_DATE + TIME '10:30', 'Gobernación de Santa Cruz, Salón Chiquitano', 'alta', 'Unidad de Comunicación - Gobernación', 'Declaraciones del secretario de Economía y planos de sala.', 'cumplida', 2), -- 1
 ('Paro de transporte en el 4to anillo', 'Transportistas bloquean por falta de diésel', 'hecho_noticioso', CURRENT_DATE + TIME '07:30', CURRENT_DATE + TIME '12:00', '4to anillo y Av. Banzer', 'alta', 'Fed. de Autotransporte 1ro de Mayo', 'Imágenes del bloqueo y testimonios de pasajeros.', 'en_curso', 2), -- 2
 ('Posesión de autoridades facultativas', 'El rector posesiona a los decanos electos', 'acto_institucional', CURRENT_DATE + TIME '11:30', CURRENT_DATE + TIME '13:00', 'Paraninfo Universitario, UAGRM', 'media', 'Dirección de Comunicación UAGRM', 'Juramento de decanos y entrevista breve al rector.', 'planificada', 2), -- 3
 ('Clásico cruceño: conferencia previa', 'Blooming y Oriente antes del clásico', 'deportes', CURRENT_DATE + TIME '15:00', CURRENT_DATE + TIME '16:30', 'Estadio Ramón Tahuichi Aguilera', 'media', 'Prensa Club Blooming', 'Conferencia de ambos técnicos.', 'planificada', 2), -- 4
 ('Plan de bacheo en el Plan 3000', 'Entrevista al secretario de Obras Públicas', 'entrevista', CURRENT_DATE + TIME '17:00', CURRENT_DATE + TIME '18:30', 'Subalcaldía Distrito 8, Plan 3000', 'media', 'Secretaría de Obras Públicas GAMSC', 'Contrastar la denuncia vecinal.', 'planificada', 2), -- 5
 ('Incendios en la Chiquitanía: informe del COED', 'Reporte de focos de calor activos', 'conferencia', CURRENT_DATE + TIME '15:30', CURRENT_DATE + TIME '17:00', 'COED Departamental', 'alta', 'Dirección de Gestión de Riesgos', 'Pendiente de asignar equipo.', 'planificada', 2), -- 6
 -- próximos días
 ('Feria de ciencia y tecnología UAGRM', 'Estudiantes presentan proyectos', 'acto_institucional', CURRENT_DATE + 1 + TIME '10:00', CURRENT_DATE + 1 + TIME '12:00', 'Módulos 236, Ciudad Universitaria', 'baja', 'Vicerrectorado UAGRM', NULL, 'planificada', 2), -- 7
 ('Concejo trata la tarifa del micro', 'Propuesta de pasaje a Bs 2,50', 'acto_institucional', CURRENT_DATE + 2 + TIME '09:00', CURRENT_DATE + 2 + TIME '11:00', 'Concejo Municipal de Santa Cruz', 'alta', 'Presidencia del Concejo', NULL, 'planificada', 2), -- 8
 -- días anteriores (para reportes y analítica)
 ('Marcha de gremiales en el centro', NULL, 'hecho_noticioso', CURRENT_DATE - 1 + TIME '08:00', CURRENT_DATE - 1 + TIME '09:30', 'Plaza 24 de Septiembre', 'media', NULL, NULL, 'cumplida', 2),      -- 9
 ('Conferencia de la FUL', NULL, 'conferencia', CURRENT_DATE - 1 + TIME '10:30', CURRENT_DATE - 1 + TIME '12:30', 'Módulo 236, UAGRM', 'alta', NULL, NULL, 'cumplida', 2),                        -- 10
 ('Sesión de la Asamblea Legislativa Departamental', NULL, 'acto_institucional', CURRENT_DATE - 1 + TIME '15:00', CURRENT_DATE - 1 + TIME '16:00', 'ALD Santa Cruz', 'baja', NULL, NULL, 'cancelada', 2), -- 11
 ('Torneo interfacultativo de fútbol', NULL, 'deportes', CURRENT_DATE - 2 + TIME '08:15', CURRENT_DATE - 2 + TIME '10:45', 'Coliseo Universitario', 'baja', NULL, NULL, 'cumplida', 2),            -- 12
 ('Entrevista al director del Sedes', NULL, 'entrevista', CURRENT_DATE - 2 + TIME '11:00', CURRENT_DATE - 2 + TIME '12:00', 'Sedes Santa Cruz', 'media', NULL, NULL, 'cumplida', 2),              -- 13
 ('Bloqueo vecinal en la Doble Vía a La Guardia', NULL, 'hecho_noticioso', CURRENT_DATE - 2 + TIME '15:15', CURRENT_DATE - 2 + TIME '17:45', 'Km 9, Doble Vía La Guardia', 'alta', NULL, NULL, 'cumplida', 2),  -- 14
 -- cruza la medianoche: mañana de 22:00 a 02:00 (R10)
 ('Llegada de Oriente Petrolero tras la Copa Sudamericana', 'Declaraciones del plantel en el aeropuerto', 'deportes', CURRENT_DATE + 1 + TIME '22:00', CURRENT_DATE + 2 + TIME '02:00', 'Aeropuerto Internacional Viru Viru', 'media', 'Prensa Club Oriente Petrolero', 'El vuelo suele llegar con retraso; esperar al técnico.', 'planificada', 2); -- 15

UPDATE cobertura SET parte_retorno = 'Se obtuvieron declaraciones del secretario de Economía y planos generales.',
                     incidencias_retorno = 'Sin novedad', fecha_hora_retorno = CURRENT_DATE + TIME '10:50'
WHERE id = 1;
UPDATE cobertura SET parte_retorno = 'Cobertura completa.', fecha_hora_retorno = fecha_hora_fin + INTERVAL '20 minutes'
WHERE id IN (9,10,12,13,14);
UPDATE cobertura SET parte_retorno = 'La sesión se suspendió por falta de quórum.' WHERE id = 11;

INSERT INTO cobertura_personal (cobertura_id, personal_id, funcion) VALUES
 (1, 1, 'periodista'), (1, 6, 'camarografo'),
 (2, 2, 'periodista'), (2, 5, 'camarografo'), (2, 12, 'conductor'),
 (3, 1, 'periodista'), (3, 6, 'camarografo'),
 (4, 4, 'periodista'), (4, 7, 'camarografo'),
 (5, 3, 'periodista'), (5, 7, 'camarografo'),
 (7, 2, 'periodista'), (7, 5, 'camarografo'),
 (8, 1, 'periodista'), (8, 6, 'camarografo'),   -- Rubén tiene permiso ese día: la validación lo detecta
 (9, 1, 'periodista'), (9, 5, 'camarografo'),
 (10, 2, 'periodista'), (10, 6, 'camarografo'),
 (11, 3, 'periodista'), (11, 7, 'camarografo'),
 (12, 4, 'periodista'), (12, 7, 'camarografo'),
 (13, 3, 'periodista'), (13, 5, 'camarografo'),
 (14, 2, 'periodista'), (14, 6, 'camarografo'), (14, 12, 'conductor');

INSERT INTO cobertura_recurso (cobertura_id, recurso_id) VALUES
 (1, 12), (1, 1), (1, 6),
 (2, 13), (2, 2), (2, 7), (2, 11),
 (3, 1), (3, 6),
 (4, 12), (4, 5), (4, 9), (4, 10),
 (5, 13), (5, 3), (5, 8),
 (7, 2), (7, 7),
 (8, 12), (8, 1), (8, 6),
 (9, 12), (9, 1), (9, 6),
 (10, 2), (10, 7),
 (11, 3), (11, 8),
 (12, 13), (12, 5), (12, 9),
 (13, 3), (13, 8),
 (14, 13), (14, 2), (14, 7), (14, 10),
 (15, 13), (15, 3);

INSERT INTO prestamo (recurso_id, responsable_id, cobertura_id, fecha_entrega, fecha_devolucion, observacion_entrega, observacion_devolucion) VALUES
 (13, 5, 2, CURRENT_DATE + TIME '07:10', NULL, 'Cobertura paro de transporte', NULL),
 ( 2, 5, 2, CURRENT_DATE + TIME '07:10', NULL, 'Cobertura paro de transporte', NULL),
 ( 7, 5, 2, CURRENT_DATE + TIME '07:10', NULL, 'Cobertura paro de transporte', NULL),
 (11, 5, 2, CURRENT_DATE + TIME '07:10', NULL, 'Cobertura paro de transporte', NULL),
 (12, 6, 1, CURRENT_DATE + TIME '08:40', CURRENT_DATE + TIME '10:50', 'Cobertura Gobernación', 'Sin novedad'),
 ( 1, 6, 1, CURRENT_DATE + TIME '08:40', CURRENT_DATE + TIME '10:50', 'Cobertura Gobernación', 'Sin novedad'),
 ( 6, 6, 1, CURRENT_DATE + TIME '08:40', CURRENT_DATE + TIME '10:50', 'Cobertura Gobernación', 'Sin novedad');

INSERT INTO mantenimiento (recurso_id, tipo, descripcion, cobertura_id, usuario_id, fecha_hora) VALUES
 (4, 'incidencia', 'Falla en el visor LCD; enviada a servicio técnico', NULL, 7, CURRENT_DATE - 3 + TIME '18:20');

-- ---------- CU-05 ----------
INSERT INTO nota (slug, titulo, tipo_pieza, guion, fecha, estado, cobertura_id, autor_id) VALUES
 ('GOBERNACION-PRESUPUESTO', 'Gobernación presenta presupuesto 2027 con recorte en inversión', 'nota_completa',
  'La Gobernación de Santa Cruz presentó esta mañana el anteproyecto de presupuesto para la gestión 2027. El documento alcanza los cuatro mil doscientos millones de bolivianos, un monto menor al del año pasado debido a la caída de las regalías. Según el secretario de Economía, la mayor reducción se aplicará en inversión pública, mientras que salud y caminos vecinales mantendrán sus asignaciones. El anteproyecto será remitido a la Asamblea Legislativa Departamental, que tiene treinta días para su tratamiento.',
  CURRENT_DATE, 'aprobada', 1, 1), -- 1
 ('PARO-TRANSPORTE', 'Transportistas bloquean el 4to anillo por la falta de diésel', 'vo_sot',
  'Desde primeras horas de hoy, el transporte libre y sindicalizado mantiene bloqueado el cuarto anillo a la altura de la avenida Banzer. Los choferes exigen la regularización en la provisión de diésel y denuncian filas de hasta dos días en los surtidores. Miles de pasajeros tuvieron que caminar largas distancias para llegar a sus fuentes de trabajo.',
  CURRENT_DATE, 'aprobada', 2, 2), -- 2
 ('UAGRM-AUTORIDADES', 'Posesionan a nuevas autoridades en cinco facultades de la UAGRM', 'vo',
  'En un acto realizado en el Paraninfo Universitario, el rector de la UAGRM posesionó a los nuevos decanos y directores de carrera de cinco facultades, elegidos en los comicios del mes pasado. Las nuevas autoridades se comprometieron a acelerar la acreditación de carreras.',
  CURRENT_DATE, 'en_revision', 3, 1), -- 3
 ('INCENDIOS-CHIQUITANIA', 'COED reporta 14 focos de calor activos en la Chiquitanía', 'lectura',
  'El Centro de Operaciones de Emergencia Departamental reportó catorce focos de calor activos en la Chiquitanía, la mayoría en los municipios de San Ignacio de Velasco y Concepción. Bomberos forestales y voluntarios trabajan desde el fin de semana para contener el avance del fuego.',
  CURRENT_DATE, 'aprobada', 6, 3), -- 4
 ('CLASICO-CRUCENO', 'Blooming y Oriente definen sus onces para el clásico', 'vo_sot',
  'Blooming y Oriente Petrolero ultiman detalles para el clásico cruceño del domingo en el estadio Tahuichi Aguilera. En la conferencia previa, ambos técnicos',
  CURRENT_DATE, 'borrador', 4, 4), -- 5
 ('BACHES-PLAN3000', 'Vecinos del Plan 3000 exigen bacheo antes de las lluvias', 'nota_completa',
  'Vecinos de diferentes barrios del Plan 3000 denunciaron que las calles principales del distrito ocho están llenas de baches y que el sistema de drenaje no fue limpiado antes de la temporada de lluvias. Los dirigentes vecinales anunciaron que, si no hay trabajos en los próximos quince días, realizarán una marcha hasta la Alcaldía.',
  CURRENT_DATE, 'observada', 5, 3), -- 6
 ('DENGUE-SEDES', 'Sedes confirma aumento de casos de dengue en la capital', 'vo',
  'El Servicio Departamental de Salud confirmó un aumento de casos de dengue en la capital cruceña, con más de trescientos casos en lo que va del mes. Los distritos más afectados son el doce y el catorce. El Sedes intensificará las campañas de fumigación.',
  CURRENT_DATE, 'aprobada', NULL, 2), -- 7
 ('EXPOCRUZ-AFLUENCIA', 'Expocruz 2026 supera el medio millón de visitantes', 'vo_sot',
  'La Feria Exposición de Santa Cruz superó el medio millón de visitantes a pocos días de su cierre. Los pabellones de alimentos y de maquinaria agrícola concentran la mayor afluencia.',
  CURRENT_DATE, 'aprobada', NULL, 4), -- 8
 -- ayer (ya emitidas)
 ('GREMIALES-MARCHA', 'Gremiales marchan contra el nuevo impuesto municipal', 'vo_sot',
  'Cientos de gremiales marcharon por el centro de la ciudad en rechazo al nuevo impuesto municipal a las patentes. Los dirigentes advierten con un paro de cuarenta y ocho horas.',
  CURRENT_DATE - 1, 'emitida', 9, 1), -- 9
 ('FUL-CONFERENCIA', 'La FUL pide ampliar los cupos de admisión', 'vo',
  'La Federación Universitaria Local pidió ampliar los cupos de admisión para la gestión 2027 y mejorar el transporte al campus.',
  CURRENT_DATE - 1, 'emitida', 10, 2), -- 10
 ('CLIMA-SURAZO', 'Llega un surazo con descenso de temperatura', 'lectura',
  'El Senamhi anuncia la llegada de un frente frío con descenso de temperatura de hasta diez grados y lluvias dispersas para el fin de semana.',
  CURRENT_DATE - 1, 'emitida', NULL, 3); -- 11

-- cantidad de palabras del guion (en el sistema lo calcula Java al guardar)
UPDATE nota SET palabras = COALESCE(array_length(regexp_split_to_array(trim(guion), '\s+'), 1), 0);
-- noticia grave: se lee más pausado que el estándar del noticiero
UPDATE nota SET palabras_por_minuto = 130 WHERE slug = 'INCENDIOS-CHIQUITANIA';

INSERT INTO nota_rotulo (nota_id, orden, nombre, cargo) VALUES
 (1, 1, 'Juan Carlos Rivero',   'Secretario de Economía y Hacienda - Gobernación'),
 (2, 1, 'Rolando Peña',         'Dirigente - Fed. de Autotransporte 1ro de Mayo'),
 (2, 2, 'María Eugenia Suárez', 'Pasajera'),
 (3, 1, 'Vicente Remberto Cuéllar', 'Rector - UAGRM'),
 (6, 1, 'Nelson Guzmán',        'Presidente de la junta vecinal Barrio 18 de Marzo'),
 (7, 1, 'Dra. Carola Antezana', 'Jefa de Epidemiología - Sedes'),
 (8, 1, 'Jean Pierre Antelo',   'Presidente - Fexpocruz');

INSERT INTO material_audiovisual (nota_id, descripcion, tipo, duracion, ubicacion, estado, editor_id) VALUES
 (1, 'Nota editada presupuesto',      'video', 95, '/media/2026/presupuesto_v1.mxf', 'listo',      8),
 (2, 'VO bloqueo + SOT dirigente',    'sot',   55, '/media/2026/paro_transporte.mxf','en_edicion', 8),
 (3, 'Imágenes del acto',             'video', 40, NULL,                             'pendiente',  NULL),
 (5, 'Conferencia técnicos',          'sot',   45, NULL,                             'pendiente',  NULL),
 (6, 'Recorrido calles y entrevista', 'video', 80, '/media/2026/baches_bruto.mxf',   'ingestado',  9),
 (7, 'Imágenes fumigación',           'video', 35, '/media/2026/dengue.mxf',         'listo',      8),
 (8, 'Pabellones Expocruz + SOT',     'sot',   50, '/media/2026/expocruz.mxf',       'listo',      9),
 (9, 'Marcha + SOT dirigente',        'sot',   60, '/media/2026/gremiales.mxf',      'listo',      8),
 (10,'Conferencia FUL',               'video', 30, '/media/2026/ful.mxf',            'listo',      9);

-- versión 1 de cada guion (autor = usuario del periodista)
INSERT INTO nota_version (nota_id, numero, guion, palabras, usuario_id, fecha_hora)
SELECT n.id, 1, n.guion, n.palabras, p.usuario_id, n.fecha + TIME '10:00'
FROM nota n JOIN personal p ON p.id = n.autor_id;
-- la nota 6 tuvo un borrador previo más corto
UPDATE nota_version SET numero = 2 WHERE nota_id = 6;
INSERT INTO nota_version (nota_id, numero, guion, palabras, usuario_id, fecha_hora) VALUES
 (6, 1, 'Vecinos del Plan 3000 denunciaron baches en las calles principales del distrito ocho.', 13, 5, CURRENT_DATE + TIME '09:05');

INSERT INTO revision_nota (nota_id, usuario_id, accion, motivo, fecha_hora) VALUES
 (1, 3, 'envio',       NULL, CURRENT_DATE + TIME '11:20'),
 (1, 2, 'aprobacion',  NULL, CURRENT_DATE + TIME '11:45'),
 (2, 4, 'envio',       NULL, CURRENT_DATE + TIME '12:10'),
 (2, 2, 'aprobacion',  NULL, CURRENT_DATE + TIME '12:40'),
 (3, 3, 'envio',       NULL, CURRENT_DATE + TIME '13:30'),
 (4, 5, 'envio',       NULL, CURRENT_DATE + TIME '17:15'),
 (4, 2, 'aprobacion',  NULL, CURRENT_DATE + TIME '17:25'),
 (6, 5, 'envio',       NULL, CURRENT_DATE + TIME '18:40'),
 (6, 2, 'observacion', 'Falta la versión de la Alcaldía. Contrastar con Obras Públicas antes de volver a enviar.', CURRENT_DATE + TIME '19:00'),
 (7, 4, 'envio',       NULL, CURRENT_DATE + TIME '15:00'),
 (7, 2, 'aprobacion',  NULL, CURRENT_DATE + TIME '15:50'),
 (8, 6, 'envio',       NULL, CURRENT_DATE + TIME '16:30'),
 (8, 2, 'aprobacion',  NULL, CURRENT_DATE + TIME '17:40'),
 (9, 3, 'envio',       NULL, CURRENT_DATE - 1 + TIME '12:00'),
 (9, 2, 'aprobacion',  NULL, CURRENT_DATE - 1 + TIME '13:10'),
 (10, 4, 'envio',      NULL, CURRENT_DATE - 1 + TIME '14:00'),
 (10, 2, 'observacion','Agregar la cifra de postulantes del año pasado.', CURRENT_DATE - 1 + TIME '14:30'),
 (10, 4, 'envio',      NULL, CURRENT_DATE - 1 + TIME '15:10'),
 (10, 2, 'aprobacion', NULL, CURRENT_DATE - 1 + TIME '15:30'),
 (11, 5, 'envio',      NULL, CURRENT_DATE - 1 + TIME '17:00'),
 (11, 2, 'aprobacion', NULL, CURRENT_DATE - 1 + TIME '17:05');

-- ---------- CU-06 ----------
INSERT INTO programa (nombre, tipo, descripcion) VALUES
 ('Noticiero Central',   'noticiero', 'Edición principal de la noche'),                          -- 1
 ('Noticiero Matinal',   'noticiero', 'Edición de la mañana'),                                   -- 2
 ('Edición Especial',    'especial',  'Cobertura especial de un día (ej. elecciones, aniversario)'), -- 3
 ('Película de la noche','otro',      'Solo referencia en la grilla; su contenido no lo gestiona este sistema'); -- 4

-- Grilla: una fila por programa y día de la semana
INSERT INTO grilla (programa_id, dia_semana, hora_inicio, hora_fin, vigente_desde, vigente_hasta)
SELECT 1, d, TIME '20:00', TIME '21:00', CURRENT_DATE - 30, NULL::date FROM generate_series(1, 7) AS d   -- Central: todos los días (ids 1-7)
UNION ALL
SELECT 2, d, TIME '06:30', TIME '07:30', CURRENT_DATE - 30, NULL::date FROM generate_series(1, 5) AS d   -- Matinal: lunes a viernes
UNION ALL
SELECT 4, d, TIME '21:00', TIME '23:00', CURRENT_DATE - 30, NULL::date FROM generate_series(1, 5) AS d;  -- Película: lunes a viernes
-- Especial: solo el próximo domingo
INSERT INTO grilla (programa_id, dia_semana, hora_inicio, hora_fin, vigente_desde, vigente_hasta) VALUES
 (3, 7, '18:30', '19:00',
  CURRENT_DATE + (7 - EXTRACT(ISODOW FROM CURRENT_DATE))::INT,
  CURRENT_DATE + (7 - EXTRACT(ISODOW FROM CURRENT_DATE))::INT);

-- Plantillas: estructura de cada noticiero (la hora la pone la grilla)
INSERT INTO plantilla (programa_id, nombre, duracion_total, cantidad_bloques, palabras_por_minuto) VALUES
 (1, 'Noticiero Central',    3600, 4, 150), -- 1
 (2, 'Noticiero Matinal',    3600, 3, 165), -- 2 ritmo más ágil
 (3, 'Especial del domingo', 1800, 2, 150); -- 3

INSERT INTO plantilla_corte (plantilla_id, numero, minuto_inicio, duracion) VALUES
 (1, 1, 12, 180), (1, 2, 28, 180), (1, 3, 45, 150),
 (2, 1, 20, 180), (2, 2, 40, 180),
 (3, 1, 15, 120);

-- escaleta de hoy (borrador) y de ayer (emitida)
INSERT INTO escaleta (grilla_id, plantilla_id, fecha, hora_inicio, duracion_total, estado, hora_inicio_real, duracion_real, responsable_id)
SELECT g.id, 1, x.fecha, g.hora_inicio, 3600, x.estado, x.real_ini, x.real_dur, 2
FROM (VALUES (CURRENT_DATE,     'borrador', NULL::time, NULL::int, 1),          -- 1
             (CURRENT_DATE - 1, 'emitida',  '20:00:20'::time, 3642, 2)) AS x(fecha, estado, real_ini, real_dur, orden)
JOIN grilla g ON g.programa_id = 1 AND g.dia_semana = EXTRACT(ISODOW FROM x.fecha)
ORDER BY x.orden;

-- Los cortes los genera el sistema (R21): uno por bloque, al final (orden 999), copiados de la plantilla.
-- Es lo mismo que hace INSESC al crear una escaleta.
INSERT INTO item_escaleta (escaleta_id, orden, bloque, tipo, titulo, duracion_estimada)
SELECT e.id, 999, CHR(64 + c.numero), 'corte', 'Corte comercial ' || c.numero, c.duracion
FROM escaleta e JOIN plantilla_corte c ON c.plantilla_id = e.plantilla_id
ORDER BY e.id, c.numero;

-- El orden es la posición dentro del bloque (1 a 998, R22)
INSERT INTO item_escaleta (escaleta_id, orden, bloque, tipo, nota_id, titulo, presentador_id, duracion_estimada, duracion_real, estado, ultima_hora) VALUES
 -- hoy
 (1, 1, 'A', 'apertura',    NULL, 'Apertura y saludo',                  10,  30, NULL, 'programado', FALSE),
 (1, 2, 'A', 'titulares',   NULL, 'Titulares',                          10,  75, NULL, 'programado', FALSE),
 (1, 3, 'A', 'nota',        1,    NULL,                                 10,   0, NULL, 'programado', FALSE),
 (1, 4, 'A', 'nota',        2,    NULL,                                 11,   0, NULL, 'programado', FALSE),
 (1, 5, 'A', 'entrevista',  NULL, 'Rector de la UAGRM: admisión 2027',  11, 410, NULL, 'programado', FALSE), -- bloque A cuadra (ok)
 (1, 1, 'B', 'nota',        4,    NULL,                                 10,   0, NULL, 'programado', FALSE),
 (1, 2, 'B', 'nota',        7,    NULL,                                 11,   0, NULL, 'programado', FALSE),
 (1, 3, 'B', 'enlace_vivo', NULL, 'Enlace en vivo: paro de transporte', 10, 120, NULL, 'programado', FALSE),
 (1, 4, 'B', 'entrevista',  NULL, 'Secretario de Obras Públicas en estudio', 11, 660, NULL, 'programado', FALSE), -- bloque B se pasa (exceso)
 (1, 1, 'C', 'nota',        8,    NULL,                                 10,   0, NULL, 'flotado',    FALSE), -- flotada: no suma (déficit)
 (1, 1, 'D', 'despedida',   NULL, 'Despedida',                          10,  40, NULL, 'programado', FALSE),
 -- ayer (emitida, con duraciones reales)
 (2, 1, 'A', 'apertura',    NULL, 'Apertura y saludo',                  10,  30,  34, 'emitido', FALSE),
 (2, 2, 'A', 'nota',        9,    NULL,                                 10,   0, 118, 'emitido', FALSE),
 (2, 3, 'A', 'nota',        10,   NULL,                                 11,   0,  52, 'emitido', FALSE),
 (2, 4, 'A', 'entrevista',  NULL, 'Ministro de Educación en estudio',   11, 480, 555, 'emitido', FALSE),
 (2, 1, 'B', 'nota',        11,   NULL,                                 10,   0,  20, 'emitido', FALSE),
 (2, 2, 'B', 'otro',        NULL, 'Accidente en la Av. Cristo Redentor',10,  45,  60, 'emitido', TRUE),
 (2, 1, 'D', 'despedida',   NULL, 'Despedida',                          10,  40,  38, 'emitido', FALSE);

-- ayer se emitió: sus cortes también
UPDATE item_escaleta SET estado = 'emitido', duracion_real = duracion_estimada
WHERE escaleta_id = 2 AND tipo = 'corte';

-- ---------- RNF-24 ----------
INSERT INTO bitacora_comando (remitente, usuario_id, asunto, comando, resultado, respuesta, fecha_hora) VALUES
 ('jefe.prensa@canal11.test', 2, 'LISCOB["*"]', 'LISCOB', 'exito', 'Listado de 8 coberturas', CURRENT_DATE + TIME '08:00'),
 ('carla.rojas@canal11.test', 3, 'INSNOT["GOBERNACION-PRESUPUESTO","Gobernación presenta presupuesto 2027","nota_completa","1"]', 'INSNOT', 'exito', 'Éxito: nota registrada correctamente', CURRENT_DATE + TIME '10:00'),
 ('desconocido@gmail.com', NULL, 'LISUSU', 'LISUSU', 'error', 'Error: remitente no registrado', CURRENT_DATE + TIME '10:15'),
 ('jefe.prensa@canal11.test', 2, 'INSCPE["8","6","camarografo"]', 'INSCPE', 'error', 'Error: Rubén Cuéllar Antelo tiene permiso ese día', CURRENT_DATE + TIME '10:30'),
 ('jefe.prensa@canal11.test', 2, 'INSCOB["Clásico cruceño: conferencia previa","Blooming y Oriente antes del clásico","deportes","' || CURRENT_DATE || '","15:00","16:30","Estadio Ramón Tahuichi Aguilera","media","Prensa Club Blooming","Conferencia de ambos técnicos; llegar 30 minutos antes para ubicar la cámara y el micrófono de mesa."]', 'INSCOB', 'exito', 'Éxito: cobertura registrada correctamente', CURRENT_DATE + TIME '07:45'), -- asunto de más de 255 caracteres
 ('carla.rojas@canal11.test', 3, 'Consulta sobre mi turno del domingo', NULL, 'error', 'Error: comando no reconocido. Envíe AYUDA para ver los comandos disponibles', CURRENT_DATE + TIME '11:05'); -- correo sin comando (R2)
