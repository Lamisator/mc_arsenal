package net.antwire.arsenal.block;

import java.util.UUID;
import net.antwire.arsenal.config.ArsenalConfig;
import net.antwire.arsenal.explosive.Explosives;
import net.antwire.arsenal.registry.ModBlockEntities;
import net.antwire.arsenal.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class ClaymoreBlockEntity extends BlockEntity {
	/** Seconds after placing before the sensor is live: time to walk away. */
	public static final int ARM_TICKS = 100;
	public static final double SENSOR_RANGE = 6.5;
	private @Nullable UUID owner;
	private long armedAt;
	private boolean sensor = true;

	public ClaymoreBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.CLAYMORE, pos, state);
	}

	void placed(@Nullable LivingEntity placer, long now) {
		this.owner = placer == null ? null : placer.getUUID();
		this.armedAt = now + ARM_TICKS;
		this.setChanged();
	}

	boolean toggleSensor(long now) {
		this.sensor = !this.sensor;
		this.armedAt = now + ARM_TICKS;
		this.setChanged();
		return this.sensor;
	}

	void commandMode() {
		this.sensor = false;
		this.setChanged();
	}

	@Nullable Entity owner(ServerLevel level) {
		return this.owner == null ? null : level.getEntity(this.owner);
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, ClaymoreBlockEntity be) {
		if (!be.sensor || level.getGameTime() < be.armedAt || level.getGameTime() % 2 != 0 || !(level instanceof ServerLevel server)) {
			return;
		}
		Direction f = state.getValue(ClaymoreBlock.FACING);
		Vec3 front = new Vec3(f.getStepX(), 0, f.getStepZ());
		Vec3 eye = Vec3.atBottomCenterOf(pos).add(0, 0.4, 0);
		AABB box = new AABB(eye, eye.add(front.scale(SENSOR_RANGE))).inflate(4, 2, 4);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
			if (e instanceof ArmorStand || e.isSpectator() || e instanceof Player p && p.isCreative()) {
				continue;
			}
			if (ArsenalConfig.get().claymoreSparesOwner && e.getUUID().equals(be.owner)) {
				continue;
			}
			Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
			double d = to.length();
			if (d < 0.5 || d > SENSOR_RANGE) {
				continue;
			}
			// the sensor looks into a 70° fan in front
			Vec3 flat = new Vec3(to.x, 0, to.z).normalize();
			if (flat.dot(front) < Math.cos(Math.toRadians(35)) || Math.abs(to.y) > 2.5) {
				continue;
			}
			if (!Explosives.clear(server, eye, e.getBoundingBox().getCenter())) {
				continue;
			}
			level.playSound(null, pos, ModSounds.MINE_CLICK.value(), SoundSource.BLOCKS, 1.0F, 1.0F);
			Entity owner = be.owner(server);
			Explosives.claymore(server, pos, front, owner);
			return;
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.owner = input.read("owner", UUIDUtil.CODEC).orElse(null);
		this.armedAt = input.getLongOr("armed_at", 0L);
		this.sensor = input.getBooleanOr("sensor", true);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.storeNullable("owner", UUIDUtil.CODEC, this.owner);
		output.putLong("armed_at", this.armedAt);
		output.putBoolean("sensor", this.sensor);
	}
}
