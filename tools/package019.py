"""Assemble a private BWE release from a tested engine and passive converted resources."""
import zipfile,io,pathlib,json,hashlib
base=zipfile.ZipFile('base-recovered/BarttoloWorldExpansion-0.14.2-MANSIONES-Y-BASES-test.jar')
artifact=zipfile.ZipFile('build019.zip');engine=zipfile.ZipFile(io.BytesIO(artifact.read(next(n for n in artifact.namelist() if n.endswith('.jar')))))
updates={n:engine.read(n) for n in engine.namelist() if n.startswith('net/barttolo/bwe/') and n.endswith('.class')}
root=pathlib.Path('expansion');catalog=json.loads((root/'catalogo-ampliacion-v1.json').read_text());report=json.loads((root/'report.json').read_text())
name='BarttoloWorldExpansion-0.15.0-CATALOGOS-Y-ESTRUCTURAS-test.jar'
counts={group:sum(e['id'].startswith(ns+':') for e in catalog['entries']) for group,ns in [('TT','bwe_tt'),('ITP','bwe_itp'),('IDAS','bwe_idas')]}
notes=f'''BWE 0.15.0 - Catalogos por mod y estructuras sin poblacion

INSTALACION: sustituir BWE anterior en cliente y servidor, reiniciar y conservar config/bwe y los respaldos. No necesitas instalar los tres mods originales para estos esquemas. El paquete incluye solo sus recursos NBT adaptados; no carga su codigo, generacion natural ni mecanicas.

/bwe listar muestra los grupos abreviados. Clic en el grupo para abrir sus estructuras; [Anterior], [Siguiente] y [Volver al catalogo] navegan. HOSTILES es una seleccion transversal: las estructuras tambien aparecen bajo su mod. Puedes usar /bwe listar TT, /bwe listar ITP, /bwe listar IDAS o /bwe listar HOSTILES. /bwe listar 1 conserva el listado completo anterior.

Nuevas opciones: {counts}; total añadido: {len(catalog['entries'])}. Se conservan las 70 anteriores. Cada opcion es una disposicion fija preparada a partir de las piezas del mod, no todas las variaciones aleatorias que genera el original.

Se mantienen vista previa, bordes, margen de un bloque, rotacion, altura elegida, limpieza, cimentacion, restauracion y conversion de cofres/barriles a Lootr al finalizar. Los esquemas acuaticos identificados utilizan contorno de agua en lugar de plataforma de tierra.

En los NUEVOS esquemas se eliminan todas las entidades guardadas (mobs, cuadros, marcos, soportes, etc.), generadores, carteles, banderas y bloques de comandos. Las maquinas y numerosos bloques de mods se sustituyen por equivalentes estaticos vanilla o se retiran si son decoracion activa. Cofres y barriles quedan con botin diferido, que BWE convierte mediante Lootr. Los bloques arquitectonicos personalizados pueden tener un aspecto diferente. La categoria Hostiles describe la estructura; no incorpora enemigos ni eventos. Las reglas normales de aparicion de Minecraft siguen vigentes.

Las estructuras anteriores y las ya construidas no se limpian de entidades retroactivamente. No se restauran mobs retirados por la limpieza. El deshacer conserva el comportamiento existente de recuperacion de terreno e inventarios respaldados.

Comprobaciones: compilacion Forge 1.20.1, validacion de paletas y limites de todas las opciones nuevas, supresion de entidades en composicion, categorias y comandos de listado; regresion de alturas, rotacion, terreno, restauracion y Lootr. No probado visualmente en el servidor completo del usuario.

Opciones no incluidas por limites o falta de plantilla: {report['excluded']}

NOTAS ANTERIORES
'''
with zipfile.ZipFile(name,'w',zipfile.ZIP_DEFLATED) as out:
 for n in base.namelist():
  d=updates.pop(n,base.read(n))
  if n=='META-INF/mods.toml':d=d.replace(b'0.14.2',b'0.15.0')
  if n=='BWE-LEEME.txt':d=notes.encode()+d
  out.writestr(n,d)
 for n,d in updates.items():out.writestr(n,d)
 for f in ['catalogo-ampliacion-v1.json','bwe_tt.jar','bwe_itp.jar','bwe_idas.jar']:out.writestr('assets/barttoloworldexpansion/'+f,(root/f).read_bytes())
 for p in (root/'previews').glob('*.png'):out.writestr('assets/barttoloworldexpansion/textures/gui/previews/'+p.name,p.read_bytes())
 out.writestr('BWE-IMPORTACION.json',json.dumps(report,ensure_ascii=False,indent=2))
z=zipfile.ZipFile(name);assert z.testzip() is None
for n in base.namelist():
 if n.startswith('assets/'):assert z.read(n)==base.read(n),n
for n in engine.namelist():
 if n.startswith('net/barttolo/bwe/') and n.endswith('.class'):assert z.read(n)==engine.read(n),n
assert not any('RegressionGameTests' in n or 'bwe_regression' in n for n in z.namelist())
ids=[e['id'] for e in catalog['entries']];assert len(ids)==len(set(ids))
for e in catalog['entries']:
 assert max(e['sizeX'],e['sizeY'],e['sizeZ'])<=256 and len(e['pieces'])<=128
 assert 'assets/barttoloworldexpansion/textures/gui/previews/'+e['id'].replace(':','_')+'.png' in z.namelist()
 for piece in e['pieces']:
  ns,path=piece['template'].split(':');sub=zipfile.ZipFile(io.BytesIO(z.read('assets/barttoloworldexpansion/'+ns+'.jar')))
  assert 'data/'+ns+'/structures/'+path+'.nbt' in sub.namelist()
  assert all(n.startswith('data/'+ns+'/structures/') and n.endswith('.nbt') for n in sub.namelist())
print('PASS',name,pathlib.Path(name).stat().st_size,counts,'total',70+len(ids),hashlib.sha256(pathlib.Path(name).read_bytes()).hexdigest())
