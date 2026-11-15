"""Static protocol audit; a Minecraft server test is still required."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]/"src/main/java/net/barttolo/bwe"
w=(root/"WorldData.java").read_text()
b=(root/"BuildEngine.java").read_text()
assert 'boolean completed)' in w
assert 'this(dimension, name, blocks, entities, false);' in w
assert 'u.putBoolean("Completed", undo.completed())' in w
assert 'u.contains("Completed", Tag.TAG_BYTE) && u.getBoolean("Completed")' in w
assert 'if (!snapshot.completed() || snapshot.blocks().isEmpty())' in w
assert 'if(!snapshot.completed())' in b
assert 'if(snapshot.blocks().isEmpty())' in b
assert 'if(!job.world.addFreshEntity(villager))' in b
assert 'new WorldData.Undo(job.plan.dimension(),job.plan.kind(),finalBlocks,job.reservedEntities,true)' in b
assert 'new WorldData.Undo(p.dimension(),p.kind(),undoBlocks,reservedEntities)' in b
assert b.index('if(!job.world.addFreshEntity(villager))') < b.index('new WorldData.Undo(job.plan.dimension(),job.plan.kind(),finalBlocks,job.reservedEntities,true)')
print('OK: marcador persistente de finalizacion y fallo seguro ante aldeanos incompletos (auditoria estatica)')
