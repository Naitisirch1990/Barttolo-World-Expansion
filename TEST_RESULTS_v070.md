# Validación v0.7.0 — 2026-11-02

## CI verificada (commit `7a49b3f59b7ce32582a002e57e004a0edf1e205f`)

- GitHub Actions [ejecución #127](https://github.com/Naitisirch1990/Barttolo-World-Expansion/actions/runs/41450558701): **success**.
- Java 17, Forge 1.20.1, compilación Gradle, reobfuscación, integridad ZIP y bytecode Java 17: pasos completados.
- `gradle runGameTestServer` ejecutó **1 GameTest compuesto** en un mundo de ensayo generado por Forge; el log contiene **15 marcadores PASS distintos** para las 15 construcciones originales. `GameTestServer`: `All 1 required tests passed`.
- El GameTest verifica colocación/limpieza y rollback de estados de bloque, incluido un conflicto legado de conexión de panel de vidrio, eliminación sin drops de slime/ítem y simulación sin borrar entidades.
- Artefacto CI: `BarttoloWorldExpansion-Forge-1.20.1-v0.7.0`, ID `13144388144`. El artefacto **no** se ha desplegado en producción.

## Inspección offline de dos archivos de mundo facilitados anteriormente

Se inspeccionaron **copias locales**, sin tocar el servidor, de los archivos `Mundo nuevo.rar` y `Mundo nuevo (1).rar`. El entorno permitió extraer los archivos `data/barttolo_world_expansion.dat` y leer sus etiquetas NBT (gzip), pero **no** extraer íntegramente los archivos RAR5: el extractor informó `Unsupported block header size` al llegar a archivos de región POI. No se inició Minecraft sobre estas copias.

- `Mundo nuevo.rar`: respaldo `campamento` con `Completed=false`, **562** posiciones; sin posiciones duplicadas ni etiquetas `Before/After` malformadas en la auditoría estructural.
- `Mundo nuevo (1).rar`: respaldo `refugio` con `Completed=true`, **386** posiciones; sin posiciones duplicadas ni etiquetas `Before/After` malformadas en la auditoría estructural.
- Estas verificaciones **no** garantizan que los estados correspondan al terreno actual, que todos los bloques de mods sigan registrados, ni que la restauración funcione tras reiniciar Forge. Tampoco constituyen una prueba del mundo RingWorld completo.

## Pendiente antes de liberar

1. Ensayar el JAR en una **copia completa y extraíble** del mundo RingWorld, con Forge 47.4.10, Java 17 y su conjunto real de mods, y verificar integridad de regiones antes/después.
2. Probar colocación y deshacer de una pieza NBT CTOV/Explorify con esos mods **instalados legalmente**, sin incluir sus recursos en BWE. El GameTest actual solo prueba las 15 construcciones originales.
3. Comprobar rollback tras parada/reinicio real a mitad de construcción, límites de chunks, jugadores entrando durante el proceso, inventarios y entidades (estos dos últimos **no** se respaldan).
4. Medir TPS/MSPT, memoria y coste de guardado de NBT con volúmenes grandes. Mantener la versión **experimental** hasta completar esas pruebas.

**Seguridad:** no se modificó el servidor activo ni se redistribuyeron assets de CTOV o Explorify.
