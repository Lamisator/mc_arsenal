package net.antwire.arsenal.entity;

import java.util.UUID;
import net.antwire.arsenal.explosive.Explosives;
import net.antwire.arsenal.item.GrenadeItem;
import net.antwire.arsenal.registry.ModEntities;
import net.antwire.arsenal.registry.ModItems;
import net.antwire.arsenal.registry.ModSounds;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** A thrown grenade: it flies, bounces, rolls to a stop and goes off when its fuse has burnt down. */
public class GrenadeEntity extends Entity implements ItemSupplier {
	private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(GrenadeEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> SMOKE = SynchedEntityData.defineId(GrenadeEntity.class, EntityDataSerializers.INT);
	/** M18: about 50 to 90 seconds of smoke. */
	public static final int SMOKE_TICKS = 1200;
	private static final double GRAVITY = 0.035;
	private int fuse;
	private @Nullable UUID ownerId;
	public float spin;
	public float oSpin;

	public GrenadeEntity(EntityType<? extends GrenadeEntity> type, Level level) {
		super(type, level);
	}

	public GrenadeEntity(Level level, GrenadeItem.Kind kind, @Nullable Entity owner, Vec3 at, Vec3 velocity, int fuse) {
		this(ModEntities.GRENADE, level);
		this.entityData.set(KIND, kind.ordinal());
		this.setPos(at.x, at.y, at.z);
		this.setDeltaMovement(velocity);
		this.fuse = fuse;
		this.ownerId = owner == null ? null : owner.getUUID();
	}

	public GrenadeItem.Kind kind() {
		return GrenadeItem.Kind.byOrdinal(this.entityData.get(KIND));
	}

	/** Ticks of smoke left, 0 if not (yet) smoking. */
	public int smoke() {
		return this.entityData.get(SMOKE);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(KIND, 0);
		builder.define(SMOKE, 0);
	}

	@Override
	public ItemStack getItem() {
		return new ItemStack(switch (this.kind()) {
			case FRAG -> ModItems.FRAG_GRENADE;
			case FLASHBANG -> ModItems.FLASHBANG;
			case SMOKE -> ModItems.SMOKE_GRENADE;
		});
	}

	@Override
	public void tick() {
		super.tick();
		Vec3 v = this.getDeltaMovement();
		if (!this.onGround() || v.lengthSqr() > 1.0E-4) {
			v = v.add(0, -GRAVITY, 0);
		}
		if (this.isInWater()) {
			v = v.scale(0.8).add(0, 0.01, 0);
		} else {
			v = v.scale(0.99);
		}
		Vec3 before = v;
		this.move(MoverType.SELF, v);
		Vec3 after = this.getDeltaMovement();
		// bounce off what it hit, losing most of its speed
		double bx = this.horizontalCollision && Math.abs(after.x) < Math.abs(before.x) * 0.5 ? -before.x * 0.35 : before.x;
		double bz = this.horizontalCollision && Math.abs(after.z) < Math.abs(before.z) * 0.5 ? -before.z * 0.35 : before.z;
		double by = before.y;
		if (this.verticalCollision) {
			by = Math.abs(before.y) < 0.12 ? 0 : -before.y * 0.3;
			bx *= 0.7;
			bz *= 0.7;
		}
		if ((this.horizontalCollision || this.verticalCollision) && before.length() > 0.12 && !this.level().isClientSide()) {
			this.level().playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.GRENADE_BOUNCE.value(), SoundSource.PLAYERS,
				(float) Math.min(1.0, before.length()), 0.9F + this.random.nextFloat() * 0.2F);
		}
		if (this.onGround()) {
			// rolling friction
			bx *= 0.85;
			bz *= 0.85;
		}
		this.setDeltaMovement(bx, by, bz);
		this.oSpin = this.spin;
		this.spin += (float) (new Vec3(bx, by, bz).length() * 40);

		if (this.level() instanceof ServerLevel level) {
			if (this.smoke() > 0) {
				this.entityData.set(SMOKE, this.smoke() - 1);
				if (this.smoke() % 40 == 0) {
					level.playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.SMOKE_HISS.value(), SoundSource.PLAYERS, 0.7F, 1.0F);
				}
				if (this.smoke() <= 1) {
					this.discard();
				}
				return;
			}
			if (--this.fuse <= 0) {
				this.detonate(level);
			}
		}
	}

	private void detonate(ServerLevel level) {
		Entity owner = this.ownerId == null ? null : level.getEntity(this.ownerId);
		switch (this.kind()) {
			case FRAG -> {
				Explosives.fragGrenade(level, this.position(), this, owner);
				this.discard();
			}
			case FLASHBANG -> {
				Explosives.flashbang(level, this.position(), owner);
				this.discard();
			}
			case SMOKE -> {
				this.entityData.set(SMOKE, SMOKE_TICKS);
				level.playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.SMOKE_HISS.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
			}
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return false;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 128.0 * 128.0;
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		this.entityData.set(KIND, input.getIntOr("kind", 0));
		this.entityData.set(SMOKE, input.getIntOr("smoke", 0));
		this.fuse = input.getIntOr("fuse", 40);
		this.ownerId = input.read("owner", UUIDUtil.CODEC).orElse(null);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.putInt("kind", this.entityData.get(KIND));
		output.putInt("smoke", this.smoke());
		output.putInt("fuse", this.fuse);
		output.storeNullable("owner", UUIDUtil.CODEC, this.ownerId);
	}
}
