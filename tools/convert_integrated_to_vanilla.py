import struct,io,gzip,zipfile,json,collections,re,os
from pathlib import Path
class NBT:
 def __init__(self,data):self.f=io.BytesIO(data)
 def num(self,fmt):return struct.unpack('>'+fmt,self.f.read(struct.calcsize('>'+fmt)))[0]
 def string(self):return self.f.read(self.num('H')).decode('utf-8')
 def val(self,t):
  if t in [1,2,3,4,5,6]:return self.num({1:'b',2:'h',3:'i',4:'q',5:'f',6:'d'}[t])
  if t==8:return self.string()
  if t in [7,11,12]:
   n=self.num('i');return (n,self.f.read(n*{7:1,11:4,12:8}[t]))
  if t==9:
   st=self.num('b');return (st,[self.val(st) for _ in range(self.num('i'))])
  if t==10:
   d={}
   while True:
    ty=self.num('b')
    if ty==0:return d
    name=self.string();d[name]=(ty,self.val(ty))
  raise ValueError(t)
 def read(self):assert self.num('b')==10;name=self.string();return name,self.val(10)
def encstr(s):b=s.encode('utf-8');return struct.pack('>H',len(b))+b
def enc(t,v):
 if t in [1,2,3,4,5,6]:return struct.pack('>'+{1:'b',2:'h',3:'i',4:'q',5:'f',6:'d'}[t],v)
 if t==8:return encstr(v)
 if t in [7,11,12]:return struct.pack('>i',v[0])+v[1]
 if t==9:return struct.pack('>bi',v[0],len(v[1]))+b''.join(enc(v[0],a) for a in v[1])
 if t==10:return b''.join(bytes([ty])+encstr(k)+enc(ty,x) for k,(ty,x) in v.items())+b'\0'
def sval(d,k,default=''):return d.get(k,(8,default))[1]
BLOCKS={ 'minecraft:'+b['name']:b for b in json.load(open('vanilla-blocks.json'))};ITEMS={'minecraft:'+x['name'] for x in json.load(open('vanilla-items.json'))}
BASE=zipfile.ZipFile('BarttoloWorldExpansion-0.10.0-INTEGRATED-COMPACTO-test.jar');SRC=zipfile.ZipFile(io.BytesIO(BASE.read('assets/barttoloworldexpansion/integrated_villages.jar')))
if __name__=='__main__' and '--inventory' in __import__('sys').argv:
 ids=set()
 for n in SRC.namelist():
  _,d=NBT(gzip.decompress(SRC.read(n))).read()
  ids.update(sval(p,'Name') for p in d['palette'][1][1] if not sval(p,'Name').startswith('minecraft:'))
 print('\n'.join(sorted(ids)))
COUNTS=collections.Counter();MAPPING={}
EXACT={
 'create:andesite_casing':'minecraft:polished_andesite','create:brass_casing':'minecraft:cut_copper','create:copper_casing':'minecraft:cut_copper','create:railway_casing':'minecraft:polished_blackstone',
 'create:shaft':'minecraft:iron_bars','create:cogwheel':'minecraft:oak_trapdoor','create:large_cogwheel':'minecraft:oak_trapdoor','create:gearbox':'minecraft:polished_andesite','create:vertical_gearbox':'minecraft:polished_andesite',
 'create:water_wheel':'minecraft:oak_trapdoor','create:large_water_wheel':'minecraft:oak_trapdoor','create:mechanical_press':'minecraft:anvil','create:mechanical_mixer':'minecraft:cauldron','create:blaze_burner':'minecraft:campfire',
 'create:depot':'minecraft:smooth_stone_slab','create:basin':'minecraft:cauldron','create:fluid_tank':'minecraft:glass','create:fluid_pipe':'minecraft:iron_bars','create:chute':'minecraft:hopper','create:encased_fan':'minecraft:iron_trapdoor',
 'create:mechanical_bearing':'minecraft:polished_andesite','create:windmill_bearing':'minecraft:polished_andesite','create:millstone':'minecraft:grindstone','create:schematicannon':'minecraft:dispenser','create:rope_pulley':'minecraft:chain','create:belt':'minecraft:black_concrete',
 'supplementaries:rope':'minecraft:chain','supplementaries:rope_knot':'minecraft:chain','supplementaries:jar':'minecraft:glass','supplementaries:urn':'minecraft:decorated_pot','supplementaries:globe':'minecraft:lantern','supplementaries:clock_block':'minecraft:chiseled_stone_bricks','supplementaries:speaker_block':'minecraft:note_block',
 'waystones:waystone':'minecraft:chiseled_stone_bricks','waystones:mossy_waystone':'minecraft:mossy_stone_bricks','waystones:sandy_waystone':'minecraft:chiseled_sandstone',
 'farmersdelight:stove':'minecraft:smoker','farmersdelight:cutting_board':'minecraft:oak_pressure_plate','farmersdelight:cooking_pot':'minecraft:cauldron','farmersdelight:skillet':'minecraft:cauldron','farmersdelight:basket':'minecraft:composter',
}

