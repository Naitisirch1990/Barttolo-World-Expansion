# BWE Maestro de las Construcciones — contrato de implementación (borrador)

Estado: DISEÑO, NO IMPLEMENTADO. No habilitar colocación de plantillas externas hasta contar con respaldo NBT reversible probado.

## Flujo objetivo
- `/bwe listar`: catálogo dinámico de piezas CTOV/Explorify disponibles en los recursos instalados, sin redistribuir NBT.
- `/bwe seleccionar <id>`: seleccionar una pieza real, permitiendo repetirla en ubicaciones distintas. Una pieza jigsaw NO equivale a una aldea completa.
- `/bwe margen <5..10>`: margen horizontal de limpieza; valor predeterminado 7.
- `/bwe proteger si|no`: opción **por obra**, predeterminada 'no'; no anula protecciones existentes. Aplicar protección únicamente tras terminar la obra.
- `/bwe terraformar`: preparar la plataforma según huella, alturas de apoyo y límites del mundo. No iniciar escrituras hasta tener respaldo durable y verificado.
- `/bwe crear`: despejar obstáculos dentro de la huella y margen, construir la plantilla, por lotes limitados por tick.
- `/bwe deshacer`: restaurar TODOS los bloques originales, block entities con NBT, líquidos y estado de protección creado por la obra.

## Invariantes de seguridad
1. La selección y los comandos de lectura nunca alteran bloques.
2. Una sola obra activa por mundo y un único respaldo pendiente. No sobrescribir respaldo existente.
3. Respaldo durable antes del primer cambio; después de reinicio debe ser recuperable. Si no hay espacio suficiente para el respaldo, no empezar.
4. Nunca tocar chunks no cargados, fuera del límite vertical, fuera del mundo, ni regiones protegidas previamente.
5. Respetar jugadores: nunca eliminar ni encerrar jugadores; pausar si un jugador entra en el volumen de trabajo.
6. Las entidades no-jugador deben tener una política explícita de traslado o eliminación; para deshacer realmente la aldea anterior se necesitaría restaurar también sus entidades y datos. No prometer reversibilidad total hasta implementarlo.
7. No usar `/fill air` masivo: operaciones escalonadas y registro de progreso.
8. Colocación de plantillas externas solo con recursos del servidor y sus permisos; nunca incluir NBT CTOV/Explorify en el JAR.
9. La opción de protección afecta únicamente a la obra terminada; deshacer elimina la protección creada por esa obra, sin alterar protecciones previas.
10. La superficie no basta para restaurar: respaldar todo el volumen que se modificará, incluyendo margen y subsuelo terraformado.

## Pruebas exigidas antes de publicar JAR
- Mundo desechable con relieve irregular, árboles, agua, contenedores NBT y una aldea antigua.
- Construcción repetida de una pieza auténtica (recursos CTOV/Explorify instalados localmente).
- Reinicio del servidor durante construcción y durante deshacer; reanudar sin pérdida.
- Comparar hashes de estados de bloques y NBT antes/después de deshacer.
- Verificar `proteger si`, `proteger no`, áreas previamente protegidas y retirada de protección tras deshacer.
- Comprobar que los jugadores y el servidor activo no son afectados.

Este documento define requisitos; no certifica compilación ni pruebas dentro del juego.
