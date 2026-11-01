"""Source audit: pause before each batch if a mob/player overlaps pending solid blocks.
This does NOT exercise a running Forge world."""
from pathlib import Path
s=(Path(__file__).resolve().parents[1]/'src/main/java/net/barttolo/bwe/BuildEngine.java').read_text()
start=s.index('public static void serverTick()')
end=s.index('private static void undoTick()',start)
tick=s[start:end]
assert 'if (occupied(job.world, job.collisionZone))' in tick
assert tick.index('if (occupied(job.world, job.collisionZone))') < tick.index('job.world.setBlock(')
assert 'job.waitingForEntities = true;' in tick
assert 'job.collisionZone.solidBlocks().remove(entry.getKey())' in tick
assert 'return;' in tick[tick.index('if (occupied(job.world, job.collisionZone))'):tick.index('job.waitingForEntities = false;')]
assert 'new AABB(minX,minY,minZ,maxX+1.0,maxY+1.0,maxZ+1.0)' in s
assert 'BlockPos.asLong(x,y,z)' in s
assert 'running = null;' in tick and 'snapshot conservado' in tick
print('OK: espera segura con colision en bloques pendientes (solo fuente)')
