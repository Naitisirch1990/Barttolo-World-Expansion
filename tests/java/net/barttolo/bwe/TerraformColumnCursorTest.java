package net.barttolo.bwe;

import java.util.NoSuchElementException;

/** Java 17 tests for bounded clear/fill sequencing, without a Minecraft world. */
public final class TerraformColumnCursorTest {
    private static int checks;
    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        // The surface probe returns the FIRST AIR Y, not the last solid block.
        var plan = ClearanceVolume.planColumnFromSurface(65, 68, 72, 12, 7, -64, 320);
        check(plan.fillFromY() == 65 && plan.fillToY() == 71,
            "surface first-air Y must be included in foundation fill");
        check(plan.clearFromY() == 72 && plan.clearToY() == 90,
            "clearance spans the building and overhead");
        var cursor = new TerraformColumnCursor(plan);
        check(cursor.total() == 26 && cursor.processed() == 0, "bounded position count");
        check(cursor.peek().equals(new TerraformColumnCursor.Step(TerraformColumnCursor.Phase.CLEAR, 72)),
            "first clearance step");
        check(cursor.peek().y() == 72 && cursor.processed() == 0,
            "peek does not consume a failed write");
        for (int y = 72; y <= 90; y++) {
            check(cursor.peek().phase() == TerraformColumnCursor.Phase.CLEAR && cursor.peek().y() == y,
                "clear ascending Y=" + y);
            cursor.advance();
        }
        for (int y = 65; y <= 71; y++) {
            check(cursor.peek().phase() == TerraformColumnCursor.Phase.FILL && cursor.peek().y() == y,
                "fill ascending Y=" + y);
            cursor.advance();
        }
        check(!cursor.hasNext() && cursor.processed() == cursor.total(), "all steps consumed");
        try { cursor.peek(); throw new AssertionError("peek after completion"); }
        catch (NoSuchElementException expected) { checks++; }
        try { cursor.advance(); throw new AssertionError("advance after completion"); }
        catch (NoSuchElementException expected) { checks++; }

        var hill = ClearanceVolume.planColumnFromSurface(91, 92, 70, 10, 5, -64, 320);
        var hillCursor = new TerraformColumnCursor(hill);
        check(hillCursor.total() == 23 && hill.fillPositions() == 0,
            "hill is cut, never filled above foundation");
        while (hillCursor.hasNext()) {
            check(hillCursor.peek().phase() == TerraformColumnCursor.Phase.CLEAR,
                "hill has no fill steps");
            hillCursor.advance();
        }
        check(hillCursor.processed() == 23, "hill cleared completely");
        try { ClearanceVolume.planColumnFromSurface(-64, 70, 72, 3, 7, -64, 320);
            throw new AssertionError("surface at min height lacks a support block"); }
        catch (IllegalArgumentException expected) { checks++; }
        System.out.println("OK: " + checks + " terraform cursor checks (Java 17)");
    }
}
