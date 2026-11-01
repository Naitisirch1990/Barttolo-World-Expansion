from pathlib import Path
s=Path('src/main/java/net/barttolo/bwe/WorldChecks.java').read_text()
assert 'for (long cx = minX >> 4' in s and 'for (long cz = minZ >> 4' in s
assert s.index('for (long cx = minX >> 4') < s.index('int y = ground(world,x,z)')
assert 'if (!world.hasChunk((int) cx, (int) cz))' in s
assert 'if (!world.getWorldBorder().isWithinBounds' in s
print('OK: guard de chunks (inspección estática; no prueba Minecraft)')