EXACT.update({
 'structure_gel:blue_gel':'minecraft:structure_void',
 'create:water_wheel_structure':'minecraft:air',
 'create:mechanical_saw':'minecraft:stonecutter','create:mechanical_drill':'minecraft:dispenser','create:mechanical_pump':'minecraft:polished_andesite','create:mechanical_arm':'minecraft:iron_bars',
 'create:lit_blaze_burner':'minecraft:campfire','create:analog_lever':'minecraft:lever','create:display_board':'minecraft:dark_oak_planks','create:nixie_tube':'minecraft:lantern','create:redstone_link':'minecraft:lightning_rod','create:pulse_repeater':'minecraft:repeater','create:industrial_iron_block':'minecraft:iron_block','create:zinc_block':'minecraft:iron_block','create:item_vault':'minecraft:barrel','create:hand_crank':'minecraft:lever','create:copper_valve_handle':'minecraft:lever','create:gray_valve_handle':'minecraft:lever','create:clipboard':'minecraft:oak_sign','create:placard':'minecraft:oak_sign','create:metal_girder':'minecraft:iron_bars','create:flywheel':'minecraft:iron_bars',
 'supplementaries:daub':'minecraft:white_terracotta','supplementaries:daub_frame':'minecraft:white_terracotta','supplementaries:daub_brace':'minecraft:white_terracotta','supplementaries:daub_cross_brace':'minecraft:white_terracotta',
 'supplementaries:timber_frame':'minecraft:stripped_spruce_wood','supplementaries:timber_brace':'minecraft:stripped_spruce_wood','supplementaries:timber_cross_brace':'minecraft:stripped_spruce_wood',
 'supplementaries:awning':'minecraft:white_wool','supplementaries:awning_pink':'minecraft:pink_wool','supplementaries:wind_vane':'minecraft:lightning_rod','supplementaries:fire_pit':'minecraft:campfire','supplementaries:flower_box':'minecraft:flower_pot','supplementaries:doormat':'minecraft:brown_carpet','supplementaries:fodder':'minecraft:hay_block','supplementaries:flax_block':'minecraft:hay_block','supplementaries:sack':'minecraft:barrel','supplementaries:safe':'minecraft:barrel','supplementaries:present':'minecraft:chest','supplementaries:stone_tile':'minecraft:stone_bricks','supplementaries:statue':'minecraft:chiseled_stone_bricks','supplementaries:pedestal':'minecraft:quartz_pillar','supplementaries:soap_block':'minecraft:white_concrete','supplementaries:sugar_cube':'minecraft:white_concrete','supplementaries:sconce':'minecraft:torch','supplementaries:sconce_wall':'minecraft:wall_torch','supplementaries:iron_gate':'minecraft:iron_bars','supplementaries:cage':'minecraft:iron_bars','supplementaries:checker_block':'minecraft:chiseled_stone_bricks',
 'quark:paper_wall':'minecraft:white_stained_glass_pane','quark:paper_wall_big':'minecraft:white_stained_glass_pane','quark:paper_wall_sakura':'minecraft:pink_stained_glass_pane','quark:midori_block':'minecraft:green_terracotta','quark:feeding_trough':'minecraft:composter','quark:blackstone_furnace':'minecraft:furnace','quark:charcoal_block':'minecraft:coal_block','quark:bonded_leather':'minecraft:brown_wool','quark:cactus_block':'minecraft:green_terracotta','quark:sugar_cane_block':'minecraft:hay_block','quark:gunpowder_sack':'minecraft:barrel','quark:glowberry_sack':'minecraft:barrel','quark:berry_sack':'minecraft:barrel',
 'amendments:wall_lantern':'minecraft:lantern','farmersdelight:white_canvas_wall_sign':'minecraft:oak_wall_sign','bakery:wall_display':'minecraft:oak_slab','bakery:breadbox':'minecraft:barrel','furnish:picture_frame':'minecraft:oak_trapdoor','sawmill:sawmill':'minecraft:stonecutter','monobank:monobank':'minecraft:barrel',
 'biomesoplenty:waterlily':'minecraft:lily_pad','biomesoplenty:cattail':'minecraft:fern','regions_unexplored:cattail':'minecraft:fern','biomesoplenty:willow_vine':'minecraft:vine','paraglider:kakariko_goddess_statue':'minecraft:chiseled_stone_bricks','twilightforest:cinder_furnace':'minecraft:furnace',
})
# Material-aware equivalents for construction families, including slabs and stairs.
for source_id in Path('foreign-blocks.txt').read_text().splitlines():
 if source_id in EXACT:continue
 n=source_id.split(':')[1]
 material=next((v for k,v in [('waxed_oxidized','waxed_oxidized_cut_copper'),('waxed_copper','waxed_cut_copper'),('limestone','sandstone'),('ochrum','sandstone'),('calcite','quartz'),('dripstone','sandstone'),('granite','granite'),('diorite','diorite'),('andesite','andesite'),('tuff','stone_brick'),('scoria','red_sandstone'),('crimsite','red_sandstone'),('veridium','prismarine'),('deepslate','deepslate_brick'),('dirt_brick','mud_brick'),('mud_brick','mud_brick'),('ash_brick','stone_brick'),('thatch','oak'),('shingles','brick')] if k in n),None)
 if 'window' in n:EXACT[source_id]='minecraft:glass_pane' if 'pane' in n else 'minecraft:glass'
 elif 'hedge' in n or 'shrub' in n:EXACT[source_id]='minecraft:flowering_azalea_leaves' if 'flower' in n else 'minecraft:oak_leaves'
 elif n.startswith('potted_'):EXACT[source_id]='minecraft:potted_fern'
 elif 'furnace' in n:EXACT[source_id]='minecraft:furnace'
 elif 'toolbox' in n or 'present' in n:EXACT[source_id]='minecraft:barrel'
 elif material:
  suffix=next((x for x in ['stairs','slab','wall'] if x in n),None)
  if suffix:
   target='minecraft:'+material+'_'+suffix
   if target not in BLOCKS:target='minecraft:stone_brick_'+suffix
  else:
   target='minecraft:'+{'quartz':'quartz_block','stone_brick':'stone_bricks','deepslate_brick':'deepslate_bricks','mud_brick':'mud_bricks','brick':'bricks','oak':'hay_block'}.get(material,material)
   if target not in BLOCKS:target='minecraft:stone_bricks'
  EXACT[source_id]=target

