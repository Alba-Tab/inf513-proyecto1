# Base de datos `db_grupo14sa`

Scripts de PostgreSQL del sistema. **Son la fuente de verdad.** El script es el de la sección 3.2.4.2 del documento oficial con tres cambios que el Word todavía no tiene: el comentario de `usuario.password` (bcrypt en vez de SHA-256, D5), la versión del motor (probado en la 17; ya no hace falta la 11, porque los datos no usan `sha256()`) y el cargo `jefe_prensa` en `personal` (D14). El diseño completo (modelo conceptual, dominios y reglas R1–R26) está en `../../docs/base_de_datos/bd_formal_v2.md`.

## Archivos

| Archivo | Contenido |
|---|---|
| `script_db_grupo14sa.sql` | Borra y crea las 26 tablas, sus restricciones, los índices y las 8 vistas de cálculo. Casi todas las restricciones tienen nombre propio (`ck_*`, `uq_*`, `ux_*`); seis CHECK conservan el nombre que les pone PostgreSQL (`item_escaleta_check`, `item_escaleta_check1`, `turno_check`, `grilla_check`, `prestamo_check` y `revision_nota_check`), y `TraductorErrores` los reconoce por ese nombre |
| `datos_prueba.sql` | Datos de la demo: 7 roles, 15 usuarios, 112 permisos y 206 asignaciones rol–permiso, personal, recursos, coberturas, notas y escaletas. Las fechas son relativas a `CURRENT_DATE`, así que la demo funciona cualquier día |
| `consultas_negocio.sql` | Las consultas que usa la capa de Negocio, probadas con variables de psql (`:variable` = `?` del `PreparedStatement`). Las que modifican datos van dentro de `BEGIN … ROLLBACK` |
| `pruebas_integridad.sql` | 21 pruebas automáticas de las restricciones (OK / FALLA). No dejan cambios: todo se deshace al final |

## Orden de ejecución

En una base vacía (en tecnoweb la base `db_grupo14sa` ya viene creada):

```bash
psql -h localhost -U grupo14sa -d db_grupo14sa -f script_db_grupo14sa.sql
```

```bash
psql -h localhost -U grupo14sa -d db_grupo14sa -f datos_prueba.sql
```

```bash
psql -h localhost -U grupo14sa -d db_grupo14sa -f pruebas_integridad.sql
```

Las 21 líneas deben decir `OK`. `consultas_negocio.sql` es opcional: muestra las consultas funcionando sobre los datos de prueba.

⚠️ `script_db_grupo14sa.sql` **borra todas las tablas** antes de crearlas. No correrlo sobre una base con datos reales: una vez en uso, se pierden la bitácora y los correos reales de los usuarios. Si hace falta, respaldar antes con `pg_dump -Fc -h localhost -U grupo14sa db_grupo14sa > respaldo.dump`.

## Antes de cargar los datos en el servidor

- Cambiar los correos `@canal11.test` de `datos_prueba.sql` por los correos reales del grupo: el sistema identifica a cada usuario por su remitente. Como mínimo, el del Director (usuario 1) y el de un usuario por rol para la demo.
- La contraseña de todas las cuentas de prueba es `canal11`, guardada con bcrypt (`$2y$`, costo 10). Solo sirve para el sistema web del siguiente hito: por correo, al usuario lo identifica su remitente.

## Si se cambia un script

1. Correr el script, los datos y las pruebas en una base vacía; las 21 deben seguir en `OK`.
2. Si cambió una tabla o una restricción, anotar el cambio para el documento en `../../docs/` (el Word no se toca) y actualizar los datos de `../../docs/diagramas/EA_generar_BD_Canal11.js`, que se generan desde el esquema real.
