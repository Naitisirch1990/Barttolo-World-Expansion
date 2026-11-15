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

    /** Deterministic geometry for one column of a future destructive terraform job.
     *  groundY is the LAST SOLID supporting block (not the first free Y returned by TerrainColumnProbe); occupiedTopY is the highest
     *  obstruction (roof/tree included), not necessarily the ground surface.
     *  Clearing includes AIR positions deliberately: it is an upper work bound,
     *  not a prediction of how many setBlock calls will be necessary.
     *  No chunks are accessed and this method NEVER edits a world.
     */
    public record ColumnPlan(long clearFromY, long clearToY,
                             long fillFromY, long fillToY) {
        public long clearPositions() { return Math.max(0L, clearToY - clearFromY + 1L); }
        public long fillPositions() { return Math.max(0L, fillToY - fillFromY + 1L); }
        public long totalPositions() { return Math.addExact(clearPositions(), fillPositions()); }
    }

    /** Prepare a column by clearing from the proposed foundation upward and
     *  filling the gap between natural ground and the foundation with solid support.
     *  The existing top obstruction must be cleared even when it is taller than
     *  the template plus overhead. Height limits are checked before any work.
     *  The last buildable Y is maxBuildHeight - 1 (exclusive upper bound).
     */
    public static ColumnPlan planColumn(int groundY, int occupiedTopY, int foundationY,
                                        int templateHeight, int overheadMargin,
                                        int minBuildHeight, int maxBuildHeight) {
        if (maxBuildHeight <= minBuildHeight || templateHeight < 1 || overheadMargin < 0
            || groundY < minBuildHeight || occupiedTopY < groundY
            || occupiedTopY >= maxBuildHeight
            || foundationY < minBuildHeight || foundationY >= maxBuildHeight)
            throw new IllegalArgumentException("Invalid terraform column or build height");
        long roof = (long) foundationY + templateHeight + overheadMargin - 1L;
        long clearTop = Math.max(roof, (long) occupiedTopY);
        if (clearTop >= maxBuildHeight)
            throw new IllegalArgumentException("Terraform clearance exceeds world ceiling");
        long fillStart = (long) groundY + 1L;
        long fillEnd = (long) foundationY - 1L;
        return new ColumnPlan(foundationY, clearTop, fillStart, fillEnd);
    }

    /** TerrainColumnProbe.Result.groundY() is the FIRST FREE Y above ground,
     *  while planColumn requires the LAST SOLID Y. Convert explicitly so
     *  a raised foundation does not retain a one-block air gap beneath it.
     *  occupiedTopY is the highest occupied block (heightmap first-free Y - 1).
     *  Read-only geometry: no chunks accessed or blocks modified.
     */
    public static ColumnPlan planColumnFromSurface(int firstFreeY, int occupiedTopY,
                                                   int foundationY, int templateHeight,
                                                   int overheadMargin, int minBuildHeight,
                                                   int maxBuildHeight) {
        if (firstFreeY <= minBuildHeight || firstFreeY > maxBuildHeight)
            throw new IllegalArgumentException("Surface has no valid supporting block");
        return planColumn(firstFreeY - 1, occupiedTopY, foundationY,
            templateHeight, overheadMargin, minBuildHeight, maxBuildHeight);
    }
}
