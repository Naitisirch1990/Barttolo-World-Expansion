import zipfile,json,nbtlib as n,gzip,io,collections,random,pathlib,math,copy
from PIL import Image,ImageDraw
KNOWN={'minecraft:'+s['name']:s for s in json.load(open('vanilla-blocks.json'))}
DIR={'north':(0,0,-1),'south':(0,0,1),'west':(-1,0,0),'east':(1,0,0),'up':(0,1,0),'down':(0,-1,0)}
AIR={'minecraft:air','minecraft:cave_air','minecraft:void_air','minecraft:structure_void','minecraft:structure_block'}
SOIL={'minecraft:dirt','minecraft:grass_block','minecraft:dirt_path','minecraft:coarse_dirt','minecraft:sand','minecraft:red_sand','minecraft:podzol','minecraft:moss_block','minecraft:snow_block','minecraft:rooted_dirt'}
BIOMES={'plains':'Pradera','desert':'Desierto','savanna':'Sabana','snowy':'Nieve','taiga':'Taiga'}
def rot(p,r):
 x,y,z=p;return [(x,y,z),(-z,y,x),(-x,y,-z),(z,y,-x)][r%4]
def add(a,b):return tuple(a[i]+b[i] for i in range(3))
def sub(a,b):return tuple(a[i]-b[i] for i in range(3))
def blockstate(text):
 name,_,props=text.partition('[');s=n.Compound({'Name':n.String(name)})
 if props:s['Properties']=n.Compound({k:n.String(v) for k,v in (pair.split('=') for pair in props.rstrip(']').split(','))})
 return s
