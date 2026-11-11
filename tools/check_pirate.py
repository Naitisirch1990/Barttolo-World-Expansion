import zipfile,io,json,gzip,nbtlib as n,collections
z=zipfile.ZipFile('BarttoloWorldExpansion-0.12.0-DIRIGIBLES-Y-9-ESQUEMAS-test.jar');e=next(e for e in json.loads(z.read('assets/barttoloworldexpansion/catalogo-iv-v2.json'))['entries'] if 'pirate' in e['id']);s=zipfile.ZipFile(io.BytesIO(z.read('assets/barttoloworldexpansion/integrated_vanilla.jar')));cells={}
for p in e['pieces']:
 t=n.File.parse(io.BytesIO(gzip.decompress(s.read('data/'+p['template'].replace(':','/structures/')+'.nbt'))));pal=t['palette']
 for b in t['blocks']:
  name=str(pal[int(b['state'])]['Name']);x,y,zz=map(int,b['pos']);x,y,zz=[(x,y,zz),(-zz,y,x),(-x,y,-zz),(zz,y,-x)][p['rotation']];x+=p['x'];y+=p['y'];zz+=p['z']
  if name=='minecraft:jigsaw':name=str(b.get('nbt',{}).get('final_state','minecraft:air')).split('[')[0]
  if name in ['minecraft:structure_void','minecraft:structure_block']:continue
  if 0<=x<e['sizeX'] and 0<=y<e['sizeY'] and 0<=zz<e['sizeZ']:cells[(x,y,zz)]=name
w,d,h=e['sizeX']+2,e['sizeZ']+2,22;blocked=set()
for (x,y,zz),name in cells.items():
 if name not in ['minecraft:air','minecraft:cave_air','minecraft:void_air'] and y<=19:blocked.add(((y+2)*d+zz+1)*w+x+1)
water=set();q=collections.deque()
for y in range(h):
 for zz in range(d):
  for x in range(w):
   if x not in (0,w-1) and zz not in (0,d-1) and y!=0:continue
   i=(y*d+zz)*w+x
   if i not in blocked and i not in water:water.add(i);q.append(i)
while q:
 i=q.popleft();x=i%w;zz=i//w%d;y=i//(w*d)
 for j in [i-1 if x>0 else -1,i+1 if x<w-1 else -1,i-w if zz>0 else -1,i+w if zz<d-1 else -1,i-w*d if y>0 else -1,i+w*d if y<h-1 else -1]:
  if j>=0 and j not in blocked and j not in water:water.add(j);q.append(j)
dry=w*d*h-len(water)-len(blocked)
print('PASS real pirate layout:',len(cells),'cells; waterline local Y=19; docks at Y=20;',len(water),'exterior water cells;',dry,'enclosed dry cells below waterline')
assert dry>0
