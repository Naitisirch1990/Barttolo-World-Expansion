# Plan de ensayo seguro — Barttolo World Expansion v0.6.10

Estado: **pruebas de construccion/restauracion NO ejecutadas**. Hay captura del 01/11/2026 con `/bwe listar` funcionando en un mundo descartable usando v0.6.9; no se ha proporcionado copia del mundo para ensayos automatizados. v0.6.10 requiere compilacion CI independiente.

## Preparacion (fuera del servidor activo)

1. Detener exclusivamente la instancia de ensayo. Obtener un respaldo consistente del servidor de origen por el procedimiento habitual del host; no copiar ficheros de region mientras se escriben.
2. Crear otra carpeta y otro puerto para una instancia Forge 1.20.1 / 47.4.10 / Java 17. **No reutilizar la carpeta world del servidor activo**.
3. Copiar completos `world/`, dimensiones y subcarpetas (`region`, `entities`, `poi`, `data`), `server.properties`, `config/`, `defaultconfigs/`, `mods/`, `kubejs/` o `datapacks/` si existen. No copiar credenciales, tokens ni listas de acceso a un entorno publico.
4. Deshabilitar el acceso de jugadores y la publicacion de la instancia de ensayo. Asegurarse de que AutoModpack no publique cambios del ensayo a clientes del servidor activo.
5. Registrar hashes de `level.dat`, de los ficheros de region relevantes y el listado de mods. Conservar un respaldo inmutable adicional para comparar despues.
6. **Solo si GitHub Actions genera un JAR real y este supera la inspeccion**, instalarlo en `mods/` de la instancia de ensayo, nunca en produccion.

## Secuencia inicial

1. Arrancar la instancia y confirmar en `latest.log` que BWE carga sin errores de registro. Verificar `/bwe ayuda` con OP de nivel 2 y que no existe acceso de usuario normal.
2. Proteger manualmente las coordenadas de spawn, misiones, bases, estructuras previas y camino iluminado; confirmar con `/bwe protegidas` y `/bwe areas`.
3. Buscar terreno llano, natural y cargado, lejos de jugadores y entidades. Ejecutar `/bwe analizar 12` y `/bwe planear campamento`.
4. Salir de la huella y ejecutar `/bwe simular`; anotar numero de bloques y coordenadas. Probar con un item tirado dentro: debe rechazar la simulacion sin modificar el mundo.
5. Retirar el item, repetir simulacion, ejecutar `/bwe confirmar SI`, verificar `/bwe estado`. Introducir un item **despues de iniciar** una obra con varios lotes: debe detenerse y conservar el snapshot de deshacer. Registrar logs.
6. Quitar el item y ejecutar `/bwe deshacer`; comprobar que termina y que la zona coincide con el respaldo original. Si hay un error, **no** usar `/bwe finalizar SI` y restaurar el backup del ensayo.
7. Repetir con obra completa, probar reinicio de la instancia de ensayo antes de `/bwe deshacer` y verificar integridad de bloques y entidades. Revisar que el registro de asentamientos no permita solapamientos al finalizar una obra.
8. Medir MSPT y memoria durante plan, construccion y rollback; repetir en distintos biomas y cerca de limites laterales del anillo.

## Criterios de salida

- JAR reobfuscado construido por CI y con version/metadatos correctos.
- Ninguna excepcion de Forge o RingWorld al iniciar y al ejecutar los comandos.
- Sin cambios fuera de la huella, sin entidades atrapadas y sin generacion accidental de chunks.
- Deshacer exacto de bloques en el escenario controlado; respaldo de mundo restaurable y validado.
- No se publican recursos CTOV/Explorify ni se modifica produccion.

La integracion de estructuras CTOV/Explorify continua desactivada. La aprobacion de estas pruebas **no autoriza** desplegar automaticamente en produccion.

## Caso adicional v0.6.6: restauracion con propiedades de bloque

En la copia, realizar una obra con camas y bloques orientables; confirmar que `/bwe deshacer` restaura el estado original exacto. Ensayar aparte un respaldo NBT manipulado **solo en un mundo descartable** con una propiedad de bloque desconocida: el rollback debe detenerse con error, sin restaurar ese bloque a un estado por defecto. Conservar la copia para recuperacion; esta prueba no se ha ejecutado.

## Caso adicional v0.6.6: reinicio de servidor

1. En una copia aislada, inicia un campamento, observa `/bwe estado` y detén el servidor antes de terminar la obra.
2. Reinicia el mismo mundo de prueba; verifica que no se reanuda un trabajo antiguo sobre una referencia `ServerLevel` cerrada.
3. Verifica que `/bwe estado` muestre un respaldo pendiente; **no** ejecutes `/bwe finalizar SI`.
4. Carga los chunks afectados y prueba `/bwe deshacer`; compara con un respaldo previo de la copia.

## Prueba de seguridad v0.6.7: entidades durante restauracion

En una copia aislada del mundo, construir un campamento, salir del volumen y completar la obra.
Antes de `/bwe deshacer`, colocar una entidad o entrar con un segundo jugador en el volumen:
la orden debe rechazarse sin modificar bloques y conservar el respaldo. Despejar el
volumen, iniciar `/bwe deshacer` y volver a introducir una entidad antes del siguiente
lote: la restauracion debe detenerse sin borrar el respaldo. Retirar entidades y repetir
`/bwe deshacer` para completar. En aldeas, los aldeanos creados por BWE no bloquean
la restauracion, pero cualquier entidad ajena si. Comparar regiones y NBT contra la
copia original. Esta prueba no se ha ejecutado en Minecraft.


## Caso adicional: reinicio en el ultimo lote (parche de seguridad)

- En la copia, interrumpir una obra despues de colocar los bloques pero antes de finalizar el ciclo de aldeanos (solo en entorno de depuracion).
- Verificar que el NBT de `barttolo_world_expansion` conserva `Undo.Completed=false` y que `/bwe finalizar SI` es rechazado.
- Ejecutar `/bwe deshacer` con el area vacia y comprobar restauracion y limpieza de aldeanos rastreados.
- Tras una obra terminada, comprobar `Undo.Completed=true` y que `/bwe finalizar SI` se habilita solo con estados de bloques intactos.
- Respaldos antiguos sin `Completed` se consideran incompletos: no forzar finalizacion ni editar NBT manualmente.

## Caso adicional v0.6.10: cancelar no detiene obras y respaldo vacio

1. En mundo descartable, crear un plan sin confirmar y ejecutar `/bwe cancelar`: debe eliminar solo ese plan.
2. Iniciar una obra de varios lotes y ejecutar `/bwe cancelar`: debe rechazar la orden, sin afirmar que detuvo el motor. Consultar `/bwe estado` y comprobar que sigue activa o conserva respaldo.
3. Despues de una obra terminada y antes de finalizar/deshacer, ejecutar `/bwe cancelar`: debe rechazarla y conservar el respaldo.
4. `/bwe estado` debe indicar `COMPLETO` o `INCOMPLETO` segun la marca persistida.
5. Verificar que una simulacion sin cambios no permita crear un respaldo vacio. Estos escenarios todavia no se han ejecutado en Minecraft.
