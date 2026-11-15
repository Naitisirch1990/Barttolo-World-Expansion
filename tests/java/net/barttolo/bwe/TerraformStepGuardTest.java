package net.barttolo.bwe;

import java.util.HashMap;
import java.util.Map;

/** Fake-world regression tests; no Forge runtime and no world edits. */
public final class TerraformStepGuardTest {
    private static int checks;
    private static void check(boolean yes, String message) {
        checks++;
        if (!yes) throw new AssertionError(message);
    }
    private static void fails(Runnable task, String message) {
        try { task.run(); throw new AssertionError("Expected failure: " + message); }
        catch (IllegalStateException | NullPointerException expected) { checks++; }
    }
    private static final class Fake implements TerraformStepGuard.WorldAccess<String> {
        final Map<TerraformAreaCursor.Step, String> before = new HashMap<>();
        final Map<TerraformAreaCursor.Step, String> current = new HashMap<>();
        boolean blocked, failWrite, corruptAfterWrite, editDuringRead;
        int writes, safetyChecks, reads;
        @Override public void requireSafe(TerraformAreaCursor.Step step) {
            safetyChecks++;
            if (blocked) throw new IllegalStateException("player/protected/chunk/world border");
        }
        @Override public String savedBefore(TerraformAreaCursor.Step step) { return before.get(step); }
        @Override public String targetState(TerraformAreaCursor.Step step) {
            return step.phase() == TerraformColumnCursor.Phase.CLEAR ? "air" : "stone";
        }
        @Override public String currentState(TerraformAreaCursor.Step step) {
            reads++;
            if (editDuringRead && reads == 2) current.put(step, "player-edit");
            return current.get(step);
        }
        @Override public boolean setState(TerraformAreaCursor.Step step, String state) {
            if (failWrite) return false;
            writes++;
            current.put(step, corruptAfterWrite ? "wrong" : state);
            return true;
        }
        void reset() { blocked=false; failWrite=false; corruptAfterWrite=false;
            editDuringRead=false; reads=0; safetyChecks=0; }
    }
    public static void main(String[] args) {
        var clear = new TerraformAreaCursor.Step(4, 5, TerraformColumnCursor.Phase.CLEAR, 70);
        var fill = new TerraformAreaCursor.Step(4, 5, TerraformColumnCursor.Phase.FILL, 69);
        var fake = new Fake();
        fake.before.put(clear, "oak"); fake.current.put(clear, "oak");
        fake.before.put(fill, "air"); fake.current.put(fill, "air");
        var guard = new TerraformStepGuard<>(fake);
        check(guard.apply(clear) == TerraformStepGuard.Outcome.WRITTEN, "clear written");
        check("air".equals(fake.current.get(clear)), "clear target is air");
        check(fake.safetyChecks == 2 && fake.writes == 1, "double pre-write safety check");
        check(guard.apply(clear) == TerraformStepGuard.Outcome.ALREADY_TARGET && fake.writes == 1,
            "crash replay is idempotent");
        check(guard.apply(fill) == TerraformStepGuard.Outcome.WRITTEN, "foundation fill written");
        check("stone".equals(fake.current.get(fill)), "foundation target solid");

        fake.reset(); fake.current.put(clear, "player-edit");
        fails(() -> guard.apply(clear), "unexpected third state rejected");
        check(fake.writes == 2, "conflict does not overwrite");
        fake.reset(); fake.current.put(clear, "oak"); fake.blocked = true;
        fails(() -> guard.apply(clear), "unloaded/protected/player guard");
        check(fake.writes == 2, "unsafe location unchanged");
        fake.reset(); fake.before.remove(clear);
        fails(() -> guard.apply(clear), "missing durable snapshot");
        fake.before.put(clear, "oak");
        fake.reset(); fake.failWrite = true;
        fails(() -> guard.apply(clear), "failed world set");
        check(fake.writes == 2 && "oak".equals(fake.current.get(clear)), "failed set not counted");
        fake.reset(); fake.editDuringRead = true;
        fails(() -> guard.apply(clear), "conflict between read and write");
        check(fake.writes == 2, "concurrent edit not overwritten");
        fake.reset(); fake.current.put(clear, "oak"); fake.corruptAfterWrite = true;
        fails(() -> guard.apply(clear), "post-write mismatch");
        check(fake.writes == 3, "failed verification detected");
        fake.reset(); fake.current.put(clear, "oak");
        var cursor = new TerraformAreaCursor(4, 5, 1, 1,
            (x,z) -> new ClearanceVolume.ColumnPlan(70, 70, 69, 69));
        check(cursor.execute(1, guard) == 1 && cursor.peek().equals(fill), "cursor integration clear");
        check(cursor.execute(1, guard) == 1 && !cursor.hasNext(), "cursor integration fill");
        check(cursor.completedSteps() == 2, "exactly two committed operations");
        System.out.println("OK: " + checks + " terraform step guard checks (Java 17)");
    }
}
