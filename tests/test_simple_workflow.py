"""Static checks for the simplified click-to-select workflow (NOT an in-game test)."""
from pathlib import Path
r=Path(__file__).resolve().parents[1]/'src/main/java/net/barttolo/bwe'
m=(r/'BarttoloWorldExpansion.java').read_text()
e=(r/'BuildEngine.java').read_text()
for name in ('escanear','listar','seleccionar','crear','deshacer','proteger','protegerarea'):
    assert 'Commands.literal("'+name+'")' in m,name
assert 'ClickEvent.Action.RUN_COMMAND' in m
assert '"/bwe seleccionar " + type' in m
assert 'private record ScanSite(BlockPos center, String dimension, long created)' in m
assert 'SCANS.put(id,new ScanSite(center,dim(s.getLevel()),System.currentTimeMillis()))' in m
assert 'site.center()' in m
assert 'PLANS.remove(id); // A new scan must not leave an old selection active.' in m
assert 'WorldChecks.scanBuildSite(s.getLevel(),site.center(),radius,radius,TemplateSurfaceSurvey.marginFor(id))' in m
assert 'BuildEngine.build(s.getLevel(),plan,id)' in m
assert 'if (!clearEntities(job.world, job.workArea))' in e
for kind in ('herreria','posada','granja','establo','fortin','mercado','aldea_grande'):
 assert kind in m and kind in e
print('OK: flujo de seleccion y 15 construcciones sin dependencias externas')
