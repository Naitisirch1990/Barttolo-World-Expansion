"""Inspeccion estatica: guardia entre lotes; NO simula ticks de Minecraft."""
from pathlib import Path
s = (Path(__file__).resolve().parents[1] / 'src/main/java/net/barttolo/bwe/BuildEngine.java').read_text()
start = s.index('public static void serverTick()')
end = s.index('private static void undoTick()', start)
tick = s[start:end]
assert 'if (occupied(job.world, job.plan))' in tick
assert tick.index('if (occupied(job.world, job.plan))') < tick.index('job.world.setBlock(')
assert 'running = null;' in tick and 'snapshot conservado' in tick
assert 'world.getEntitiesOfClass(Entity.class, footprint)' in s
print('OK: guardia de entidades antes de cada lote (inspeccion estatica).')
