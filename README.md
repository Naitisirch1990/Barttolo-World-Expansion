# Barttolo World Expansion v0.6.11 — PRUEBAS EXPERIMENTALES

**Minecraft 1.20.1 · Forge 47.4.10 · Java 17 · servidor RingWorld**

> ⚠️ El código de la rama de desarrollo se compila con Forge mediante GitHub Actions; **eso no demuestra que arranque ni funcione en un mundo real**. El JAR solo es apto para ensayos aislados hasta superar las pruebas de construcción y restauración. **No instalar en el mundo principal**.

> **Actualización v0.6.13 (1 nov 2026, experimental):** `/bwe listar` y `/bwe listar <pagina>` descubren dinamicamente los IDs `ctov:` y `explorify:` expuestos por el gestor de plantillas del servidor, muestran su huella X × Z real (8 por pagina) y ofrecen un clic de **inspección** con `/bwe inspeccionar <id>`. No hay límite artificial de 150 × 150 para **catalogar**. Las estructuras compuestas por jigsaw no se ensamblan ni se miden como conjunto: solo aparecen las piezas NBT individuales. **NO se colocan plantillas externas, no se terraforman aldeas vanilla, ni se restaura NBT de contenedores.** La inspección es de solo lectura; se requiere Forge 1.20.1 y los recursos externos cargados en el servidor. La compilación y las pruebas en copia del mundo se deben verificar por separado antes de instalar.

## Objetivo

Construir **sobre chunks existentes** sin volver a generar el mundo. Los 8 tipos originales del prototipo son `aldea`, `campamento`, `ruina`, `torre`, `refugio`, `altar`, `pozo` y `carreta`. Para una prueba inicial, usar primero un `campamento` en una copia del mundo. CTOV y Explorify solo aparecen como referencias a plantillas instaladas: **el constructor NO coloca aún estructuras externas ni ensambla aldeas CTOV**.

El mod se plantea **solo en el servidor** (`displayTest="IGNORE_SERVER_VERSION"` en `mods.toml`), sin GUI ni paquetes de red propios; debe confirmarse la compatibilidad real con Forge/AutoModpack mediante una prueba.

## Estado de pruebas

El usuario mostro el 1 de noviembre de 2026 una captura de un mundo descartable donde `/bwe listar` respondia con las ocho estructuras y las referencias CTOV/Explorify. Es evidencia limitada de carga del JAR anterior v0.6.9, **no** demuestra que una construccion, restauracion o v0.6.10 funcione. La colocacion de plantillas externas continua desactivada.

**v0.6.10:** `/bwe cancelar` ahora solo cancela un plan sin ejecutar. Rechaza la orden si existe una obra activa o un respaldo pendiente, para que nadie interprete el mensaje como si hubiese detenido la construccion. `/bwe estado` indica si el respaldo es completo o incompleto. El motor rechaza obras de cero cambios antes de crear un respaldo inutilizable.

## Correcciones de seguridad acumuladas

**Seguridad v0.6.5:** se repite la comprobacion de entidades antes de cada lote de hasta 200 bloques; si alguien entra durante una obra, se detiene sin borrar el snapshot de deshacer. Comprobacion de codigo, aun no probada en Forge.

**Seguridad v0.6.5:** la guarda de ocupación comprueba todas las entidades, no solo seres vivos. Por tanto, también bloquea la obra si hay objetos tirados, vagonetas, botes, marcos o soportes de armadura en la huella. Esto evita modificar el terreno bajo entidades existentes. No se destruyen ni se mueven entidades para despejar el lugar. Se corrigió además la ruta del artefacto en GitHub Actions y se añadió una comprobación explícita para no mostrar compilaciones exitosas cuando faltan fuentes. La verificación CI local es estática; todavía no hay JAR Forge compilado.


