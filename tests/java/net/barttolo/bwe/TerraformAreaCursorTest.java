package net.barttolo.bwe;

import java.util.ArrayList;
import java.util.NoSuchElementException;

/** Java 17 geometry tests; fake writer only, no Minecraft or world mutations. */
public final class TerraformAreaCursorTest {
    private static int checks;
    private static void check(boolean ok, String why) {
        checks++;
        if (!ok) throw new AssertionError(why);
    }
    private static ClearanceVolume.ColumnPlan plan(int x, int z) {
        int ground = x == 10 && z == -2 ? 64 : x == 11 && z == -2 ? 72
            : x == 10 ? 69 : 63;
        int top = x == 11 && z == -2 ? 74 : ground;
        return ClearanceVolume.planColumn(ground, top, 70, 2, 1, -64, 320);
    }
    public static void main(String[] args) {
        var cursor = new TerraformAreaCursor(10, -2, 2, 2, TerraformAreaCursorTest::plan);
        check(cursor.columns() == 4 && cursor.completedSteps() == 0, "four columns");
        check(cursor.peek().equals(new TerraformAreaCursor.Step(10, -2,
            TerraformColumnCursor.Phase.CLEAR, 70)), "first clear position");
        ArrayList<TerraformAreaCursor.Step> writes = new ArrayList<>();
        check(cursor.execute(3, writes::add) == 3, "three operations per tick");
        var saved = cursor.checkpoint();
        check(saved.columnIndex() == 0 && saved.stepInColumn() == 3
            && saved.completedSteps() == 3, "checkpoint after first column clear");
        var pending = cursor.peek();
        try {
            cursor.execute(1, step -> { throw new IllegalStateException("simulated write failure"); });
            throw new AssertionError("expected failure");
        } catch (IllegalStateException expected) { checks++; }
        check(cursor.peek().equals(pending) && cursor.checkpoint().equals(saved),
            "failed write cannot advance cursor");
        cursor = TerraformAreaCursor.resume(10, -2, 2, 2, TerraformAreaCursorTest::plan, saved);
        check(cursor.peek().equals(pending), "restart resumes same step");
        while (cursor.hasNext()) check(cursor.execute(2, writes::add) > 0, "bounded batches");
        check(cursor.completedSteps() == 25, "all 25 clear/fill positions processed");
        check(writes.size() == 25, "every position written exactly once in test");
        check(writes.get(8).x() == 11 && writes.get(8).z() == -2,
            "X advances before Z");
        check(writes.get(13).x() == 10 && writes.get(13).z() == -1,
            "second row after first row");
        check(cursor.checkpoint().columnIndex() == 4 && cursor.checkpoint().stepInColumn() == 0,
            "completed checkpoint");
        try { cursor.peek(); throw new AssertionError("expected end"); }
        catch (NoSuchElementException expected) { checks++; }
        var finished = TerraformAreaCursor.resume(10, -2, 2, 2,
            TerraformAreaCursorTest::plan, cursor.checkpoint());
        check(!finished.hasNext(), "completed checkpoint resumes completed");
        try { TerraformAreaCursor.resume(10, -2, 2, 2, TerraformAreaCursorTest::plan,
            new TerraformAreaCursor.Checkpoint(0, 100, 100));
            throw new AssertionError("expected bad checkpoint"); }
        catch (IllegalArgumentException expected) { checks++; }
        try { new TerraformAreaCursor(Integer.MAX_VALUE, 0, 2, 1, TerraformAreaCursorTest::plan);
            throw new AssertionError("expected coordinate overflow"); }
        catch (IllegalArgumentException expected) { checks++; }
        try { new TerraformAreaCursor(0, 0, 0, 1, TerraformAreaCursorTest::plan);
            throw new AssertionError("expected invalid dimensions"); }
        catch (IllegalArgumentException expected) { checks++; }
        try { finished.execute(-1, writes::add); throw new AssertionError("expected negative budget"); }
        catch (IllegalArgumentException expected) { checks++; }
        var large = new TerraformAreaCursor(0, 0, 100_000, 100_000,
            (x, z) -> new ClearanceVolume.ColumnPlan(70, 70, 1, 0));
        check(large.columns() == 10_000_000_000L, "large area count uses long");
        check(large.execute(1, writes::add) == 1, "large area is lazy");
        System.out.println("OK: " + checks + " terraform area cursor checks (Java 17)");
    }
}
