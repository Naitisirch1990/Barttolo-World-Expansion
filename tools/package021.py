"""Package the reviewed IDAS placement map without changing any model geometry."""
import zipfile,io,pathlib,json,hashlib
base=zipfile.ZipFile('BarttoloWorldExpansion-0.15.1-ALTURAS-Y-PESCADOR-test.jar')
artifact=zipfile.ZipFile('build021.zip');engine=zipfile.ZipFile(io.BytesIO(artifact.read(next(n for n in artifact.namelist() if n.endswith('.jar')))))
updates={n:engine.read(n) for n in engine.namelist() if n.startswith('net/barttolo/bwe/') and n.endswith('.class')}
rulepath='assets/barttoloworldexpansion/idas-placement-v1.json';rules=json.loads(engine.read(rulepath));byid={e['id']:e for e in rules['entries']};assert len(byid)==83
name='BarttoloWorldExpansion-0.15.2-REVISION-IDAS-test.jar'
notes='''BWE 0.15.2 - Revision de alturas del catalogo IDAS

Sustituir BWE en CLIENTE Y SERVIDOR y reiniciar. Conservar config/bwe y los respaldos. Las 83 referencias auditadas se cargan desde el JAR por identificador, incluso si config/bwe conserva el catalogo anterior. No hace falta borrar la configuracion.

Se evita tambien que los primeros bloques de una pieza superior se traten como cimentacion dentro de una sala: solo se apoya el bloque estructural inferior de cada columna. Se conservan los apoyos de plataformas realmente elevadas.

Se han contrastado las capas inferiores, pavimentos, accesos, terreno y agua de las 83 opciones IDAS. Se corrigen 13 referencias adicionales respecto de 0.15.1. La causa principal era tomar conectores de ensamblaje de los tejados como si fueran el suelo.

Fortaleza de saqueadores: pavimento principal local Y=3, en lugar de Y=46 cerca del techo. Las capas Y=0..2 son cimentacion; la fortaleza queda sobre el terreno elegido.
Otros ajustes: Mansion encantada, ambos portales antiguos, estatua del desierto, casa del boticario, casa del botanico, refugio del ermitaño, Nexo, Taller del artesano, Arbol de la sabiduria y Santuario del viento. La Torre del nigromante se trata como construccion flotante: sin plataforma de tierra y con espacio de aire inferior.

Los modelos con sotanos o galerias inferiores conservan una referencia de entrada en superficie: por ejemplo Minas antiguas, Laberinto y Cripta helada. Es normal que sus dependencias subterraneas queden debajo del suelo; no se usa el techo de todo el conjunto como altura de entrada. La descripcion clicable lo indica. Las construcciones flotantes tambien se identifican en la descripcion.

Conserva las correcciones anteriores del Bazar, Ciudadela del terror y Refugio del pescador, asi como los cinco modelos acuaticos IDAS. /bwe altura Y sigue permitiendo ajustar la referencia del terreno, agua o base aerea, y /bwe rotar mantiene dicha referencia.

Las correcciones se aplican al seleccionar y crear de nuevo. No mueven automaticamente construcciones existentes ni restauran terraformaciones antiguas. El deshacer solo puede recuperar el ultimo respaldo disponible de la dimension.

Conserva las 214 opciones, modelos NBT, vistas previas, margenes, Lootr y restauracion. Pruebas Forge: 83 referencias con metadatos antiguos, modos agua/aire y cuatro giros; fortaleza con suelo Y=3 y cubierta Y=47; pavimento a la altura elegida, entrada despejada y deshacer; ademas de las regresiones previas de bazar, ciudadela, pescador, Lootr y respaldos. Revision de datos y pruebas automatizadas: pendiente verificacion visual de todos los modelos en el modpack del servidor.

NOTAS ANTERIORES
'''
catpath='assets/barttoloworldexpansion/catalogo-ampliacion-v1.json';cat=json.loads(base.read(catpath));changes=[]
for e in cat['entries']:
 if e['id'] in byid:
  r=byid[e['id']]
  if (e['ground']-1,e.get('placement'))!=(r['surfaceY'],r['placement']):changes.append(e['id'])
  e['ground']=r['surfaceY']+1;e['placement']=r['placement']
assert len(changes)==13,changes
with zipfile.ZipFile(name,'w',zipfile.ZIP_DEFLATED) as out:
 for n in base.namelist():
  d=updates.pop(n,base.read(n))
  if n=='META-INF/mods.toml':d=d.replace(b'0.15.1',b'0.15.2')
  elif n=='BWE-LEEME.txt':d=notes.encode()+d
  elif n==catpath:d=json.dumps(cat,ensure_ascii=False,indent=2).encode()
  elif n=='BWE-IMPORTACION.json':
   report=json.loads(d)
   for e in report['entries']:
    if e['id'] in byid:
     r=byid[e['id']];e['surfaceY']=r['surfaceY'];e['placement']=r['placement'];e['subterranean']=r.get('subterranean',False)
   d=json.dumps(report,ensure_ascii=False,indent=2).encode()
  out.writestr(n,d)
 for n,d in updates.items():out.writestr(n,d)
 out.writestr(rulepath,engine.read(rulepath))
z=zipfile.ZipFile(name);assert z.testzip() is None
for n in base.namelist():
 if n.startswith('assets/') and n!=catpath:assert z.read(n)==base.read(n),n
for n in engine.namelist():
 if n.startswith('net/barttolo/bwe/') and n.endswith('.class'):assert z.read(n)==engine.read(n),n
assert not any('RegressionGameTests' in n or 'bwe_regression' in n for n in z.namelist())
assert len(cat['entries'])==144 and len(rules['entries'])==83
for e,old in zip(cat['entries'],json.loads(base.read(catpath))['entries']):
 assert e['id']==old['id'] and e['pieces']==old['pieces']
 if e['id'] not in byid:assert e==old
assert b'0.15.2' in z.read('META-INF/mods.toml')
print('PASS',name,pathlib.Path(name).stat().st_size,'bytes; all 214 models retained; 83 IDAS references reviewed, 13 corrected;',hashlib.sha256(pathlib.Path(name).read_bytes()).hexdigest())
