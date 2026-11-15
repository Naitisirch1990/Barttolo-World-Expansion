# Barttolo World Expansion v0.6.6 — comprobaciones locales (01/11/2026)

## Cambios

- Se libera `ServerLevel` de trabajos y planes al recibir `ServerStoppedEvent`; no se borra el snapshot persistido para deshacer.
- `/bwe plantilla <namespace:ruta>` utiliza `StringArgumentType.greedyString()`; el argumento anterior `word()` no admitía `:` ni `/`.
- Se añadieron dos comprobaciones estáticas para estas rutas y se incorporaron al workflow.

## Resultados

- 14 pruebas Java de geometría: correctas con `javac --release 17` (JDK 21 en el entorno).
- 10 verificaciones Python de texto, metadatos, protecciones y CI: correctas.
- Archivos NBT de CTOV/Explorify: **no incluidos**.
- Compilación real con ForgeGradle: **NO realizada** (sin Gradle/JDK 17/dependencias en el entorno; salida de red bloqueada).
- Prueba en Minecraft Forge 47.4.10 y copia del mundo: **NO realizada**.
- GitHub Actions #3: falla en `Exigir fuentes completos`; los pasos Java/Gradle/build se omiten. No se ha generado JAR.

## Seguridad

No instalar en el servidor principal. En copia aislada: probar `/bwe planear campamento`, salir de la zona, `/bwe simular`, `/bwe confirmar SI`, `/bwe estado`, `/bwe deshacer` y comparar terreno con copia previa. Repetir con parada/reinicio a mitad de construcción. No usar `/bwe finalizar SI` hasta validar restauración.
