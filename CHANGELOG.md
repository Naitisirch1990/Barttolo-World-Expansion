## 0.6.18 — 2026-11-01 (experimental)

- `/bwe margen [5..10]`: margen de comprobacion horizontal por operador, temporal hasta reinicio, predeterminado 7.
- `/bwe superficie` muestrea solo la cimentacion de la pieza NBT. El margen se comprueba para chunks, limites y protecciones pero no afecta la mediana ni las estimaciones de corte/relleno.
- Geometria `TemplateFootprint.expanded` con pruebas de margenes, coordenadas negativas y overflow; nuevas auditorias estaticas.
- **Solo lectura.** No se ha implementado terraformacion, limpieza, pegado de plantillas ni respaldo/restauracion NBT. CI y pruebas en Minecraft deben verificarse por separado. No modificar servidor activo.

# v0.6.17 — 2026-11-01
- Medicion incremental de superficie para plantillas CTOV/Explorify con margen 7 y altura base mediana; sin escrituras ni carga de chunks.
- Comandos `/bwe superficie <id>`, `/bwe superficieestado`, `/bwe cancelarsuperficie`.
- Histograma puro Java 17 y pruebas de corte/relleno; auditoria estatica de lectura.
- NO implementa terraformacion, colocacion externa ni rollback de block entities. Solo pruebas en copia del mundo.

## v0.6.16 (2026-11-01, auditoria 3D incremental, solo lectura)
- Nuevo comando /bwe auditarplantilla <id> tras verificar la huella: recorre el volumen 3D de una pieza NBT por lotes de 1024 bloques/tick, sin cargar chunks ni modificar bloques.
- Limite de 8 millones de bloques por auditoria; no limita el catalogo de estructuras grandes. Progreso y cancelacion mediante /bwe auditoria y /bwe cancelarauditoria.
- Cuenta aire, liquidos, block entities y estados no clasificados como naturales. No inspecciona NBT, entidades, jigsaw ni detecta con certeza bloques de jugadores.
- Cursor de geometria Java 17 independiente de Minecraft con pruebas puras; CI incluye auditoria estatica adicional.
- Pendiente: prueba dentro de Minecraft/copia del mundo, colocacion reversible con NBT, terraformacion y ensamblaje de aldeas.

## v0.6.14 (2026-11-01, inspeccion sin colocar)
- /bwe inspeccionar muestra huella X × Z, superficie, conectores jigsaw, marcadores structure y cofres/barriles vanilla detectados. Indicadores parciales de una pieza, no auditoria NBT completa ni ensamblaje de aldeas.
- /bwe plantilla <id> es alias de inspeccion, sin planes falsos de construccion. Eliminada la lista estatica de IDs de ejemplo como restriccion de comandos.
- Las plantillas CTOV/Explorify siguen solo en catalogo; sin recursos de terceros empaquetados ni colocacion externa.
- Pendiente: pruebas en copia real, rollback NBT seguro, ensamblaje jigsaw y terraformacion.

## 0.6.12 (propuesta en rama de pruebas)
- Nuevo flujo sencillo: /bwe escanear, /bwe listar con opciones clicables, /bwe seleccionar y /bwe crear.
- Escaneo anclado al punto original: el jugador puede alejarse antes de construir.
- Nueva espera no destructiva por entidades que intersectan bloques solidos pendientes; los slimes cercanos fuera de esos bloques no bloquean.
- Conserva el reescaneo completo antes de construir, el respaldo persistente y /bwe deshacer.
- /bwe plantillas muestra referencias opcionales, sin copiar ni colocar activos CTOV/Explorify.
- Pendiente: pruebas reales en copia del mundo; auditorias estaticas no las sustituyen.

## v0.6.10 — seguridad de cancelacion y respaldo vacio (2026-11-01)

- `/bwe cancelar` ya no anuncia falsamente que detuvo una construccion: solo cancela planes no iniciados y rechaza trabajos activos o respaldos pendientes.
- `/bwe estado` distingue respaldos completos e incompletos para evitar finalizar accidentalmente un trabajo interrumpido.
- `BuildEngine.build` y `simulate` rechazan construcciones sin cambios; evita persistir un Undo vacio que bloquearia nuevos planes.
- Prueba estatica nueva y CI actualizado. No implica prueba de construccion/rollback dentro de Minecraft.

## v0.6.9 — marca persistente de finalizacion (solo desarrollo)

- `Undo.Completed` comienza en `false` y se persiste como `true` solo tras completar los bloques y aldeanos.
- `/bwe finalizar SI` no elimina respaldos incompletos o anteriores sin la marca; evita aceptar una obra tras un reinicio en el ultimo lote.
- Fallos al crear aldeanos detienen la obra y conservan el respaldo para restauracion.
- Rechaza deshacer respaldos vacios y registra una prueba estatica de regresion en CI.
- **Pendiente:** ejecutar pruebas reales en una copia aislada de RingWorld; no instalar en produccion.