EXACT.update({
 'supplementaries:feather_block':'minecraft:white_wool','supplementaries:goblet':'minecraft:flower_pot','supplementaries:globe_sepia':'minecraft:lantern','supplementaries:faucet':'minecraft:tripwire_hook','supplementaries:flint_block':'minecraft:gravel','supplementaries:ash':'minecraft:gray_carpet','supplementaries:pulley_block':'minecraft:oak_log','supplementaries:crank':'minecraft:lever','supplementaries:notice_board':'minecraft:oak_wall_sign','supplementaries:crystal_display':'minecraft:amethyst_block','supplementaries:lunch_basket':'minecraft:barrel','supplementaries:spring_launcher':'minecraft:piston','supplementaries:spring_launcher_head':'minecraft:piston_head','supplementaries:bamboo_spikes':'minecraft:bamboo','supplementaries:raked_gravel':'minecraft:gravel','supplementaries:gunpowder':'minecraft:black_carpet',
 'create:copycat_panel':'minecraft:smooth_stone_slab','create:copycat_step':'minecraft:stone_stairs','create:steam_whistle':'minecraft:lightning_rod','create:steam_whistle_extension':'minecraft:lightning_rod','create:speedometer':'minecraft:note_block','create:stressometer':'minecraft:note_block','create:cuckoo_clock':'minecraft:note_block','create:mysterious_cuckoo_clock':'minecraft:note_block','create:piston_extension_pole':'minecraft:end_rod','create:weighted_ejector':'minecraft:slime_block','create:pulley_magnet':'minecraft:iron_block','create:mechanical_piston_head':'minecraft:piston_head','create:sticky_mechanical_piston':'minecraft:sticky_piston','create:metal_girder_encased_shaft':'minecraft:iron_bars','create:spout':'minecraft:hopper','create:fluid_valve':'minecraft:iron_bars','create:experience_block':'minecraft:emerald_block','create:honey':'minecraft:honey_block','create:linear_chassis':'minecraft:oak_log','create:secondary_linear_chassis':'minecraft:oak_log','create:radial_chassis':'minecraft:oak_log',
 'farmersdelight:red_mushroom_colony':'minecraft:red_mushroom','farmersdelight:wild_carrots':'minecraft:carrots','farmersdelight:straw_bale':'minecraft:hay_block','farmersdelight:roast_chicken_block':'minecraft:cake','farmersdelight:honey_glazed_ham_block':'minecraft:cake','farmersdelight:shepherds_pie_block':'minecraft:cake','quark:mud_pillar':'minecraft:packed_mud','quark:iron_rod':'minecraft:iron_bars','quark:grate':'minecraft:iron_trapdoor','quark:glow_lichen_growth':'minecraft:glow_lichen',
 'graveyard:vase_block':'minecraft:decorated_pot','graveyard:oak_coffin':'minecraft:barrel','graveyard:brown_urn':'minecraft:decorated_pot','graveyard:middle_bone_staff':'minecraft:bone_block','iceandfire:pixie_jar_1':'minecraft:lantern','furnish:paper_sheet_filled_ink':'minecraft:white_carpet','furnish:paper_rolls':'minecraft:white_carpet','bakery:tray':'minecraft:iron_trapdoor',
})
WORK={'magenta':'crafting_table','light_blue':'lectern','purple':'brewing_stand','gray':'stonecutter','black':'smithing_table','orange':'cartography_table','light_gray':'smoker','white':'fletching_table','red':'cauldron'}
def choose(name):
 if name in BLOCKS:
  local=name.split(':')[1]
  if local.endswith('_glazed_terracotta') and local[:-18] in WORK:return 'minecraft:'+WORK[local[:-18]]
  return name
 if name in EXACT:return EXACT[name]
 s=name.split(':')[-1];wood=next((w for w in ['dark_oak','spruce','birch','jungle','acacia','mangrove','cherry','bamboo','crimson','warped','oak'] if w in s),'spruce')
 color=next((c for c in ['light_gray','light_blue','black','brown','cyan','gray','green','lime','magenta','orange','pink','purple','red','white','yellow','blue'] if c in s),'white')
 for word,target in [('trapdoor',wood+'_trapdoor'),('door',wood+'_door'),('fence_gate',wood+'_fence_gate'),('fence',wood+'_fence'),('railing',wood+'_fence'),('post',wood+'_fence'),('ladder','ladder'),('stairs',('sandstone' if 'sand' in s else 'stone_brick' if any(w in s for w in ['stone','andesite','tuff','limestone','granite','calcite','diorite']) else 'brick' if 'brick' in s else wood)+'_stairs'),('slab',('sandstone' if 'sand' in s else 'stone_brick' if any(w in s for w in ['stone','andesite','tuff','limestone','granite','calcite','diorite']) else 'brick' if 'brick' in s else wood)+'_slab'),('wall','stone_brick_wall'),('glass_pane','glass_pane'),('window','glass_pane'),('glass','glass'),('leaves',wood+'_leaves'),('sapling',wood+'_sapling'),('stripped', 'stripped_'+wood+'_log'),('log',wood+'_log'),('beam',wood+'_log'),('planks',wood+'_planks'),('wood',wood+'_wood'),('bookshelf','bookshelf'),('book','bookshelf'),('shelf','bookshelf'),('barrel','barrel'),('crate','barrel'),('chest','chest'),('cabinet','barrel'),('drawer','barrel'),('chair',wood+'_stairs'),('stool',wood+'_stairs'),('bench',wood+'_stairs'),('table',wood+'_slab'),('sofa',color+'_wool'),('seat',color+'_wool'),('curtain',color+'_banner'),('carpet',color+'_carpet'),('bed',color+'_bed'),('lantern','lantern'),('lamp','lantern'),('light','lantern'),('torch','torch'),('candle','candle'),('sign',wood+'_sign'),('flag',color+'_banner'),('banner',color+'_banner'),('planter','flower_pot'),('pot','flower_pot'),('flax','wheat'),('tomato','wheat'),('cabbage','carrots'),('rice','wheat'),('onion','potatoes'),('crop','wheat'),('flower','poppy'),('fern','fern'),('moss','moss_block'),('grass','grass_block'),('leaf','oak_leaves'),('chain','chain'),('rope','chain'),('rack','iron_bars'),('pipe','iron_bars'),('chimney','bricks'),('brick','bricks'),('copper','cut_copper'),('brass','cut_copper'),('andesite','polished_andesite'),('granite','polished_granite'),('diorite','polished_diorite'),('sandstone','sandstone'),('limestone','smooth_sandstone'),('dripstone','dripstone_block'),('deepslate','deepslate_bricks'),('obsidian','obsidian'),('stone','stone_bricks'),('sail','white_wool'),('canvas','white_wool'),('cake','cake'),('bread','hay_block'),('food','hay_block'),('water','water'),('rail','rail'),('bell','bell')]:
  if word in s and 'minecraft:'+target in BLOCKS:return 'minecraft:'+target
 return 'minecraft:polished_andesite'