1. **Registro persistente de obras finalizadas**. Tras `/bwe finalizar SI`, almacena en `SavedData` la caja de la construcción. Prohíbe obras BWE nuevas que intersecten esa zona o estén a **16 bloques o menos** de su límite.
2. `/bwe asentamientos` lista hasta 30 sitios registrados por comando. `/bwe olvidar <numero> SI` elimina únicamente el registro de protección; **no borra edificios ni bloques**, y se rechaza mientras haya trabajo pendiente. Los sitios se guardan junto a las exclusiones manuales.
3. **Seis paletas originales según el bioma:** desierto/badlands, sabana, jungla, pantano/manglar, taiga/nieve y pradera/general. Cambian paredes, techos y caminos de las casas. La elección de paleta es orientativa; **no prueba adaptación completa a todos los 14 biomas personalizados**.
4. **Reserva persistente de UUID de aldeanos** antes de empezar a colocar bloques. Esto permite identificar los aldeanos creados por una obra si el servidor se interrumpe durante su generación. La eliminación todavía requiere que esas entidades estén cargadas al deshacer.
5. **Deshacer más acotado por tick:** cuenta también los bloques que ya estaban restaurados y no solo los modificados (máximo 200 entradas procesadas por tick).
6. **Geometría probada con Java 17:** `SiteMath.java` no depende de Forge y tiene 14 casos de prueba ejecutables, incluidos límites de coordenadas y separación de estructuras.
7. Flujo de GitHub Actions ampliado con tests Java, auditoría de fuentes y de `mods.toml` antes de ejecutar la compilación Forge.

## Comandos disponibles en el código (aún NO probados dentro del juego)

```text
/bwe ayuda
/bwe listar
/bwe analizar 12
/bwe buscar aldea 64
/bwe planear campamento
/bwe planear aldea
/bwe planear pozo
/bwe previsualizar
/bwe simular
/bwe confirmar SI
/bwe estado
/bwe deshacer
/bwe finalizar SI
/bwe cancelar
/bwe proteger 100
/bwe protegidas
/bwe desproteger 1
/bwe protegerarea 500 250 700 280
/bwe areas
/bwe liberararea 1 SI
/bwe asentamientos
/bwe olvidar 1 SI
/bwe plantilla explorify:campsite/main/01
```

**Advertencia:** `/bwe plantilla <id>` solo comprueba datos y genera un plan textual; `/bwe confirmar SI` bloquea deliberadamente la colocación de NBT externos. `/bwe previsualizar` es **texto**, no un holograma; `/bwe buscar` se limita a sondeos en chunks cargados. Solo administradores OP de nivel 2.

## Seguridad: límites conocidos

- El detector de bloques construidos no identifica todas las bases y zonas de misiones. Se deben proteger manualmente las regiones relevantes del RingWorld actual: bases, spawn, misiones y camino principal antes de cualquier prueba.
- Una zona de aldeas vanilla/CTOV/Explorify preexistente **no aparece automáticamente** en `/bwe asentamientos`. El registro cubre solamente obras BWE expresamente finalizadas desde esta versión en adelante.
- Solo Overworld, suelo relativamente plano (desnivel <=4), chunks cargados, sin fluidos en el área y sin inventarios preexistentes en la huella. Para evitar daño, el sistema cancela cuando no reconoce un bloque como natural.
- Un respaldo BWE no sustituye un backup completo. Dinámica de líquidos, cultivos, mods, físicas de Minecraft, trozos de terreno descargados y cambios de otros jugadores pueden impedir un deshacer perfecto.
- No debe usarse `/bwe finalizar SI` antes de verificar visualmente una obra completa: al finalizar se elimina el registro que permitía deshacerla desde BWE.
- La generación es limitada a una obra a la vez; no hay distribución automática por los 14 biomas, ni algoritmo jigsaw de CTOV, ni colocación autorizada de activos de terceros en un solo `.jar` todavía.

## Cómo compilar (cuando exista acceso a ForgeGradle)