class Profile:
 def __init__(self,path,namespace,label):
  self.z=zipfile.ZipFile(path);self.namespace=namespace;self.label=label;self.cache={};self.files={};self.pools={};self.used=set();self.converted={};self.replacements=collections.Counter()
  for file in self.z.namelist():
   if file.endswith('.nbt') and '/structures/' in file:
    ns=file.split('/')[1];self.files[ns+':'+file.split('/structures/')[1][:-4]]=file
   if file.endswith('.json') and '/worldgen/template_pool/' in file:
    ns=file.split('/')[1];self.pools[ns+':'+file.split('/worldgen/template_pool/')[1][:-5]]=json.loads(self.z.read(file))
 def choices(self,id):
  if id in self.pools:
   result=[]
   for e in self.pools[id]['elements']:
    loc=e['element'].get('location')
    if loc in self.files:result.extend([loc]*min(e['weight'],12))
   if not result and (fb:=self.pools[id].get('fallback')) not in [None,'minecraft:empty',id]:return self.choices(fb)
   return result
  if id in self.files:return [id]
  if id.startswith('minecraft:village/'):
   return sorted(f for f in self.files if f.startswith(id+'/') and f.count('/')==id.count('/')+1)
  return []
 def read(self,id):
  if id in self.cache:return self.cache[id]
  file=self.files.get(id)
  if not file:return None
  t=n.File.parse(io.BytesIO(gzip.decompress(self.z.read(file))));pal=t['palette'];names=[str(s['Name']) for s in pal];solids=[];jigs=[]
  for b in t['blocks']:
   pos=tuple(map(int,b['pos']));name=names[int(b['state'])];data=b.get('nbt',{})
   if name=='minecraft:jigsaw':
    orient=str(pal[int(b['state'])].get('Properties',{}).get('orientation','north_up')).split('_');data={k:str(v) for k,v in data.items()};jigs.append((pos,DIR[orient[0]],DIR[orient[1]],data));name=data.get('final_state','minecraft:air').split('[')[0]
   if name not in AIR:solids.append((pos,name))
  lo=tuple(min(p[i] for p,name in solids) for i in range(3)) if solids else (0,0,0)
  hi=tuple(max(p[i] for p,name in solids) for i in range(3)) if solids else tuple(int(v)-1 for v in t['size'])
  info={'lo':lo,'hi':hi,'solid':solids,'jigs':jigs,'entities':len(t.get('entities',[]))};self.cache[id]=info;return info
 def box(self,id,o,r):
  t=self.read(id);a=rot(t['lo'],r);b=rot(t['hi'],r);return tuple(min(a[i],b[i])+o[i] for i in range(3)),tuple(max(a[i],b[i])+o[i] for i in range(3))
 def state(self,s):
  old=str(s['Name']);name={'minecraft:short_grass':'minecraft:grass','minecraft:iron_chain':'minecraft:chain','minecraft:grass_path':'minecraft:dirt_path'}.get(old,old)
  assert name in KNOWN,(self.namespace,'unknown block',name)
  if name!=old:self.replacements[old]+=1
  result=n.Compound({'Name':n.String(name)});allowed={p['name']:p for p in KNOWN[name]['states']};props={}
  for k,v in s.get('Properties',{}).items():
   if k not in allowed:continue
   p=allowed[k];valid=p.get('values',['true','false'] if p['type']=='bool' else [str(v) for v in range(p['num_values'])])
   if str(v) in valid:props[k]=n.String(str(v))
  if props:result['Properties']=n.Compound(props)
  return result
 def npc(self,pool,pos,biome,seed):
  kind=None
  for ending,e in [('/villagers','villager'),('/cats','cat'),('/iron_golem','iron_golem'),('/sheep','sheep'),('/camel','camel'),('/butcher_animals','pig'),('/animals','cow')]:
   if pool.endswith(ending):kind=e
  candidates=self.choices(pool)
  helper=candidates[0] if candidates and all('/entities/' in cid for cid in candidates) else None
  if helper:
   t=n.File.parse(io.BytesIO(gzip.decompress(self.z.read(self.files[helper]))));es=t.get('entities',[])
   if es:
    d=copy.deepcopy(es[seed%len(es)]['nbt']);d.pop('UUID',None);return d
  if not kind:return None
  d=n.Compound({'id':n.String('minecraft:'+kind)})
  if kind=='villager':d['VillagerData']=n.Compound({'type':n.String('minecraft:'+{'snowy':'snow','plains':'plains'}.get(biome,biome)),'profession':n.String('minecraft:none'),'level':n.Int(1)})
  if kind=='cat':d['variant']=n.String('minecraft:tabby')
  if kind=='iron_golem':d['PlayerCreated']=n.Byte(0)
  return d
 def convert(self,id):
  if id in self.converted:return self.converted[id]
  info=self.read(id);lo,hi=info['lo'],info['hi'];t=n.File.parse(io.BytesIO(gzip.decompress(self.z.read(self.files[id]))));oldpal=t['palette'];palette=[];lookup={};blocks=[];ens=[]
  biome=next((b for b in BIOMES if '/'+b+'/' in id),'plains');generated=set()
  for en in t.get('entities',[]):
   en=copy.deepcopy(en);pos=[float(v)-lo[i] for i,v in enumerate(en['pos'])];data=en['nbt'];
   if not str(data.get('id','')).startswith('minecraft:'):continue
   original=data.get('Pos',en['pos']);
   for i,axis in enumerate('XYZ'):
    if 'Tile'+axis in data:data['Tile'+axis]=n.Int(round(int(data['Tile'+axis])+pos[i]-float(original[i])))
   data.pop('UUID',None);data['Pos']=n.List[n.Double]([n.Double(v) for v in pos]);en['pos']=data['Pos'];en['blockPos']=n.List[n.Int]([n.Int(math.floor(v)) for v in pos]);ens.append(en)
  for b in t['blocks']:
   oldpos=tuple(map(int,b['pos']));pos=tuple(oldpos[i]-lo[i] for i in range(3));s=oldpal[int(b['state'])];data=copy.deepcopy(b.get('nbt'));name=str(s['Name'])
   if name=='minecraft:jigsaw':
    pool=str(data.get('pool','')) if data else '';npc=self.npc(pool,pos,biome,len(generated)) if pool!='minecraft:empty' else None
    if npc and oldpos not in generated:
     generated.add(oldpos);ep=[pos[0]+.5,pos[1]+1.,pos[2]+.5];npc['Pos']=n.List[n.Double]([n.Double(v) for v in ep]);ens.append(n.Compound({'pos':npc['Pos'],'blockPos':n.List[n.Int]([n.Int(math.floor(v)) for v in ep]),'nbt':npc}))
    s=blockstate(str(data.get('final_state','minecraft:air')) if data else 'minecraft:air');data=None
   if str(s['Name']) in ['minecraft:structure_void','minecraft:structure_block']:s=blockstate('minecraft:air');data=None
   if not all(lo[i]<=oldpos[i]<=hi[i] for i in range(3)):continue
   s=self.state(s);key=str(s)
   if key not in lookup:lookup[key]=len(palette);palette.append(s)
   out=n.Compound({'pos':n.List[n.Int]([n.Int(v) for v in pos]),'state':n.Int(lookup[key])})
   if data:
    for k in ['x','y','z']:data.pop(k,None)
    if 'LootTable' in data:
     table=str(data['LootTable'])
     if not table.startswith('minecraft:'):data['LootTable']=n.String('minecraft:chests/simple_dungeon')
    out['nbt']=data
   blocks.append(out)
  size=tuple(hi[i]-lo[i]+1 for i in range(3));out=n.File({'DataVersion':n.Int(3465),'size':n.List[n.Int]([n.Int(v) for v in size]),'palette':n.List[n.Compound](palette),'blocks':n.List[n.Compound](blocks),'entities':n.List[n.Compound](ens)})
  buf=io.BytesIO();out.write(buf);newid=self.namespace+':'+id.split(':')[1];item=(newid,gzip.compress(buf.getvalue()),size,len(blocks),len(ens),out);self.converted[id]=item;return item

