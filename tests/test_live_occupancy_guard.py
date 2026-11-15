"""Static wiring only; real build/purge is exercised by game tests."""
from pathlib import Path
s=Path('src/main/java/net/barttolo/bwe/BuildEngine.java').read_text()
assert 'if (!clearEntities(job.world, job.workArea))' in s
purge=s[s.index('private static boolean clearEntities'):s.index('private static boolean isVillage')]
assert 'instanceof net.minecraft.world.entity.player.Player' in purge
assert purge.index('anyMatch') < purge.index('entity.discard()')
assert '256' in purge and 'entity.kill()' not in purge
assert 'WRITE_FLAGS = 2 | 16 | 32' in s
print('OK: purga acotada sin matar jugadores ni generar drops (fuentes)')