def state(p):
 old=sval(p,'Name');new=choose(old);assert new in BLOCKS,new
 if new!=old:MAPPING[old]=new;COUNTS[old]+=1
 props=p.get('Properties',(10,{}))[1];allowed={a['name']:a for a in BLOCKS[new]['states']};keep={}
 for k,v in props.items():
  if k not in allowed:continue
  info=allowed[k];val=v[1];valid=info.get('values',['true','false'] if info['type']=='bool' else [str(i) for i in range(info['num_values'])])
  if val in valid:keep[k]=v
 out={'Name':(8,new)}
 if keep:out['Properties']=(10,keep)
 return out,new!=old
def clean(d):
 for k,(t,v) in list(d.items()):
  if k.lower().startswith('forge') or k in ['Capabilities','ForgeCaps','ForgeData']:d.pop(k);continue
  if t==8:
   if k=='LootTable' and not v.startswith('minecraft:'):d[k]=(8,'minecraft:chests/village/village_plains_house')
   elif k=='id' and ':' in v and not v.startswith('minecraft:'):d[k]=(8,'minecraft:bread' if 'Count' in d or 'Slot' in d else 'minecraft:pig')
   elif k=='Name' and ':' in v and not v.startswith('minecraft:'):d[k]=(8,choose(v))
  elif t==10:clean(v)
  elif t==9 and v[0]==10:
   items=[a for a in v[1] if k!='Attributes' or sval(a,'Name').startswith('minecraft:')]
   d[k]=(9,(10,items))
   for a in items:clean(a)
