package net.barttolo.bwe;

/** Read-only geometric clearance estimate for one template, independent of Minecraft.
 *  This is a volume estimate, NOT a block-change count or an undo allocation.
 *  No world edits are authorized by this calculation. */
public final class ClearanceVolume {
    private ClearanceVolume() {}

    public record Estimate(long columns, long blocks, long minY, long maxY, boolean withinWorld) {}

    /** The proposed clearance spans the template plus the same vertical margin
     *  above and below. Minecraft maxBuildHeight is EXCLUSIVE.
     *  An out-of-world proposal returns withinWorld=false without allocating blocks. */
    public static Estimate calculate(long width, long length, int templateHeight, int margin,
                                     int foundationY, int minBuildHeight, int maxBuildHeight) {
        if (width < 1 || length < 1 || templateHeight < 1 || margin < 0
            || maxBuildHeight <= minBuildHeight)
            throw new IllegalArgumentException("Invalid clearance dimensions or world height");
        long columns = Math.multiplyExact(width, length);
        long bottom = (long)foundationY - margin;
        long top = (long)foundationY + templateHeight + margin - 1L;
        if (bottom < minBuildHeight || top >= maxBuildHeight)
            return new Estimate(columns, 0L, bottom, top, false);
        long height = Math.addExact(Math.subtractExact(top, bottom), 1L);
        return new Estimate(columns, Math.multiplyExact(columns, height), bottom, top, true);
    }
}
