package net.barttolo.bwe;

/**
 * Coordinates of a single external structure piece (not a complete jigsaw village).
 * Validates the entire intended clearance volume BEFORE world edits are allowed.
 * Pure Java 17; does not read NBT or modify Minecraft worlds.
 */
public final class TemplatePlacementVolume {
    private TemplatePlacementVolume() {}

    public record Bounds(long minX, long minY, long minZ,
                         long maxX, long maxY, long maxZ) {
        public Bounds {
            if (minX > maxX || minY > maxY || minZ > maxZ)
                throw new IllegalArgumentException("Invalid structure volume");
        }
        public long width() { return Math.addExact(Math.subtractExact(maxX, minX), 1L); }
        public long height() { return Math.addExact(Math.subtractExact(maxY, minY), 1L); }
        public long length() { return Math.addExact(Math.subtractExact(maxZ, minZ), 1L); }
        public long blockCount() {
            return Math.multiplyExact(Math.multiplyExact(width(), height()), length());
        }
        public boolean withinWorld(int minBuildY, int maxBuildYExclusive) {
            return minY >= minBuildY && maxY < maxBuildYExclusive
                && minX >= -30_000_000L && maxX <= 30_000_000L
                && minZ >= -30_000_000L && maxZ <= 30_000_000L;
        }
    }

    /** Existing structure size is measured in blocks, starting at the anchor Y. */
    public static Bounds centered(int centerX, int baseY, int centerZ,
                                  int sizeX, int sizeY, int sizeZ, boolean quarterTurn) {
        if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0)
            throw new IllegalArgumentException("Structure dimensions must be positive");
        TemplateFootprint.Bounds area = TemplateFootprint.centered(
            centerX, centerZ, sizeX, sizeZ, quarterTurn);
        long top = Math.addExact((long) baseY, (long) sizeY - 1L);
        return new Bounds(area.minX(), baseY, area.minZ(), area.maxX(), top, area.maxZ());
    }

    /** Entire clearance box, including margin; never just the blocks stored in NBT. */
    public static Bounds clearance(Bounds structure, int margin, int foundationDepth) {
        if (margin < 0 || margin > 10 || foundationDepth < 0)
            throw new IllegalArgumentException("Invalid margin or foundation depth");
        return new Bounds(Math.subtractExact(structure.minX(), margin),
            Math.subtractExact(structure.minY(), foundationDepth),
            Math.subtractExact(structure.minZ(), margin),
            Math.addExact(structure.maxX(), margin), structure.maxY(),
            Math.addExact(structure.maxZ(), margin));
    }
}