def convert():
 dst=io.BytesIO();num=0;changed=0
 with zipfile.ZipFile(dst,'w',zipfile.ZIP_DEFLATED) as out:
  for n in SRC.namelist():
   root,d=NBT(gzip.decompress(SRC.read(n))).read();pal=d['palette'][1][1];replaced=set();newpal=[]
   for i,p in enumerate(pal):
    np,swap=state(p);newpal.append(np)
    if swap:replaced.add(i)
   d['palette']=(9,(10,newpal))
   for b in d['blocks'][1][1]:
    idx=b['state'][1]
    if idx in replaced:b.pop('nbt',None);changed+=1
    elif 'nbt' in b:
     be=b['nbt'][1]
     if sval(newpal[idx],'Name')=='minecraft:jigsaw':
      fs=sval(be,'final_state','minecraft:air');name=fs.split('[')[0]
      if name not in BLOCKS:be['final_state']=(8,choose(name))
     clean(be)
   entities=[]
   for e in d.get('entities',(9,(10,[])))[1][1]:
    ent=e.get('nbt',(10,{}))[1]
    if not sval(ent,'id').startswith('minecraft:'):continue
    clean(ent);entities.append(e)
   d['entities']=(9,(10,entities));d['DataVersion']=(3,3465)
   for p in newpal:assert sval(p,'Name') in BLOCKS
   raw=b'\x0a'+encstr(root)+enc(10,d);out.writestr(n,gzip.compress(raw));num+=1
   if num%50==0:print('converted',num,flush=True)
 open('integrated_vanilla.jar','wb').write(dst.getvalue());json.dump(MAPPING,open('vanilla-replacements.json','w'),indent=2);print('DONE',num,'templates',changed,'blocks',len(MAPPING),'substitutions',flush=True)
if __name__=='__main__' and '--convert' in __import__('sys').argv:convert()
