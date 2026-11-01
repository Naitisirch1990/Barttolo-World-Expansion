package net.barttolo.bwe;

/** Geometry only: no world reads or Forge dependency. */
public final class SiteMath {
    private SiteMath() {}
    /** Circle/rectangle overlap with inclusive bounds. Radius and coordinates use longs
     *  until verified, avoiding overflow at Minecraft's extreme X/Z values. */
    public static boolean circleIntersectsRect(int cx,int cz,int radius,
                                                int x0,int z0,int x1,int z1) {
        if(radius < 0 || x0>x1 || z0>z1) throw new IllegalArgumentException("Invalid circle or rectangle");
        long nearestX = Math.max((long)x0, Math.min((long)cx, (long)x1));
        long nearestZ = Math.max((long)z0, Math.min((long)cz, (long)z1));
        long dx=(long)cx-nearestX, dz=(long)cz-nearestZ;
        if(Math.abs(dx)>radius || Math.abs(dz)>radius) return false;
        return dx*dx+dz*dz <= (long)radius*radius;
    }
    /** Is A within the safety margin of B? Rectangle bounds are inclusive. */
    public static boolean overlaps(int ax0, int az0, int ax1, int az1,
                                   int bx0, int bz0, int bx1, int bz1, int margin) {
        if (ax0 > ax1 || az0 > az1 || bx0 > bx1 || bz0 > bz1 || margin < 0)
            throw new IllegalArgumentException("Invalid area or separation margin");
        return (long) ax1 + margin >= bx0 && (long) ax0 - margin <= bx1
            && (long) az1 + margin >= bz0 && (long) az0 - margin <= bz1;
    }
}
