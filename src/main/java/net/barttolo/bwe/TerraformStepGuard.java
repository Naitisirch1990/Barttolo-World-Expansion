package net.barttolo.bwe;

import java.util.Objects;

/**
 * Fail-closed, idempotent block-write gate for a future Forge terraform job.
 * Pure Java 17: does not edit Minecraft worlds without a caller-supplied adapter.
 *
 * Before invoking this gate, the caller MUST persist immutable column plans
 * and the complete original block-state snapshot on disk. Neither the cursor
 * nor this guard supplies that persistence. Block-entity NBT and entities are
 * intentionally outside the rollback contract.
 *
 * The adapter's requireSafe MUST reject unloaded chunks, world-border/spawn
 * exclusions, existing protected sites, and any player in the affected area.
 * All calls must run on the server thread, not asynchronously.
 */
public final class TerraformStepGuard<S> implements TerraformAreaCursor.StepWriter {
    public enum Outcome { WRITTEN, ALREADY_TARGET }

    public interface WorldAccess<S> {
        /** Throw if this location may not be changed RIGHT NOW. */
        void requireSafe(TerraformAreaCursor.Step step);
        /** Return the durable BEFORE state; never sample a partly modified world. */
        S savedBefore(TerraformAreaCursor.Step step);
        /** Return air for CLEAR, solid foundation for FILL. */
        S targetState(TerraformAreaCursor.Step step);
        S currentState(TerraformAreaCursor.Step step);
        /** Return false if Minecraft rejected the write. */
        boolean setState(TerraformAreaCursor.Step step, S state);
    }

    private final WorldAccess<S> world;

    public TerraformStepGuard(WorldAccess<S> world) {
        this.world = Objects.requireNonNull(world, "world");
    }

    @Override public void write(TerraformAreaCursor.Step step) { apply(step); }

    /**
     * A failed operation throws, so TerraformAreaCursor.execute will not advance.
     * A crash after a successful write but before a cursor checkpoint is safe to
     * retry: an already-correct target is accepted without a duplicate setBlock.
     */
    public Outcome apply(TerraformAreaCursor.Step step) {
        Objects.requireNonNull(step, "step");
        world.requireSafe(step);
        S before = Objects.requireNonNull(world.savedBefore(step), "Missing durable BEFORE state");
        S target = Objects.requireNonNull(world.targetState(step), "Missing target state");
        S current = Objects.requireNonNull(world.currentState(step), "Missing current state");
        if (Objects.equals(current, target)) return Outcome.ALREADY_TARGET;
        if (!Objects.equals(current, before))
            throw new IllegalStateException("Block changed outside terraform plan at " + step);

        // Revalidate immediately before the write; never overwrite a player edit
        // that occurred between the first read and the actual setBlock call.
        world.requireSafe(step);
        if (!Objects.equals(world.currentState(step), before))
            throw new IllegalStateException("Block changed during terraform write at " + step);
        if (!world.setState(step, target))
            throw new IllegalStateException("World rejected terraform write at " + step);
        if (!Objects.equals(world.currentState(step), target))
            throw new IllegalStateException("Terraform post-write verification failed at " + step);
        return Outcome.WRITTEN;
    }
}
