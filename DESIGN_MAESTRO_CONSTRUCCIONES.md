# BWE Maestro de las Construcciones — contrato simplificado (02/11/2026)

Estado: **DISEÑO EN DESARROLLO**. El codigo actual NO terraforma ni coloca CTOV/Explorify. No habilitar comandos destructivos hasta tener motor por lotes, respaldo de estados de bloques y pruebas en una copia del mundo.

## Decisión del propietario

La ubicación la decide el operador. Se permite **reemplazar completamente** una aldea, casas, arboles, vegetacion, rocas, agua y otros bloques dentro del volumen aprobado. No bloquear por detectar bloques artificiales ni contenedores. **No** se exige conservar ni restaurar inventarios, datos de block entities ni entidades. El operador acepta esa perdida al confirmar una obra destructiva.

El único respaldo interno requerido para `/bwe deshacer` es de **estados de bloques** (identidad, propiedades y fluidos representados en el estado). Deshacer NO recupera inventarios, loot, texto de carteles, NBT de block entities, aldeanos, animales ni otras entidades. Por eso se exige **ademas** un respaldo completo externo del mundo de ensayo antes de ejecutar terraformacion.

## Flujo deseado (NO implementado)

1. `/bwe listar`: catálogo de plantillas NBT CTOV/Explorify que ya estén instaladas en el servidor. No empaquetar NBT de terceros en BWE.
2. `/bwe seleccionar <id>`: seleccionar una pieza auténtica y repetirla en diferentes ubicaciones. Una pieza jigsaw no equivale a una aldea entera.
3. `/bwe margen <5..10>`: ampliar el rectángulo de despeje, predeterminado 7.
4. `/bwe proteger si|no`: decidir por obra si protegerla **después** de finalizar; nunca ignorar protecciones previas.
5. `/bwe terraformar`: confirmar y ejecutar, por lotes, el despeje con aire desde la cota de cimentación hasta superar tanto el tejado nuevo como las obstrucciones existentes; rellenar con bloques sólidos el subsuelo necesario hasta sostener la base. No equivale a un único `/fill air`.
6. `/bwe crear`: colocar la plantilla real por lotes en el terreno preparado, preservando la misma operación de deshacer.
7. `/bwe deshacer`: restaurar **solo los estados de bloques** anteriores a la primera operación de esa obra, sin prometer recuperación de inventarios o entidades.

## Invariantes de seguridad

- Nunca ejecutar obras sobre el servidor activo sin una autorización y pruebas separadas. Un comando en producción no forma parte del desarrollo.
- Una sola obra activa y un único respaldo pendiente; el respaldo de bloques debe persistir en disco **antes** de la primera escritura. `terraformar` y `crear` pertenecen a la misma transacción: no sobrescribir el snapshot entre etapas.
- Comprobar y revalidar chunks cargados, worldborder, altura mínima/máxima, spawn, zonas protegidas manualmente y asentamientos BWE protegidos antes de cada fase. No generar chunks involuntariamente.
- Nunca borrar, sofocar ni encerrar jugadores. Pausar una operación si un jugador entra en la zona de escritura.
- La política sobre mobs y otras entidades no-jugador puede ser destructiva, pero no se implementa su restauración; no afirmar que `/bwe deshacer` recupera la aldea anterior en su totalidad.
- Ejecutar cambios con presupuesto limitado por tick; comprobar antes de escribir el estado esperado y detenerse ante conflictos, conservando el respaldo.
- Usar recursos CTOV/Explorify únicamente desde los mods ya instalados; no redistribuir sus NBT. Las piezas jigsaw requieren un ensamblador independiente.
- No limitar artificialmente el tamaño de las plantillas en el catálogo. El motor de escritura puede rechazar un trabajo cuyo coste o almacenamiento exceda sus límites seguros.
- La geometría pura `ClearanceVolume.planColumn` solo calcula intervalos de despeje/relleno. **No lee ni escribe chunks**; todavía falta integrarla en un motor de terraformación.

## Pruebas pendientes antes de entregar un JAR definitivo

- Compilación Forge 1.20.1 / Java 17 y pruebas unitarias de geometría y presupuesto; una CI verde no equivale a una prueba en juego.
- Instancia aislada con copia íntegra del mundo, relieve desigual, árboles, agua, una aldea antigua, cofres y mods originales instalados legalmente.
- Verificar que se despeja todo el volumen, se rellenan huecos y la estructura queda apoyada; comprobar especialmente techos más altos que la nueva plantilla.
- Reiniciar durante terraformación, colocación y deshacer; verificar que se conserva el snapshot y que la restauración de estados de bloques funciona.
- Verificar que los contenidos de cofres y entidades **no** se prometen como recuperables. Restaurar el respaldo externo del mundo para recuperar esos datos.
- Probar protecciones opcionales, protección preexistente, jugadores en el volumen, límites del anillo, chunks descargados y ausencia de picos de MSPT.
- Confirmar que no hay NBT de terceros dentro del JAR y que el servidor activo permanece intacto.

Este documento describe objetivos y límites de recuperación; **no** certifica que los comandos futuros ya funcionen.

## Avance 02/11/2026 — normalizacion de superficie y cursor incremental (SIN escritura)

- Detectada una incompatibilidad de cotas: `TerrainColumnProbe.Result.groundY()` representa el **primer bloque libre** encima del suelo, mientras que `ClearanceVolume.planColumn` recibe el **ultimo bloque solido**. Pasar la cota directamente deja un bloque sin rellenar bajo una cimentacion elevada. El nuevo `planColumnFromSurface` convierte explicitamente esa cota y rechaza una superficie sin bloque de apoyo valido.
- Nuevo `TerraformColumnCursor`: recorre primero las posiciones de despeje (aire) y despues las de relleno, sin crear una lista gigante. `peek()` no avanza; `advance()` debe ejecutarse **solo despues** de completar una operacion, para no saltarse bloques si hay un fallo.
- Prueba Java 17 de 60 comprobaciones para orden, recuento, soporte, colinas y reintentos. Integrada en `tests/run_geometry_tests.sh`.
- **Solo geometria y planificacion:** no se conecto al motor Forge, no se ejecutaron comandos destructivos, no se colocaron NBT y no se hizo prueba en copia del mundo. El cursor tampoco persiste por reinicio; antes de escribir se necesita snapshot de estados de bloques y revalidacion por tick.

## Avance 02/11/2026 — recorrido incremental del area (SIN escritura real)

- Nuevo `TerraformAreaCursor`: recorre por columnas un rectangulo X/Z, usando `TerraformColumnCursor` para despejar y rellenar. No construye un mapa gigante de posiciones; el proveedor de planes se consulta por columna.
- `execute(presupuesto, writer)` ejecuta como maximo el numero de operaciones indicado. Si el escritor falla, el paso queda pendiente y se puede reintentar.
- `checkpoint()` y `resume(...)` permiten reconstruir la posicion logica del recorrido con los **mismos planes de columna inmutables**. Esto NO es por si solo recuperacion segura tras un crash: faltan persistencia de los planes, backup previo de estados, comprobaciones de conflictos y un escritor Forge idempotente.
- Pruebas Java 17 con un escritor falso: lotes, fallo simulado, reanudacion, orden X/Z, coordenadas extremas y rectangulo de 10.000 millones de columnas sin materializarlo.
- No se han ejecutado cambios de bloques ni pruebas en una copia real de RingWorld; no se autoriza aun `/bwe terraformar`.
