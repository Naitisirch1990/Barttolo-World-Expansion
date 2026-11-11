"""Spanish display names and thematic hostile flags; IDs/templates remain stable."""
import json,zipfile,pathlib
root=pathlib.Path('expansion');cat=json.loads((root/'catalogo-ampliacion-v1.json').read_text());report=json.loads((root/'report.json').read_text())
NAMES={
'abandonedhouse':'Casa abandonada','abandoned_lighthouse':'Faro abandonado','abandoned_vineyard':'Viñedo abandonado','ancient_mines':'Minas antiguas','ancient_portal':'Portal antiguo','nether_ancient_portal':'Portal antiguo del Nether','ancient_statue':'Estatua antigua','forest_den':'Guarida del bosque','foxhound_den':'Guarida de zorros','polar_bear_den':'Guarida de osos polares','apothecary_abode':'Casa del boticario','archmages_tower':'Torre del archimago','bazaar':'Bazar','bearclaw_inn':'Posada Garra de Oso','beekeepers_house':'Casa del apicultor','botanist':'Casa del botanico','brickhouse':'Casa de ladrillos','castle':'Castillo','collectors_museum':'Museo del coleccionista','cottage':'Casa de campo','desert_camp':'Campamento del desierto','desert_market':'Mercado del desierto','desert_pyramid':'Piramide del desierto','desert_ruins':'Ruinas del desierto','dig_site':'Excavacion','enchantingtower':'Torre de encantamientos','farmhouse':'Granja','fishermans_lodge':'Refugio del pescador','frozen_crypt':'Cripta helada','haunted_manor':'Mansion encantada','hermits_hollow':'Refugio del ermitaño','hunters_cabin':'Cabaña del cazador','dread_citadel':'Ciudadela del terror','sirens_cove':'Cala de las sirenas','labyrinth':'Laberinto','lumber_camp':'Campamento de leñadores','mason_house':'Casa del cantero','necromancers_spire':'Torre del nigromante','nether_pump_camp':'Campamento de bombas del Nether','nexus':'Nexo','pillager_camp':'Campamento de saqueadores','pillager_fortress':'Fortaleza de saqueadores','redhorn_guild':'Gremio Cuerno Rojo','ruined_church':'Iglesia en ruinas','ruined_fort':'Fuerte en ruinas','ruined_well':'Pozo en ruinas','ruins_of_the_deep':'Ruinas de las profundidades','snifferhenge':'Monumento del sniffer','sunken_ship':'Barco hundido','the_log':'El tronco','tinkers_citadel':'Ciudadela del artesano','tinkers_workshop':'Taller del artesano','train_ruins':'Tren en ruinas','treetop_tavern':'Taberna entre arboles','tree_of_wisdom':'Arbol de la sabiduria','tudor_pub':'Taberna Tudor','underground_camp':'Campamento subterraneo','wacky_wares':'Tienda de curiosidades','washing_camp':'Campamento de lavado','windswept_shrine':'Santuario del viento','winter_wagon':'Carromato invernal','witches_treestump':'Tocon de las brujas','wizard_tower':'Torre del mago'}
VAR={'oak':'Roble','dark_oak':'Roble oscuro','spruce':'Abeto','birch':'Abedules','jungle':'Selva','acacia':'Acacia','bopmahogany':'Caoba BOP','bygmahogany':'Caoba BYG','bopredwood':'Secuoya BOP','bygredwood':'Secuoya BYG','bygwindswept':'Arena barrida','desert':'Desierto','plains':'Pradera','red':'Rojo','orange':'Naranja','deep':'Profundo','coral':'Coral','ruins':'Ruinas'}
BIO={'badlands':'Tierras aridas','beach':'Playa','birch_forest':'Bosque de abedules','desert':'Desierto','flower_forest':'Bosque de flores','forest':'Bosque','grove':'Arboleda','jungle':'Selva','meadow':'Pradera alpina','mushroom_fields':'Isla de hongos','ocean':'Oceano','old_growth_taiga':'Taiga antigua','savanna':'Sabana','savanna_plateau':'Meseta de sabana','snowy_beach':'Playa nevada','snowy_plains':'Llanura nevada','snowy_slopes':'Laderas nevadas','sparse_jungle':'Selva dispersa','sunflower_plains':'Pradera de girasoles','swamp':'Pantano','taiga':'Taiga','snowy_taiga':'Taiga nevada','wooded_badlands':'Tierras aridas boscosas','classic':'Clasica','iberian':'Iberica','mediterranean':'Mediterranea','nilotic':'Nilotica','rustic':'Rustica','swedish':'Sueca','tudor':'Tudor','wandering_trader_camp':'Campamento del comerciante','piglin':'Piglin','oriental':'Oriental'}
lookup={}
for source,ns in [('idas-original.jar','bwe_idas'),('upload/Towns-and-Towers-1.12-Fabric+Forge.jar','bwe_tt')]:
 z=zipfile.ZipFile(source)
 for name in z.namelist():
  if '/worldgen/structure/' not in name or '/tags/' in name or not name.endswith('.json'):continue
  slug=name.split('/worldgen/structure/')[1][:-5];lookup[ns+':'+slug.replace('/','_')]=slug
for e in cat['entries']:
 id=e['id'];slug=lookup.get(id,'');last=slug.split('/')[-1]
 if id.startswith('bwe_idas:'):
  name=NAMES.get(last)
  if name is None:
   for key in sorted(NAMES,key=len,reverse=True):
    if last.startswith(key+'_'):
     variant=last[len(key)+1:];name=NAMES[key]+' — '+VAR.get(variant,variant.replace('_',' ').title());break
  e['title']='IDAS — '+(name or last.replace('_',' ').title())
  if 'necromancer' in slug or 'sirens_cove' in slug or 'archmages_tower' in slug:
   if 'Hostil' not in e['description']:e['description']='Hostil · '+e['description']
 elif id.startswith('bwe_tt:'):
  if last.startswith('pillager_outpost_'):kind='Puesto de saqueadores';biome=last[len('pillager_outpost_'):]
  elif last.startswith('village_'):kind='Aldea';biome=last[len('village_'):]
  else:kind='Restos de naufragio';biome='ocean'
  e['title']='TT — '+kind+' — '+BIO.get(biome,biome.replace('_',' ').title())
 elif id=='bwe_itp:bastille':e['title']='ITP — Bastilla'
 elif id=='bwe_itp:pillager_camp':e['title']='ITP — Campamento de saqueadores'
for r in report['entries']:
 e=next(e for e in cat['entries'] if e['id']==r['id']);r['title']=e['title'];r['hostile']='Hostil' in e['description']
(root/'catalogo-ampliacion-v1.json').write_text(json.dumps(cat,ensure_ascii=False,indent=2));(root/'report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
v=json.loads((root/'validation.json').read_text());v['entries']=report['entries'];(root/'validation.json').write_text(json.dumps(v,ensure_ascii=False,separators=(',',':')))
print('Named',len(cat['entries']),'structures')
