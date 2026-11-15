"""Static regression audit for dynamic CTOV/Explorify catalog and experimental placement.

This checks source wiring only. It is NOT a Forge runtime or modpack integration test.
"""
from pathlib import Path

src = Path("src/main/java/net/barttolo/bwe/BarttoloWorldExpansion.java").read_text()
engine = Path("src/main/java/net/barttolo/bwe/BuildEngine.java").read_text()

assert 'Commands.literal("listar").executes(ctx -> listing(ctx.getSource(),1))' in src
assert 'Commands.argument("pagina",IntegerArgumentType.integer(1))' in src
assert 'Commands.literal("inspeccionar")' in src
assert 'getStructureManager().listTemplates()' in src
assert 'key.getNamespace().equals("ctov")' in src
assert 'key.getNamespace().equals("explorify")' in src
assert '.distinct().sorted(Comparator.comparing(ResourceLocation::toString)).toList()' in src
assert 'CATALOG_PAGE_SIZE = 8' in src
assert 'Math.min(ids.size(),begin+CATALOG_PAGE_SIZE)' in src
assert 'size.getX()+" x "+size.getZ()+" bloques"' in src
assert '"/bwe inspeccionar "+id' in src
assert '"/bwe listar "+(page+1)' in src
assert '"/bwe listar "+(page-1)' in src
assert 'NO construye' in src
assert 'Blocks.JIGSAW' in src and 'Blocks.STRUCTURE_BLOCK' in src
assert 'filterBlocks(BlockPos.ZERO,settings,Blocks.JIGSAW,true)' in src
assert 'PIEZA JIGSAW' in src
assert 'area>22500L' in src
assert 'return inspectTemplate(s,id);' in src
assert 'OPTIONAL_TEMPLATES' not in src
assert 'buildExternal(world,p,operator)' in engine
assert 'TemplatePieceDraft.prepare(world,p)' in engine
assert 'running=new Job(world,p,operator,changed,originals,List.of(),draft.volume());' in engine
assert 'data.setUndo(new WorldData.Undo(p.dimension(),p.template(),originals,List.of()))' in engine
assert 'new StructurePlaceSettings' not in engine
assert not any(p.suffix == ".nbt" for p in Path("src/main").rglob("*"))
print("OK: catalogo paginado, seleccion externa y respaldo previo (auditoria estatica; NO es prueba en Minecraft)")

