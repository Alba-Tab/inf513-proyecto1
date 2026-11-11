# Despliegue en el servidor tecnoweb

La aplicación corre **en el servidor `www.tecnoweb.org.bo`**, como programa de consola, y usa `localhost` para el buzón (POP3 110), el envío (SMTP 25) y la base (PostgreSQL 5432). Desde fuera del servidor, Sendmail no deja responder a Gmail («Relaying denied»): por eso no se corre desde una PC.

Resumen:

1. **En la PC:** `scripts/subir.sh` compila, prueba y sube todo por SSH.
2. **En el servidor:** crear `config.properties` con las contraseñas.
3. **En el servidor:** `scripts/verificar-servidor.sh` revisa Java, la base, el buzón y el envío **sin borrar ni enviar nada**.
4. **En el servidor:** `scripts/cargar-bd.sh` carga la base; después se ponen los correos reales del grupo.
5. **En el servidor:** `scripts/run.sh` la arranca. Se prueba enviando AYUDA desde Gmail.

---

## 1. Lo que hace falta

| Qué | Para qué |
|---|---|
| Usuario y contraseña de SSH que dio el docente | Subir y correr la aplicación |
| Usuario y contraseña del buzón `grupo14sa` | `config.properties` (POP3) |
| Usuario y contraseña de la base `db_grupo14sa` | `config.properties` y `cargar-bd.sh` |
| Java 17 o superior en el servidor | Correr el jar (si no hay, ver la sección 9) |
| Un correo de Gmail de cada integrante | Ser usuarios del sistema y probar |

## 2. Subir desde la PC

Desde la carpeta `canal11-pipeline/`:

```bash
scripts/subir.sh grupo14sa@www.tecnoweb.org.bo
```

- Si el usuario de SSH no es `grupo14sa`, cambiarlo en ese comando.
- Corre las pruebas, compila y sube a `~/canal11/` el jar, los scripts, la base y esta guía. Pide la contraseña de SSH en cada paso.
- **Nunca sube `config.properties`**: ese archivo se crea en el servidor.
- Para ver los comandos sin ejecutarlos, agregar `--simular` antes del destino.
- Si el nombre `www.tecnoweb.org.bo` no se encuentra (el DNS de tecnoweb falla a ratos), usar su IP. Se obtiene con `dig @8.8.8.8 www.tecnoweb.org.bo`; el 13/10/2026 era `139.99.68.169`.

## 3. Crear la configuración (en el servidor)

```bash
ssh grupo14sa@www.tecnoweb.org.bo
```

```bash
cd canal11 && cp config.example.properties config.properties && chmod 600 config.properties && nano config.properties
```

Completar `pop3.contrasena`, `bd.usuario` y `bd.contrasena`. El resto ya trae los valores del servidor (`localhost`). El permiso `600` impide que otros grupos del servidor lean las contraseñas.

## 4. Verificar el servidor (paso 0)

```bash
scripts/verificar-servidor.sh su.correo@gmail.com
```

Revisa:
- la versión de Java (17 o más);
- el disco;
- si hay `screen` y `crontab`;
- con las credenciales de `config.properties`: la base, el buzón y si el SMTP deja responder a un correo externo.

No borra correos ni envía nada: la prueba de envío se corta antes de `DATA`. Cada problema aparece marcado con **[FALLA]**:

| Si dice… | Qué hacer |
|---|---|
| Java anterior a 17, o no hay Java | Sección 9 (JDK portable) |
| La base todavía no tiene las tablas | Es normal la primera vez: paso 5 |
| No se pudo leer el buzón | Revisar `pop3.usuario` y `pop3.contrasena` |
| Rechaza enviar a … (Relaying denied) | El servidor exige autenticarse: hay que hacer la rama `feat/smtp-cram-md5` antes de seguir. Avisar en el grupo |
| El buzón tiene N correos | Al arrancar se van a procesar (y borrar). Si son pruebas viejas, conviene vaciarlo antes |

El mismo chequeo se puede correr solo: `java -jar canal11-pipeline.jar --verificar config.properties su.correo@gmail.com`.

## 5. Cargar la base de datos

```bash
scripts/cargar-bd.sh
```

**Borra todas las tablas** de `db_grupo14sa` y carga el esquema y los datos de prueba. Por eso pide escribir el nombre de la base para confirmar. Termina corriendo las 21 pruebas de integridad, que deben dar OK.

⚠️ Es para la **primera carga**. Una vez que el sistema está en uso, volver a correrlo borra la bitácora y los correos reales de los usuarios. Si de verdad hay que recargar, respaldar antes:

```bash
pg_dump -Fc -h localhost -U grupo14sa db_grupo14sa > ~/canal11/respaldo-$(date +%Y%m%d-%H%M).dump
```

Después, poner los correos reales del grupo: el sistema reconoce a cada usuario por su remitente. Como mínimo, un usuario por integrante:

```bash
psql -h localhost -U grupo14sa -d db_grupo14sa
```

```sql
UPDATE usuario SET correo = 'correo.del.director@gmail.com'  WHERE id = 1;  -- Director (Américo Claros)
UPDATE usuario SET correo = 'correo.del.jefe@gmail.com'      WHERE id = 2;  -- Jefe de Prensa (Roberto Arteaga)
UPDATE usuario SET correo = 'correo.del.periodista@gmail.com' WHERE id = 3; -- Periodista (Carla Rojas)
```

La contraseña de todas las cuentas de prueba es `canal11`. Solo sirve para el sistema web del siguiente hito.

