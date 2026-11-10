import pathlib,json,collections,pickle,hashlib,zipfile
ROOT=pathlib.Path('expansion/cache-v3/bwe_idas');cat=json.loads(zipfile.ZipFile('BarttoloWorldExpansion-0.15.1-ALTURAS-Y-PESCADOR-test.jar').read('assets/barttoloworldexpansion/catalogo-ampliacion-v1.json'))['entries']
SOIL={'minecraft:grass_block','minecraft:dirt','minecraft:coarse_dirt','minecraft:rooted_dirt','minecraft:sand','minecraft:red_sand','minecraft:gravel','minecraft:podzol','minecraft:mycelium','minecraft:moss_block','minecraft:snow_block','minecraft:dirt_path','minecraft:stone','minecraft:deepslate','iceandfire:graveyard_soil'}
def info(piece):
 id=piece['template'].replace('bwe_idas:','idas:');return pickle.loads((ROOT/(hashlib.sha256(id.encode()).hexdigest()+'.profile')).read_bytes())
allrows=[]
for e in cat:
 if not e['id'].startswith('bwe_idas:'):continue
 p=e['pieces'][0];t=info(p);lo=t['lo'];cells={tuple(q[i]-lo[i] for i in range(3)):name for q,name in t['solid']};h=collections.defaultdict(collections.Counter);walk=collections.Counter();terrain=collections.Counter();doors=collections.Counter();allwater=collections.Counter()
 for q,name in cells.items():
  x,y,z=q;g=y+p['y'];h[g][name]+=1
  if name.endswith('_door') or name.endswith('planks_lock'):doors[g]+=1
  if name=='minecraft:water':allwater[g]+=1
  if y>12:continue
  # Exposed tops near root base; ignore posts, plants, leaves, vertical decor and liquids.
  above=cells.get((x,y+1,z),'minecraft:air');free=above in ['minecraft:air','minecraft:grass','minecraft:tall_grass','minecraft:snow','minecraft:fern','minecraft:large_fern'] or above.endswith('_door') or above.endswith('planks_lock')
  if not free:continue
  if name in SOIL:terrain[g]+=1
  if name not in ['minecraft:water','minecraft:lava'] and not any(s in name for s in ['leaves','log','wood','fence','wall','flower','grass','mushroom','chain','bars','vine','candle','button','sign','door','torch','rail','ladder','lantern','coral','azalea','pot','campfire','wheat','carrot','beetroot','potato','crop','cobweb']):walk[g]+=1
 width=t['hi'][0]-lo[0]+1;depth=t['hi'][2]-lo[2]+1;sizeY=t['hi'][1]-lo[1]+1
 row=dict(id=e['id'],title=e['title'],old=e['ground']-1,mode=e.get('placement'),rootY=p['y'],rootSize=[width,sizeY,depth],solidMin=min(h),baseLayers=[(y,sum(h[y].values()),h[y].most_common(3)) for y in sorted(h)[:8]],walk=walk.most_common(5),terrain=terrain.most_common(4),doors=doors.most_common(6),water=allwater.most_common(3))
 allrows.append(row)
 print(f"{e['id'].split(':')[1]:45} old={row['old']:3} root={p['y']:3} H={sizeY:3} walk={row['walk'][:3]} soil={row['terrain'][:2]} doors={row['doors'][:3]} water={row['water'][:1]}",flush=True)
pathlib.Path('height-audit021.json').write_text(json.dumps(allrows,ensure_ascii=False,indent=2))
