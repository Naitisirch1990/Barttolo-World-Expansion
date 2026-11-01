package net.barttolo.bwe;

/** Pure horizontal geometry for previewing a single structure NBT piece. */
public final class TemplateFootprint {
    private TemplateFootprint() {}

    public record Bounds(long minX, long minZ, long maxX, long maxZ) {
        public Bounds {
            if (minX > maxX || minZ > maxZ)
                throw new IllegalArgumentException("Invalid template footprint");
        }
        public long width() { return maxX - minX + 1L; }
        public long length() { return maxZ - minZ + 1L; }
        public long area() { return Math.multiplyExact(width(), length()); }
        public long chunkCount() {
            long xChunks = (maxX >> 4) - (minX >> 4) + 1L;
            long zChunks = (maxZ >> 4) - (minZ >> 4) + 1L;
            return Math.multiplyExact(xChunks, zChunks);
        }
        public boolean withinMinecraftCoordinates() {
            return minX >= -30_000_000L && maxX <= 30_000_000L
                && minZ >= -30_000_000L && maxZ <= 30_000_000L;
        }
    }

    /** Centre at the operator's block. A quarter turn swaps X/Z sizes.
     *  This is NOT a terrain check or a guarantee that jigsaw pieces assemble. */
    public static Bounds centered(int centerX, int centerZ, int sizeX, int sizeZ, boolean quarterTurn) {
        if (sizeX < 1 || sizeZ < 1)
            throw new IllegalArgumentException("Template dimensions must be positive");
        long width = quarterTurn ? sizeZ : sizeX;
        long length = quarterTurn ? sizeX : sizeZ;
        long minX = (long) centerX - width / 2L;
        long minZ = (long) centerZ - length / 2L;
        return new Bounds(minX, minZ, minX + width - 1L, minZ + length - 1L);
    }
}
