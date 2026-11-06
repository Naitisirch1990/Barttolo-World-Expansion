# PRIORIDAD — prueba de colocacion de UNA pieza NBT CTOV/Explorify (PENDIENTE EN MINECRAFT)

**Cambio posterior al plan read-only historico:** la rama de desarrollo contiene ya una ruta **DESTRUCTIVA EXPERIMENTAL** conectada a \`/bwe crear\` y \`/bwe confirmar SI\` para una pieza CTOV/Explorify. Los procedimientos historicos de mas abajo que dicen "crear sigue bloqueado" se redactaron antes de esta implementacion. **NO hay prueba runtime ni autorizacion para usarlo en el servidor activo.**

1. Preparar copia nueva e INDEPENDIENTE de RingWorld, mods, configuraciones y dimensiones, mas un segundo respaldo inmutable para restaurar. No compartir AutoModpack del ensayo con clientes activos.
2. Verificar \`/bwe listar\`, \`/bwe inspeccionar ctov:<id>\`, \`/bwe margen 7\`, \`/bwe seleccionar ctov:<id>\` con una pieza pequena y terreno desechable. La ubicacion debe ser lejana al spawn, zonas protegidas y otros jugadores.
3. Salir del volumen de despeje sin cambiar la seleccion. Ejecutar \`/bwe simular\`. Registrar bloques de aire, cimentacion, construccion, limite de changes, y advertencias sobre NBT. Cambiar el terreno y volver a simular para confirmar que el plan se calcula de nuevo.
4. **Solo en copia** ejecutar \`/bwe crear\`. Inspeccionar \`/bwe estado\` y \`latest.log\`, contar bloques colocados y comprobar que no quedan restos de arboles/tejados dentro del despeje. Anotar si la fisica de bloques causa discrepancias.
5. Detener cuando termine; retirar entidades ajenas y ejecutar \`/bwe deshacer\`. Comparar estados de bloques en el volumen contra la copia previa; no se espera recuperacion de contenidos de cofres, block-entity NBT ni entidades.
6. Repetir con techos superiores al edificio, colinas, agua, base sin soporte, marcador jigsaw, variaciones de paleta, worldborder, chunks descargados, proteccion, spawn y un jugador que entra DURANTE la obra. Debe detenerse o rechazar segun corresponda, conservando el respaldo.
7. Reiniciar a mitad de obra en una **segunda copia desechable**, verificar que el snapshot conserva BEFORE/AFTER y que \`/bwe deshacer\` puede funcionar con los chunks cargados; cualquier fallo debe permitir restaurar la copia externa. Evaluar MSPT y memoria.
8. No pasar a produccion hasta confirmar todo lo anterior y auditar que el JAR no incluye NBT CTOV/Explorify.


---


## Ensayo siguiente — simulacion de pieza NBT CTOV/Explorify (NO EXECUTADA)

**Este ensayo todavia NO se ha ejecutado dentro de Minecraft y NO autoriza /bwe crear sobre NBT.**
Preparar solo un mundo descartable con los archivos CTOV/Explorify autenticos ya instalados; conservar un respaldo completo e inmovil antes del primer comando.

1. Identificar un ID individual en \`/bwe listar\` y comprobar la informacion de \`/bwe inspeccionar <id>\`. No confundir una pieza jigsaw con una aldea completa.
2. Situarse en la base Y elegida, lejos del spawn y de protecciones, con los chunks del margen cargados. Ejecutar \`/bwe margen 7\` y \`/bwe seleccionar <id>\`.
3. Alejarse de la zona de construccion **sin seleccionar una posicion nueva**. Ejecutar \`/bwe simular\`. El informe debe distinguir aire, tierra/cimentacion, bloques restantes y NBT especial ignorado.
4. Repetir con margen 5 y 10, volviendo a seleccionar el ID en el mismo anclaje. Confirmar que cambian los limites horizontales y el conteo, y que la ubicacion Y permanece constante.
5. Ensayar junto a un techo alto, arboles, agua y desniveles. La simulacion no debe dejar estructuras flotantes ni omitir techos por encima de la nueva pieza. Si no existe soporte hasta 12 bloques debajo, debe **rechazar** la propuesta.
6. Entrar al volumen con otro jugador: debe rechazarse. Repetir al lado de un chunk descargado, worldborder, spawn, area protegida y sitio BWE finalizado; ningun caso debe cargar/generar chunks ni cambiar bloques.
7. Comprobar que \`/bwe crear\` **sigue bloqueado para NBT externo**, incluso despues de una simulacion exitosa. Este paso prueba que la nueva funcionalidad es exclusivamente preparatoria.
8. Comparar el mundo antes y despues (bloques, NBT, entidades, ficheros region y \`latest.log\`). Cualquier diferencia de bloques producida por BWE es un fallo.

Limites provisionales: 160.000 posiciones examinadas, 55.000 cambios calculados. Los casos mayores deben permanecer en el catalogo, pero rechazarse en esta simulacion hasta implementar escaneo por lotes. Las capturas anteriores del campamento BWE original no prueban la construccion con NBT externos.


## Regresion de auditoria 3D — revalidacion al terminar (PENDIENTE EN JUEGO)

**No ejecutada en Minecraft. Usar exclusivamente copia aislada.**

1. Iniciar `/bwe auditarvolumen <id>` en una zona cargada y autorizada, con un volumen suficientemente grande para que tarde varios ticks.
2. Antes de que termine, proteger una parte de la zona mediante los comandos BWE; al finalizar debe indicar `Auditoria de despeje INVALIDADA`, sin presentar un informe como vigente.
3. Repetir desplazando el worldborder hasta intersectar el margen; debe invalidarse.
4. Repetir descargando un chunk del margen ya examinado (sin forzar su carga); debe invalidarse.
5. Repetir cambiando la reserva de spawn si el entorno de pruebas permite hacerlo; debe invalidarse.
6. Repetir sin cambios de estado; debe completar la auditoria en modo solo lectura.
7. Comparar regiones, entidades y block entities antes/despues; ninguna auditoria debe modificar el mundo.

La revalidacion ocurre **al terminar**; no es un bloqueo transaccional y no autoriza colocar estructuras ni terraformar.

## Prueba pendiente — sondeo bajo tejados (desarrollo posterior a 0.6.18)

**No ejecutada en Minecraft. Solo sobre copia aislada.**

1. En una aldea vanilla existente, identificar una pieza de plantilla CTOV/Explorify en el catalogo. Usar el mismo anclaje y margen 7 en todas las mediciones.
2. Medir una zona con tejados de madera sobre suelo de tierra: comprobar si el resumen informa `obstaculos sobre base` y recomienda una altura cercana al suelo, no al tejado.
3. Repetir sobre edificio de piedra o arenisca: esos materiales pueden confundirse con terreno. **No usar la altura sugerida como permiso para terraformar**.
4. Repetir sobre superficie modded/no reconocida y sobre una columna con mas de 48 bloques de obstaculos. Si mas del 10% queda sin clasificar, debe mostrar `SIN BASE SUGERIDA`.
5. Comparar las regiones antes y despues: este comando debe seguir siendo exclusivamente de lectura.
6. Medir MSPT en un area cargada con muchas estructuras: el sondeo procesa 16 columnas por tick, con un maximo de 48 lecturas de bloque por columna.

## Prueba adicional v0.6.18 — cimentacion frente a margen (PENDIENTE EN JUEGO)

Usar **solo una copia aislada** del mundo con Forge 1.20.1 y los mods originales CTOV/Explorify instalados por su propietario. No instalar en el servidor activo.

1. Elegir una plantilla individual del catalogo (no asumir que una pieza jigsaw es una aldea completa).
2. Situarse sobre una base relativamente plana con una colina o edificio alto a 5..10 bloques **fuera** de la huella NBT.
3. Ejecutar `/bwe margen 5` y `/bwe superficie <id>`; anotar la base Y, corte, relleno y area de despeje propuesta.
4. Repetir con `/bwe margen 10`. La altura Y, corte y relleno deben permanecer iguales cuando el punto y la plantilla no cambian; solo debe aumentar el area de comprobacion.
5. Verificar `/bwe margen`, `/bwe superficieestado` y `/bwe cancelarsuperficie`. Los margenes fuera de 5..10 deben rechazarse.
6. Tomar una copia de seguridad del mundo antes y despues del sondeo y confirmar que **no se modifico ningun bloque ni entidad**.
7. Confirmar que el sondeo rechaza chunks descargados, worldborder, spawn y protecciones previas, sin generarlos.
8. Reiniciar la instancia de prueba y verificar que el margen vuelve a 7.

**Resultado actual:** no ejecutado dentro de Minecraft; el ensayo necesita una copia completa del mundo y los JAR externos. Este protocolo no autoriza terraformacion ni colocacion.

# Plan de ensayo seguro — Barttolo World Expansion v0.6.17

Estado: **pruebas de construccion/restauracion NO ejecutadas**. Hay captura del 01/11/2026 con `/bwe listar` funcionando en un mundo descartable usando v0.6.9; no se ha proporcionado copia del mundo para ensayos automatizados. v0.6.10 requiere compilacion CI independiente.

## Preparacion (fuera del servidor activo)

1. Detener exclusivamente la instancia de ensayo. Obtener un respaldo consistente del servidor de origen por el procedimiento habitual del host; no copiar ficheros de region mientras se escriben.
2. Crear otra carpeta y otro puerto para una instancia Forge 1.20.1 / 47.4.10 / Java 17. **No reutilizar la carpeta world del servidor activo**.
3. Copiar completos `world/`, dimensiones y subcarpetas (`region`, `entities`, `poi`, `data`), `server.properties`, `config/`, `defaultconfigs/`, `mods/`, `kubejs/` o `datapacks/` si existen. No copiar credenciales, tokens ni listas de acceso a un entorno publico.
4. Deshabilitar el acceso de jugadores y la publicacion de la instancia de ensayo. Asegurarse de que AutoModpack no publique cambios del ensayo a clientes del servidor activo.
5. Registrar hashes de `level.dat`, de los ficheros de region relevantes y el listado de mods. Conservar un respaldo inmutable adicional para comparar despues.
6. **Solo si GitHub Actions genera un JAR real y este supera la inspeccion**, instalarlo en `mods/` de la instancia de ensayo, nunca en produccion.

## Secuencia inicial

1. Arrancar la instancia y confirmar en `latest.log` que BWE carga sin errores de registro. Verificar `/bwe ayuda` con OP de nivel 2 y que no existe acceso de usuario normal.
2. Proteger manualmente las coordenadas de spawn, misiones, bases, estructuras previas y camino iluminado; confirmar con `/bwe protegidas` y `/bwe areas`.
3. Buscar terreno llano, natural y cargado, lejos de jugadores y entidades. Ejecutar `/bwe analizar 12` y `/bwe planear campamento`.
4. Salir de la huella y ejecutar `/bwe simular`; anotar numero de bloques y coordenadas. Probar con un item tirado dentro: debe rechazar la simulacion sin modificar el mundo.
5. Retirar el item, repetir simulacion, ejecutar `/bwe confirmar SI`, verificar `/bwe estado`. Introducir un item **despues de iniciar** una obra con varios lotes: debe detenerse y conservar el snapshot de deshacer. Registrar logs.
6. Quitar el item y ejecutar `/bwe deshacer`; comprobar que termina y que la zona coincide con el respaldo original. Si hay un error, **no** usar `/bwe finalizar SI` y restaurar el backup del ensayo.
7. Repetir con obra completa, probar reinicio de la instancia de ensayo antes de `/bwe deshacer` y verificar integridad de bloques y entidades. Revisar que el registro de asentamientos no permita solapamientos al finalizar una obra.
8. Medir MSPT y memoria durante plan, construccion y rollback; repetir en distintos biomas y cerca de limites laterales del anillo.

## Criterios de salida

- JAR reobfuscado construido por CI y con version/metadatos correctos.
- Ninguna excepcion de Forge o RingWorld al iniciar y al ejecutar los comandos.
- Sin cambios fuera de la huella, sin entidades atrapadas y sin generacion accidental de chunks.
- Deshacer exacto de bloques en el escenario controlado; respaldo de mundo restaurable y validado.
- No se publican recursos CTOV/Explorify ni se modifica produccion.

La integracion de estructuras CTOV/Explorify continua desactivada. La aprobacion de estas pruebas **no autoriza** desplegar automaticamente en produccion.

## Caso adicional v0.6.6: restauracion con propiedades de bloque

En la copia, realizar una obra con camas y bloques orientables; confirmar que `/bwe deshacer` restaura el estado original exacto. Ensayar aparte un respaldo NBT manipulado **solo en un mundo descartable** con una propiedad de bloque desconocida: el rollback debe detenerse con error, sin restaurar ese bloque a un estado por defecto. Conservar la copia para recuperacion; esta prueba no se ha ejecutado.

## Caso adicional v0.6.6: reinicio de servidor

1. En una copia aislada, inicia un campamento, observa `/bwe estado` y detén el servidor antes de terminar la obra.
2. Reinicia el mismo mundo de prueba; verifica que no se reanuda un trabajo antiguo sobre una referencia `ServerLevel` cerrada.
3. Verifica que `/bwe estado` muestre un respaldo pendiente; **no** ejecutes `/bwe finalizar SI`.
4. Carga los chunks afectados y prueba `/bwe deshacer`; compara con un respaldo previo de la copia.

## Prueba de seguridad v0.6.7: entidades durante restauracion

En una copia aislada del mundo, construir un campamento, salir del volumen y completar la obra.
Antes de `/bwe deshacer`, colocar una entidad o entrar con un segundo jugador en el volumen:
la orden debe rechazarse sin modificar bloques y conservar el respaldo. Despejar el
volumen, iniciar `/bwe deshacer` y volver a introducir una entidad antes del siguiente
lote: la restauracion debe detenerse sin borrar el respaldo. Retirar entidades y repetir
`/bwe deshacer` para completar. En aldeas, los aldeanos creados por BWE no bloquean
la restauracion, pero cualquier entidad ajena si. Comparar regiones y NBT contra la
copia original. Esta prueba no se ha ejecutado en Minecraft.


## Caso adicional: reinicio en el ultimo lote (parche de seguridad)

- En la copia, interrumpir una obra despues de colocar los bloques pero antes de finalizar el ciclo de aldeanos (solo en entorno de depuracion).
- Verificar que el NBT de `barttolo_world_expansion` conserva `Undo.Completed=false` y que `/bwe finalizar SI` es rechazado.
- Ejecutar `/bwe deshacer` con el area vacia y comprobar restauracion y limpieza de aldeanos rastreados.
- Tras una obra terminada, comprobar `Undo.Completed=true` y que `/bwe finalizar SI` se habilita solo con estados de bloques intactos.
- Respaldos antiguos sin `Completed` se consideran incompletos: no forzar finalizacion ni editar NBT manualmente.

## Caso adicional v0.6.10: cancelar no detiene obras y respaldo vacio

1. En mundo descartable, crear un plan sin confirmar y ejecutar `/bwe cancelar`: debe eliminar solo ese plan.
2. Iniciar una obra de varios lotes y ejecutar `/bwe cancelar`: debe rechazar la orden, sin afirmar que detuvo el motor. Consultar `/bwe estado` y comprobar que sigue activa o conserva respaldo.
3. Despues de una obra terminada y antes de finalizar/deshacer, ejecutar `/bwe cancelar`: debe rechazarla y conservar el respaldo.
4. `/bwe estado` debe indicar `COMPLETO` o `INCOMPLETO` segun la marca persistida.
5. Verificar que una simulacion sin cambios no permita crear un respaldo vacio. Estos escenarios todavia no se han ejecutado en Minecraft.


## Regresion de campamento tras fallo (v0.6.11)

Conservar una copia de `Mundo nuevo` antes de abrirlo con otra version. En esa copia, ejecutar `/bwe diagnosticar` y registrar conteos y primer conflicto; no usar `/bwe finalizar SI` ni borrar el respaldo. En un **segundo mundo nuevo descartable** sin respaldo, despejar la huella, `/bwe planear campamento`, `/bwe simular`, `/bwe confirmar SI`, `/bwe estado`, `/bwe diagnosticar` y, si termina, `/bwe deshacer`. Revisar `latest.log` para mensajes `[BWE]` y excepciones completas. No interpretar un CI verde como prueba en Minecraft. El servidor activo permanece fuera del alcance.


## v0.6.17: medicion de superficie CTOV/Explorify (SOLO LECTURA)

En una instancia descartable, instalar los mods originales CTOV y/o Explorify **por separado y con sus licencias**. No incluir sus NBT en el JAR BWE.

1. Verificar `/bwe listar` y seleccionar un ID real del catalogo. Colocarse en el centro de la ubicacion propuesta, con la huella y 7 bloques extra por lado ya cargados.
2. Ejecutar `/bwe superficie <namespace:id>` y consultar `/bwe superficieestado` durante el escaneo. Al terminar, registrar Y minima, maxima, mediana y estimaciones de corte/relleno. Comprobar visualmente si hay hojas, agua, cuevas, viviendas o tejados que el heightmap no describe.
3. Probar `/bwe cancelarsuperficie` durante una medicion grande; debe detenerse sin tocar bloques.
4. Repetir con un chunk del margen descargado, junto a worldborder, spawn y zonas protegidas. Debe fallar sin cargar/generar terreno ni mover entidades.
5. Comparar hashes y archivos de regiones antes y despues de la medicion. Verificar que no cambia el mundo. La medicion NO autoriza terraformacion ni colocacion de NBT.
6. Registrar `latest.log`, resultados y capturas. No pasar a produccion hasta disponer de pruebas de colocacion y deshacer de NBT y entidades.

## Regresion 2026-11-02: margen cambiante durante auditoria (SOLO LECTURA)

Estas comprobaciones son **procedimientos pendientes**, no pruebas ya ejecutadas.
En una copia descartable de RingWorld con CTOV/Explorify instalados legalmente:

1. Elegir una pieza NBT del catalogo y una zona cuyo despeje expandido (5..10 bloques) quede a menos de 8 bloques de una proteccion manual, o a menos de 16 bloques de una obra BWE finalizada. Ejecutar `/bwe auditarvolumen <namespace:id>`. Debe rechazar la auditoria del despeje; `/bwe auditarplantilla` conserva su verificacion independiente de la huella.
2. Iniciar `/bwe superficie <namespace:id>` en un area grande y, antes de que termine, descargar un chunk que solo pertenezca al **margen** (sin descargar la huella). El resultado final debe invalidarse, no mostrar una base sugerida. Repetir con los chunks cargados para verificar que no se produce un falso rechazo.
3. Repetir el escaneo y, durante su ejecucion, agregar una zona protegida que intersecte el margen. Debe invalidarse al terminar. Probar tambien un worldborder reducido despues de iniciar el escaneo.
4. Inspeccionar `latest.log`, hashes de archivos de region y NBT antes/despues. No ejecutar comandos destructivos, ni fusionar el PR ni desplegar en el servidor activo.

Las comprobaciones estaticas de CI verifican la presencia de estos guardias; **no sustituyen estas pruebas dentro de Minecraft**.

## Persistencia de terraformacion (prototipo de almacenamiento, 02/11/2026)

La clase Java 17 `TerraformBackupFile` guarda y comprueba una carga binaria opaca
mediante SHA-256, longitud, version y renombrado atomico. Rechaza archivos
truncados, alterados y respaldos vacios; no sobrescribe deliberadamente un
respaldo existente. El archivo debe escribirse y verificarse **antes** de
modificar cualquier bloque. Se prohíbe usar un sistema de archivos que no
admita el renombrado atomico; la durabilidad ante corte electrico requiere
sincronizacion de directorio y soporte fiable del almacenamiento.

**Aun no hay un serializador de estados Minecraft, ni un trabajo Forge de
terraformacion, ni una restauracion conectada a esta clase.** Los tests
`TerraformBackupFileTest` trabajan con archivos temporales y NO son pruebas
de una copia del mundo. Inventarios y entidades quedan fuera del contrato
solicitado; solo se proyecta restaurar estados de bloques.
