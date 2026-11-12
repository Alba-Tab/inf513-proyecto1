# canal11-pipeline

Sistema de Gestión de Operaciones y Noticias del Canal 11 (TVU-UAGRM), operado por correo electrónico, con arquitectura de **tubería y filtros** (*pipes and filters*).
Proyecto 1 de Tecnología Web (INF513) — Grupo 14 SA — Semestre II-2026.

Es la segunda versión de `canal11-mail`: las mismas reglas de negocio y la misma base de datos, con el flujo de cada correo partido en etapas y **66 comandos** que cubren los 8 casos de uso aprobados por el docente.

## Qué hace

Un usuario del canal envía un correo a `grupo14sa@tecnoweb.org.bo` con un comando en el asunto:

```
LISCOB["2026-10-12"]
```

El sistema lee el buzón por POP3, pasa el correo por la tubería y le responde por SMTP con el resultado en HTML. Si el asunto es `AYUDA`, responde con los comandos que puede usar el rol del remitente.

## Cómo está armado

```
MailVerificationThread ──▶ Decodificar ▶ Filtrar ▶ Identificar ▶ Interpretar ▶ Ayuda
      (fuente, POP3)                                                            │
                                                                                ▼
SendEmailThread ◀── Responder ◀── Registrar ◀── Ejecutar ◀── Validar ◀── Autorizar
 (sumidero, SMTP)
```

| Etapa | Qué hace | Si encuentra un problema |
|---|---|---|
| 1. Decodificar | Lee el MIME: remitente, asunto, cuerpo sin firma ni citas, Message-ID | Nunca falla: lo ilegible queda vacío |
| 2. Filtrar | Descarta rebotes, respuestas automáticas y correos propios; detecta los de más de 1 MB | Descarta sin responder, o responde «no se aceptan adjuntos» |
| 3. Identificar | Abre la conexión a la base y busca al remitente | «Intente más tarde» si la base no responde |
| 4. Interpretar | Separa el código y los parámetros del asunto y busca la ficha del comando | Formato con ejemplo, o «comando no reconocido» |
| 5. Ayuda | Responde AYUDA con los comandos del rol | «No está registrado» |
| 6. Autorizar | Remitente activo y rol con permiso (R1) | «No tiene permiso para ejecutar …» |
| 7. Validar | Cantidad y tipo de los parámetros | Qué parámetro está mal, con el ejemplo |
| 8. Ejecutar | La acción del comando, en una transacción | Se deshace todo y se responde el error traducido |
| 9. Registrar | La bitácora, también de los rechazados (corre siempre) | Se anota en el log |
| 10. Responder | Arma la respuesta en HTML base64 y la encola (corre siempre) | Se anota en el log |

El orden completo está en `Pipeline.completo(...)`. Cada etapa es una clase de `pipeline/etapas` y tiene sus pruebas.

## Diagrama de flujo del analizador de correos

Es el mismo flujo de la consigna: recepción, parser y validador, negocio y base de datos, y el correo de éxito o de error.

```mermaid
flowchart TD
    A([Llega un correo a grupo14sa@tecnoweb.org.bo]) --> B["Leer el buzón por POP3 cada 30 s (MailVerificationThread)"]
    B --> C["Decodificar el MIME: UTF-8 y asuntos RFC 2047"]
    C --> D{"¿Automático, rebote o del propio sistema?"}
    D -- sí --> D1[Anotar el descarte en access.log] --> Z1([Fin, sin respuesta])
    D -- no --> E[Identificar al remitente en la base]
    E --> E1{"¿La base responde?"}
    E1 -- no --> R1["Error: intente más tarde"]
    E1 -- sí --> F["Interpretar el asunto: código de 6 letras y parámetros entre corchetes"]
    F --> F1{"¿Sintaxis válida?"}
    F1 -- no --> R2["Error: formato esperado, con un ejemplo"]
    F1 -- AYUDA --> R3[Responder los comandos del rol]
    F1 -- sí --> G[Autorizar: remitente registrado, activo y con permiso]
    G --> G1{"¿Autorizado?"}
    G1 -- no --> R4["Error: no tiene permiso"]
    G1 -- sí --> H[Validar la cantidad y el tipo de cada parámetro]
    H --> H1{"¿Parámetros válidos?"}
    H1 -- no --> R5["Error: qué parámetro está mal, con un ejemplo"]
    H1 -- sí --> I["Ejecutar en una transacción con PreparedStatement"]
    I --> I1{"¿Error de ejecución?"}
    I1 -- sí --> R6["ROLLBACK y Error: el detalle de la falla"]
    I1 -- no --> R7["COMMIT y Éxito: los datos o el resumen"]
    R1 & R2 & R3 & R4 & R5 & R6 & R7 --> J[Registrar en la bitácora y en access.log]
    J --> K["Responder por SMTP al remitente (SendEmailThread)"]
    K --> Z2([Fin])
```

