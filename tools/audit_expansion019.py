import zipfile,gzip,io,json,nbtlib as n,collections,pathlib
root=pathlib.Path('expansion');catalog=json.loads((root/'catalogo-ampliacion-v1.json').read_text())['entries'];specs={};counts=collections.Counter()
for ns in ['bwe_tt','bwe_itp','bwe_idas']:
 z=zipfile.ZipFile(root/(ns+'.jar'));assert z.testzip() is None
 for file in z.namelist():
  assert file.startswith('data/'+ns+'/structures/') and file.endswith('.nbt'),file
  t=n.File.parse(io.BytesIO(gzip.decompress(z.read(file))));size=tuple(map(int,t['size']));assert not t.get('entities'),file
  palette=t['palette'];names=[str(s['Name']) for s in palette];assert all(name.startswith('minecraft:') for name in names)
  assert not any(any(w in name for w in ['spawner','command_block','jigsaw','sign','banner','skull','head']) for name in names),file
  seen=set()
  for b in t['blocks']:
   pos=tuple(map(int,b['pos']));assert all(0<=pos[i]<size[i] for i in range(3)),(file,pos,size);assert pos not in seen;seen.add(pos)
   state=int(b['state']);assert 0<=state<len(palette)
   if 'nbt' in b:
    assert names[state] in ['minecraft:chest','minecraft:trapped_chest','minecraft:barrel'],(file,names[state])
    assert 'LootTable' in b['nbt'] and 'Items' not in b['nbt'];counts['loot_containers']+=1
  specs[ns+':'+file.split('/structures/')[1][:-4]]=(size,len(t['blocks']));counts[ns]+=1
 for e in catalog:
  if not e['id'].startswith(ns+':'):continue
  total=0
  for p in e['pieces']:
   size,cost=specs[p['template']];total+=cost
   for x in [0,size[0]-1]:
    for z1 in [0,size[2]-1]:
     a,b=[(x,z1),(-z1,x),(-x,-z1),(z1,-x)][p['rotation']%4];a+=p['x'];b+=p['z']
     assert 0<=a<e['sizeX'] and 0<=b<e['sizeZ'],(e['id'],p,a,b)
   assert 0<=p['y'] and p['y']+size[1]<=e['sizeY']
  assert total<=1000000,(e['id'],total)
 print('PASS',ns,counts[ns],'templates',flush=True)
print('PASS all',len(catalog),'layouts; no entities, no custom blocks, no scripted/decorative NBT; lazy loot containers',counts['loot_containers'])
