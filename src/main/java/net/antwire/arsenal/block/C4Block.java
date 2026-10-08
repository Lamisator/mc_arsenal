package net.antwire.arsenal.block;

import net.antwire.arsenal.explosive.Explosives;
import net.antwire.arsenal.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * An M112 block of C4 with a blasting cap, stuck to any surface. Set off by a firing device it is wired to, by a
 * redstone signal, or by another explosion.
 */
public class C4Block extends Block implements Detonatable {
	public static final EnumProperty<Direction> FACING = DirectionalBlock.FACING;

	public C4Block(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.UP));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState state = this.defaultBlockState().setValue(FACING, context.getClickedFace());
		return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return switch (state.getValue(FACING)) {
			case UP -> Block.box(3, 0, 5, 13, 3, 11);
			case DOWN -> Block.box(3, 13, 5, 13, 16, 11);
			case NORTH -> Block.box(3, 5, 13, 13, 11, 16);
			case SOUTH -> Block.box(3, 5, 0, 13, 11, 3);
			case WEST -> Block.box(13, 5, 3, 16, 11, 13);
			case EAST -> Block.box(0, 5, 3, 3, 11, 13);
		};
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		Direction f = state.getValue(FACING);
		BlockPos support = pos.relative(f.getOpposite());
		return level.getBlockState(support).isFaceSturdy(level, support, f);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction dir, BlockPos neighbourPos,
		BlockState neighbour, RandomSource random) {
		return dir == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState() : state;
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean moved) {
		if (level instanceof ServerLevel server && level.hasNeighborSignal(pos)) {
			level.playSound(null, pos, ModSounds.MINE_CLICK.value(), SoundSource.BLOCKS, 1.0F, 1.6F);
			Explosives.c4(server, pos, null);
		}
	}

	@Override
	public void detonate(ServerLevel level, BlockPos pos, @Nullable Entity cause) {
		if (level.getBlockState(pos).is(this)) {
			Explosives.c4(level, pos, cause);
		}
	}

	@Override
	public boolean linkable() {
		return true;
	}

	@Override
	public void chainDetonate(ServerLevel level, BlockPos pos, BlockState state, @Nullable Entity cause) {
		Explosives.c4(level, pos, cause);
	}

	@Override
	protected void onExplosionHit(BlockState state, ServerLevel level, BlockPos pos, Explosion explosion, java.util.function.BiConsumer<net.minecraft.world.item.ItemStack, BlockPos> onHit) {
		if (explosion.getBlockInteraction() != Explosion.BlockInteraction.TRIGGER_BLOCK) {
			level.removeBlock(pos, false);
			Explosives.chain(level, pos, state, explosion.getIndirectSourceEntity());
		}
	}
}
