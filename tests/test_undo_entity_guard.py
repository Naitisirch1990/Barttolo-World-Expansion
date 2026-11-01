"""Static regression checks for safe rollback entity occupancy (not Forge integration)."""
from pathlib import Path
src=(Path(__file__).resolve().parents[1] / "src/main/java/net/barttolo/bwe/BuildEngine.java").read_text()
assert "private static AABB undoFootprint(WorldData.Undo snapshot)" in src
assert "this.ownedEntities = Set.copyOf(snapshot.entities());" in src
assert "private static boolean undoOccupied(UndoJob job)" in src
assert "!job.ownedEntities.contains(entity.getUUID())" in src
assert "if (undoOccupied(job))" in src
assert "undoRunning = job;" in src
assert src.index("if (undoOccupied(job))",src.index("public static Result undo(ServerLevel world)")) < src.index("undoRunning = job;")
assert 'throw new IllegalStateException("Entidad entro en el area durante la restauracion")' in src
print("OK: rollback aborta ante entidades ajenas y preserva snapshot; comprobacion estatica")
