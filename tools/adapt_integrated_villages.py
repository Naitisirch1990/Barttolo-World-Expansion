import zipfile,json,nbtlib,gzip,io,collections,random,os
from PIL import Image,ImageDraw
Z=zipfile.ZipFile('upload/integrated_villages-1.3.2+1.20.1-forge.jar'); cache={}
D={'north':(0,0,-1),'south':(0,0,1),'east':(1,0,0),'west':(-1,0,0),'up':(0,1,0),'down':(0,-1,0)}
def rot(p,r):
 x,y,z=p
 return [(x,y,z),(-z,y,x),(-x,y,-z),(z,y,-x)][r]
def add(a,b):return tuple(x+y for x,y in zip(a,b))
def sub(a,b):return tuple(x-y for x,y in zip(a,b))
def pool(id):
 path='data/'+id.replace(':','/worldgen/template_pool/')+'.json'
 if path not in Z.namelist():return []
 d=json.loads(Z.read(path));return [e['element'].get('location') for e in d['elements'] for _ in range(min(e['weight'],10)) if e['element'].get('location')]
def template(id):
 if id in cache:return cache[id]
 path='data/'+id.replace(':','/structures/')+'.nbt'
 if path not in Z.namelist():return None
 t=nbtlib.File.parse(io.BytesIO(gzip.decompress(Z.read(path)))); pal=t['palette']; solid=[];js=[]; namespaces=set()
 for b in t['blocks']:
  p=tuple(map(int,b['pos'])); st=pal[int(b['state'])];name=str(st['Name']);nb=b.get('nbt',{})
  if name=='minecraft:jigsaw':
   orient=str(st.get('Properties',{}).get('orientation','north_up')).split('_');js.append((p,D[orient[0]],D[orient[1]],{k:str(v) for k,v in nb.items()}));name=str(nb.get('final_state','minecraft:air')).split('[')[0]
  if name not in ['minecraft:air','minecraft:cave_air','minecraft:void_air','minecraft:structure_void','minecraft:structure_block']:
   solid.append((p,name));namespaces.add(name.split(':')[0])
 if not solid:
  size=list(map(int,t["size"]));cache[id]=((0,0,0),tuple(v-1 for v in size),js,solid,namespaces);return cache[id]
 lo=tuple(min(p[i] for p,n in solid) for i in range(3));hi=tuple(max(p[i] for p,n in solid) for i in range(3))
 cache[id]=(lo,hi,js,solid,namespaces);return cache[id]
def box(id,o,r):
 lo,hi,*_=template(id);a=rot(lo,r);b=rot(hi,r);return tuple(min(a[i],b[i])+o[i] for i in range(3)),tuple(max(a[i],b[i])+o[i] for i in range(3))
def overlap(a,b):return all(a[0][i]<=b[1][i] and a[1][i]>=b[0][i] for i in range(3))
def layout(root,seed):
 rng=random.Random(seed);id=pool(root)[0];pieces=[(id,(0,0,0),0)];boxes=[box(id,(0,0,0),0)];queue=collections.deque();used=set()
 def push(id,o,r,depth):
  for p,f,t,d in template(id)[2]:queue.append((add(rot(p,r),o),rot(f,r),rot(t,r),d,depth))
 push(id,(0,0,0),0,0)
 while queue and len(pieces)<56:
  p,f,top,d,depth=queue.popleft()
  if depth>=9:continue
  candidates=pool(d.get('pool','minecraft:empty'));rng.shuffle(candidates);placed=False
  for cid in dict.fromkeys(candidates):
   ct=template(cid)
   if ct is None:continue
   for cp,cf,ctop,cd in ct[2]:
    if cd.get('name')!=d.get('target'):continue
    for r in range(4):
     if rot(cf,r)!=tuple(-v for v in f):continue
     if d.get('joint','aligned')!='rollable' and rot(ctop,r)!=top:continue
     o=sub(add(p,f),rot(cp,r));b=box(cid,o,r)
     if max(abs(b[k][i]) for k in (0,1) for i in (0,2))>115:continue
     if any(overlap(b,prev) for prev in boxes):continue
     low=tuple(min([b[0][i]]+[x[0][i] for x in boxes]) for i in range(3));high=tuple(max([b[1][i]]+[x[1][i] for x in boxes]) for i in range(3))
     if any(high[i]-low[i]+1>224 for i in range(3)):continue
     pieces.append((cid,o,r));boxes.append(b);push(cid,o,r,depth+1);placed=True;break
    if placed:break
   if placed:break
 return pieces,boxes
names={'airship_village':'Dirigibles','cabin_village':'Cabanas','clockwork_village':'Mecanica steampunk','kutcha_village':'Kutcha','marketstead_village':'Mercado','mediterranean_village':'Mediterranea','mossy_mounds':'Colinas musgosas','oasis_village':'Oasis','pirate_village':'Pirata','quark/minka_village':'Minka japonesa (Quark)','sunken_village':'Pantano hundido','tavern_village':'Taberna'}
entries=[];os.makedirs('iv-previews',exist_ok=True)
for key,title in names.items():
 cfg=json.loads(Z.read('data/integrated_villages/worldgen/structure/'+key+'.json'));pieces,boxes=max([layout(cfg['start_pool'],s) for s in range(3)],key=lambda p:len(p[0]));lo=tuple(min(b[0][i] for b in boxes) for i in range(3));hi=tuple(max(b[1][i] for b in boxes) for i in range(3));size=[hi[i]-lo[i]+1 for i in range(3)]
 # Root connector level defines the intended walking surface; preserve internal vertical offsets.
 root=template(pieces[0][0]);ys=[p[1] for p,f,t,d in root[2] if f[1]==0];ground=(collections.Counter(ys).most_common(1)[0][0]+1 if ys else root[0][1]+1)-lo[1];ground=max(1,min(ground,size[1]-1))
 slug=key.replace('quark/','');entry=dict(id='integrated_villages:aldea_'+slug,title='Aldea IV - '+title,description=str(len(pieces))+' piezas conectadas; adaptacion BWE',category='aldeas',sizeX=size[0],sizeY=size[1],sizeZ=size[2],ground=ground,pieces=[dict(template=id,x=o[0]-lo[0],y=o[1]-lo[1],z=o[2]-lo[2],rotation=r) for id,o,r in pieces]);entries.append(entry)
 im=Image.new('RGB',(256,174),'#19242b');draw=ImageDraw.Draw(im);scale=min(246/size[0],164/size[2]);ox=(256-size[0]*scale)/2;oz=(174-size[2]*scale)/2
 for id,o,r in pieces:
  for p,n in template(id)[3]:
   p=add(rot(p,r),o);x=ox+(p[0]-lo[0])*scale;y=oz+(p[2]-lo[2])*scale
   color='#91b85b' if any(a in n for a in ['leaves','grass','moss','crop']) else '#527d9c' if 'water' in n else '#bba078' if any(a in n for a in ['sand','planks','log','wood']) else '#92939a'
   draw.rectangle((x,y,x+max(1,scale),y+max(1,scale)),fill=color)
 im.save('iv-previews/'+entry['id'].replace(':','_')+'.png');print(key,len(pieces),size,'ground',ground,flush=True)
with open('catalogo-iv.json','w') as f:json.dump(dict(format=2,entries=entries),f,indent=2)
