package net.antwire.arsenal.block;

import net.antwire.arsenal.explosive.Explosives;
import net.antwire.arsenal.item.KnifeItem;
import net.antwire.arsenal.registry.ModBlockEntities;
import net.antwire.arsenal.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * M18A1 claymore on its scissor legs, "FRONT TOWARD ENEMY". The sensor fires it at anyone who walks into its fan;
 * wired to a firing device it waits for the clacker instead. Sneak + right click toggles the sensor, sneak + right
 * click with a knife disarms and picks it up.
 */
public class ClaymoreBlock extends BaseEntityBlock implements Detonatable {
	public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
	private static final VoxelShape NS = Shapes.or(Block.box(3.5, 2, 6.5, 12.5, 8, 9.5), Block.box(4, 0, 7, 5, 2, 9), Block.box(11, 0, 7, 12, 2, 9));
	private static final VoxelShape EW = Shapes.or(Block.box(6.5, 2, 3.5, 9.5, 8, 12.5), Block.box(7, 0, 4, 9, 2, 5), Block.box(7, 0, 11, 9, 2, 12));

	public ClaymoreBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection());
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
		return state.getValue(FACING).getAxis() == Direction.Axis.Z ? NS : EW;
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ClaymoreBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() ? null : createTickerHelper(type, ModBlockEntities.CLAYMORE, ClaymoreBlockEntity::serverTick);
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ClaymoreBlockEntity claymore) {
			claymore.placed(placer, level.getGameTime());
			level.playSound(null, pos, ModSounds.MINE_ARM.value(), SoundSource.BLOCKS, 0.8F, 1.0F);
		}
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		if (stack.getItem() instanceof KnifeItem && player.isShiftKeyDown()) {
			if (!level.isClientSide()) {
				level.removeBlock(pos, false);
				Block.popResource(level, pos, new ItemStack(this));
				level.playSound(null, pos, ModSounds.MINE_CLICK.value(), SoundSource.BLOCKS, 0.8F, 1.3F);
				player.sendOverlayMessage(Component.translatable("message.arsenal.disarmed"));
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!player.isShiftKeyDown()) {
			return InteractionResult.PASS;
		}
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ClaymoreBlockEntity claymore) {
			boolean sensor = claymore.toggleSensor(level.getGameTime());
			player.sendOverlayMessage(Component.translatable(sensor ? "message.arsenal.sensor_on" : "message.arsenal.sensor_off"));
			level.playSound(null, pos, ModSounds.MINE_ARM.value(), SoundSource.BLOCKS, 0.6F, sensor ? 1.2F : 0.8F);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void detonate(ServerLevel level, BlockPos pos, @Nullable Entity cause) {
		BlockState state = level.getBlockState(pos);
		if (!state.is(this)) {
			return;
		}
		Entity owner = level.getBlockEntity(pos) instanceof ClaymoreBlockEntity c ? c.owner(level) : null;
		Direction f = state.getValue(FACING);
		Explosives.claymore(level, pos, new Vec3(f.getStepX(), 0, f.getStepZ()), owner != null ? owner : cause);
	}

	@Override
	public boolean linkable() {
		return true;
	}

	@Override
	public void linked(ServerLevel level, BlockPos pos) {
		if (level.getBlockEntity(pos) instanceof ClaymoreBlockEntity claymore) {
			claymore.commandMode();
		}
	}

	@Override
	public void chainDetonate(ServerLevel level, BlockPos pos, BlockState state, @Nullable Entity cause) {
		Direction f = state.getValue(FACING);
		Explosives.claymore(level, pos, new Vec3(f.getStepX(), 0, f.getStepZ()), cause);
	}

	@Override
	protected void onExplosionHit(BlockState state, ServerLevel level, BlockPos pos, Explosion explosion, java.util.function.BiConsumer<net.minecraft.world.item.ItemStack, BlockPos> onHit) {
		if (explosion.getBlockInteraction() != Explosion.BlockInteraction.TRIGGER_BLOCK) {
			level.removeBlock(pos, false);
			Explosives.chain(level, pos, state, explosion.getIndirectSourceEntity());
		}
	}
}
