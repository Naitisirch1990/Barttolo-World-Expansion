"""Package the CI engine with the user's existing private templates; publish no templates."""
import zipfile,io,pathlib,hashlib,json
base=zipfile.ZipFile('recovered/BarttoloWorldExpansion-0.14.1-LOOTR-Y-BARRILES-test.jar')
artifact=zipfile.ZipFile('bases-build018.zip')
engine=zipfile.ZipFile(io.BytesIO(artifact.read(next(n for n in artifact.namelist() if n.endswith('.jar')))))
updates={n:engine.read(n) for n in engine.namelist() if n.startswith('net/barttolo/bwe/') and n.endswith('.class')}
name='BarttoloWorldExpansion-0.14.2-MANSIONES-Y-BASES-test.jar'
notes='''BWE 0.14.2 - Mansion ligera y bases completas

Sustituir el JAR BWE anterior en CLIENTE Y SERVIDOR y reiniciar. Conservar config/bwe y los respaldos.
Los cambios se aplican al seleccionar y construir de nuevo; no modifican obras ya colocadas ni eliminan entidades de otras construcciones.

Mansion GC del bosque: elimina los mobs incluidos en la plantilla y reduce 761 adornos de entidad a un maximo de 40 (24 cuadros, 8 marcos y 8 soportes). Se distribuyen por el edificio; no se alteran sus bloques arquitectonicos. En el esquema incluido se colocan 4 generadores de saqueadores separados al menos 24 bloques, sobre suelo real y en antiguas posiciones de saqueadores. Cada generador crea 1 mob por intento, con maximo 2 cercanos, intervalo 30-60 segundos y activacion a 12 bloques. Este limite es local a cada generador, no un tope global de enemigos vivos; los mobs que se alejan pueden permitir nuevas apariciones. No se impide la aparicion natural del mundo. Las entidades de las nuevas obras se incorporan en tandas de hasta 4 por tick, en lugar de 200.

Mansion pequena (bwe_extra:mansion), Faro realista y Coliseo natural: el esquema recortado comienza sobre una capa continua de suelo, no dentro de ella. Evita que los detalles de la primera capa sustituyan tierra y abran surcos. La base rocosa completa del faro queda expuesta: 5 bloques mas alta respecto al suelo que antes. El coliseo conserva su arquitectura y el patio queda con suelo continuo bajo su primera capa. No se extiende la limpieza fuera del contorno previsto.

Nieve 1: revisado el catalogo incluido y los 65 componentes de BV Nieve Plaza 1. Todos pertenecen al conjunto snowy; contienen nieve y hielo, sin piezas ni madera de acacia de sabana. Los botones seleccionan por identificador, no por numero de fila. No se ha reproducido el intercambio descrito y no se han cambiado a ciegas los esquemas. Si persiste con esta version, se necesita el texto exacto de Seleccionada y el catalogo-biomas-v1.json del servidor para identificar diferencias de configuracion.

Pruebas automatizadas: compilacion Forge y regresion nativa de bases recortadas, suelo de patio sin huecos, presupuesto de entidades, persistencia NBT de generadores, restauracion, Lootr, rotacion, alturas, dirigibles y puerto pirata. No probado visualmente en el modpack completo del usuario; la reduccion no garantiza por si sola eliminar todas las desconexiones.

NOTAS ANTERIORES
'''
with zipfile.ZipFile(name,'w',zipfile.ZIP_DEFLATED) as out:
 for n in base.namelist():
  d=updates.pop(n,base.read(n))
  if n=='META-INF/mods.toml':d=d.replace(b'0.14.1',b'0.14.2')
  if n=='BWE-LEEME.txt':d=notes.encode()+d
  out.writestr(n,d)
 for n,d in updates.items():out.writestr(n,d)
z=zipfile.ZipFile(name);assert z.testzip() is None
for n in base.namelist():
 if n.startswith('assets/'):assert z.read(n)==base.read(n),n
for n in engine.namelist():
 if n.startswith('net/barttolo/bwe/') and n.endswith('.class'):assert z.read(n)==engine.read(n),n
assert not any('RegressionGameTests' in n or 'bwe_regression' in n for n in z.namelist())
assert b'0.14.2' in z.read('META-INF/mods.toml')
count=sum(len(json.loads(z.read('assets/barttoloworldexpansion/'+n))['entries']) for n in ['catalogo-iv-v2.json','catalogo-extra-v1.json','catalogo-biomas-v1.json'])
assert count==70,count
print('PASS',name,pathlib.Path(name).stat().st_size,'bytes; catalog entries',count,'sha256',hashlib.sha256(pathlib.Path(name).read_bytes()).hexdigest())
