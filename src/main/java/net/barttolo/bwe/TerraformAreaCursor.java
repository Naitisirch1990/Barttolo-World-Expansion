package net.barttolo.bwe;

import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Incremental, allocation-bounded traversal of a rectangular terraform job.
 * X changes first, then Z. No Minecraft dependency and no world edits here.
 *
 * The provider MUST return immutable, precomputed column plans: never derive
 * them from a world already being changed. A durable implementation must save
 * those plans, the original block states, and its checkpoint before writing.
 * Checkpoints alone are NOT crash-safe backups.
 */
public final class TerraformAreaCursor {
    @FunctionalInterface
    public interface ColumnPlans {
        ClearanceVolume.ColumnPlan at(int x, int z);
    }
    @FunctionalInterface
    public interface StepWriter {
        /** Throw on a failed or unverified write; cursor will not advance. */
        void write(Step step);
    }
    public record Step(int x, int z, TerraformColumnCursor.Phase phase, long y) {}
    public record Checkpoint(long columnIndex, long stepInColumn, long completedSteps) {}

    private final int minX, minZ, width, length;
    private final long columns;
    private final ColumnPlans plans;
    private long columnIndex;
    private long completedSteps;
    private TerraformColumnCursor column;

    public TerraformAreaCursor(int minX, int minZ, int width, int length, ColumnPlans plans) {
        if (width <= 0 || length <= 0
            || (long) minX + width - 1 > Integer.MAX_VALUE
            || (long) minZ + length - 1 > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Invalid terraform rectangle");
        this.minX = minX;
        this.minZ = minZ;
        this.width = width;
        this.length = length;
        this.columns = Math.multiplyExact((long) width, (long) length);
        this.plans = Objects.requireNonNull(plans, "plans");
        loadColumn();
    }

    private void loadColumn() {
        while (columnIndex < columns) {
            int x = (int) ((long) minX + columnIndex % width);
            int z = (int) ((long) minZ + columnIndex / width);
            column = new TerraformColumnCursor(Objects.requireNonNull(plans.at(x, z), "column plan"));
            if (column.hasNext()) return;
            columnIndex++;
        }
        column = null;
    }

    public long columns() { return columns; }
    public long completedSteps() { return completedSteps; }
    public boolean hasNext() { return column != null; }

    /** Does not consume the next step. */
    public Step peek() {
        if (!hasNext()) throw new NoSuchElementException("Terraform area completed");
        var next = column.peek();
        return new Step((int) ((long) minX + columnIndex % width),
            (int) ((long) minZ + columnIndex / width), next.phase(), next.y());
    }

    /** Call only after a verified, idempotent block operation. */
    public void advance() {
        if (!hasNext()) throw new NoSuchElementException("Terraform area completed");
        column.advance();
        completedSteps++;
        if (!column.hasNext()) {
            columnIndex++;
            loadColumn();
        }
    }

    /** Process at most budget operations. An exception leaves the failed step pending. */
    public int execute(int budget, StepWriter writer) {
        if (budget < 0) throw new IllegalArgumentException("Negative tick budget");
        Objects.requireNonNull(writer, "writer");
        int count = 0;
        while (count < budget && hasNext()) {
            writer.write(peek());
            advance();
            count++;
        }
        return count;
    }

    public Checkpoint checkpoint() {
        return new Checkpoint(columnIndex, column == null ? 0 : column.processed(), completedSteps);
    }

    /**
     * Resume only against the EXACT SAME immutable column plans. The block
     * writer must tolerate a replayed step after a crash (check BEFORE/AFTER).
     * This method does not persist a checkpoint or world snapshot itself.
     */
    public static TerraformAreaCursor resume(int minX, int minZ, int width, int length,
                                             ColumnPlans plans, Checkpoint checkpoint) {
        Objects.requireNonNull(checkpoint, "checkpoint");
        TerraformAreaCursor result = new TerraformAreaCursor(minX, minZ, width, length, plans);
        if (checkpoint.columnIndex() < 0 || checkpoint.columnIndex() > result.columns
            || checkpoint.stepInColumn() < 0 || checkpoint.completedSteps() < checkpoint.stepInColumn())
            throw new IllegalArgumentException("Invalid terraform checkpoint");
        result.columnIndex = checkpoint.columnIndex();
        result.completedSteps = checkpoint.completedSteps();
        result.loadColumn();
        if (result.columnIndex != checkpoint.columnIndex()) {
            if (checkpoint.stepInColumn() != 0)
                throw new IllegalArgumentException("Checkpoint targets empty column");
            return result;
        }
        if (!result.hasNext()) {
            if (checkpoint.stepInColumn() != 0)
                throw new IllegalArgumentException("Completed checkpoint has pending steps");
            return result;
        }
        long count = checkpoint.stepInColumn();
        if (count >= result.column.total())
            throw new IllegalArgumentException("Checkpoint exceeds column plan");
        for (long i = 0; i < count; i++) result.column.advance();
        return result;
    }
}
