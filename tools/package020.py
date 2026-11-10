"""Hotfix three placement references while preserving all 214 models and source archives."""
import zipfile,io,pathlib,json,hashlib
base=zipfile.ZipFile('BarttoloWorldExpansion-0.15.0-CATALOGOS-Y-ESTRUCTURAS-test.jar')
artifact=zipfile.ZipFile('build020.zip');engine=zipfile.ZipFile(io.BytesIO(artifact.read(next(n for n in artifact.namelist() if n.endswith('.jar')))))
updates={n:engine.read(n) for n in engine.namelist() if n.startswith('net/barttolo/bwe/') and n.endswith('.class')}
name='BarttoloWorldExpansion-0.15.1-ALTURAS-Y-PESCADOR-test.jar'
notes='''BWE 0.15.1 - Bazar, ciudadela y refugio del pescador

Sustituir el BWE anterior en CLIENTE Y SERVIDOR y reiniciar. Conservar config/bwe y respaldos. Las referencias se corrigen por identificador tambien para servidores que conservan catalogo-ampliacion-v1.json de 0.15.0; no hace falta borrarlo.

Bazar IDAS: se usa el pavimento local Y=5 en lugar de los conectores superiores del techo (Y=29). El pavimento queda en el nivel de suelo elegido.
Ciudadela del terror: la base inferior local Y=0 queda en el suelo elegido, en lugar de usar una capa intermedia Y=38 que enterraba gran parte de la fortaleza. Conserva toda la geometria y los accesos de madera de la adaptacion.
Refugio del pescador: colocacion acuatica. Agua local hasta Y=17; cubierta en Y=18 y puertas en Y=19. Los pilares inferiores quedan rodeados de agua y no se crea plataforma de tierra. Seleccionarlo sobre agua permite detectar su nivel; /bwe altura Y ajusta la superficie del agua.

Las correcciones se aplican al seleccionar y crear una obra nueva. No se desplazan construcciones ya colocadas ni se repara su terreno automaticamente. /bwe deshacer puede restaurar la ultima obra de la dimension si todavia conserva su respaldo; empezar otra obra reemplaza ese respaldo.

Conserva las 214 opciones, menus, vistas previas, contornos, rotacion, limpieza y Lootr. Archivos NBT de todos los modelos sin cambios. Las tres referencias se mantienen al rotar la seleccion.

Pruebas: compilacion y regresion nativa Forge con metadatos antiguos; pavimento del bazar a la altura elegida; base inferior y acceso de madera de la ciudadela sin tierra encima; agua entre pilares, cubierta seca, deteccion del agua y restauracion de terreno sumergido. Tambien las regresiones anteriores de Lootr, giros, contornos, respaldos, dirigibles y puerto pirata. Pendiente comprobacion visual en el servidor completo del usuario.

NOTAS ANTERIORES
'''
catpath='assets/barttoloworldexpansion/catalogo-ampliacion-v1.json'
cat=json.loads(base.read(catpath));fixed={'bwe_idas:bazaar':(6,'ground'),'bwe_idas:iceandfire_dread_citadel':(1,'ground'),'bwe_idas:fishermans_lodge':(18,'water')}
for e in cat['entries']:
 if e['id'] in fixed:e['ground'],e['placement']=fixed[e['id']]
with zipfile.ZipFile(name,'w',zipfile.ZIP_DEFLATED) as out:
 for n in base.namelist():
  d=updates.pop(n,base.read(n))
  if n=='META-INF/mods.toml':d=d.replace(b'0.15.0',b'0.15.1')
  elif n=='BWE-LEEME.txt':d=notes.encode()+d
  elif n==catpath:d=json.dumps(cat,ensure_ascii=False,indent=2).encode()
  elif n=='BWE-IMPORTACION.json':
   report=json.loads(d)
   for e in report['entries']:
    if e['id'] in fixed:e['surfaceY']=fixed[e['id']][0]-1;e['placement']=fixed[e['id']][1]
   d=json.dumps(report,ensure_ascii=False,indent=2).encode()
  out.writestr(n,d)
 for n,d in updates.items():out.writestr(n,d)
z=zipfile.ZipFile(name);assert z.testzip() is None
for n in base.namelist():
 if n.startswith('assets/') and n!=catpath:assert z.read(n)==base.read(n),n
for n in engine.namelist():
 if n.startswith('net/barttolo/bwe/') and n.endswith('.class'):assert z.read(n)==engine.read(n),n
assert not any('RegressionGameTests' in n or 'bwe_regression' in n for n in z.namelist())
assert len(cat['entries'])==144
for e,old in zip(cat['entries'],json.loads(base.read(catpath))['entries']):
 assert e['id']==old['id'] and e['pieces']==old['pieces']
 if e['id'] not in fixed:assert e==old
assert b'0.15.1' in z.read('META-INF/mods.toml')
print('PASS',name,pathlib.Path(name).stat().st_size,'bytes; all 214 models retained; 3 placement references corrected;',hashlib.sha256(pathlib.Path(name).read_bytes()).hexdigest())