1. Crear un repositorio propio **privado** para este proyecto y colocar el contenido de esta carpeta en su raíz. No reemplazar RingWorld ni RingWorld-MISIONES.
2. Abrir la pestaña Actions y ejecutar **Compilar BWE para Forge 1.20.1**, o hacer push a `main`.
3. Se ejecutan tests estáticos/Java y luego `gradle --no-daemon --stacktrace build` usando **JDK 17**, Gradle 8.8 y Forge 1.20.1-47.4.10.
4. Si falla, corregir los errores del compilador; **no se ha demostrado que el código compile**. Si pasa, examinar el artefacto `BarttoloWorldExpansion-Forge-1.20.1-v0.6`.
5. Probar el jar **solo en una instancia separada**, con una copia íntegra de `world/`, `region/`, `entities/`, `poi/`, `data/`, configs y mods. Un build exitoso NO garantiza que sea seguro en el juego.

Consulta `TEST_PLAN_COPY.md` para la secuencia de ensayos en una copia aislada.

## Pruebas offline disponibles

```bash
python3 tests/verify_source.py
python3 tests/verify_toml.py
bash tests/run_geometry_tests.sh
python3 tests/verify_resource_ids.py "CTOV.jar" "Explorify.jar"
```

Las primeras tres deben volver a ejecutarse sobre v0.6 en el entorno de preparación. La cuarta comprueba presencia de 15 archivos `.nbt` en **los JAR originales proporcionados**. Son pruebas **estáticas / unitarias de geometría**; no prueban gameplay, creación de mundo, rendimiento ni la API completa de Forge.

## Primera prueba recomendada, después de compilar

1. Completar un backup real y arrancar una copia **separada** del servidor.
2. Proteger spawn, misiones, bases y zonas con `/bwe proteger 100` (ajustar el radio).
3. Buscar un sitio plano fuera del camino principal y ejecutar `/bwe planear campamento`, luego `/bwe simular`.
4. Salir del área; si la simulación es apta, `/bwe confirmar SI`.
5. Verificar `/bwe estado` y después **probar `/bwe deshacer`**. Revisar físicamente que todo se recupera.
6. Repetir y, si es correcto, aceptar una obra con `/bwe finalizar SI`, luego consultar `/bwe asentamientos`.
7. Intentar planear otra construcción muy cerca y comprobar que sea rechazada. Probar reinicio y persistencia del registro.

## Licencias y recursos

No incluye `.nbt`, modelos ni código de CTOV o Explorify, y no les asigna una licencia nueva. Las referencias a nombres de estructuras son solo para análisis con los mods originales, si están instalados. Para cualquier redistribución o inclusión de recursos de terceros en un único mod, confirmar autorización de sus respectivos titulares.

## Revisión v0.6.5 (01/11/2026)

- Antes de consultar el heightmap, **verifica todos los chunks** que intersecan el área de muestreo, no solo sus esquinas. Evita provocar carga accidental de regiones interiores mientras se busca terreno.
- Antes de construir, rechaza entidades vivas dentro del volumen de la obra (no solo jugadores). Evita encerrar animales o aldeanos existentes.
- Solicita explícitamente el plugin `java` y añade `reobfJar` al JAR de producción. Esto aún **no sustituye** la compilación Forge ni las pruebas con el servidor.
- Se añade `.gitignore` y `gradle.properties` para GitHub Actions.
- Repositorio conectado: https://github.com/Naitisirch1990/Barttolo-World-Expansion ; el repositorio estaba **público** al revisarlo y conviene configurarlo como privado antes de subir el resto del código.


## Cambio v0.6.5

La simulación (`/bwe simular`) ahora comprueba entidades vivas en la misma
región tridimensional que la construcción (`/bwe confirmar SI`). Si hay
jugadores, animales o aldeanos dentro, se cancela sin alterar bloques.
Se añadió `tests/verify_simulation_parity.py` a las comprobaciones locales.
La v0.6.5 aún **NO** ha sido compilada ni ejecutada dentro de Forge.

## Cambio v0.6.5

Restauracion estricta: si el estado respaldado contiene una propiedad desconocida o un valor invalido, `/bwe deshacer` se rechaza antes de modificar ningun bloque y conserva el respaldo en lugar de restaurar un estado por defecto incorrecto. Sigue siendo obligatorio ensayar en una copia.

