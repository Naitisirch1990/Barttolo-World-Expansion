"""Legacy /bwe plantilla accepts namespace:path, but must remain read-only."""
from pathlib import Path
src=Path('src/main/java/net/barttolo/bwe/BarttoloWorldExpansion.java').read_text()
assert 'Commands.literal("plantilla").then(Commands.argument("id",StringArgumentType.greedyString())' in src
assert 'private static int planTemplate(CommandSourceStack s,String id)' in src
assert 'return inspectTemplate(s,id);' in src
assert 'OPTIONAL_TEMPLATES' not in src
assert 'key.getNamespace().equals("ctov")' in src
assert 'key.getNamespace().equals("explorify")' in src
print('OK: /bwe plantilla acepta namespace:path y solo inspecciona')
