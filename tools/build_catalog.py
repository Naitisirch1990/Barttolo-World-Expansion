import gzip,struct,pathlib,io,zlib,math
class N:
 def __init__(self,b):self.f=io.BytesIO(b)
 def n(self,fmt):return struct.unpack('>'+fmt,self.f.read(struct.calcsize('>'+fmt)))[0]
 def s(self):return self.f.read(self.n('H')).decode()
 def v(self,t):
  if t in [1,2,3,4,5,6]:return self.n({1:'b',2:'h',3:'i',4:'q',5:'f',6:'d'}[t])
  if t==7:return self.f.read(self.n('i'))
  if t==8:return self.s()
  if t==9:
   k=self.n('b');return [self.v(k) for _ in range(self.n('i'))]
  if t==10:
   d={}
   while (k:=self.n('b'))!=0:
    name=self.s();d[name]=self.v(k)
   return d
  if t in [11,12]:return [self.n('i' if t==11 else 'q') for _ in range(self.n('i'))]
 def read(self):t=self.n('b');self.s();return self.v(t)

# Offline, deterministic placement plans from the user's UNMODIFIED source archives.
# Does not copy or modify third-party templates; output contains only IDs and transforms.
import json,random,collections,hashlib,argparse,zipfile
DIRS={'north':(0,0,-1),'south':(0,0,1),'east':(1,0,0),'west':(-1,0,0),'up':(0,1,0),'down':(0,-1,0)}
HORIZONTAL=['north','east','south','west']
def rot(p,r):
 x,y,z=p
 return [(x,y,z),(-z,y,x),(-x,y,-z),(z,y,-x)][r]
def add(a,b):return tuple(x+y for x,y in zip(a,b))
def sub(a,b):return tuple(x-y for x,y in zip(a,b))
def opposite(a,b):return all(x==-y for x,y in zip(a,b))
class Sources:
 def __init__(self,paths):
  self.zips=[zipfile.ZipFile(p) for p in paths];self.templates={};self.pools={};self.defs={}
  for z in self.zips:
   for n in z.namelist():
    if not n.startswith('data/'):continue
    parts=n.split('/'); ns=parts[1]
    if n.endswith('.nbt') and parts[2] in ['structures','structure']:
     ident=ns+':'+n.split('/',3)[3][:-4]
     if ident not in self.templates or parts[2]=='structures':self.templates[ident]=(z,n)
    elif n.endswith('.json') and '/worldgen/template_pool/' in n:self.pools[ns+':'+n.split('/worldgen/template_pool/')[1][:-5]]=json.loads(z.read(n))
    elif n.endswith('.json') and '/worldgen/structure/' in n and '/tags/' not in n:self.defs[ns+':'+n.split('/worldgen/structure/')[1][:-5]]=json.loads(z.read(n))
  self.cache={}
 def template(self,ident):
  if ident in self.cache:return self.cache[ident]
  z,n=self.templates[ident];d=N(gzip.decompress(z.read(n))).read();palette=d.get('palette',d.get('palettes',[[]])[0]);connectors=[]
  for b in d['blocks']:
   state=palette[b['state']]
   if state['Name']=='minecraft:jigsaw':
    c=dict(b.get('nbt',{}));c['pos']=tuple(b['pos']);c['front']=DIRS[state['Properties']['orientation'].split('_')[0]];connectors.append(c)
  value=(d['size'],connectors,d);self.cache[ident]=value;return value
 def options(self,pool):
  d=self.pools.get(pool,{})
  out=[]
  for e in d.get('elements',[]):
   el=e['element'];kind=el.get('element_type','')
   if kind.endswith('single_pool_element') and el.get('location') in self.templates:out.append((el['location'],e['weight']))
   elif kind.endswith('list_pool_element'):
    for inner in el.get('elements',[]):
     if inner.get('location') in self.templates:out.append((inner['location'],e['weight']))
  return out,d.get('fallback','minecraft:empty')

def bounds(size,r,off):
 points=[add(rot((x,y,z),r),off) for x in [0,size[0]-1] for y in [0,size[1]-1] for z in [0,size[2]-1]]
 return tuple(min(p[i] for p in points) for i in range(3)),tuple(max(p[i] for p in points) for i in range(3))
