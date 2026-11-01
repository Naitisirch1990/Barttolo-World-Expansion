# Barttolo World Expansion v0.6.7 — verificacion local (2026-11-01)

- Fuentes recuperadas del ZIP v0.6.6 de la conversacion.
- `bash tests/run_geometry_tests.sh`: 14 pruebas Java puras, correctas (`javac --release 17`, ejecutadas en JDK 21).
- 11 verificaciones Python de CI, metadatos, fuente y protecciones: correctas (no son pruebas de integracion).
- Dos scripts de verificacion opcional (`verify_resource_ids.py` y `report_templates.py`) requieren JAR de CTOV/Explorify y NO se ejecutaron.
- `javac --release 17` sobre fuentes Forge: no compilable sin Brigadier/Minecraft/Forge en el classpath (dependencias no instaladas).
- Gradle/ForgeGradle: no disponible en el contenedor y red de salida bloqueada; NO se genero JAR.
- No existe copia del mundo RingWorld en los archivos accesibles; NO hubo prueba in-game.
- No se modifico el servidor ni se incorporaron activos de terceros.

## GitHub Actions comprobado en esta ejecucion

- PR #2 actualizado con parche v0.6.7 y workflow que ejecuta en pull requests.
- Ejecucion #8: https://github.com/Naitisirch1990/Barttolo-World-Expansion/actions/runs/41307694079
- Estado: `failure` en `Exigir fuentes completos`. Todos los pasos Java/Gradle/build/artifact fueron `skipped`. No hay JAR.
- La rama GitHub conserva parches y workflow, NO los 34 archivos del ZIP.