## Formato del asunto

```
CODIGO["param1","param2",...]
```

- El código tiene 6 letras: 3 de la operación (`LIS`, `INS`, `UPD`, `DEL` y otras) y 3 de la tabla. Da igual escribirlo en mayúsculas o en minúsculas.
- Los parámetros van entre comillas y separados por comas. Se ignoran los espacios de más y los prefijos `Re:` o `Fwd:`. Un parámetro vacío se escribe `""`.
- **Comillas dentro de un parámetro:** se escriben con barra invertida, `\"`, y la barra misma con `\\`. Por ejemplo, `OBSNOT["6","El vecino dijo \"no\""]`.
- Se aceptan tildes y eñes (UTF-8), también en los asuntos codificados que mandan Gmail y Outlook.
- Cada tipo se valida en el servidor antes de llegar a la base (fechas, horas, números, opciones), y todas las consultas usan `PreparedStatement`.
- La respuesta empieza con **«Éxito:»** y trae los datos o un resumen, o con **«Error:»** y dice qué falló.

Ejemplos: `LISPER["*"]`, `INSUSU["Carla","Rojas Suárez","3100003","carla.rojas@gmail.com","71234580","clave123","3"]`, `AYUDA`.

## Los 66 comandos

| Caso de uso | Comandos |
|---|---|
| CU-01 Usuarios (8) | LISUSU INSUSU UPDUSU DELUSU · LISROL · LISRPM INSRPM DELRPM |
| CU-02 Personal, turnos y disponibilidad (9) | LISPER INSPER UPDPER DELPER · LISTUR INSTUR · LISAUS INSAUS · DISPER |
| CU-03 Recursos (9) | LISREC INSREC UPDREC DELREC · INCREC ALTREC · LISPRE INSPRE DEVPRE |
| CU-04 Coberturas (10) | LISCOB INSCOB UPDCOB DELCOB · INSCPE DELCPE · INSCRE DELCRE · HOJCOB RETCOB |
| CU-05 Notas, material audiovisual y aprobación (11) | LISNOT INSNOT UPDNOT DELNOT · GUINOT · ENVNOT APRNOT OBSNOT · LISMAT INSMAT UPDMAT |
| CU-06 Plantillas y escaleta (14) | LISPLA UPDPLA UPDCOR · LISPRG INSPRG · LISGRI INSGRI · LISESC INSESC UPDESC EXPESC · INSITE UPDITE DELITE |
| CU-07 Analítica (2) | ANAPER ANACOB |
| CU-08 Reportes (3) | REPCOB REPREC LISBIT |

Cada parte del nombre de los 8 casos de uso aprobados tiene sus comandos. Los catálogos de apoyo (roles, tipos de recurso, plantillas con sus cortes) vienen cargados en `db/datos_prueba.sql`. El porqué de cada decisión está en [docs/DECISIONES.md](docs/DECISIONES.md).

## Base de datos

PostgreSQL, con **26 tablas** y 8 vistas. `db/script_db_grupo14sa.sql` crea todo (antes borra lo que haya) y `db/datos_prueba.sql` carga los roles, los permisos, los catálogos de apoyo y datos de ejemplo. En el servidor, `scripts/cargar-bd.sh` corre los dos. Todas las consultas del sistema usan `PreparedStatement`.

