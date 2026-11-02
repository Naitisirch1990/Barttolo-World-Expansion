# BWE 0.8.0 — construcciones manuales en chunks existentes

Minecraft 1.20.1, Forge 47.4.10, Java 17. El entregable privado incorpora los JAR
originales aportados por el usuario como archivos de recursos PASIVOS dentro de assets,
no como mods ni datapacks. Se extraen a config/bwe/ solo si no existen. No se registra
su worldgen, no se cargan sus clases ni se requieren sus dependencias.
Los recursos de terceros NO se publican en este repositorio ni en los artefactos CI.
El JAR CI contiene el motor; el entregable privado se completa con los archivos del usuario.

## Flujo

En la zona elegida: `/bwe listar`, clic en la construccion, alejarse horizontalmente del
rectangulo indicado y `/bwe crear`. No hay auditorias, simulaciones ni finalizacion.
La lista muestra ancho x largo exactos de cada ensamblaje. Su altura no se muestra.
La seleccion calcula la mediana de suelo natural; las plantillas conservan la cota de
entrada declarada en el plan. Se limpia el rectangulo mas 5 bloques de margen y se
rellenan huecos hasta hallar apoyo (maximo 64 bloques). `/bwe margen 5..10` es opcional.

`/bwe estado` informa etapa y espera por jugadores. Se eliminan entidades no jugadoras
en el rectangulo, sin drops; nunca se elimina al operador. Construye 200 posiciones/tick.
`/bwe deshacer` RETIRA la obra mas reciente sin restaurar terreno, cofres ni entidades.
La referencia de retirada se conserva solamente durante la sesion. No se bloquea la
siguiente obra por un respaldo pendiente. Compatibilidad: si no hay obra nueva, deshacer
puede recuperar un respaldo v0.7 pendiente mediante el motor anterior.

El catalogo privado contiene aldeas CTOV ensambladas por conectores con semilla fija,
estructuras Explorify y edificios individuales. Es un ensamblaje manual determinista:
no reproduce todos los procesadores aleatorios ni la proyeccion del generador original.
El entregable privado incluye sus tablas de botin como datos pasivos, sin incluir
definiciones de generacion. Se conserva NBT de bloques y
entidades de las plantillas. Las particulas/funciones/datapacks originales no se activan.

## Recursos

Mantener config/bwe/ctov.jar, explorify.jar y catalogo.json juntos. `/bwe recargar`
actualiza el catalogo tras cambios. Los archivos originales se usan sin modificarlos.
`tools/build_catalog.py CTOV.jar Explorify.jar catalogo.json` prepara SOLO planes JSON
(identificadores, coordenadas y rotaciones) sin copiar las plantillas. Usa los pools y
conectores de los archivos aportados; descarta recursos de add-ons no disponibles y
comprueba que las piezas no se solapan. Ni el generador ni el constructor regeneran chunks.

## Pruebas

CI compila Forge y ejecuta las 15 regresiones anteriores, mas un ensayo de importacion
por ZIP, dos piezas nativas rotadas, deteccion de suelo, purga de entidades, carga de NBT
de cofre y retirada. El ensayo usa recursos originales sinteticos, no los JAR privados.
Los planes privados se validan offline contra los archivos exactos del usuario. Esto no
sustituye una inspeccion visual de las 43 opciones en su modpack. Probar en copia primero.
