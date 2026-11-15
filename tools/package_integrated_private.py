import zipfile,io,json,os,nbtlib,gzip,collections
base=zipfile.ZipFile('BarttoloWorldExpansion-0.9.0-RESPALDO-Y-MUROS.jar');art=zipfile.ZipFile('iv-build.zip');engine=zipfile.ZipFile(io.BytesIO(art.read(next(n for n in art.namelist() if n.endswith('.jar')))))
cat=json.load(open('catalogo-iv-v1.json'));source=zipfile.ZipFile('upload/integrated_villages-1.3.2+1.20.1-forge.jar');used={p['template'] for e in cat['entries'][:12] for p in e['pieces']};templates={};blob=io.BytesIO();report=[]
# Keep only selected NBTs and prune unused palette entries; original block states and NBT stay intact.
with zipfile.ZipFile(blob,'w',zipfile.ZIP_DEFLATED) as slim:
 for id in sorted(used):
  n='data/'+id.replace(':','/structures/')+'.nbt';t=nbtlib.File.parse(io.BytesIO(gzip.decompress(source.read(n))));old=t['palette'];indices=sorted({int(b['state']) for b in t['blocks']});lookup={v:i for i,v in enumerate(indices)};t['palette']=nbtlib.List[nbtlib.Compound]([old[i] for i in indices])
  for b in t['blocks']:b['state']=nbtlib.Int(lookup[int(b['state'])])
  templates[id]=t;out=io.BytesIO();t.write(out);slim.writestr(n,gzip.compress(out.getvalue()))
for e in cat['entries'][:12]:
 mods=set();solid=set();aircount=0
 for p in e['pieces']:
  t=templates[p['template']];pal=t['palette'];r=p['rotation'];
  for b in t['blocks']:
   name=str(pal[int(b['state'])]['Name']);x,y,z=map(int,b['pos']);x,y,z=[(x,y,z),(-z,y,x),(-x,y,-z),(z,y,-x)][r];x+=p['x'];y+=p['y'];z+=p['z'];ns=name.split(':')[0]
   if ns!='minecraft':mods.add(ns)
   if name in ['minecraft:air','minecraft:cave_air','minecraft:structure_void','minecraft:structure_block']:continue
   if name=='minecraft:jigsaw':
    name=str(b.get('nbt',{}).get('final_state','minecraft:air')).split('[')[0]
    if name in ['minecraft:air','minecraft:structure_void']:continue
   assert 0<=x<e['sizeX'] and 0<=y<e['sizeY'] and 0<=z<e['sizeZ'],(e['id'],x,y,z,name)
   solid.add((x,z))
 footprint={(x+dx,z+dz) for x,z in solid for dx in [-1,0,1] for dz in [-1,0,1]};rect=(e['sizeX']+2)*(e['sizeZ']+2)
 line=f"{e['title']}: total {e['sizeX']+2} x {e['sizeZ']+2}; {len(footprint)}/{rect} columnas niveladas; mods de bloques: {', '.join(sorted(mods))}"
 report.append(line);print(line,flush=True)
readme='''BWE 0.10.0 - Integrated Villages, colocacion manual de prueba\n\n12 tipos de aldeas adaptados del JAR proporcionado y 14 estructuras secundarias de Explorify.\nSon planos manuales fijos conectados por jigsaws; no reproducen todas las variantes aleatorias ni los procesadores de worldgen del mod original. Se conservan bloques y metadatos originales. Las piezas opcionales pueden usar bloques de otros mods.\n\nIMPORTANTE: Instalar los mods que proporcionan los bloques de la aldea elegida en cliente y servidor. BWE comprueba las paletas antes de modificar el terreno y cancela si faltan bloques. El archivo fuente se usa como recurso pasivo y no inicia generacion de mundo. No sustituye las dependencias de bloques por aire. No incluye los procesadores dinamicos de Create/Integrated API.\n\nReemplazar el BWE anterior en cliente y servidor. El nuevo catalogo usa config/bwe/catalogo-iv-v1.json y se instala automaticamente. El viejo catalogo queda en disco sin utilizarse.\n\n/bwe escanear -> /bwe listar -> elegir -> /bwe altura Y si hace falta -> alejarse -> /bwe crear.\nMargen fijo 1. La lista indica dimensiones totales con margen. Un unico perimetro verde. Se nivela solamente la huella de construcciones y caminos, con un bloque alrededor; los huecos externos no se nivelan. No se alisan laderas fuera de ese limite.\n\n/bwe restaurar recupera los bloques del ultimo respaldo de la dimension. /bwe deshacer solo retira la construccion reciente.\n\nDirigibles, pirata y pantano hundido conservan su geometria especial: no se convierten en aldeas convencionales planas. Las diferencias de altura internas no se han eliminado.\n\nCompilado con Forge 1.20.1 / Java17. Limites de planos comprobados fuera del juego. Falta prueba visual en Minecraft; usar primero una copia del mundo.\n\nCreditos: Integrated Villages por CraisinLord y su equipo; archivo aportado por el usuario, adaptacion privada.\n\n'''+ '\n'.join(report)
open('BWE-0.10.0-LEEME.txt','w').write(readme)
replacements={n:engine.read(n) for n in engine.namelist() if n.endswith('.class') and any(n.startswith('net/barttolo/bwe/'+c) for c in ['BweCatalog','SurfaceBuilder','BarttoloWorldExpansion'])}
outname='BarttoloWorldExpansion-0.10.0-INTEGRATED-COMPACTO-test.jar'
with zipfile.ZipFile(outname,'w',zipfile.ZIP_DEFLATED) as out:
 for n in base.namelist():
  if n.startswith('assets/barttoloworldexpansion/textures/gui/previews/ctov_') or n=='assets/barttoloworldexpansion/catalogo.json':continue
  data=replacements.pop(n,base.read(n))
  if n=='META-INF/mods.toml':data=data.replace(b'0.9.0',b'0.10.0')
  out.writestr(n,data)
 for n,d in replacements.items():out.writestr(n,d)
 out.writestr('assets/barttoloworldexpansion/integrated_villages.jar',blob.getvalue())
 out.writestr('assets/barttoloworldexpansion/catalogo-iv-v1.json',json.dumps(cat,ensure_ascii=False))
 out.writestr('BWE-LEEME.txt',readme)
 for n in os.listdir('iv-previews'):out.write('iv-previews/'+n,'assets/barttoloworldexpansion/textures/gui/previews/'+n)
 for n in source.namelist():
  if '/loot_tables/' in n and n.endswith('.json'):out.writestr(n,source.read(n))
assert zipfile.ZipFile(outname).testzip() is None
print('OK',outname,os.path.getsize(outname))