| Caso de uso | Tablas |
|---|---|
| CU-01 Usuarios | `rol`, `usuario`, `permiso`, `rol_permiso` |
| CU-02 Personal | `turno`, `personal`, `ausencia` |
| CU-03 Recursos | `tipo_recurso`, `recurso`, `prestamo`, `mantenimiento` |
| CU-04 Coberturas | `cobertura`, `cobertura_personal`, `cobertura_recurso` |
| CU-05 Notas | `nota`, `nota_rotulo`, `nota_version`, `revision_nota`, `material_audiovisual` |
| CU-06 Programación y escaleta | `programa`, `grilla`, `plantilla`, `plantilla_corte`, `escaleta`, `item_escaleta` |
| Bitácora (RNF-24) | `bitacora_comando` |

Vistas: `v_ocupacion_personal` y `v_ocupacion_recurso` (choques de horario), `v_nota_duracion` (duración estimada), `v_plantilla_bloque` y `v_plantilla_control` (bloques y control R18), `v_escaleta_grilla` y `v_bloque_balance` (tiempos de la escaleta) y `v_grilla_semanal`.

Relaciones (cada tabla tiene `id` como clave primaria; las líneas son las claves foráneas):

```mermaid
erDiagram
    rol ||--o{ usuario : asigna
    rol ||--o{ rol_permiso : otorga
    permiso ||--o{ rol_permiso : "se otorga en"
    usuario ||--o| personal : "tiene ficha"
    turno ||--o{ personal : "turno fijo"
    personal ||--o{ ausencia : registra
    tipo_recurso ||--o{ recurso : clasifica
    recurso ||--o{ prestamo : "se entrega en"
    personal ||--o{ prestamo : "es responsable de"
    cobertura |o--o{ prestamo : motiva
    recurso ||--o{ mantenimiento : registra
    cobertura |o--o{ mantenimiento : "afecta a"
    usuario |o--o{ mantenimiento : reporta
    usuario ||--o{ cobertura : planifica
    cobertura |o--o{ cobertura : "se reprograma en"
    cobertura ||--o{ cobertura_personal : requiere
    personal ||--o{ cobertura_personal : "participa en"
    cobertura ||--o{ cobertura_recurso : reserva
    recurso ||--o{ cobertura_recurso : "se reserva en"
    cobertura |o--o{ nota : origina
    personal ||--o{ nota : redacta
    nota ||--o{ nota_rotulo : lleva
    nota ||--o{ nota_version : guarda
    usuario ||--o{ nota_version : edita
    nota ||--o{ revision_nota : "se revisa en"
    usuario ||--o{ revision_nota : revisa
    nota ||--o{ material_audiovisual : tiene
    personal |o--o{ material_audiovisual : edita
    programa ||--o{ grilla : "sale en"
    programa ||--o{ plantilla : "se arma con"
    plantilla ||--o{ plantilla_corte : define
    grilla ||--o{ escaleta : genera
    plantilla ||--o{ escaleta : estructura
    usuario ||--o{ escaleta : "es responsable de"
    escaleta ||--o{ item_escaleta : contiene
    nota |o--o{ item_escaleta : "se incluye como"
    personal |o--o{ item_escaleta : presenta
    usuario |o--o{ bitacora_comando : "envía"
```

El diseño conceptual (MER), el lógico y el físico, con todas las columnas y restricciones, están en la sección 3.2 del documento técnico.

## Requisitos

| Herramienta | Para qué |
|---|---|
| JDK 17 o superior | Compilar y correr |
| PostgreSQL | Base de datos (en el servidor: `db_grupo14sa`) |
| Acceso SSH a `www.tecnoweb.org.bo` | Subir y correr la aplicación |

Maven no hace falta: el repositorio trae el Maven Wrapper (`./mvnw`).

## Compilar y probar

```bash
./mvnw verify
```

Deja un solo jar con todo adentro en `target/canal11-pipeline.jar`.

Las pruebas marcadas `@Tag("bd")` necesitan un PostgreSQL y se saltan solas si no lo hay. Para correrlas:

```bash
createdb db_grupo14sa_test
```

