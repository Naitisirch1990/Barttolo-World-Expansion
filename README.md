# Barttolo World Expansion (BWE)

Proyecto experimental para **Minecraft Java 1.20.1 / Forge 47.4.10 / Java 17** destinado a ampliar RingWorld con construcciones originales en chunks existentes.

## Estado — 1 de noviembre de 2026

- **Fuentes locales disponibles: v0.6.3**, preparados como ZIP fuera del repositorio.
- Ocho tipos originales: `aldea`, `campamento`, `ruina`, `torre`, `refugio`, `altar`, `pozo`, `carreta`.
- Los planes y la simulación comprueban terreno, chunks, protecciones, entidades y límites de construcción.
- **14 pruebas Java de geometría superadas**, junto con verificaciones estáticas de metadatos, protección de chunks, paridad y CI.
- **No hay compilación Forge confirmada, ni JAR instalable, ni prueba de restauración dentro de Minecraft.**
- El repositorio contiene documentación y GitHub Actions, pero **todavía no contiene el árbol completo de fuentes**. GitHub Actions falla deliberadamente si faltan fuentes, para no mostrar una compilación falsamente exitosa.

## Precauciones

**No instalar en el servidor principal.** Usar una copia completa del mundo para la primera prueba. Proteger misiones, bases y camino principal. Las comprobaciones no identifican todos los bloques construidos por jugadores.

Las estructuras de **CTOV y Explorify no están redistribuidas** en este proyecto; su colocación está desactivada hasta verificar permisos y restauración segura de datos NBT.

## Próximos pasos

1. Cambiar el repositorio a privado si se desea mantener el proyecto interno (actualmente es público).
2. Incorporar los fuentes originales v0.6.3 a la raíz del repositorio.
3. Ejecutar GitHub Actions, corregir errores de ForgeGradle y obtener el primer JAR experimental.
4. Probar `/bwe ayuda`, `/bwe planear campamento`, `/bwe simular`, `/bwe confirmar SI` y `/bwe deshacer` en una copia del mundo.
5. Verificar comportamiento tras reinicio, integridad del terreno y compatibilidad con RingWorld.

Seguimiento: [Hito 1 — primer JAR y pruebas seguras](../../issues/1).
