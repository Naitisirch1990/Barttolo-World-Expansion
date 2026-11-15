# Resultados de verificación v0.6.8 — 2026-11-01

- Geometría Java independiente de Minecraft: 14 pruebas superadas con el JDK disponible.
- Verificaciones estáticas: todas las invocadas por el workflow pasaron localmente.
- Auditoría de integridad NBT: se comprobó estáticamente que la decodificación exige `Name`, `Properties` y el conjunto completo de propiedades; NO se probó en runtime Forge.
- Dependencias ForgeGradle/Minecraft: no descargables en el entorno local; no existe compilación local validada.
- Pruebas dentro de copia del mundo: no realizadas, no se dispone de una copia del mundo ni de un servidor de prueba.
- Recursos CTOV/Explorify: no se incluyen.
