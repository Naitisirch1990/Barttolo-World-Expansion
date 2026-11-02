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

# The operator must be able to retrieve a completed/failed report after reconnecting.
assert 'private static String lastResult' in audit
assert 'if(a==null) return lastResult;' in audit
assert 'lastResult=message;' in audit
assert 'lastResult="Auditoria "+id+" cancelada' in audit
assert 'lastResult="No hay auditoria de plantilla anterior."' in audit
assert 'posibles block entities=' in audit
print("OK: resultado de auditoria recuperable hasta reinicio (solo memoria)")

# Do not let an audit start while the other read-only scanner is active.
assert 'public static boolean isRunning()' in audit
assert 'TemplateSurfaceSurvey.isRunning()' in audit
assert 'medicion de superficie en curso' in audit

# Waterlogged artificial blocks must be counted as fluid AND non-natural.
# This is a static regression; integration in a Forge test world is still required.
assert 'if(!state.getFluidState().isEmpty()) a.fluids++;' in audit
assert 'if(!WorldChecks.natural(state) && !state.is(Blocks.WATER) && !state.is(Blocks.LAVA))' in audit
assert 'else if(!WorldChecks.natural(state))' not in audit
assert 'categorias no excluyentes' in audit
print('OK: fluidos y bloques no naturales se contabilizan independientemente (estatico)')

# Clearance must keep the same protected-area buffers as the piece preflight.
assert 'data.protectedFootprint(dim,x0,z0,x1,z1,8)' in audit
assert 'data.overlapsCompleted(dim,x0,z0,x1,z1,16)' in audit
assert 'data.protectedFootprint(dim,x0,z0,x1,z1,0)' not in audit
print('OK: despeje 3D respeta separacion de zonas protegidas (estatico)')
