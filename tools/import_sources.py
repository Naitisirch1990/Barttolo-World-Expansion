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

# Manual-only expansion: no source mod code/worldgen, entities, spawners or scripted blocks.
class CleanProfile(Profile):
 def disk(self,id,kind):
  import hashlib
  root=pathlib.Path('expansion/cache-v3')/self.namespace;root.mkdir(parents=True,exist_ok=True)
  return root/(hashlib.sha256(id.encode()).hexdigest()+'.'+kind)
 def read(self,id):
  import pickle
  if id in self.cache:return self.cache[id]
  path=self.disk(id,'profile')
  if path.exists():
   self.cache[id]=pickle.loads(path.read_bytes());return self.cache[id]
  result=super().read(id)
  if result:path.write_bytes(pickle.dumps(result))
  return result
 def state(self,s):
  if not hasattr(self,'state_cache'):self.state_cache={}
  key=str(s)
  if key not in self.state_cache:self.state_cache[key]=self.clean_state(s)
  return self.state_cache[key]
 def npc(self,*args):return None
 def clean_state(self,s):
  old=str(s['Name']);name=old
  leaf=old.split(':')[-1]
  if old not in KNOWN:
   exact={'minecraft:short_grass':'minecraft:grass','minecraft:grass_path':'minecraft:dirt_path'}
   name=exact.get(old)
   if not name:
    wood=next((w for w in ['dark_oak','spruce','birch','jungle','acacia','mangrove','crimson','warped','cherry','bamboo'] if w in leaf),'oak')
    material='stone_bricks'
    for word,mat in [('deepslate','deepslate_bricks'),('blackstone','polished_blackstone_bricks'),('tuff','stone_bricks'),('limestone','sandstone'),('sandstone','sandstone'),('granite','granite'),('jasper','granite'),('diorite','diorite'),('calcite','quartz_block'),('ochrum','sandstone'),('crimsite','red_nether_bricks'),('scoria','polished_blackstone'),('scorchia','polished_blackstone'),('asurine','diorite'),('veridium','prismarine'),('dripstone','stone_bricks'),('permafrost','snow_block'),('shingles','bricks'),('myalite','purpur_block')]:
     if word in leaf:material=mat;break
    woody=any(w in leaf for w in ['plank','wood','log','post']) or any(w in leaf for w in ['spruce','oak','birch','acacia','jungle','blossom','azalea','archwood'])
    if 'chest' in leaf:name='minecraft:chest'
    elif 'barrel' in leaf or 'cabinet' in leaf:name='minecraft:barrel'
    elif 'sign' in leaf or 'spawner' in leaf or 'monster_box' in leaf:name='minecraft:air'
    elif 'bookshelf' in leaf:name='minecraft:bookshelf'
    elif 'ladder' in leaf:name='minecraft:ladder'
    elif 'window' in leaf or 'glass' in leaf:name='minecraft:glass_pane' if 'pane' in leaf else 'minecraft:glass'
    elif 'leaves' in leaf or 'hedge' in leaf:name='minecraft:oak_leaves'
    elif 'trapdoor' in leaf:name='minecraft:'+wood+'_trapdoor'
    elif leaf.endswith('_door'):name='minecraft:'+wood+'_door'
    elif leaf.endswith('_fence_gate'):name='minecraft:'+wood+'_fence_gate'
    elif leaf.endswith('_fence'):name='minecraft:'+wood+'_fence'
    elif leaf.endswith('_stairs'):
     base=wood if woody else material.replace('bricks','brick').replace('_block','');name='minecraft:'+base+'_stairs'
     if name not in KNOWN:name='minecraft:stone_brick_stairs'
    elif leaf.endswith('_slab'):
     if 'vertical_slab' in leaf:name='minecraft:'+ (wood+'_planks' if woody else material)
     else:
      base=wood if woody else material.replace('bricks','brick').replace('_block','');name='minecraft:'+base+'_slab'
      if name not in KNOWN:name='minecraft:stone_brick_slab'
    elif leaf.endswith('_wall'):name='minecraft:'+material.replace('bricks','brick').replace('_block','')+'_wall';name=name if name in KNOWN else 'minecraft:stone_brick_wall'
    elif 'plank' in leaf:name='minecraft:'+wood+'_planks'
    elif any(w in leaf for w in ['wood','log','post','beam']):name='minecraft:'+wood+'_log';name=name if name in KNOWN else 'minecraft:oak_log'
    elif 'lantern' in leaf or 'lamp' in leaf:name='minecraft:sea_lantern'
    elif 'rope' in leaf or 'bars' in leaf or 'rod' in leaf:name='minecraft:iron_bars'
    elif any(w in leaf for w in ['stone','brick','ore','pillar','marble','basalt','deepslate','shingles','tuff','jasper','myalite','midori','permafrost','ochrum','crimsite','asurine','scoria','scorchia','veridium','casing','plate','chassis','timber_frame']):name='minecraft:'+material
    elif any(w in leaf for w in ['dirt','soil','grass']):name='minecraft:dirt'
    elif 'sand' in leaf:name='minecraft:sandstone'
    else:name='minecraft:air'
   self.replacements[old+' -> '+name]+=1
  if any(w in name for w in ['sign','banner','head','skull','command_block','spawner','structure_block','jigsaw','sculk_sensor','sculk_shrieker']):name='minecraft:air'
  name={'minecraft:hopper':'minecraft:polished_andesite','minecraft:beehive':'minecraft:oak_planks','minecraft:bee_nest':'minecraft:oak_planks','minecraft:campfire':'minecraft:spruce_log','minecraft:soul_campfire':'minecraft:dark_oak_log','minecraft:brewing_stand':'minecraft:air','minecraft:decorated_pot':'minecraft:terracotta','minecraft:chiseled_bookshelf':'minecraft:bookshelf'}.get(name,name)
  st=n.Compound({'Name':n.String(name)})
  if 'Properties' in s:st['Properties']=copy.deepcopy(s['Properties'])
  return super().state(st)
 def convert(self,id):
  import pickle
  if id in self.converted:return self.converted[id]
  path=self.disk(id,'converted')
  if path.exists():
   self.converted[id]=pickle.loads(path.read_bytes());return self.converted[id]
  item=super().convert(id);new,data,size,count,ens,tag=item
  tag['entities']=n.List[n.Compound]([])
  pal=tag['palette']
  for b in tag['blocks']:
   name=str(pal[int(b['state'])]['Name'])
   if name in ['minecraft:chest','minecraft:trapped_chest','minecraft:barrel']:
    # Keep chest loot lazy; BWE converts it to native Lootr after construction.
    b['nbt']=n.Compound({'id':n.String('minecraft:barrel' if name.endswith('barrel') else 'minecraft:chest'),'LootTable':n.String('minecraft:chests/simple_dungeon')})
   else:b.pop('nbt',None)
  tops={}
  for b in tag['blocks']:
   if str(pal[int(b['state'])]['Name']) not in AIR:
    x,y,z=map(int,b['pos']);tops[x,z]=max(y,tops.get((x,z),-1))
  # The builder clears above ground before placing. Only retain air below a real roof/floor.
  tag['blocks']=n.List[n.Compound]([b for b in tag['blocks'] if str(pal[int(b['state'])]['Name']) not in AIR or int(b['pos'][1])<=tops.get((int(b['pos'][0]),int(b['pos'][2])),-1)])
  count=len(tag['blocks'])
  buf=io.BytesIO();tag.write(buf);item=(new,gzip.compress(buf.getvalue()),size,count,0,{'palette':list(tag['palette'])});self.converted[id]=item;path.write_bytes(pickle.dumps(item));return item