def overlaps(a,b):return all(a[0][i]<=b[1][i] and a[1][i]>=b[0][i] for i in range(3))
def assemble(src,start_pool,seed,max_depth=5,max_pieces=36,extent=65):
 rng=random.Random(seed);opts,_=src.options(start_pool)
 if not opts:return None
 root=rng.choices([v[0] for v in opts],weights=[v[1] for v in opts])[0]
 size,cons,_=src.template(root);parts=[{'template':root,'x':0,'y':0,'z':0,'rotation':0}];boxes=[bounds(size,0,(0,0,0))];queue=collections.deque()
 ground=min([c['pos'][1] for c in cons if c['front'][1]==0] or [1])
 for c in cons:queue.append((c,tuple(c['pos']),c['front'],0))
 while queue and len(parts)<max_pieces:
  parent,pos,front,depth=queue.popleft()
  if depth>=max_depth:continue
  options,fallback=src.options(parent.get('pool','minecraft:empty'))
  if not options:options,_=src.options(fallback)
  # Weighted random permutation, then retry compatible variants/rotations.
  options=sorted(options,key=lambda x:rng.random()**(1/max(1,x[1])),reverse=True)
  placed=False
  for ident,_ in options:
   ss,connectors,_=src.template(ident);rots=list(range(4));rng.shuffle(rots)
   for r in rots:
    for child in connectors:
     if child.get('name')!=parent.get('target') or not opposite(rot(child['front'],r),front):continue
     off=sub(add(pos,front),rot(child['pos'],r));box=bounds(ss,r,off)
     if max(abs(box[k][axis]) for k in [0,1] for axis in [0,2])>extent:continue
     if any(overlaps(box,b) for b in boxes):continue
     parts.append({'template':ident,'x':off[0],'y':off[1],'z':off[2],'rotation':r});boxes.append(box)
     for c in connectors:
      if c is child:continue
      queue.append((c,add(rot(c['pos'],r),off),rot(c['front'],r),depth+1))
     placed=True;break
    if placed:break
   if placed:break
 mins=[min(b[0][i] for b in boxes) for i in range(3)];maxs=[max(b[1][i] for b in boxes) for i in range(3)]
 for p in parts:
  for i,k in enumerate(['x','y','z']):p[k]-=mins[i]
 return {'sizeX':maxs[0]-mins[0]+1,'sizeY':maxs[1]-mins[1]+1,'sizeZ':maxs[2]-mins[2]+1,'ground':ground-mins[1],'pieces':parts}

def create(paths,dest):
 src=Sources(paths);entries=[]
 translations={'plains':'Pradera','desert':'Desierto','desert_oasis':'Oasis','taiga':'Taiga','taiga_fortified':'Taiga fortificada','snowy_igloo':'Iglus','savanna':'Sabana','savanna_na':'Sabana africana','beach':'Costa','jungle':'Selva','jungle_tree':'Selva arboles','mesa':'Mesa','mesa_fortified':'Mesa fortificada','mountain':'Montana','mountain_alpine':'Alpina','mushroom':'Hongos','swamp':'Pantano','swamp_fortified':'Pantano fortificado','dark_forest':'Bosque oscuro','plains_fortified':'Pradera fortificada','christmas':'Navidad'}
 for ident,d in sorted(src.defs.items()):
  if ident.startswith('ctov:medium/') and not ident.endswith('underground'):
   style=ident.split('village_',1)[1];best=None
   for seed in range(8):
    p=assemble(src,d['start_pool'],seed,max_depth=5,max_pieces=46,extent=70)
    if p and (best is None or len(p['pieces'])>len(best['pieces'])):best=p
   if not best:continue
   homes=sum('/house/' in x['template'] or '/houses/' in x['template'] or '/profession/' in x['template'] for x in best['pieces'])
   best.update(id='ctov:aldea_'+style,title='Aldea CTOV — '+translations.get(style,style),category='aldeas',description=f'{homes} edificios; {len(best["pieces"])} piezas conectadas',seed=next((i for i in []),0))
   entries.append(best)
  elif ident.startswith('explorify:') and not any(k in ident for k in ['black_spiral','end_shipwreck','supply_cache']):
   pool=d.get('start_pool')
   if not pool:continue
   p=assemble(src,pool,1729,max_depth=min(d.get('size',5),6),max_pieces=40,extent=80)
   if p:
    name=ident.split(':')[1].replace('/',' ').replace('_',' ')
    p.update(id=ident,title='Explorify — '+name,category='estructuras',description=f'{len(p["pieces"])} piezas conectadas');entries.append(p)
  elif ident.startswith('ctov:pillager_outpost/'):
   p=assemble(src,d['start_pool'],1729,max_depth=4,max_pieces=20,extent=40)
   if p:p.update(id=ident,title='CTOV — Puesto '+ident.split('/')[-1],category='estructuras',description=f'{len(p["pieces"])} piezas');entries.append(p)
 # Also include individually placeable substantial buildings from actual house/profession pools.
 for style in ['plains','desert','taiga','savanna']:
  for suffix in ['house/small_1','house/big_1','profession/armorer','profession/library']:
   ident='ctov:village/'+style+'/'+suffix
   if ident not in src.templates:continue
   size,cons,_=src.template(ident);ground=min([c['pos'][1] for c in cons if c['front'][1]==0] or [1])
   entries.append(dict(id=ident,title='CTOV — '+style+' '+suffix.replace('/',' '),category='edificios',description='Edificio individual',sizeX=size[0],sizeY=size[1],sizeZ=size[2],ground=ground,pieces=[dict(template=ident,x=0,y=0,z=0,rotation=0)]))
 data={'format':1,'sources':[{'file':('ctov.jar' if 'ctov' in p.name.lower() else 'explorify.jar'),'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in paths],'entries':entries}
 pathlib.Path(dest).write_text(json.dumps(data,ensure_ascii=False,indent=2))
 for e in entries:print(e['id'],f'{e["sizeX"]}x{e["sizeZ"]}',e['description'])
 print('TOTAL',len(entries))
if __name__=='__main__':
 ap=argparse.ArgumentParser();ap.add_argument('ctov',type=pathlib.Path);ap.add_argument('explorify',type=pathlib.Path);ap.add_argument('output');a=ap.parse_args();create([a.ctov,a.explorify],a.output)
