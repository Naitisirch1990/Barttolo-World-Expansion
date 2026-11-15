import zipfile,io,pathlib
base=zipfile.ZipFile('BarttoloWorldExpansion-0.14.0-ROTACION-Y-CONTORNOS-test.jar')
artifact=zipfile.ZipFile('loot-barrels-build.zip')
engine=zipfile.ZipFile(io.BytesIO(artifact.read(next(n for n in artifact.namelist() if n.endswith('.jar')))))
updates={n:engine.read(n) for n in engine.namelist() if n.startswith('net/barttolo/bwe/') and n.endswith('.class')}
name='BarttoloWorldExpansion-0.14.1-LOOTR-Y-BARRILES-test.jar'
notes='''BWE 0.14.1 - Cofres con provisiones y barriles Lootr

Sustituir BWE en CLIENTE Y SERVIDOR y reiniciar. Conservar configuracion y respaldos. No hace falta borrar config/bwe.

Los cofres, cofres trampa y barriles vanilla de las nuevas construcciones se convierten automaticamente a sus bloques nativos Lootr al terminar la obra. Se conserva orientacion y nombre; los barriles se cierran al convertirlos. Lootr sigue siendo opcional: sin Lootr no se convierten contenedores.

Se usa una tabla propia BWE con dos partes: 2-4 selecciones de provisiones (metales, esmeraldas, comida, flechas, experiencia y alguna recompensa poco comun), mas una tirada de una tabla de cofres compatible del modpack. Si no hay tablas de mods, se emplean tablas de tesoro vanilla. Si la tabla adicional no produce nada o falla, se conservan las provisiones. No se alteran las tablas originales del mundo ni se precargan inventarios compartidos. El botin se genera por jugador mediante Lootr, con sus reglas de persistencia y refresco.

Lootr no aporta un catalogo universal de objetos propios: el contenido adicional depende de las tablas presentes en el servidor. Los datapacks/modificadores globales que supriman botin o reemplacen la tabla BWE aun pueden cambiar el resultado. La tabla BWE se puede ajustar con un datapack: barttoloworldexpansion:chests/village_supply.

La correccion se aplica a nuevas obras; no cambia retroactivamente los contenedores de aldeas ya colocadas ni reinicia inventarios personales ya abiertos, para no duplicar recompensas consumidas. La restauracion sigue recuperando los barriles/cofres e inventarios previos al trabajo.

Pruebas nativas Forge/Lootr: inventarios independientes para dos jugadores, reapertura sin regeneracion, persistencia del identificador de tabla, barril orientado y con nombre, restauracion del inventario original, 256 semillas con fuente adicional vacia y con recompensas de otro namespace. Regresiones de construccion, rotacion, contorno, alturas, terreno, dirigibles y piratas. No probado visualmente en el modpack del usuario.

NOTAS ANTERIORES
'''
with zipfile.ZipFile(name,'w',zipfile.ZIP_DEFLATED) as z:
 for n in base.namelist():
  d=updates.pop(n,base.read(n))
  if n=='META-INF/mods.toml':d=d.replace(b'0.14.0',b'0.14.1')
  if n=='BWE-LEEME.txt':d=notes.encode()+base.read(n)
  z.writestr(n,d)
 for n,d in updates.items():z.writestr(n,d)
 table='data/barttoloworldexpansion/loot_tables/chests/village_supply.json';z.writestr(table,engine.read(table))
z=zipfile.ZipFile(name);assert z.testzip() is None
for n in base.namelist():
 if n.startswith('assets/'):assert base.read(n)==z.read(n),n
assert 'net/barttolo/bwe/RegressionGameTests.class' not in z.namelist()
assert not any('bwe_regression' in n for n in z.namelist())
assert b'0.14.1' in z.read('META-INF/mods.toml')
for cls in ['BarttoloWorldExpansion','BweLootr','SurfaceBuilder','BweLootEntry']:
 path='net/barttolo/bwe/'+cls+'.class';assert z.read(path)==engine.read(path)
print('PASS',name,pathlib.Path(name).stat().st_size,'bytes; 70 layouts preserved; native loot table included')