SPECS=[('upload/Towns-and-Towers-1.12-Fabric+Forge.jar','bwe_tt','TT'),('upload/takesapillage-1.0.3-1.20.1.jar','bwe_itp','ITP'),('idas-original.jar','bwe_idas','IDAS')]
OUT=pathlib.Path('expansion');OUT.mkdir(exist_ok=True);PRE=OUT/'previews';PRE.mkdir(exist_ok=True)
entries=[];reports=[];palettes={};excluded=[]
translate={'village':'Aldea','pillager_outpost':'Puesto de saqueadores','pillager_camp':'Campamento de saqueadores','pillager_fortress':'Fortaleza de saqueadores','bastille':'Bastilla','desert':'Desierto','plains':'Pradera','snowy':'Nevado','jungle':'Selva','swamp':'Pantano','forest':'Bosque','birch':'Abedules','savanna':'Sabana','beach':'Playa','ocean':'Oceano','taiga':'Taiga','castle':'Castillo','labyrinth':'Laberinto','ancient_mines':'Minas antiguas','haunted_manor':'Mansion encantada','desert_pyramid':'Piramide del desierto','sunken_ship':'Barco hundido','wizard_tower':'Torre del mago','lumber_camp':'Campamento de leñadores'}
def label(name):
 for key in sorted(translate,key=len,reverse=True):name=name.replace(key,translate[key])
 return name.replace('_',' ').replace('/',' - ')
