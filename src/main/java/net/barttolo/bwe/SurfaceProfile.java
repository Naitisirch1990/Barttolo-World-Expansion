package net.barttolo.bwe;

/** Exact height histogram for a bounded surface survey. No Minecraft dependency. */
public final class SurfaceProfile {
    private final int minAllowed, maxAllowed;
    private final long[] heights;
    private long count;
    private int low = Integer.MAX_VALUE, high = Integer.MIN_VALUE;

    public SurfaceProfile(int minAllowed, int maxAllowed) {
        if (maxAllowed < minAllowed || (long)maxAllowed - minAllowed > 4096L)
            throw new IllegalArgumentException("Invalid height range");
        this.minAllowed = minAllowed;
        this.maxAllowed = maxAllowed;
        this.heights = new long[maxAllowed - minAllowed + 1];
    }
    public void add(int y) {
        if (y < minAllowed || y > maxAllowed)
            throw new IllegalArgumentException("Height outside build range: " + y);
        heights[y - minAllowed]++;
        count++;
        low = Math.min(low, y);
        high = Math.max(high, y);
    }
    public long count() { return count; }
    public int low() { requireSamples(); return low; }
    public int high() { requireSamples(); return high; }
    /** Lower median, robust against isolated peaks and deep pits. */
    public int median() {
        requireSamples();
        long target = (count + 1L) / 2L, seen = 0;
        for (int i = 0; i < heights.length; i++) {
            seen += heights[i];
            if (seen >= target) return i + minAllowed;
        }
        throw new IllegalStateException("Corrupt histogram");
    }
    /** Approximate vertical blocks to cut above the selected level. */
    public long cut(int targetY) {
        requireSamples();
        long sum = 0;
        for (int i = 0; i < heights.length; i++)
            if (minAllowed + i > targetY)
                sum = Math.addExact(sum, Math.multiplyExact(heights[i], (long)minAllowed + i - targetY));
        return sum;
    }
    /** Approximate vertical blocks to fill below the selected level. */
    public long fill(int targetY) {
        requireSamples();
        long sum = 0;
        for (int i = 0; i < heights.length; i++)
            if (minAllowed + i < targetY)
                sum = Math.addExact(sum, Math.multiplyExact(heights[i], (long)targetY - minAllowed - i));
        return sum;
    }
    private void requireSamples() {
        if (count == 0) throw new IllegalStateException("No surface samples");
    }
}
