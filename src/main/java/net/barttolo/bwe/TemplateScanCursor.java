package net.barttolo.bwe;

/** Pure Java 17 cursor over a single template's X/Z footprint and vertical extent.
 *  No Minecraft APIs, chunk loads, or world mutations. */
public final class TemplateScanCursor {
    public record Position(int x, int y, int z) {}
    private final TemplateFootprint.Bounds bounds;
    private final int minY;
    private final long width, plane, total;
    private long index;

    public TemplateScanCursor(TemplateFootprint.Bounds bounds, int minY, int maxY) {
        if (bounds == null || !bounds.withinMinecraftCoordinates())
            throw new IllegalArgumentException("Huella fuera de coordenadas Minecraft");
        if (minY > maxY)
            throw new IllegalArgumentException("Intervalo vertical invalido");
        this.bounds = bounds;
        this.minY = minY;
        this.width = bounds.width();
        this.plane = Math.multiplyExact(width, bounds.length());
        this.total = Math.multiplyExact(plane, (long)maxY - minY + 1L);
    }

    public long total() { return total; }
    public long processed() { return index; }
    public boolean hasNext() { return index < total; }

    /** Enumerates Y planes, then Z rows, then X blocks; safe at negative coordinates. */
    public Position next() {
        if (!hasNext()) throw new java.util.NoSuchElementException("Auditoria finalizada");
        long n = index++;
        int y = (int)((long)minY + n / plane);
        int z = (int)(bounds.minZ() + (n % plane) / width);
        int x = (int)(bounds.minX() + (n % width));
        return new Position(x, y, z);
    }
}
