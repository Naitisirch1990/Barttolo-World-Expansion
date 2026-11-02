"""Source-only safety audit for the incremental, read-only template-site scan.
Not a Forge compile or Minecraft integration test."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
main=(root/'src/main/java/net/barttolo/bwe/BarttoloWorldExpansion.java').read_text()
audit=(root/'src/main/java/net/barttolo/bwe/TemplateSiteAudit.java').read_text()
cursor=(root/'src/main/java/net/barttolo/bwe/TemplateScanCursor.java').read_text()
assert 'Commands.literal("auditarplantilla")' in main
assert 'Commands.literal("auditoria")' in main
assert 'Commands.literal("cancelarauditoria")' in main
assert 'verifyTemplate(s,id)' in main
assert 'TemplateSiteAudit.serverTick()' in main
assert 'TemplateSiteAudit.resetForShutdown()' in main
assert 'BLOCKS_PER_TICK = 1024' in audit
assert 'MAX_SCAN_BLOCKS = 8_000_000L' in audit
assert 'world.hasChunk' in audit and 'a.world.hasChunk' in audit
assert 'state.hasBlockEntity()' in audit and 'WorldChecks.natural(state)' in audit
assert 'BuildEngine.busy()' in audit
assert 'TemplateScanCursor' in audit and 'Math.multiplyExact' in cursor
assert 'getBlockState(' in audit
for forbidden in ('setBlock(', 'placeInWorld(', 'getChunk(', 'getHeight(', 'addFreshEntity(', 'getBlockEntity('):
    assert forbidden not in audit, forbidden
assert 'setBlock(' not in cursor
assert 'NO se coloco ninguna plantilla' in audit
print('OK: auditoria incremental sin escrituras ni cargas de chunks (auditoria estatica)')

# Full clearance audit is a distinct command: the original piece audit stays available.
assert 'Commands.literal("auditarvolumen")' in main
assert 'TemplateSiteAudit.startClearance' in main
assert 'startInternal(world, anchor, size, operator, templateId, false)' in audit
assert 'startInternal(world, anchor, size, operator, templateId, true)' in audit
assert 'TemplateFootprint.expanded(bounds,margin)' in audit
assert 'ClearanceVolume.calculate(' in audit
assert 'TemplateSurfaceSurvey.marginFor(operator)' in audit
assert 'cursor=new TemplateScanCursor(bounds,(int)bottom,(int)top)' in audit
assert 'data.protectedFootprint' in audit and 'data.overlapsCompleted' in audit
assert 'SiteMath.circleIntersectsRect' in audit
assert 'getWorldBorder().isWithinBounds' in audit
assert 'MAX_SCAN_BLOCKS' in audit
assert 'Y provisional del operador' in audit
