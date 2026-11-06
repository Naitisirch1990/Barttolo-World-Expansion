"""Static safety check for read-only surface measurement (not a Forge runtime test)."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
main=(root/'src/main/java/net/barttolo/bwe/BarttoloWorldExpansion.java').read_text()
survey=(root/'src/main/java/net/barttolo/bwe/TemplateSurfaceSurvey.java').read_text()
assert 'Commands.literal("superficie")' in main
assert 'Commands.literal("superficieestado")' in main
assert 'Commands.literal("cancelarsuperficie")' in main
assert 'TemplateSurfaceSurvey.serverTick()' in main
assert 'TemplateSurfaceSurvey.resetForShutdown()' in main
assert 'Heightmap.Types.MOTION_BLOCKING_NO_LEAVES' in survey
assert 'DEFAULT_MARGIN = 7' in survey and 'COLUMNS_PER_TICK = 16' in survey
assert 'Commands.literal("margen")' in main
assert 'IntegerArgumentType.integer(5,10)' in main
assert 'TemplateSurfaceSurvey.configureMargin' in main
assert 'TemplateSurfaceSurvey.marginFor' in main
assert 'TemplateFootprint.expanded(base,margin)' in survey
assert 'this.total=foundation.area()' in survey
assert 'this.bounds=foundation' in survey and 'this.clearance=clearance' in survey
assert 's.bounds.minX()' in survey and 's.bounds.minZ()' in survey
assert 'area.chunkCount()>MAX_CHUNKS || base.area()>MAX_COLUMNS' in survey
assert 'OPERATOR_MARGINS.clear()' in survey
assert 'MAX_COLUMNS = 1_000_000L' in survey and 'MAX_CHUNKS = 1024L' in survey
assert 'world.hasChunk' in survey and 's.world.hasChunk' in survey
assert 'protectedFootprint' in survey and 'overlapsCompleted' in survey
assert 'BuildEngine.busy()' in survey
for forbidden in ('setBlock(', 'placeInWorld(', 'getChunk(', 'getBlockEntity(', 'addFreshEntity('):
    assert forbidden not in survey, forbidden
assert 'NO se terraformo ni coloco NBT' in survey
print('OK: medicion solo de base con margen configurable, sin escrituras ni cargas de chunks (auditoria estatica)')

assert 'TerrainColumnProbe.findGroundLayered' in survey
assert 'classifyNaturalGround' in survey
assert 'TerrainColumnProbe.ROCK' in survey
assert 'TerrainColumnProbe.SOIL' in survey
assert 's.unresolved>s.total/10L' in survey
assert 'isLikelyNaturalGround' in survey
assert 'SIN BASE SUGERIDA' in survey
assert 'coveredObstacles' in survey

# The proposed 3D clearance is a read-only estimate, not a write authorization.
assert 'ClearanceVolume.calculate(' in survey
assert 's.templateHeight' in survey
assert 'size.getY()<1' in survey
assert 'SIN BASE SUGERIDA: volumen vertical propuesto' in survey
assert 'despeje geometrico=' in survey

# The layered probe can read up to 48 blocks per column. Cap its per-tick work.
probe=(root/'src/main/java/net/barttolo/bwe/TerrainColumnProbe.java').read_text()
assert 'MAX_DEPTH = 48' in probe
assert 16 * 48 <= 768
assert 'public static boolean isRunning()' in survey
assert 'TemplateSiteAudit.isRunning()' in survey
assert 'auditoria 3D en curso' in survey

# The clearance margin can unload or become protected during a long scan.
# Revalidate at completion before returning a suggested foundation level.
assert 'for(long cx=s.clearance.minX()>>4;cx<=s.clearance.maxX()>>4;cx++)' in survey
assert 'for(long cz=s.clearance.minZ()>>4;cz<=s.clearance.maxZ()>>4;cz++)' in survey
assert 'Medicion invalidada: chunk del margen descargado' in survey
assert 'current.protectedFootprint' in survey
assert 'current.overlapsCompleted' in survey
assert 'Medicion invalidada: el margen ahora sale del worldborder.' in survey
assert survey.index('if(s.processed==s.total)') < survey.index('Medicion invalidada: chunk del margen descargado') < survey.index('String coverage=')
print('OK: margen y protecciones revalidados al terminar (estatico)')
