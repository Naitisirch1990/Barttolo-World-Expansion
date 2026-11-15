import nbtlib as n, pathlib, json, math, collections, io,gzip,zipfile,copy
from PIL import Image,ImageDraw
KNOWN={'minecraft:'+b['name']:b for b in json.load(open('vanilla-blocks.json'))}
REPLACE={'minecraft:short_grass':'minecraft:grass','minecraft:iron_chain':'minecraft:chain','minecraft:firefly_bush':'minecraft:flowering_azalea','minecraft:pale_oak_shelf':'minecraft:bookshelf','minecraft:spruce_shelf':'minecraft:bookshelf','minecraft:waxed_weathered_lightning_rod':'minecraft:lightning_rod','minecraft:waxed_exposed_copper_trapdoor':'minecraft:iron_trapdoor','minecraft:waxed_weathered_copper_trapdoor':'minecraft:iron_trapdoor'}
NAMES={'Casa_Grande':'Casa grande','Castillo_Medieval':'Castillo medieval','Coliseo_natural':'Coliseo natural','Faro_realista':'Faro realista','Greenhouse':'Invernadero','Mansion':'Mansión','Piramide_Azteka':'Pirámide azteca','Prismarine Pavilion - WidgeMan5':'Pabellón de prismarina','shadypond-foxglovebuilds':'Estanque Shady Pond'}
def state(s):
 name=str(s['Name']);new=REPLACE.get(name,name.replace('pale_oak','birch'));assert new in KNOWN,new
 props={};allowed={p['name']:p for p in KNOWN[new]['states']}
 for k,v in s.get('Properties',{}).items():
  if k not in allowed:continue
  a=allowed[k];vals=a.get('values',['true','false'] if a['type']=='bool' else [str(i) for i in range(a['num_values'])])
  if str(v) in vals:props[k]=n.String(str(v))
 out=n.Compound({'Name':n.String(new)})
 if props:out['Properties']=n.Compound(props)
 return out

def parse_state(s):
 name,_,props=s.partition('[');d=n.Compound({'Name':n.String(name)})
 if props:d['Properties']=n.Compound({k:n.String(v) for k,v in (p.split('=') for p in props.rstrip(']').split(','))})
 return d

