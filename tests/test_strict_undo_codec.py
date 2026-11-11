"""Static safety regression: strict blockstate restore requires a Forge runtime test."""
from pathlib import Path
p=Path(__file__).resolve().parents[1]/"src/main/java/net/barttolo/bwe/WorldData.java"
s=p.read_text()
assert "if (parsed.isEmpty())" in s
assert "Incomplete block properties" in s
assert "Missing/invalid property" in s
assert "throw new IllegalArgumentException" in s
assert "parsed.isPresent() ? state.setValue" not in s
assert "if (property != null) state = applyProperty" not in s
build=(Path(__file__).resolve().parents[1]/"src/main/java/net/barttolo/bwe/BuildEngine.java").read_text()
assert "WorldData.decode(b.before());" in build, "Preflight must decode every state before rollback"
assert build.index("WorldData.decode(b.before());") < build.index("undoRunning = job;"), "Preflight before undo job"
# A truncated NBT compound cannot restore a block with default orientation.
assert 't.contains("Name", Tag.TAG_STRING)' in s
assert 't.contains("Properties", Tag.TAG_COMPOUND)' in s
assert 'props.getAllKeys().size() != block.getStateDefinition().getProperties().size()' in s
assert 'for (Property<?> property : block.getStateDefinition().getProperties())' in s

print("OK: rollback fail-closed ante propiedades ausentes, desconocidas o invalidas (auditoria estatica)")