```bash
export CANAL11_BD_PRUEBA_URL=jdbc:postgresql://localhost:5432/db_grupo14sa_test CANAL11_BD_PRUEBA_USUARIO=postgres CANAL11_BD_PRUEBA_CONTRASENA=
```

```bash
./mvnw verify
```

La primera prueba carga `db/script_db_grupo14sa.sql` y `db/datos_prueba.sql`, y cada prueba deshace sus cambios. Como el script **borra todas las tablas**, solo se acepta una base con «test» en el nombre.

## Configurar y correr

```bash
cp config.example.properties config.properties
```

Completar `config.properties` con las contraseñas del buzón y de la base de datos. No se versiona; en el servidor, darle permisos `600`.

```bash
java -Duser.timezone=America/La_Paz -jar target/canal11-pipeline.jar config.properties
```

Corre una sola copia (bloqueo en `canal11.lock`). Se detiene con Ctrl+C o `kill`: termina el correo en curso y envía las respuestas pendientes.

## Instalar en el servidor

Resumen; el paso a paso completo está en [docs/despliegue.md](docs/despliegue.md).

1. Desde la PC, compilar, probar y subir: `scripts/subir.sh grupo14sa@www.tecnoweb.org.bo`.
2. En el servidor, crear la configuración: `cp config.example.properties config.properties`, completar las contraseñas y `chmod 600 config.properties`.
3. Crear la base y cargar los datos: `scripts/cargar-bd.sh`.
4. Revisar la base, el buzón y el SMTP sin tocar nada: `scripts/verificar-servidor.sh su.correo@gmail.com`.
5. Arrancar el demonio: `scripts/run.sh`. Para que vuelva solo si se cae, agregar a `crontab -e` la línea `*/5 * * * * <carpeta>/scripts/run.sh --si-no-corre`.
6. Ver el estado y los logs: `scripts/estado.sh`. Detener: `scripts/stop.sh`.

## Comprimido de entrega

```bash
scripts/empaquetar.sh ../docs-pipeline/Documento_Canal11_Pipeline.docx
```

Crea `2026-2_INF513-P1_grupo14sa.zip`, al lado del repositorio, con todo lo versionado en `HEAD` (sin `config.properties`, `target/` ni logs) y el documento técnico en la raíz del zip.

## Logs

En la carpeta `logs/`:

| Archivo | Qué guarda |
|---|---|
| `access.log` | Una línea por correo: fecha, remitente, comando, **Éxito**, **Error** o **Descartado**, el asunto (con la contraseña enmascarada) y el resumen |
| `error.log` | Los avisos y los errores, con la traza de cada excepción |
| `canal11.0.log` | Todos los eventos (rota a 5 archivos de 5 MB) |


## Estructura

```
canal11-pipeline/
├── docs/              decisiones y guía de despliegue
├── db/                script de la BD, datos de prueba, consultas y pruebas de integridad
├── scripts/           subir, verificar el servidor, cargar la BD, arrancar, detener y ver el estado
└── src/main/java/bo/edu/uagrm/ficct/canal11/
    ├── App.java           arma la tubería y arranca la fuente y el sumidero
    ├── config/            configuración, log y bloqueo de una sola copia
    ├── correo/            POP3 y SMTP por socket, lectura y armado de MIME
    ├── pipeline/          Pedido, Etapa, Pipeline, la fuente y el sumidero
    │   └── etapas/        las 10 etapas
    ├── comandos/          ficha, contexto y resultado de los comandos
    │   ├── catalogo/      parámetros con su tipo y ejemplo
    │   └── cu01 … cu08/   los comandos de cada caso de uso
    └── datos/             conexión, repositorio, CRUD genérico, bitácora y traductor de errores
```

Los paquetes no tienen ciclos: `config ← datos ← comandos ← pipeline`, y `correo` va aparte.

## Cómo se trabaja

- `main` recibe solo las entregas.
- Se trabaja en ramas `feat/`, `fix/`, `chore/` o `docs/` que salen de `develop`.
- Cada rama vuelve a `develop` con `git merge --no-ff`, después de que pasan las pruebas.
