package net.antwire.arsenal.block;

import net.antwire.arsenal.explosive.Explosives;
import net.antwire.arsenal.item.KnifeItem;
import net.antwire.arsenal.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A pressure mine lying flush with the ground. It arms itself a few seconds after it is laid; then stepping on it (or,
 * for an anti-tank mine, driving over it) sets it off. Digging it up the hard way does too: disarm it by sneaking and
 * right clicking it with a knife.
 */
public class MineBlock extends Block implements Detonatable {
	public static final BooleanProperty ARMED = BooleanProperty.create("armed");
	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
	public final Kind kind;

	public enum Kind {
		/** M14-style blast mine against people. */
		ANTI_PERSONNEL(Block.box(5, 0, 5, 11, 1.5, 11)),
		/** TM-62: needs the weight of a vehicle or a big animal. */
		ANTI_TANK(Block.box(2, 0, 2, 14, 3, 14));

		final VoxelShape shape;

		Kind(VoxelShape shape) {
			this.shape = shape;
		}
	}

	public MineBlock(Kind kind, Properties properties) {
		super(properties);
		this.kind = kind;
		this.registerDefaultState(this.stateDefinition.any().setValue(ARMED, false).setValue(POWERED, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(ARMED, POWERED);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return this.kind.shape;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return Shapes.empty();
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		if (!state.getValue(ARMED) && !oldState.is(this)) {
			level.scheduleTick(pos, this, 60);
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!state.getValue(ARMED)) {
			level.setBlock(pos, state.setValue(ARMED, true), Block.UPDATE_CLIENTS);
			level.playSound(null, pos, ModSounds.MINE_ARM.value(), SoundSource.BLOCKS, 0.5F, 1.4F);
		}
	}

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean precise) {
		if (!state.getValue(ARMED) || !(level instanceof ServerLevel server) || !this.heavyEnough(entity)) {
			return;
		}
		level.playSound(null, pos, ModSounds.MINE_CLICK.value(), SoundSource.BLOCKS, 1.0F, 1.0F);
		this.explode(server, pos, entity);
	}

	private boolean heavyEnough(Entity entity) {
		if (entity instanceof ArmorStand || entity.isSpectator() || entity instanceof Player p && p.isCreative()) {
			return false;
		}
		if (this.kind == Kind.ANTI_PERSONNEL) {
			return entity instanceof LivingEntity;
		}
		// a person is far too light for an anti-tank mine, a horse with rider or a cart is not
		return entity instanceof VehicleEntity || entity instanceof LivingEntity living && (living.getBbWidth() >= 1.3F || living.isVehicle());
	}

	private void explode(ServerLevel level, BlockPos pos, @Nullable Entity victim) {
		if (this.kind == Kind.ANTI_PERSONNEL) {
			Explosives.apMine(level, pos, victim);
		} else {
			Explosives.atMine(level, pos, victim);
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
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (state.getValue(ARMED) && !player.isCreative() && level instanceof ServerLevel server) {
			this.explode(server, pos, player);
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	@Override
	public void detonate(ServerLevel level, BlockPos pos, @Nullable Entity cause) {
		if (level.getBlockState(pos).is(this)) {
			this.explode(level, pos, null);
		}
	}

	@Override
	public void chainDetonate(ServerLevel level, BlockPos pos, BlockState state, @Nullable Entity cause) {
		this.explode(level, pos, null);
	}

	@Override
	protected void onExplosionHit(BlockState state, ServerLevel level, BlockPos pos, Explosion explosion, java.util.function.BiConsumer<net.minecraft.world.item.ItemStack, BlockPos> onHit) {
		if (explosion.getBlockInteraction() != Explosion.BlockInteraction.TRIGGER_BLOCK) {
			level.removeBlock(pos, false);
			Explosives.chain(level, pos, state, explosion.getIndirectSourceEntity());
		}
	}
}
