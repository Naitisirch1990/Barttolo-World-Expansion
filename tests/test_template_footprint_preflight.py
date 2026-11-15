"""Source-only safety checks for /bwe verificarplantilla. Not a Minecraft integration test."""
from pathlib import Path
root = Path(__file__).resolve().parents[1]
main = (root / "src/main/java/net/barttolo/bwe/BarttoloWorldExpansion.java").read_text()
geometry = (root / "src/main/java/net/barttolo/bwe/TemplateFootprint.java").read_text()
start = main.index("private static int verifyTemplate(CommandSourceStack s,String id)")
end = main.index("private static int scan(CommandSourceStack s,int r)", start)
preview = main[start:end]
assert 'Commands.literal("verificarplantilla")' in main
assert 'TemplateFootprint.centered(' in preview
assert 'withinMinecraftCoordinates()' in preview
assert 'chunkCount()>1024L' in preview
assert 'getMinBuildHeight()' in preview and 'getMaxBuildHeight()' in preview
assert 'protectedFootprint' in preview and 'overlapsCompleted' in preview
assert 'circleIntersectsRect' in preview
assert 'getWorldBorder().isWithinBounds' in preview
assert 'world.hasChunk' in preview
assert preview.index('chunkCount()>1024L') < preview.index('world.hasChunk')
assert 'setBlock(' not in preview and 'placeInWorld(' not in preview
assert 'Math.multiplyExact' in geometry and 'long minX' in geometry
print("OK: preflight externo solo lectura, limites, protecciones y chunks (auditoria estatica)")
