package net.antwire.arsenal.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** A charge that goes off when told to: by a firing device, a bullet, fire or a nearby explosion. */
public interface Detonatable {
	void detonate(ServerLevel level, BlockPos pos, @Nullable Entity cause);

	/** Set off by another explosion; the block may already be gone, {@code state} is what it was. */
	void chainDetonate(ServerLevel level, BlockPos pos, BlockState state, @Nullable Entity cause);

	/** Whether a firing device can be wired to it. */
	default boolean linkable() {
		return false;
	}

	/** Called when a firing device is wired to it. */
	default void linked(ServerLevel level, BlockPos pos) {
	}
}
