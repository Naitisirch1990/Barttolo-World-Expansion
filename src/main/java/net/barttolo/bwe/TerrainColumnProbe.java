package net.barttolo.bwe;

import java.util.function.IntPredicate;

/** Bounded read-only probe of a terrain column. No Minecraft dependencies. */
public final class TerrainColumnProbe {
    public static final int MAX_DEPTH = 48;
    private TerrainColumnProbe() {}

    /** topY is the first free block above the heightmap's highest obstruction.
     * Natural ground requires two consecutive qualifying blocks. A candidate
     * is not proof of natural generation. */
    public record Result(boolean found, int groundY, int ignoredAbove) {}

    public static Result findGround(int topY, int minY, IntPredicate naturalAtY) {
        if (naturalAtY == null || topY < minY)
            throw new IllegalArgumentException("Invalid column probe");
        int bottom = (int)Math.max((long)minY, (long)topY - MAX_DEPTH);
        if (topY <= bottom) return new Result(false, topY, 0);
        boolean above = naturalAtY.test(topY - 1);
        for (int y = topY - 1; y > bottom; y--) {
            boolean below = naturalAtY.test(y - 1);
            if (above && below)
                return new Result(true, y + 1, topY - y - 1);
            above = below;
        }
        return new Result(false, topY, topY - bottom);
    }
}