## 6. Arrancar y probar

```bash
scripts/run.sh
```

1. Desde uno de los Gmail registrados, enviar un correo a `grupo14sa@tecnoweb.org.bo` con el asunto `AYUDA`.
2. En menos de un minuto debe llegar la respuesta «Re: AYUDA», con «Hola, … (rol)». **La primera vez, revisar Spam** y marcarla como «No es spam».
3. Desde un correo no registrado, la respuesta debe decir «No está registrado en el sistema».

```bash
scripts/estado.sh
```

Muestra si está corriendo y las últimas líneas del log (`logs/canal11.0.log`). El log anota cada correo recibido y cada respuesta enviada, con las contraseñas enmascaradas.

Si las dos pruebas responden bien, la versión queda lista para el tag `v0.1.0`.

## 7. Que siga corriendo

`run.sh` la deja en segundo plano: sigue corriendo al cerrar el SSH. Para que vuelva a arrancar si reinician el servidor (o si se cae), agregar dos líneas con `crontab -e`:

```
*/5 * * * * $HOME/canal11/scripts/run.sh --si-no-corre
@reboot $HOME/canal11/scripts/run.sh --si-no-corre
```

`--si-no-corre` no hace nada si ya está corriendo. Además, la aplicación nunca corre dos veces: tiene su propio bloqueo.

## 8. Detener y actualizar

```bash
scripts/stop.sh
```

Termina el correo en curso, envía las respuestas pendientes y anota «Aplicación detenida» en el log. Si en 45 segundos no terminó, la fuerza a terminar.

Para subir una versión nueva, **primero detenerla**: `subir.sh` reemplaza el jar en el mismo lugar, y la aplicación encendida lo sigue leyendo mientras corre (puede fallar en medio de un correo). Desde la PC, en este orden:

```bash
ssh grupo14sa@www.tecnoweb.org.bo "cd canal11 && scripts/stop.sh"
```

```bash
scripts/subir.sh grupo14sa@www.tecnoweb.org.bo
```

```bash
ssh grupo14sa@www.tecnoweb.org.bo "cd canal11 && scripts/run.sh"
```

Si el crontab de la sección 7 está activo, puede volver a arrancarla antes de que termine la subida: hacer los tres pasos seguidos (el cron revisa cada 5 minutos). El mensaje final de `subir.sh` todavía propone detener después de subir; se corrige en una rama `chore/` (revisión del 14/10, A6).

## 9. Java portable (solo si el servidor no tiene Java 17)

Sin permisos de administrador se puede usar un JDK copiado en la carpeta de la aplicación.

1. En la PC, descargar de [adoptium.net](https://adoptium.net/temurin/releases/?version=17) el **JRE 17** para Linux (`.tar.gz`) de la arquitectura del servidor. `verificar-servidor.sh` la muestra: `x86_64` → x64; `aarch64` → aarch64.
2. Subirlo:

   ```bash
   scp OpenJDK17U-jre_x64_linux_*.tar.gz grupo14sa@www.tecnoweb.org.bo:canal11/
   ```

3. En el servidor, descomprimirlo como `canal11/jdk`:

   ```bash
   cd canal11 && mkdir jdk && tar -xzf OpenJDK17U-jre_x64_linux_*.tar.gz -C jdk --strip-components=1 && rm OpenJDK17U-jre_*.tar.gz
   ```

Los scripts usan `canal11/jdk/bin/java` automáticamente. Para comprobarlo, volver a correr `verificar-servidor.sh`.

## 10. Problemas frecuentes

| Síntoma | Causa y solución |
|---|---|
| `ssh` o `scp` no encuentran `www.tecnoweb.org.bo` | El DNS de tecnoweb falla a ratos: usar la IP (sección 2) |
| No llega la respuesta | 1) `scripts/estado.sh`: ¿está corriendo? 2) Buscar el correo en el log. 3) Revisar Spam. 4) ¿El remitente está registrado con ese correo exacto? |
| El log dice «El buzón está abierto en otra sesión POP3» | Alguien tiene el buzón `grupo14sa` abierto con Thunderbird o con el celular por POP. Cerrarlo: mientras la app corre, nadie más debe leer ese buzón |
| El log dice «SMTP rechazó la respuesta… Relaying denied» | El servidor no deja enviar a ese dominio sin autenticar: rama `feat/smtp-cram-md5` |
| «Ya hay otra copia de la aplicación corriendo» | Ya está corriendo (`scripts/estado.sh`). Para reiniciarla: `scripts/stop.sh && scripts/run.sh` |
| `UnsupportedClassVersionError` al arrancar | El Java del servidor es anterior a 17: sección 9 |
| «La base todavía no tiene las tablas» | Paso 5 |
| Las fechas u horas salen corridas | La app fija `America/La_Paz` sola; revisar `app.zona_horaria` en `config.properties` |

## 11. Qué queda en el servidor

```
~/canal11/
├── canal11-pipeline.jar
├── config.properties          contraseñas (permiso 600, nunca en git)
├── config.example.properties  plantilla
├── despliegue.md              esta guía
├── scripts/                   run, stop, estado, verificar-servidor, cargar-bd
├── db/                        script de la base, datos de prueba, consultas y pruebas
├── logs/                      canal11.0.log (rota a 5 archivos de 5 MB) y consola.log
├── canal11.pid, canal11.lock  los manejan los scripts y la aplicación
└── jdk/                       solo si se usó un Java portable
```
