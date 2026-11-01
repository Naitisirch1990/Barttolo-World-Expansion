from pathlib import Path
src = Path('src/main/java/net/barttolo/bwe/BarttoloWorldExpansion.java').read_text()
assert 'Commands.literal("plantilla").then(Commands.argument("id",StringArgumentType.greedyString())' in src
assert '"explorify:campsite/main/01"' in src
assert '"ctov:village/plains/house/small_1"' in src
print('OK: argumento de plantilla acepta rutas namespace:path (auditoria estatica)')
