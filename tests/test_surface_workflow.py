"""Source wiring; actual placement and import are tested by Forge GameTest."""
from pathlib import Path
r=Path('src/main/java/net/barttolo/bwe')
m=(r/'BarttoloWorldExpansion.java').read_text();c=(r/'BweCatalog.java').read_text();b=(r/'SurfaceBuilder.java').read_text()
for cmd in ('listar','seleccionar','crear','estado','deshacer','margen'):
 assert f'Commands.literal("{cmd}")' in m
for cmd in ('auditarplantilla','auditarvolumen','verificarplantilla','auditoria','superficieestado','finalizar','simular'):
 assert f'Commands.literal("{cmd}")' not in m
assert 'e.sizeX()+" x "+e.sizeZ()' in m
assert 'new ZipFile(archive.toFile())' in c and 'result.load(BuiltInRegistries.BLOCK.asLookup(),tag)' in c
assert 'state.rotate(rotations' in c and 'ctov.jar' in c and 'explorify.jar' in c
assert 'Stage.CIMENTACION' in b and 'Stage.LIMPIEZA' in b
assert 'e instanceof Player' in b and 'e.discard()' in b
assert 'be.load(tag)' in b and 'EntityType.loadEntityRecursive' in b
assert 'setUndo' not in b and 'getStructureManager().get' in c
assert '0.8.0' in Path('src/main/resources/META-INF/mods.toml').read_text()
print('OK: flujo manual simple, plantillas nativas, rotaciones, dimensiones y limpieza')
