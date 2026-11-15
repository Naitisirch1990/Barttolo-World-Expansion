"""Curated placement references from all 83 IDAS root profiles, floors and source dimensions."""
import json,pathlib
rows=json.load(open('height-audit021.json'));overrides={
 'ancient_portal_ancient_portal':(0,'ground','Base del monumento; el conector superior Y=34 no es suelo.'),
 'ancient_portal_nether_ancient_portal':(0,'ground','Base del monumento; el conector superior Y=34 no es suelo.'),
 'ancient_statue_ancient_statue_desert':(0,'ground','Pedestal inferior expuesto; conservar los escalones del monumento.'),
 'apothecary_abode':(0,'ground','Jardin exterior en Y=0; evitar taparlo con un bloque adicional de terreno.'),
 'botanist':(1,'ground','Suelo de tierra/cesped Y=1; la capa Y=0 es relleno inferior.'),
 'haunted_manor':(0,'ground','Base exterior de piedra y cesped Y=0; Y=46 es un conector de cubierta.'),
 'hermits_hollow':(1,'ground','Pavimento de musgo y acceso en Y=1; puerta inferior en Y=2.'),
 'necromancers_spire':(0,'air','Aguja suspendida: punta inferior Y=0, cuerpo por encima; no cimentar con tierra.'),
 'nexus':(0,'ground','Cuenca y base completas visibles; no usar la plataforma interior Y=5 como terreno exterior.'),
 'pillager_fortress':(3,'ground','Pavimento principal Y=3; cimentacion maciza Y=0..2 y conector de techo Y=47.'),
 'tinkers_workshop':(95,'ground','Entrada exterior y pavimento de la pieza inicial en Y=95; conservar dependencias inferiores.'),
 'tree_of_wisdom':(0,'ground','Base y raices completas en Y=0; Y=5 corresponde al interior del arbol.'),
 'windswept_shrine':(9,'ground','Base rocosa expuesta en Y=9; el relieve del santuario asciende sobre ella.'),
}
subterranean={'ancient_mines','castle','desert_pyramid','desert_ruins','dig_site_dig_site','dig_site_dig_site_desert','frozen_crypt','labyrinth','ruined_well','ruins_of_the_deep','tinkers_workshop','tudor_pub','wizard_tower','abandoned_vineyard','snifferhenge','windswept_shrine'}
result=[];changes=[]
for r in rows:
 slug=r['id'].split(':')[1];y,mode,reason=overrides.get(slug,(r['old'],r['mode'],'Referencia baja contrastada con pavimento, terreno exterior o acceso de la pieza inicial.'))
 if slug=='bazaar':reason='Pavimento Y=5; conservar la correccion previa del conector superior Y=30.'
 if slug=='iceandfire_dread_citadel':reason='Base completa Y=0; conservar expuestos los accesos inferiores de madera.'
 if slug=='fishermans_lodge':reason='Agua Y=17, cubierta Y=18 y puertas Y=19; sin tierra entre pilares.'
 if slug in ['sunken_ship_sunken_ship','sunken_ship_sunken_ship_coral','sunken_ship_sunken_ship_ruins','iceandfire_sirens_cove']:reason='Nivel de agua contrastado con el esquema; conservar contorno acuatico.'
 assert 0<=y<max(r['old']+1,r['rootY']+r['rootSize'][1])
 item=dict(id=r['id'],surfaceY=y,placement=mode,previousSurfaceY=r['old'],rootY=r['rootY'],rootSize=r['rootSize'],subterranean=slug in subterranean,reason=reason)
 result.append(item)
 if (y,mode)!=(r['old'],r['mode']):changes.append(dict(id=r['id'],before=r['old'],after=y,placement=mode))
pathlib.Path('idas-placement-v1.json').write_text(json.dumps({'schema':1,'entries':result},ensure_ascii=False,indent=2))
pathlib.Path('height-changes021.json').write_text(json.dumps(changes,ensure_ascii=False,indent=2))
print('Reviewed',len(result),'changed',len(changes));print(json.dumps(changes,ensure_ascii=False,indent=2))
