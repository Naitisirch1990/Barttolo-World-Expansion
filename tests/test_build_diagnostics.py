"""Static regression checks; NOT an in-game test."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]/"src/main/java/net/barttolo/bwe"
engine=(root/"BuildEngine.java").read_text()
main=(root/"BarttoloWorldExpansion.java").read_text()
start=engine.index("public static void serverTick()")
end=engine.index("private static void undoTick()",start)
tick=engine[start:end]
assert 'if(!actual.equals(desired) || entry.getValue().hasBlockEntity())' in tick
assert 'job.world.setBlock(at, entry.getValue(), WRITE_FLAGS)' in tick
assert 'job.world.removeBlockEntity(at)' in tick
assert 'LOGGER.error("[BWE] Fallo durante construccion' in tick
assert "String.valueOf(error.getMessage())" in tick
a=engine.index("public static Result diagnose(ServerLevel world)")
b=engine.index("public static Result undo(ServerLevel world)",a)
diagnose=engine[a:b]
assert "world.hasChunk" in diagnose and "world.getBlockState" in diagnose
assert "world.setBlock" not in diagnose and ".save()" not in diagnose and "clearUndo" not in diagnose
assert "discrepancias=" in diagnose and "primer conflicto " in diagnose
assert '.then(Commands.literal("diagnosticar")' in main
assert "BuildEngine.diagnose(s.getLevel())" in main
print("OK: diagnostico solo lectura, logger de errores y destino ya colocado (auditoria estatica)")