## v0.6.8 — 2026-11-01
- Corregida la decodificación de estados de bloque del respaldo: ahora exige los campos NBT `Name` y `Properties` con sus tipos correctos y **todas** las propiedades del bloque. Un respaldo truncado ya no puede restaurar silenciosamente la orientación/mitad por defecto.
- Añadidas aserciones estáticas para la presencia y completitud de propiedades. La compilación y las pruebas Forge dentro del juego siguen pendientes.
- Los archivos fuente se preparan para publicarse en la rama de desarrollo del PR #2 y activar la compilación CI real.

## v0.6.7 (01/11/2026)
- Proteccion conservadora ante entidades ajenas durante `/bwe deshacer`, tanto al iniciar como antes de cada lote de restauracion.
- Los UUID de aldeanos reservados por BWE no impiden deshacer la propia aldea.
- GitHub Actions incluye PRs hacia main (sin duplicar ejecuciones por push en dev), con permisos de lectura y timeout.
- Verificacion estatica adicional; NO equivale a compilacion Forge ni prueba in-game.

## v0.6.6 — 2026-11-01
- Limpieza de referencias `ServerLevel` y planes al detener un servidor de pruebas; el respaldo de deshacer persiste.
- Corregido el argumento `/bwe plantilla namespace:ruta` usando Brigadier `greedyString()`; antes `word()` rechazaba `:` y `/`.
- Pruebas estáticas de ciclo de vida y argumento de plantilla, integradas en CI.
- Sin cambio en bloques/estructuras; sigue sin JAR compilado ni pruebas Forge runtime.

## v0.6.5 — seguridad de rollback

- El decodificador NBT de BlockState falla explicitamente ante propiedades desconocidas, valores no validos o tipos NBT inesperados. Evita una restauracion silenciosamente incorrecta.
- La validacion de `/bwe deshacer` ahora decodifica todos los estados guardados ANTES de comenzar, evitando una restauracion parcial por propiedades invalidas.
- Se agrega `tests/test_strict_undo_codec.py` a CI. Es una verificacion estatica; no sustituye una prueba Forge.
- No se incorpora ningun activo de CTOV o Explorify.

## v0.6.5 (fuentes experimentales, no compilado)
- Bloqueo de entidades en cada tick antes de colocar el siguiente lote de bloques.
- Mensajes de ocupacion incluyen objetos, vehiculos y decoraciones.
- Nueva verificacion estatica de la guardia entre lotes.
- Sin pruebas en Minecraft ni JAR confirmado.

# v0.6.3 (2026-11-01)

- Rechazar cualquier entidad en el área de la construcción (incluye drops, botes, vagonetas, marcos y soportes).
- GitHub Actions: fallar explícitamente si faltan fuentes, validar paridad simulación/obra y corregir ruta del artefacto.
- Nuevas verificaciones estáticas de CI y entidad.
- **Sin compilación Forge verificada ni pruebas en mundo de Minecraft.**

# v0.6.2
- Simulación con protección frente a entidades vivas; mismo volumen que construcción.
- Extracción de guarda compartida para evitar divergencias entre simular y construir.
- Test estático adicional para comparar barreras de seguridad.
- Sin cambios a activos o plantillas de mods de terceros.
- Aún pendiente: compilación y prueba dentro de Forge.

# v0.6.0 — correcciones, zonas rectangulares y pruebas

- Arreglado `Heightmap` import incorrecto para Minecraft 1.20.1.
- Nuevas exclusiones rectangulares persistentes por coordenadas X/Z.
- Detector de intersección de toda la huella, no solo del centro.
- Bloqueo conservador para no cortar troncos altos por la mitad.
- 14 casos unitarios Java de colisiones y geometría.
- **No se compiló contra Forge y no se probó en Minecraft.**
- CTOV y Explorify **no están incorporados** en el JAR; las plantillas externas siguen deshabilitadas hasta validar reversión de NBT y permisos de distribución.

# Historial de cambios

## v0.5.0 — noviembre 2026
- Registro persistente y anti-solapamiento para edificios finalizados.
- Comandos `/bwe asentamientos` y `/bwe olvidar <numero> SI`.
- Seis paletas orientativas para construcciones según el bioma.
- UUID de aldeanos reservados en respaldo previo a la construcción.
- Presupuesto acotado de 200 entradas de rollback por tick.
- Mod configurado para uso en servidor, con pruebas pendientes de compatibilidad.
- Siete pruebas unitarias Java de geometría y comprobaciones de fuentes/TOML.

Estado: **fuentes sin compilación Forge**. No apto para mundo principal.

## v0.6.1
- Verificación completa de chunks antes de acceder al heightmap.
- Detección de entidades vivas en el volumen a construir.
- Ajustes Gradle `java` y `reobfJar`; nueva configuración de CI.
- Sin cambios en los recursos de terceros (CTOV / Explorify no incorporados).