def read(p):
 t=n.load(p);cells={};entities=[]
 if 'Regions' in t:
  expected=0
  for r in t['Regions'].values():
   size=[abs(int(r['Size'][a])) for a in 'xyz'];origin=[int(r['Position'][a]) for a in 'xyz'];lo=[origin[i]+min(0,int(r['Size'][a])+1) for i,a in enumerate('xyz')]
   pal=r['BlockStatePalette'];bits=max(2,(len(pal)-1).bit_length());mask=(1<<bits)-1;words=[int(v)&((1<<64)-1) for v in r['BlockStates']];nonair=0
   tes={tuple(int(te[a]) for a in 'xyz'):te for te in r.get('TileEntities',[])}
   for idx in range(math.prod(size)):
    bit=idx*bits;wi,off=divmod(bit,64);v=words[wi]>>off
    if off+bits>64:v|=words[wi+1]<<(64-off)
    v&=mask;assert v<len(pal),(p,idx,v,len(pal))
    x=idx%size[0];z=(idx//size[0])%size[2];y=idx//(size[0]*size[2]);s=pal[v]
    if str(s['Name']) not in ['minecraft:air','minecraft:cave_air','minecraft:void_air']:nonair+=1
    # Litematica tile positions use the minimum corner; entity positions use region origin.
    cells[(lo[0]+x,lo[1]+y,lo[2]+z)]=(s,tes.get((x,y,z)))
   expected+=nonair
   for en in r.get('Entities',[]):
    e=copy.deepcopy(en);pos=[float(v)+origin[i] for i,v in enumerate(e['Pos'])];entities.append((e,pos))
  assert expected==int(t['Metadata']['TotalBlocks']),(p,expected,t['Metadata']['TotalBlocks'])
  author=str(t['Metadata'].get('Author',''))
 else:
  size=[int(t[a]) for a in ['Width','Height','Length']];pal={int(v):parse_state(k) for k,v in t['Palette'].items()};tes={tuple(map(int,te['Pos'])):te for te in t.get('BlockEntities',[])}
  vals=[];value=shift=0
  for b in t['BlockData']:
   b=int(b)&255;value|=(b&127)<<shift
   if b&128:shift+=7;assert shift<35
   else:vals.append(value);value=shift=0
  assert len(vals)==math.prod(size)
  for idx,v in enumerate(vals):
   pos=(idx%size[0],idx//(size[0]*size[2]),(idx//size[0])%size[2]);cells[pos]=(pal[v],tes.get(pos))
  for en in t.get('Entities',[]):entities.append((copy.deepcopy(en),list(map(float,en['Pos']))))
  author='FoxgloveBuilds'
 return cells,entities,author

def item(it):
 d=copy.deepcopy(it)
 if 'count' in d:d['Count']=n.Byte(min(127,int(d.pop('count'))))
 comp=d.pop('components',{});tag=d.get('tag',n.Compound())
 if 'minecraft:custom_name' in comp:tag['display']=n.Compound({'Name':comp['minecraft:custom_name']})
 if 'minecraft:damage' in comp:tag['Damage']=n.Int(int(comp['minecraft:damage']))
 if tag:d['tag']=tag
 return d

def metadata(te,st):
 if te is None:return None
 d=copy.deepcopy(te)
 for k in ['x','y','z','Pos','Id','components']:d.pop(k,None)
 # Modern shelves are regular bookshelves here and have no block entity.
 name=str(st['Name']);kind=next((a for a in ['chest','barrel','furnace','smoker','blast_furnace','campfire','sign','banner','skull','decorated_pot','spawner','beehive','shulker_box','hopper','dispenser','dropper','brewing_stand','chiseled_bookshelf'] if a in name),None)
 if kind is None:return None
 if 'Items' in d:d['Items']=n.List[n.Compound]([item(x) for x in d['Items']])
 if 'sign' in name:
  for side in ['front_text','back_text']:
   if side in d:d[side]['messages']=n.List[n.String]([n.String(str(x) if str(x) else '""') for x in d[side].get('messages',[])])
 # Old 1.19 signs migrate their four text fields to the 1.20 front side.
  if 'Text1' in d:d['front_text']=n.Compound({'messages':n.List[n.String]([d.pop('Text'+str(i),n.String('""')) for i in range(1,5)]),'color':d.pop('Color',n.String('black')),'has_glowing_text':d.pop('GlowingText',n.Byte(0))})
 if 'sign' in name:kind='hanging_sign' if 'hanging' in name else 'sign'
 elif 'banner' in name:kind='banner'
 elif 'skull' in name or 'head' in name:kind='skull'
 elif 'shulker_box' in name:kind='shulker_box'
 elif 'campfire' in name:kind='campfire'
 elif 'chest' in name:kind='chest'
 d['id']=n.String('minecraft:'+kind)
 return d

entries=[];report=[];outdir=pathlib.Path('extra-previews');outdir.mkdir(exist_ok=True)
with zipfile.ZipFile('bwe_extra.jar','w',zipfile.ZIP_DEFLATED) as z:
 for p in sorted(pathlib.Path('schem-input').iterdir()):
  cells,entities,author=read(p);solids={q:state(s) for q,(s,te) in cells.items() if str(s['Name']) not in ['minecraft:air','minecraft:cave_air','minecraft:void_air','minecraft:structure_void']}
  low=tuple(min(q[i] for q in solids) for i in range(3));high=tuple(max(q[i] for q in solids) for i in range(3));size=tuple(high[i]-low[i]+1 for i in range(3))
  # Surface is the mode of exposed ground/lowest walkable floors near the bottom.
  terrain={'minecraft:grass_block','minecraft:dirt','minecraft:coarse_dirt','minecraft:podzol','minecraft:mycelium','minecraft:sand','minecraft:red_sand','minecraft:dirt_path','minecraft:rooted_dirt','minecraft:moss_block'}
  ground=collections.Counter()
  for (x,y,zz),s in solids.items():
   if y-low[1]>max(6,size[1]//4):continue
   name=str(s['Name']);above=solids.get((x,y+1,zz));an=str(above['Name']) if above else 'minecraft:air'
   if name in terrain and (an=='minecraft:air' or an.endswith('_grass') or an in ['minecraft:grass','minecraft:fern','minecraft:snow']):ground[y-low[1]]+=1
  if not ground:
   for (x,y,zz),s in solids.items():
    if y-low[1]<=3 and (x,y+1,zz) not in solids and not str(s['Name']).endswith(('_leaves','_log','_fence','_wall')):ground[y-low[1]]+=1
  floor=ground.most_common(1)[0][0] if ground else 0
  palette=[];lookup={};blocks=[]
  for y in range(low[1],high[1]+1):
   for zz in range(low[2],high[2]+1):
    for x in range(low[0],high[0]+1):
     source,te=cells.get((x,y,zz),(n.Compound({'Name':n.String('minecraft:air')}),None));s=state(source);key=str(s)
     if key not in lookup:lookup[key]=len(palette);palette.append(s)
     b=n.Compound({'pos':n.List[n.Int]([n.Int(x-low[0]),n.Int(y-low[1]),n.Int(zz-low[2])]),'state':n.Int(lookup[key])});data=metadata(te,s)
     if data:b['nbt']=data
     blocks.append(b)
  ens=[];skipped=0
  for e,ep in entities:
   eid=str(e.get('id',''));pos=[ep[i]-low[i] for i in range(3)]
   if eid not in ['minecraft:armor_stand','minecraft:item_frame','minecraft:glow_item_frame','minecraft:painting','minecraft:boat','minecraft:chest_boat','minecraft:cat','minecraft:villager'] or any(v<0 or v>=size[i] for i,v in enumerate(pos)):
    skipped+=1;continue
   for k in ['UUID','UUIDMost','UUIDLeast','home_pos','home_radius','BalmData','attributes','equipment','drop_chances']: 
    if k=='equipment' and k in e:
     eq=e[k];e['ArmorItems']=n.List[n.Compound]([item(eq.get(k,n.Compound())) for k in ['feet','legs','chest','head']]);e['HandItems']=n.List[n.Compound]([item(eq.get(k,n.Compound())) for k in ['mainhand','offhand']])
    e.pop(k,None)
   if 'Item' in e:e['Item']=item(e['Item'])
   if eid in ['minecraft:painting','minecraft:item_frame','minecraft:glow_item_frame']:
    # Vanilla movement won't relocate stale world anchors saved by some exporters.
    for a,v in zip('XYZ',pos):e['Tile'+a]=n.Int(math.floor(v))
   e['Pos']=n.List[n.Double]([n.Double(v) for v in pos]);ens.append(n.Compound({'pos':e['Pos'],'blockPos':n.List[n.Int]([n.Int(math.floor(v)) for v in pos]),'nbt':e}))
  template=n.File({'DataVersion':n.Int(3465),'size':n.List[n.Int]([n.Int(v) for v in size]),'palette':n.List[n.Compound](palette),'blocks':n.List[n.Compound](blocks),'entities':n.List[n.Compound](ens)})
  slug=p.stem.lower().replace(' - ','_').replace(' ','_').replace('-','_');id='bwe_extra:'+slug
  data=io.BytesIO();template.write(data);z.writestr('data/bwe_extra/structures/'+slug+'.nbt',gzip.compress(data.getvalue()))
  entries.append({'id':id,'title':NAMES[p.stem],'description':f'Autor: {author} · Adaptado a 1.20.1','sizeX':size[0],'sizeY':size[1],'sizeZ':size[2],'ground':floor+1,'pieces':[{'template':id,'x':0,'y':0,'z':0,'rotation':0}]})
  # Preview in the same native top-down format as the existing catalogue.
  im=Image.new('RGB',(size[0]*5,size[2]*5),(29,39,39));d=ImageDraw.Draw(im);top={}
  for q,s in solids.items():
   x,y,zz=q;key=(x,zz)
   if key not in top or y>top[key][0]:top[key]=(y,str(s['Name']))
  for (x,zz),(y,name) in top.items():
   color=(125,124,115)
   for word,c in [('water',(50,118,173)),('glass',(143,194,187)),('prismarine',(75,151,143)),('copper',(181,119,72)),('sand',(212,197,140)),('quartz',(222,218,203)),('snow',(225,235,236)),('leaves',(65,117,58)),('moss',(90,127,57)),('grass',(90,127,57)),('oak',(143,111,72)),('spruce',(95,73,49)),('dark_oak',(79,57,39)),('brick',(143,105,91))]:
    if word in name:color=c;break
   k=.78+.22*(y-low[1])/max(1,size[1]);color=tuple(int(v*k) for v in color);xx=(x-low[0])*5;zz=(zz-low[2])*5;d.rectangle((xx,zz,xx+4,zz+4),fill=color)
  im.thumbnail((244,162));canvas=Image.new('RGB',(256,174),(29,39,39));canvas.paste(im,((256-im.width)//2,(174-im.height)//2));canvas.save(outdir/(id.replace(':','_').replace('/','_')+'.png'))
  info={'name':NAMES[p.stem],'author':author,'size':size,'floor':floor,'blocks':len(solids),'volume':len(blocks),'entities':len(ens),'skipped_entities':skipped};report.append(info);print(info,flush=True)
pathlib.Path('catalogo-extra-v1.json').write_text(json.dumps({'entries':entries},ensure_ascii=False,indent=2))
pathlib.Path('extra-report.json').write_text(json.dumps({'entries':report,'replacements':REPLACE},ensure_ascii=False,indent=2))