## v0.6.6: ciclo de vida de servidor

Al detener el servidor (incluidos mundos integrados de prueba), se liberan referencias a `ServerLevel` y planes pendientes en memoria. El snapshot de deshacer en SavedData **no se elimina**: si el servidor se detuvo durante una obra, el operador debe inspeccionar `/bwe estado` y usar `/bwe deshacer` en una copia. Esta corrección no está validada dentro de Forge.

**Catálogo opcional:** `/bwe plantilla explorify:campsite/main/01` acepta ahora el ID con `:` y `/`, pero la colocación externa continúa desactivada por seguridad hasta contar con rollback de NBT.

## v0.6.8: guardia de entidades durante rollback

El comando `/bwe deshacer` y cada lote de restauracion ahora rechazan la presencia
de entidades ajenas dentro del volumen de bloques afectados. Se ignoran exclusivamente
los UUID reservados para aldeanos creados por esa obra BWE. Ante una ocupacion nueva
se detiene la restauracion y se conserva el respaldo persistente. Esta modificacion
solo ha superado inspecciones estaticas; falta validar Forge y Minecraft.

El workflow se prepara para ejecutarse tambien en PRs hacia `main` (sin ejecuciones duplicadas por push en dev).


## Seguridad v0.6.10 — finalizacion persistente

La copia de seguridad NBT incluye ahora `Undo.Completed`. Se guarda como `false` antes de cambiar bloques y solo pasa a `true` cuando todos los bloques y los aldeanos previstos se han colocado correctamente. `/bwe finalizar SI` rechaza respaldos incompletos y no descarta su capacidad de deshacer. Si falla la creacion de un aldeano, el proceso se detiene conservando el respaldo. Un respaldo anterior sin esta marca se considera incompleto por seguridad; primero se debe comprobar o deshacer en una copia aislada.

**Pruebas:** las comprobaciones estáticas y de geometría no sustituyen una ejecución en Minecraft. El arranque Forge, la integración con RingWorld, los reinicios a mitad de construcción y la restauración de entidades continúan pendientes de ensayo en una copia.


## Incidente reproducido 01/11/2026 (mundo descartable)

`latest(3).log` confirma que `/bwe confirmar SI` inició un campamento de 562 bloques a las 15:12:45.819 y reportó error a las 15:12:45.842 (23 ms después). No contiene la excepción interna. El rollback rechazó una discrepancia en x=150,y=66,z=-129 y posteriormente x=151,y=68,z=-133; el backup quedó protegido. El usuario no vio construcción visible. No está probado si la causa fue una actualización vecinal, entidad, error de `setBlock` u otra condición.

**v0.6.11 (experimental):** si un bloque ya coincide exactamente con el destino previsto, la construcción lo cuenta sin invocar `setBlock` nuevamente. Si no coincide ni con el original ni con el destino, detiene la obra sin sobrescribirlo. Los errores ahora registran excepción y traza con logger de Minecraft y muestran al operador causa/progreso. `/bwe diagnosticar` es SOLO LECTURA y cuenta originales, destinos, discrepancias y chunks descargados del respaldo pendiente. No desbloquea ni fuerza restauración. Se requieren pruebas en mundo descartable antes de declarar corregido el fallo.


### Flujo simplificado (v0.6.12, pruebas)
1. En el punto deseado: `/bwe escanear` (o `/bwe escanear 22` para aldeas grandes).
2. `/bwe listar` y clic sobre la estructura deseada (ejecuta `/bwe seleccionar <tipo>`).
3. Alejarse de los bloques que se van a construir y ejecutar `/bwe crear`.
4. `/bwe estado` informa progreso o espera por entidades sobre bloques pendientes.
5. `/bwe deshacer` restaura el respaldo cuando la zona esta despejada.

El escaneo y la seleccion no modifican bloques. El motor revalida terreno y protecciones antes de crear.
Los comandos avanzados anteriores siguen disponibles por compatibilidad, pero no se muestran en el flujo basico.
**Usar solo en una copia descartable hasta verificar construccion y restauracion en Minecraft.**
