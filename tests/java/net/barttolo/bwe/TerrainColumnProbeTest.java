package net.barttolo.bwe;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/** Deterministic Java 17 tests without Minecraft or Forge. */
public final class TerrainColumnProbeTest {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        var flat = TerrainColumnProbe.findGround(65, -64, y -> y <= 64);
        check(flat.found() && flat.groundY() == 65 && flat.ignoredAbove() == 0, "flat terrain");
        Set<Integer> natural = Set.of(63,64);
        var roof = TerrainColumnProbe.findGround(81, -64, natural::contains);
        check(roof.found() && roof.groundY() == 65 && roof.ignoredAbove() == 16,
            "wooden roof must not shift foundation");
        var isolated = TerrainColumnProbe.findGround(82, -64, y -> y == 81 || y == 64 || y == 63);
        check(isolated.found() && isolated.groundY() == 65,
            "one natural-looking roof block is insufficient");
        var unknown = TerrainColumnProbe.findGround(81, -64, y -> false);
        check(!unknown.found() && unknown.ignoredAbove() == 48, "unrecognized column");
        AtomicInteger reads = new AtomicInteger();
        TerrainColumnProbe.findGround(120, -64, y -> { reads.incrementAndGet(); return false; });
        check(reads.get() == TerrainColumnProbe.MAX_DEPTH, "maximum 48 block reads");
        var deep = TerrainColumnProbe.findGround(120, -64, y -> y <= 64);
        check(!deep.found(), "bounded depth");
        var negative = TerrainColumnProbe.findGround(-58, -64, y -> y <= -59);
        check(negative.found() && negative.groundY() == -58, "negative height");
        var voidColumn = TerrainColumnProbe.findGround(-64, -64, y -> true);
        check(!voidColumn.found(), "void column");
        try { TerrainColumnProbe.findGround(-65, -64, y -> true); throw new AssertionError(); }
        catch(IllegalArgumentException expected) { checks++; }
        try { TerrainColumnProbe.findGround(65, -64, null); throw new AssertionError(); }
        catch(IllegalArgumentException expected) { checks++; }
        System.out.println("OK: " + checks + " pruebas Java 17 de deteccion de terreno bajo tejados");
    }
}
