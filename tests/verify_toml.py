"""Static metadata validation; does not mean the mod compiles or runs."""
import tomllib
from pathlib import Path
root=Path(__file__).resolve().parents[1]
with (root/'src/main/resources/META-INF/mods.toml').open('rb') as f:
    doc=tomllib.load(f)
assert doc['mods'][0]['modId']=='barttoloworldexpansion'
assert doc['mods'][0]['version']=='0.6.17'
assert doc['mods'][0]['displayTest']=='IGNORE_SERVER_VERSION'
assert doc['dependencies']['barttoloworldexpansion'][0]['modId']=='minecraft'
assert doc['dependencies']['barttoloworldexpansion'][1]['modId']=='forge'
assert "version = '0.6.17'" in (root/'build.gradle').read_text()
print('OK: metadatos TOML, Forge y version del proyecto')
