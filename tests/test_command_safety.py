"""Regression guard for safe cancellation and empty undo snapshots.
This is a SOURCE audit, not an in-game test."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]/"src/main/java/net/barttolo/bwe"
main=(root/"BarttoloWorldExpansion.java").read_text()
engine=(root/"BuildEngine.java").read_text()
start=main.index("private static int cancel(CommandSourceStack s)")
end=main.index("private static int undo(CommandSourceStack s)", start)
cancel=main[start:end]
assert cancel.index("if(BuildEngine.busy())") < cancel.index("PLANS.remove(player(s))")
assert cancel.index("if(WorldData.get(s.getLevel()).undo()!=null)") < cancel.index("PLANS.remove(player(s))")
assert "NO la detiene" in cancel and "NO lo elimina" in cancel
assert "Plan cancelado. Terreno intacto." not in cancel
assert "if(PLANS.remove(player(s))==null)" in cancel
assert "snapshot.completed() ? \"COMPLETO" in main
assert '"INCOMPLETO: /bwe finalizar SI bloqueado' in main
assert "if(changed.isEmpty()) return new Result(false" in engine
assert "if(changed==0) return new Result(false" in engine
assert engine.index("if(changed.isEmpty())") < engine.index("data.setUndo(new WorldData.Undo")
print("OK: cancelacion segura, estado explicito y bloqueo de respaldo vacio (solo fuente)")
