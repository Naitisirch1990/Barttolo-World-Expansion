# Barttolo World Expansion 0.7.0 — prueba

Minecraft 1.20.1, Forge 47.4.10, Java 17. Reemplazar el JAR anterior, no instalar duplicado.

15 construcciones originales incluidas, sin dependencias de CTOV/Explorify: campamento,
refugio, pozo, carreta, torre, ruina, altar, aldea (4 casas), aldea_grande (8 casas),
herreria, posada, granja, establo, fortin y mercado. Las piezas NBT externas siguen siendo
individuales, se leen de CTOV/Explorify instalados; no se incluyen sus archivos ni se
ensamblan aldeas CTOV jigsaw completas. No soporta .schem.

## Uso

En la copia del mundo: `/bwe margen 7`, `/bwe escanear` estando a la altura deseada
para la base, `/bwe listar`, clic o `/bwe seleccionar aldea`.
Alejarse horizontalmente de TODA el area con margen. `/bwe simular` es opcional y no
modifica bloques ni elimina entidades. `/bwe crear` inicia el reemplazo.

Se limpian los bloques de la huella mas margen hasta el mayor de techo de obra o
superficie existente. Se rellenan huecos bajo la base hasta hallar apoyo (maximo
64 bloques y sin salir de la altura del mundo). No se excava todo hasta bedrock.
Se eliminan TODAS las entidades no jugadoras en el volumen de obra y restauracion,
incluidos animales, NPC, marcos, objetos, barcos y soportes. Sin drops ni division de slimes.
Los jugadores provocan una pausa; hay que salir del area, no solo volar encima.
Se conservan spawn, protecciones explicitas, bordes del mundo y limites de trabajo.

`/bwe estado` informa el avance. `/bwe deshacer` interrumpe una obra parcial y restaura
los estados de TODOS los bloques respaldados, incluso si cambiaron despues. Muestra
progreso y confirma al terminar. No restaura inventarios, NBT de contenedores ni entidades.
Es compatible con el snapshot de 386 bloques del refugio anterior.

Si quieres CONSERVAR la construccion y habilitar una nueva: `/bwe finalizar SI`.
Esto descarta deshacer y registra su proteccion. Para poder reemplazar una obra ya
registrada: `/bwe asentamientos` y `/bwe olvidar <numero> SI` (no borra bloques).

No se puede mantener simultaneamente mas de un respaldo. No finalizar una obra
incompleta; deshacerla primero. Maximo 200.000 posiciones y 500.000 consultas por plan;
200 posiciones procesadas/tick (~4.000/s a 20 TPS). Volumen, discos o TPS pueden alargarlo.

## Validacion

Las auditorias de fuentes y pruebas Java no sustituyen el juego. La tarea CI ejecuta
ademas un servidor Forge GameTest con construccion, purga de entidades y restauracion
con conflictos inducidos. Su resultado debe comprobarse por commit. No se afirma una
prueba visual ni compatibilidad con todos los mods de RingWorld por pasar ese ensayo.
Usar en una copia independiente hasta verificar visualmente la obra y deshacer.
