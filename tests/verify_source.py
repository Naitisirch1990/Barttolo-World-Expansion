"""Auditoria estática; NO reemplaza la compilación Forge ni tests dentro del juego."""
from pathlib import Path
r=Path(__file__).resolve().parents[1]
p=r/'src/main/java/net/barttolo/bwe'
s={x.stem:x.read_text() for x in p.glob('*.java')}
assert set(s)=={'BarttoloWorldExpansion','BuildEngine','WorldChecks','WorldData','SiteMath','TemplateFootprint','TemplateScanCursor','TemplateSiteAudit','SurfaceProfile','TemplateSurfaceSurvey','TerrainColumnProbe','ClearanceVolume','TerraformColumnCursor','TerraformAreaCursor','TerraformStepGuard','TerraformBackupFile','TemplatePlacementVolume'}, s.keys()
assert 'StandardCopyOption.ATOMIC_MOVE' in s['TerraformBackupFile'], 'Publicar respaldo mediante movimiento atomico'
assert 'MessageDigest.isEqual' in s['TerraformBackupFile'], 'Validar integridad de respaldo'
assert 'Terraform backup already exists' in s['TerraformBackupFile'], 'Evitar reemplazo deliberado del respaldo'
assert 'withinWorld' in s['TemplatePlacementVolume'] and 'clearance' in s['TemplatePlacementVolume'], 'Validar limites y volumen antes de colocar esquemas'\nm=s['BarttoloWorldExpansion']; e=s['BuildEngine']; c=s['WorldChecks']
assert 'planColumnFromSurface' in s['ClearanceVolume'], 'Convertir primer bloque libre en ultimo solido'
assert 'public int execute(int budget, StepWriter writer)' in s['TerraformAreaCursor'], 'Presupuesto por lote para un area completa'
assert 'public static TerraformAreaCursor resume(' in s['TerraformAreaCursor'], 'Cursor de area con punto de reanudacion'
assert 'world.requireSafe(step)' in s['TerraformStepGuard'], 'Revalidacion de seguridad antes de escribir'
assert 'Missing durable BEFORE state' in s['TerraformStepGuard'], 'No escribir sin respaldo de estado original'
assert 'Terraform post-write verification failed' in s['TerraformStepGuard'], 'Verificar escritura del bloque'
assert 'public Step peek()' in s['TerraformColumnCursor'] and 'public void advance()' in s['TerraformColumnCursor'], 'Cursor sin saltar pasos ante fallos'
for cmd in ('escanear','seleccionar','crear','listar','analizar','buscar','planear','plantilla','previsualizar','simular','confirmar','cancelar','deshacer','proteger','estado','finalizar','asentamientos','olvidar','protegerarea','areas','liberararea'):
    assert f'Commands.literal("{cmd}")' in m,cmd
for kind in ('aldea','campamento','ruina','torre','refugio','altar','pozo','carreta'):
    assert f'"{kind}"' in e,kind
assert 'clearFootprint' in e and 'world.hasChunk' in c
assert 'Vec3i size=template.getSize()' in m
assert 'return inspectTemplate(s,id);' in m
assert 'job.world.hasChunk' in e
assert 'for (long cx = minX >> 4' in c
assert 'world.getEntitiesOfClass(Entity.class' in e
assert 'MAX_CHANGES = 55_000' in e
assert 'BLOCKS_PER_TICK = 200' in e
assert 'undoRunning' in e and 'undoTick()' in e
assert 'current.equals(b.before())' in e and 'current.equals(b.after())' in e
assert 'b.after().isEmpty() || !b.after().equals' in e
assert 'job.expectedBefore.get(entry.getKey())' in e
assert 'Plantillas CTOV/Explorify solo en catalogo' in e
assert 'setUndo(new WorldData.Undo' in e
assert 'new StructurePlaceSettings' not in e, 'Plantilla externa no se puede colocar sin rollback de NBT'
assert 'reservedEntities.add(UUID.randomUUID())' in e, 'Reservar UUIDs persistentes antes de crear aldeanos'
assert 'villager.setUUID(reserved)' in e
assert 'data.registerCompleted(snapshot)' in m, 'Registrar asentamiento al finalizar'
assert 'protectedFootprint' in c, 'Comprobar huella completa frente a circulos y rectangulos'
assert 'overlapsCompleted' in c, 'Evitar superposicion de estructuras BWE'
assert 'Arbol alto fuera del limite de limpieza' in e
assert 'import net.minecraft.world.level.levelgen.Heightmap;' in c
assert 'palette.roofSlab' in e and 'ACACIA_PLANKS' in e and 'MANGROVE_LOG' in e
assert 'Blocks.FURNACE' not in e and 'Blocks.BARREL' not in e, 'No dejar inventarios originales sin copia de NBT'
for name,code in s.items():
    assert code.count('{')==code.count('}'),name
print('OK: verificaciones estáticas v0.6.18 (NO compilación Forge).')
