"""Conservative audit: no embedded CTOV/Explorify binaries or structure files."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
for p in (root/'src').rglob('*'):
    if not p.is_file(): continue
    rel=p.relative_to(root).as_posix().lower()
    assert not any(rel.endswith(x) for x in ('.nbt','.schem','.schematic','.jar','.zip')), rel
    assert not any('/ctov/' in rel or '/explorify/' in rel for _ in [0]), rel
print('OK: sin archivos de estructuras ni binarios CTOV/Explorify embebidos')
