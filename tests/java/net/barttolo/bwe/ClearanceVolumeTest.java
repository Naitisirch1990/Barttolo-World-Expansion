package net.barttolo.bwe;

/** Deterministic Java 17 tests for proposed 3D clearance volume (no Minecraft). */
public final class ClearanceVolumeTest {
    private static int checks;
    private static void check(boolean value, String reason) {
        checks++;
        if (!value) throw new AssertionError(reason);
    }
    public static void main(String[] args) {
        var normal = ClearanceVolume.calculate(50, 50, 20, 7, 70, -64, 320);
        check(normal.withinWorld(), "ordinary site within world");
        check(normal.columns() == 2500, "base columns");
        check(normal.minY() == 63 && normal.maxY() == 96, "vertical margins inclusive");
        check(normal.blocks() == 85000, "volume includes both vertical margins");
        // Cross-check the exact volume scanned by /bwe auditarvolumen:
        // 8x10 piece, 7-block horizontal margin, 6-block height + 7 above/below.
        var piece = TemplateFootprint.centered(0, 0, 8, 10, false);
        var expanded = TemplateFootprint.expanded(piece, 7);
        var fullAudit = ClearanceVolume.calculate(
            expanded.width(), expanded.length(), 6, 7, 70, -64, 320);
        check(expanded.width() == 22 && expanded.length() == 24, "horizontal margin both sides");
        check(fullAudit.minY() == 63 && fullAudit.maxY() == 82, "vertical margin both sides");
        check(fullAudit.blocks() == 10_560L, "full proposed scan volume");
        var edgeAudit = ClearanceVolume.calculate(
            expanded.width(), expanded.length(), 6, 7, 316, -64, 320);
        check(!edgeAudit.withinWorld(), "full clearance rejects world ceiling");
        var negative = ClearanceVolume.calculate(2, 3, 1, 5, -50, -64, 320);
        check(negative.withinWorld() && negative.blocks() == 66, "negative Y");
        var minFail = ClearanceVolume.calculate(1, 1, 2, 5, -60, -64, 320);
        check(!minFail.withinWorld() && minFail.blocks() == 0, "below min height");
        var maxFail = ClearanceVolume.calculate(1, 1, 2, 5, 315, -64, 320);
        check(!maxFail.withinWorld(), "exclusive upper height");
        var maxOk = ClearanceVolume.calculate(1, 1, 1, 0, 319, -64, 320);
        check(maxOk.withinWorld() && maxOk.blocks() == 1, "last buildable block");
        var large = ClearanceVolume.calculate(1_000_000, 1_000_000, 10, 0, 70, -64, 320);
        check(large.blocks() == 10_000_000_000_000L, "long arithmetic");
        try { ClearanceVolume.calculate(0, 1, 1, 7, 70, -64, 320); throw new AssertionError("zero width"); }
        catch(IllegalArgumentException expected) { checks++; }
        try { ClearanceVolume.calculate(1, 1, 0, 7, 70, -64, 320); throw new AssertionError("zero height"); }
        catch(IllegalArgumentException expected) { checks++; }
        try { ClearanceVolume.calculate(1, 1, 1, -1, 70, -64, 320); throw new AssertionError("negative margin"); }
        catch(IllegalArgumentException expected) { checks++; }
        try { ClearanceVolume.calculate(Long.MAX_VALUE, 2, 1, 0, 70, -64, 320); throw new AssertionError("overflow"); }
        catch(ArithmeticException expected) { checks++; }

        // Destructive cut/fill is geometry only: no inventory/entity snapshots.
        var lowGround = ClearanceVolume.planColumn(64, 68, 72, 12, 7, -64, 320);
        check(lowGround.fillFromY() == 65 && lowGround.fillToY() == 71,
            "fill supports a raised foundation");
        check(lowGround.fillPositions() == 7, "seven foundation fill positions");
        check(lowGround.clearFromY() == 72 && lowGround.clearToY() == 90,
            "clearance includes full template and overhead");
        check(lowGround.clearPositions() == 19 && lowGround.totalPositions() == 26,
            "work budget includes air and support positions");
        var oldVillage = ClearanceVolume.planColumn(69, 104, 70, 10, 5, -64, 320);
        check(oldVillage.clearToY() == 104, "tall old building cleared above template");
        check(oldVillage.fillPositions() == 0, "no fill when ground meets foundation");
        var hill = ClearanceVolume.planColumn(90, 92, 70, 10, 5, -64, 320);
        check(hill.clearFromY() == 70 && hill.clearToY() == 92,
            "cut extends through hill above proposed roof");
        check(hill.fillPositions() == 0, "hill does not create negative fill");
        var atBedrock = ClearanceVolume.planColumn(-64, -64, -63, 1, 0, -64, 320);
        check(atBedrock.clearPositions() == 1 && atBedrock.fillPositions() == 0,
            "minimum world height accepted");
        try { ClearanceVolume.planColumn(300, 310, 315, 8, 7, -64, 320);
            throw new AssertionError("ceiling clearance"); }
        catch(IllegalArgumentException expected) { checks++; }
        try { ClearanceVolume.planColumn(70, 69, 72, 3, 7, -64, 320);
            throw new AssertionError("obstruction below ground"); }
        catch(IllegalArgumentException expected) { checks++; }
        try { ClearanceVolume.planColumn(-65, 70, 72, 3, 7, -64, 320);
            throw new AssertionError("ground outside world"); }
        catch(IllegalArgumentException expected) { checks++; }
        System.out.println("OK: " + checks + " clearance volume checks (Java 17)");
    }
}
