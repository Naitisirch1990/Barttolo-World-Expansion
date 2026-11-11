from pathlib import Path
s=Path('src/main/java/net/barttolo/bwe/BuildEngine.java').read_text()
build=s[s.index('public static Result build('):s.index('public static Result diagnose(')]
simulate=s[s.index('public static Result simulate('):]
for part in (build,simulate):
 assert 'prepareOriginal(world,p)' in part
 assert 'TemplatePieceDraft.prepare(world,p)' in part
assert 'WorldChecks.natural(old)' not in build+simulate
assert 'data.setUndo(new WorldData.Undo' in build
assert build.index('world.getServer().overworld().getDataStorage().save();') < build.index('running = new Job(')
print('OK: simulacion y construccion usan el mismo plan sin modificar al simular')
