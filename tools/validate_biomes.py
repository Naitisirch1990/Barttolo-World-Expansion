import zipfile,json,nbtlib as n,io,gzip,collections,pathlib,math
known={'minecraft:'+b['name']:b for b in json.load(open('vanilla-blocks.json'))};entries=json.load(open('catalogo-biomas-v1.json'))['entries'];data={};archives={ns:zipfile.ZipFile(ns+'.jar') for ns in ['bwe_better','bwe_capitals']}
for ns,z in archives.items():
 for name in z.namelist():
  t=n.File.parse(io.BytesIO(gzip.decompress(z.read(name))));size=tuple(map(int,t['size']));pal=[]
  for st in t['palette']:
   bid=str(st['Name']);assert bid in known,bid;allowed={x['name']:x for x in known[bid]['states']}
   for k,v in st.get('Properties',{}).items():
    info=allowed[k];valid=info.get('values',['true','false'] if info['type']=='bool' else [str(i) for i in range(info['num_values'])]);assert str(v) in valid,(bid,k,v)
   pal.append(bid)
  cells=[]
  for b in t['blocks']:
   pos=tuple(map(int,b['pos']));assert all(0<=pos[i]<size[i] for i in range(3)),(name,pos,size);cells.append((*pos,pal[int(b['state'])]))
  id=ns+':'+name.split('/structures/')[1][:-4];data[id]=(cells,[(list(map(float,en['pos'])),str(en['nbt'].get('id',''))) for en in t.get('entities',[])])
report=[]
for e in entries:
 assert e['biome'] in e['title'];assert e['sizeX']<=224 and e['sizeZ']<=224 and e['sizeY']<=256 and len(e['pieces'])<=128
 cells={};ents=0
 for p in e['pieces']:
  for x,y,z,b in data[p['template']][0]:
   x,y,z=[(x,y,z),(-z,y,x),(-x,y,-z),(z,y,-x)][p['rotation']];pos=(x+p['x'],y+p['y'],z+p['z']);air=b in ['minecraft:air','minecraft:cave_air','minecraft:void_air']
   if any(pos[i]<0 or pos[i]>=e[k] for i,k in enumerate(['sizeX','sizeY','sizeZ'])):
    assert air,(e['id'],pos,b);continue
   if air and pos in cells and cells[pos] not in ['minecraft:air','minecraft:cave_air','minecraft:void_air']:continue
   cells[pos]=b
  ents+=len(data[p['template']][1])
 assert len(cells)<=1000000,(e['id'],len(cells));buildings=sum(any(x in p['template'] for x in ['/houses/','/house/','/worker/']) for p in e['pieces'])
 report.append({'id':e['id'],'size':f"{e['sizeX']+2}x{e['sizeZ']+2}",'cells':len(cells),'nonair':sum(b!='minecraft:air' for b in cells.values()),'entities':ents,'buildings':buildings})
 print('PASS',e['title'],report[-1]['size'],len(cells),'cells',buildings,'buildings',flush=True)
assert len(entries)==35
pathlib.Path('biomes-verified.json').write_text(json.dumps(report,ensure_ascii=False,indent=2));print('PASS all 35 layouts, bounds, joined geometry, palettes, dimensions and budgets')
