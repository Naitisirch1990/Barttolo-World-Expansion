package net.barttolo.bwe;

/** Standalone Java 17 checks for single-piece schematic placement geometry. */
public final class TemplatePlacementVolumeTest {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static void rejected(Runnable op) {
        try { op.run(); throw new AssertionError("Expected rejection"); }
        catch (IllegalArgumentException | ArithmeticException expected) { checks++; }
    }
    public static void main(String[] args) {
        var normal = TemplatePlacementVolume.centered(100, 64, -100, 11, 6, 7, false);
        check(normal.width() == 11 && normal.length() == 7, "unrotated width and depth");
        check(normal.height() == 6 && normal.maxY() == 69, "inclusive height");
        check(normal.minX() == 95 && normal.maxX() == 105, "center X");
        check(normal.minZ() == -103 && normal.maxZ() == -97, "center Z");
        var rotated = TemplatePlacementVolume.centered(100, 64, -100, 11, 6, 7, true);
        check(rotated.width() == 7 && rotated.length() == 11, "90 degree rotation");
        check(rotated.blockCount() == 462, "block count");
        var cleared = TemplatePlacementVolume.clearance(rotated, 7, 10);
        check(cleared.width() == 21 && cleared.length() == 25, "margin all sides");
        check(cleared.minY() == 54 && cleared.maxY() == 69, "foundation depth");
        check(cleared.withinWorld(-64, 320), "valid build bounds");
        check(!TemplatePlacementVolume.centered(0, 318, 0, 1, 3, 1, false)
            .withinWorld(-64, 320), "reject ceiling overrun");
        check(!TemplatePlacementVolume.centered(0, -65, 0, 1, 3, 1, false)
            .withinWorld(-64, 320), "reject below-world placements");
        check(!TemplatePlacementVolume.centered(30_000_000, 64, 0, 3, 1, 1, false)
            .withinWorld(-64, 320), "reject coordinate border");
        rejected(() -> TemplatePlacementVolume.centered(0, 0, 0, 0, 1, 1, false));
        rejected(() -> TemplatePlacementVolume.centered(0, 0, 0, 1, -1, 1, false));
        rejected(() -> TemplatePlacementVolume.clearance(normal, 11, 0));
        rejected(() -> TemplatePlacementVolume.clearance(normal, 5, -1));
        System.out.println("OK: " + checks + " schematic volume geometry checks (Java 17)");
    }
}
