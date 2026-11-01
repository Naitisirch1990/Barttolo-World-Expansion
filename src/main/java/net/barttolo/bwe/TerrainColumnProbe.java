package net.barttolo.bwe;

import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;

/** Bounded read-only probe of a terrain column. No Minecraft dependencies. */
public final class TerrainColumnProbe {
    public static final int MAX_DEPTH = 48;
    public static final int NONE = 0, SOIL = 1, ROCK = 2;
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

    /** More conservative alternative for a replacement site. Soil must have at
     * least two layers; rock-like blocks need four, reducing false positives
     * from thin sandstone/stone roofs. Even four rock layers are NOT proof of
     * natural terrain, so this result must never authorize destructive edits. */
    public static Result findGroundLayered(int topY, int minY, IntUnaryOperator layerAtY) {
        if (layerAtY == null || topY < minY)
            throw new IllegalArgumentException("Invalid layered column probe");
        int bottom = (int)Math.max((long)minY, (long)topY - MAX_DEPTH);
        int soilStreak = 0, rockStreak = 0;
        int soilTop = 0, rockTop = 0;
        for (int y = topY - 1; y >= bottom; y--) {
            int layer = layerAtY.applyAsInt(y);
            if (layer != NONE && layer != SOIL && layer != ROCK)
                throw new IllegalArgumentException("Invalid terrain layer: " + layer);
            if (layer == SOIL) {
                if (soilStreak == 0) soilTop = y;
                soilStreak++;
            } else soilStreak = 0;
            if (layer == ROCK) {
                if (rockStreak == 0) rockTop = y;
                rockStreak++;
            } else rockStreak = 0;
            if (soilStreak >= 2) return new Result(true, soilTop + 1, topY - soilTop - 1);
            if (rockStreak >= 4) return new Result(true, rockTop + 1, topY - rockTop - 1);
        }
        return new Result(false, topY, topY - bottom);
    }

}