import os
only=os.getenv('BWE_SOURCE')
if only and (OUT/'report.json').exists():
 previous=json.loads((OUT/'report.json').read_text());reports=previous['entries'];excluded=previous['excluded']
 entries=json.loads((OUT/'catalogo-ampliacion-v1.json').read_text())['entries']
 if (OUT/'validation.json').exists():
  palettes={json.dumps(st,sort_keys=True):st for st in json.loads((OUT/'validation.json').read_text())['states']}
for source,ns,mod in SPECS:
 if only and mod!=only:continue
 profile=CleanProfile(source,ns,mod);structures={file:json.loads(profile.z.read(file)) for file in profile.z.namelist() if '/worldgen/structure/' in file and '/tags/' not in file and file.endswith('.json')}
 for si,(file,spec) in enumerate(sorted(structures.items())):
  slug=file.split('/worldgen/structure/')[1][:-5]
  if any(e['id']==ns+':'+slug.replace('/','_') for e in entries):continue
  roots=profile.choices(spec.get('start_pool',''))
  if not roots:excluded.append({'source':mod,'id':slug,'reason':'start pool has no local templates'});continue
  root=roots[0]
  try:
   pieces,boxes=layout(profile,root,si+73,64 if mod=='TT' else 110)
   lo=tuple(min(b[0][i] for b in boxes) for i in range(3));hi=tuple(max(b[1][i] for b in boxes) for i in range(3));size=[hi[i]-lo[i]+1 for i in range(3)]
   if max(size)>256:raise ValueError('layout dimensions exceed 256')
   rootinfo=profile.read(root);horizontal=[p[1]-1 for p,f,u,d in rootinfo['jigs'] if f[1]==0];floor=collections.Counter(horizontal).most_common(1)[0][0] if horizontal else rootinfo['lo'][1]
   # Surface structures without connectors: prefer the exposed terrain near their lower quarter.
   if not horizontal:
    columns={}
    for q,name in rootinfo['solid']:
     if name in SOIL and q[1]<=rootinfo['lo'][1]+max(6,(rootinfo['hi'][1]-rootinfo['lo'][1])//4):columns[q[1]]=columns.get(q[1],0)+1
    if columns:floor=max(columns,key=columns.get)
   mode='ground'
   if any(v in slug for v in ['village_ocean','wreckage_ocean','pillager_outpost_ocean','sunken_ship','sirens_cove']):
    mode='water';water=collections.Counter(q[1] for q,name in rootinfo['solid'] if name=='minecraft:water');floor=water.most_common(1)[0][0] if water else rootinfo['lo'][1]+min(5,size[1]-1)
   if any(v in slug for v in ['flying','airship','skyship']):mode='air'
   ground=max(1,floor-lo[1]+1);runtime=[];solids={};count=0
   for id,o,r in pieces:
    new,data,ts,blocks,ens,tag=profile.convert(id);count+=blocks;po=sub(add(o,rot(profile.read(id)['lo'],r)),lo)
    runtime.append(dict(template=new,x=po[0],y=po[1],z=po[2],rotation=r))
    for q,name in profile.read(id)['solid']:solids[sub(add(rot(q,r),o),lo)]=name
    for state in tag['palette']:
     st={'Name':str(state['Name'])}
     if 'Properties' in state:st['Properties']={k:str(v) for k,v in state['Properties'].items()}
     palettes[json.dumps(st,sort_keys=True)]=st
   if count>1000000:raise ValueError('layout exceeds one million template cells')
   # Hostile is a catalogue theme; all imported populations and spawners are removed.
   hostile=mod=='ITP' or any(k in slug for k in ['pillager','outpost','labyrinth','dungeon','fortress','citadel','haunted','witch','abandoned','ruin','ancient_mines','desert_pyramid','nexus','crypt','catacomb','wreckage','sunken','dread'])
   biome=str(spec.get('biomes','Varios')).split('/')[-1].replace('_biomes','');bio=label(biome)
   title=mod+' - '+label(slug);entry=dict(id=ns+':'+slug.replace('/','_'),title=title,description=bio+' · '+('Hostil · ' if hostile else '')+'Sin mobs · '+str(len(pieces))+' piezas',sizeX=size[0],sizeY=size[1],sizeZ=size[2],ground=ground,placement=mode,pieces=runtime)
   entries.append(entry);profile.used.update(i for i,o,r in pieces);reports.append(dict(id=entry['id'],title=title,pieces=len(pieces),cells=count,size=size,placement=mode,hostile=hostile,removed_entities=sum(profile.read(i)['entities'] for i,o,r in pieces)))
   im=Image.new('RGB',(256,174),'#19242b');draw=ImageDraw.Draw(im);scale=min(246/size[0],164/size[2]);ox=(256-size[0]*scale)/2;oz=(174-size[2]*scale)/2;top={}
   for (x,y,z),name in solids.items():
    if (x,z) not in top or y>top[x,z][0]:top[x,z]=(y,name)
   for (x,z),(y,name) in top.items():
    color=(135,139,145)
    for word,c in [('leaves',(70,117,65)),('grass',(100,140,75)),('water',(60,120,170)),('sand',(213,193,139)),('snow',(224,232,238)),('ice',(132,183,210)),('planks',(153,120,85)),('log',(113,89,60)),('brick',(147,111,103))]:
     if word in name:color=c;break
    draw.rectangle((ox+x*scale,oz+z*scale,ox+(x+1)*scale,oz+(z+1)*scale),fill=color)
   im.save(PRE/(entry['id'].replace(':','_')+'.png'));print(json.dumps(reports[-1]),flush=True)
  except Exception as err:
   excluded.append({'source':mod,'id':slug,'reason':str(err)});print('EXCLUDED',excluded[-1],flush=True)
  profile.cache.clear()
  # Persist catalogue progress so long imports can resume/review without losing completed work.
  (OUT/'catalogo-ampliacion-v1.json').write_text(json.dumps({'entries':entries},ensure_ascii=False,indent=2))
  (OUT/'report.json').write_text(json.dumps({'entries':reports,'excluded':excluded},ensure_ascii=False,indent=2))
 for e in entries:
  if e['id'].startswith(ns+':'):profile.used.update(p['template'].replace(ns+':',next(iter(profile.files)).split(':')[0]+':',1) for p in e['pieces'])
 with zipfile.ZipFile(OUT/(ns+'.jar'),'w',zipfile.ZIP_DEFLATED) as z:
  for id in sorted(profile.used):
   new,data,*rest=profile.convert(id);z.writestr('data/'+new.replace(':','/structures/')+'.nbt',data)
   for state in rest[-1]['palette']:
    st={'Name':str(state['Name'])}
    if 'Properties' in state:st['Properties']={k:str(v) for k,v in state['Properties'].items()}
    palettes[json.dumps(st,sort_keys=True)]=st
   profile.cache.clear()
 (OUT/(ns+'-replacements.json')).write_text(json.dumps(profile.replacements,indent=2))
 (OUT/'validation.json').write_text(json.dumps({'states':list(palettes.values()),'entries':reports},ensure_ascii=False,separators=(',',':')))
 print('FINISHED',mod,len(profile.used),'templates',flush=True)