def layout(profile,root,seed,maxpieces=70):
 rng=random.Random(seed);pieces=[];boxes=[];occupied={};seen=set();queue=collections.deque();cost=0
 def place(id,o,r,depth):
  nonlocal cost
  t=profile.read(id);box=profile.box(id,o,r);pieces.append((id,o,r));boxes.append(box);seen.add((id,o,r));cost+=math.prod(t['hi'][i]-t['lo'][i]+1 for i in range(3))
  for p,name in t['solid']:occupied[add(rot(p,r),o)]=name
  js=sorted(t['jigs'],key=lambda a:0 if a[1][1]==0 else 1)
  for p,f,u,data in js:
   if data.get('pool','').startswith('minecraft:village/') and not any(x in data['pool'] for x in ['/streets','/houses','/decor']):continue
   if profile.npc(data.get('pool',''),p,'plains',0) is not None:continue
   queue.append((add(rot(p,r),o),rot(f,r),rot(u,r),data,depth))
 place(root,(0,0,0),0,0)
 while queue and len(pieces)<maxpieces:
  p,f,u,data,depth=queue.popleft()
  if depth>=8:continue
  candidates=profile.choices(data.get('pool',''));rng.shuffle(candidates)
  if not candidates:continue
  for cid in dict.fromkeys(candidates):
   ct=profile.read(cid)
   if ct is None:continue
   if '/entities/' in cid:continue
   done=False
   for cp,cf,cu,cd in ct['jigs']:
    if cd.get('name')!=data.get('target'):continue
    for r in range(4):
     if rot(cf,r)!=tuple(-v for v in f):continue
     if data.get('joint','aligned')!='rollable' and rot(cu,r)!=u:continue
     o=sub(add(p,f),rot(cp,r))
     if (cid,o,r) in seen:continue
     box=profile.box(cid,o,r);low=tuple(min([box[0][i]]+[b[0][i] for b in boxes]) for i in range(3));high=tuple(max([box[1][i]]+[b[1][i] for b in boxes]) for i in range(3))
     if any(high[i]-low[i]+1>224 for i in range(3)):continue
     if cost+math.prod(ct['hi'][i]-ct['lo'][i]+1 for i in range(3))>850000:continue
     intersections=0;bad=False
     if any(all(box[0][i]<=b[1][i] and b[0][i]<=box[1][i] for i in range(3)) for b in boxes):
      for q,name in ct['solid']:
       old=occupied.get(add(rot(q,r),o))
       if old is None:continue
       if old in SOIL and name in SOIL:continue
       if old!=name:bad=True;break
       intersections+=1
       if intersections>max(24,len(ct['solid'])//50):bad=True;break
     if bad:continue
     place(cid,o,r,depth+1);done=True;break
    if done:break
   if done:break
 return pieces,boxes

SPECS=[('upload/bettervillage-forge-1.20.1-3.3.1-all (1).jar','bwe_better','Better Village'),('upload/luki-s-grand-capitals-0.7-forge-1.19-1.20.1.jar','bwe_capitals','Grand Capitals')]
OUT=pathlib.Path('biome-previews');OUT.mkdir(exist_ok=True);entries=[];reports=[];palettes={}
for source,ns,label in SPECS:
 profile=Profile(source,ns,label);roots=[]
 if ns=='bwe_better':
  roots=[(id,next(b for b in BIOMES if '/'+b+'/' in id),'Aldea') for id in sorted(profile.files) if '/town_centers/' in id]
 else:
  for biome,pool in [('plains','start'),('desert','desert/start'),('savanna','savanna/start'),('snowy','snowy/start'),('taiga','taiga/start')]:
   roots.extend((id,biome,'Capital') for id in dict.fromkeys(profile.choices('revampedvillages:'+pool)))
  roots.extend([('revampedvillages:mansion/bottom_front_left','dark_forest','Mansión del bosque'),('revampedvillages:outpost/base_plate','varios','Puesto de saqueadores'),('revampedvillages:hut','swamp','Cabaña de bruja')])
 for root,biome,kind in roots:
  solutions=[layout(profile,root,seed,85 if 'mansion' in root else 65) for seed in range(3)]
  pieces,boxes=max(solutions,key=lambda solution:sum(10 if any(w in i for w in ['/house','/worker','/houses']) else 1 for i,o,r in solution[0]));lo=tuple(min(b[0][i] for b in boxes) for i in range(3));hi=tuple(max(b[1][i] for b in boxes) for i in range(3));size=[hi[i]-lo[i]+1 for i in range(3)]
  rootinfo=profile.read(root);horizontal=[p[1]-1 for p,f,u,d in rootinfo['jigs'] if f[1]==0];floor=collections.Counter(horizontal).most_common(1)[0][0] if horizontal else rootinfo['lo'][1]
  ground=max(1,floor-lo[1]+1);runtime=[];cellcount=entitycount=0;solids={}
  for id,o,r in pieces:
   new,data,ts,blocks,ens,tag=profile.convert(id);profile.used.add(id);cellcount+=blocks;entitycount+=ens;po=sub(add(o,rot(profile.read(id)['lo'],r)),lo)
   runtime.append(dict(template=new,x=po[0],y=po[1],z=po[2],rotation=r))
   for q,name in profile.read(id)['solid']:solids[sub(add(rot(q,r),o),lo)]=name
   for state in tag['palette']:
    st={'Name':str(state['Name'])}
    if 'Properties' in state:st['Properties']={k:str(v) for k,v in state['Properties'].items()}
    palettes[json.dumps(st,sort_keys=True)]=st
  slug=root.split(':')[1].replace('/','_');bio=BIOMES.get(biome,{'dark_forest':'Bosque oscuro','swamp':'Pantano','varios':'Varios biomas'}.get(biome,biome));variant=root.split('/')[-1].replace(biome+'_','').replace('_',' ').replace('meeting point','Plaza').replace('fountain','Fuente').replace('well','Pozo').replace('small market','Mercado pequeño').replace('medium market','Mercado').replace('big market','Gran mercado').replace('market','Mercado').replace('shrine','Santuario').replace('spikes','Agujas de hielo').replace('tree','Árbol central').replace('skull','Calavera').replace('bonfire','Hoguera').replace('luki','Plaza Luki')
  title=f'{label} - {bio} - {variant}' if kind in ['Aldea','Capital'] else f'{kind} - {bio}'
  entry=dict(id=ns+':'+slug,title=title,description=f'{bio} · {len(pieces)} piezas · {label}',biome=bio,sizeX=size[0],sizeY=size[1],sizeZ=size[2],ground=ground,pieces=runtime)
  if root=='revampedvillages:mansion/bottom_front_left':entry['description']='Bosque oscuro · Hostil · Muchas entidades'
  elif root=='revampedvillages:outpost/base_plate':entry['description']='Varios biomas · Puesto hostil'
  elif root=='revampedvillages:hut':entry['description']='Pantano · Cabaña de bruja'
  entries.append(entry);reports.append(dict(id=entry['id'],biome=bio,title=title,pieces=len(pieces),size=size,ground=ground,cells=cellcount,entities=entitycount,root=root))
  im=Image.new('RGB',(256,174),'#19242b');d=ImageDraw.Draw(im);scale=min(246/size[0],164/size[2]);ox=(256-size[0]*scale)/2;oz=(174-size[2]*scale)/2;top={}
  for (x,y,z),name in solids.items():
   if (x,z) not in top or y>top[x,z][0]:top[x,z]=(y,name)
  for (x,z),(y,name) in top.items():
   color=(138,139,146)
   for word,c in [('leaves',(72,119,65)),('moss',(85,130,60)),('grass',(103,145,75)),('water',(61,122,173)),('sand',(214,193,139)),('snow',(225,232,238)),('ice',(131,183,212)),('wood',(142,111,76)),('planks',(156,126,85)),('log',(115,91,61)),('brick',(149,111,105)),('terracotta',(183,128,91))]:
    if word in name:color=c;break
   shade=.83+.17*y/max(1,size[1]);color=tuple(int(c*shade) for c in color);d.rectangle((ox+x*scale,oz+z*scale,ox+(x+1)*scale,oz+(z+1)*scale),fill=color)
  im.save(OUT/(entry['id'].replace(':','_')+'.png'));print(reports[-1],flush=True)
 with zipfile.ZipFile(ns+'.jar','w',zipfile.ZIP_DEFLATED) as z:
  for id in sorted(profile.used):
   new,data,*_=profile.convert(id);z.writestr('data/'+new.replace(':','/structures/')+'.nbt',data)
 print('ARCHIVE',ns,len(profile.used),'templates','block replacements',dict(profile.replacements),flush=True)
order={b:i for i,b in enumerate(['Pradera','Desierto','Sabana','Nieve','Taiga','Bosque oscuro','Pantano','Varios biomas'])};entries.sort(key=lambda e:(order.get(e['biome'],99),e['title']))
pathlib.Path('catalogo-biomas-v1.json').write_text(json.dumps({'entries':entries},ensure_ascii=False,indent=2));pathlib.Path('biomes-report.json').write_text(json.dumps(reports,ensure_ascii=False,indent=2));pathlib.Path('biomes-palettes.json').write_text(json.dumps({'states':list(palettes.values())},separators=(',',':')))
print('TOTAL',len(entries),'new entries',flush=True)
