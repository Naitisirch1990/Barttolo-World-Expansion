from pathlib import Path
s=Path('src/main/java/net/barttolo/bwe/BuildEngine.java').read_text()
assert 'private static AABB undoFootprint(WorldData.Undo snapshot)' in s
assert 'if (!clearEntities(job.world, job.footprint))' in s
assert 'Restauracion completa:' in s and 'Restauracion detenida:' in s
assert 'job.ticks % 20' in s
assert 'Bloque modificado por un jugador' not in s
assert 'running = null; // Stop a partial/paused build' in s
print('OK: restauracion total y progreso; jugadores protegidos (fuentes)')
