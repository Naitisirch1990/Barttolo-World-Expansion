package net.barttolo.bwe;

import java.util.NoSuchElementException;
import java.util.Objects;

/** Allocation-bounded traversal of a single column's demolition and foundation work.
 * No Minecraft dependencies and NO world edits. The caller must only advance
 * after the block operation succeeds, so failed writes never skip positions.
 * Clearing precedes filling; both intervals are inclusive.
 */
public final class TerraformColumnCursor {
    public enum Phase { CLEAR, FILL }
    public record Step(Phase phase, long y) {}

    private final ClearanceVolume.ColumnPlan plan;
    private Phase phase;
    private long y;
    private long processed;

    public TerraformColumnCursor(ClearanceVolume.ColumnPlan plan) {
        this.plan = Objects.requireNonNull(plan, "plan");
        if (plan.clearPositions() > 0) {
            phase = Phase.CLEAR;
            y = plan.clearFromY();
        } else if (plan.fillPositions() > 0) {
            phase = Phase.FILL;
            y = plan.fillFromY();
        }
    }

    public boolean hasNext() { return phase != null; }
    public long total() { return plan.totalPositions(); }
    public long processed() { return processed; }

    /** Does not consume the step. Recheck protections/chunks and write first. */
    public Step peek() {
        if (!hasNext()) throw new NoSuchElementException("Terraform column completed");
        return new Step(phase, y);
    }

    /** Commit one successfully handled step; never call before the world edit. */
    public void advance() {
        if (!hasNext()) throw new NoSuchElementException("Terraform column completed");
        processed++;
        if (phase == Phase.CLEAR) {
            if (y < plan.clearToY()) y++;
            else if (plan.fillPositions() > 0) {
                phase = Phase.FILL;
                y = plan.fillFromY();
            } else phase = null;
        } else {
            if (y < plan.fillToY()) y++;
            else phase = null;
        }
    }
}
