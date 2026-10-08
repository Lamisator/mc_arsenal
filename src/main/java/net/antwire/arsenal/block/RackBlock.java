package net.antwire.arsenal.block;

import net.antwire.arsenal.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Racks to put your weapons on show: a wall rack holding three long guns one above the other, and a floor stand
 * with five upright. Right click with a weapon to put it up, with an empty hand to take one down.
 */
public class RackBlock extends BaseEntityBlock {
	public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
	public final Style style;

	public enum Style {
		WALL(3), FLOOR(5);

		public final int slots;

		Style(int slots) {
			this.slots = slots;
		}
	}

	public RackBlock(Style style, Properties properties) {
		super(properties);
		this.style = style;
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction face = context.getClickedFace();
		if (this.style == Style.WALL && face.getAxis().isHorizontal()) {
			return this.defaultBlockState().setValue(FACING, face);
		}
		return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
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
		Direction f = state.getValue(FACING);
		if (this.style == Style.WALL) {
			return switch (f) {
				case SOUTH -> Block.box(0, 0, 0, 16, 16, 5);
				case WEST -> Block.box(11, 0, 0, 16, 16, 16);
				case EAST -> Block.box(0, 0, 0, 5, 16, 16);
				default -> Block.box(0, 0, 11, 16, 16, 16);
			};
		}
		VoxelShape base = Block.box(0, 0, 3, 16, 3, 13);
		VoxelShape back = Block.box(0, 12, 6, 16, 15, 10);
		VoxelShape shape = Shapes.or(base, back, Block.box(0, 0, 6, 2, 15, 10), Block.box(14, 0, 6, 16, 15, 10));
		return f.getAxis() == Direction.Axis.Z ? shape : Shapes.or(Block.box(3, 0, 0, 13, 3, 16), Block.box(6, 12, 0, 10, 15, 16),
			Block.box(6, 0, 0, 10, 15, 2), Block.box(6, 0, 14, 10, 15, 16));
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new RackBlockEntity(pos, state, this.style.slots);
	}

	/** Which slot a click at {@code hit} points at. */
	public int slotAt(BlockState state, BlockPos pos, Vec3 hit) {
		Vec3 local = hit.subtract(Vec3.atLowerCornerOf(pos));
		if (this.style == Style.WALL) {
			int row = (int) ((1.0 - local.y) * 3);
			return Math.clamp(row, 0, 2);
		}
		Direction right = state.getValue(FACING).getClockWise();
		double along = (local.x - 0.5) * right.getStepX() + (local.z - 0.5) * right.getStepZ();
		return Math.clamp((int) ((along + 0.5) * 5), 0, 4);
	}

	public static boolean rackable(ItemStack stack) {
		return !stack.isEmpty() && stack.getMaxStackSize() == 1;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		if (!rackable(stack) || !(level.getBlockEntity(pos) instanceof RackBlockEntity rack)) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		int slot = rack.freeNear(this.slotAt(state, pos, hit.getLocation()));
		if (slot < 0) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (!level.isClientSide()) {
			rack.setItem(slot, stack.copyWithCount(1));
			if (!player.hasInfiniteMaterials()) {
				stack.shrink(1);
			}
			level.playSound(null, pos, ModSounds.MAG_IN.value(), SoundSource.BLOCKS, 0.5F, 0.8F);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!(level.getBlockEntity(pos) instanceof RackBlockEntity rack)) {
			return InteractionResult.PASS;
		}
		int slot = rack.takenNear(this.slotAt(state, pos, hit.getLocation()));
		if (slot < 0) {
			return InteractionResult.PASS;
		}
		if (!level.isClientSide()) {
			ItemStack taken = rack.removeItemNoUpdate(slot);
			rack.setChanged();
			if (!player.addItem(taken)) {
				player.spawnAtLocation((net.minecraft.server.level.ServerLevel) level, taken);
			}
			level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.4F, 1.2F);
		}
		return InteractionResult.SUCCESS;
	}
}
