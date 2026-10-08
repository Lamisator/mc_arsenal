package net.antwire.arsenal.entity;

import java.util.Optional;
import java.util.UUID;
import net.antwire.arsenal.explosive.Explosives;
import net.antwire.arsenal.gun.GunType;
import net.antwire.arsenal.registry.ModEntities;
import net.antwire.arsenal.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;

/**
 * An RPG-7 rocket or a Javelin missile in flight.
 * <ul>
 * <li>PG-7V: kicked out at 115 m/s, the sustainer lights after about 11 m and pushes it to 295 m/s. Unguided; it
 * destroys itself after 920 m.</li>
 * <li>Javelin: soft launch (the motor lights only clear of the gunner), climbs to about 150 m and dives onto the top
 * of its target. Fire and forget: it follows a locked entity on its own.</li>
 * </ul>
 */
public class RocketEntity extends Entity implements ItemSupplier {
	private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> BURNING = SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.BOOLEAN);
	private @Nullable UUID ownerId;
	private @Nullable UUID targetId;
	private @Nullable BlockPos targetPos;
	private boolean diving;
	private int age;

	public RocketEntity(EntityType<? extends RocketEntity> type, Level level) {
		super(type, level);
	}

	public static RocketEntity rpg(ServerLevel level, Entity owner, Vec3 at, Vec3 dir) {
		RocketEntity r = new RocketEntity(ModEntities.ROCKET, level);
		r.entityData.set(KIND, GunType.Rocket.PG7V.ordinal());
		r.setPos(at.x, at.y, at.z);
		r.setDeltaMovement(dir.scale(115.0 / 20.0));
		r.ownerId = owner.getUUID();
		r.face(dir);
		return r;
	}

	public static RocketEntity javelin(ServerLevel level, Entity owner, Vec3 at, Vec3 dir, @Nullable Entity target, @Nullable BlockPos targetPos) {
		RocketEntity r = new RocketEntity(ModEntities.ROCKET, level);
		r.entityData.set(KIND, GunType.Rocket.JAVELIN.ordinal());
		r.setPos(at.x, at.y, at.z);
		// the launch motor only pushes it a few metres out of the tube
		r.setDeltaMovement(dir.scale(1.2).add(0, 0.25, 0));
		r.ownerId = owner.getUUID();
		r.targetId = target == null ? null : target.getUUID();
		r.targetPos = target == null ? targetPos : target.blockPosition();
		r.face(dir);
		return r;
	}

	public GunType.Rocket kind() {
		GunType.Rocket[] all = GunType.Rocket.values();
		return all[Math.floorMod(this.entityData.get(KIND), all.length)];
	}

	/** Motor burning: the client draws flame and smoke. */
	public boolean burning() {
		return this.entityData.get(BURNING);
	}

	private void face(Vec3 dir) {
		double h = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
		this.setYRot((float) (Mth.atan2(dir.x, dir.z) * Mth.RAD_TO_DEG));
		this.setXRot((float) (Mth.atan2(dir.y, h) * Mth.RAD_TO_DEG));
		this.yRotO = this.getYRot();
		this.xRotO = this.getXRot();
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(KIND, 0);
		builder.define(BURNING, false);
	}

	@Override
	public ItemStack getItem() {
		return new ItemStack(this.kind() == GunType.Rocket.PG7V ? ModItems.PG7V : ModItems.JAVELIN_MISSILE);
	}

	@Override
	public void tick() {
		super.tick();
		this.age++;
		Vec3 v = this.getDeltaMovement();
		if (!this.level().isClientSide()) {
			v = this.kind() == GunType.Rocket.PG7V ? this.flyRpg(v) : this.flyJavelin(v);
		} else if (this.burning() || this.kind() == GunType.Rocket.PG7V) {
			// between server updates the client carries on in a straight line
		}
		Vec3 from = this.position();
		Vec3 to = from.add(v);
		if (this.level() instanceof ServerLevel level) {
			Entity locked = this.targetId == null ? null : level.getEntity(this.targetId);
			if (locked != null && locked.isAlive() && this.age > 10 && locked.getBoundingBox().inflate(1.5).clip(from, to).isPresent()) {
				this.detonate(level, from.add(v.scale(0.5)), locked);
				return;
			}
			HitResult hit = this.hit(level, from, to);
			if (hit != null) {
				Entity direct = hit instanceof net.minecraft.world.phys.EntityHitResult eh ? eh.getEntity() : null;
				this.detonate(level, hit.getLocation().subtract(v.normalize().scale(0.3)), direct);
				return;
			}
			int maxAge = this.kind() == GunType.Rocket.PG7V ? 70 : 400;
			if (this.age > maxAge || to.y < level.getMinY()) {
				// PG-7V self-destructs at the end of its flight; a Javelin that lost everything does too
				this.detonate(level, from, null);
				return;
			}
		}
		this.setDeltaMovement(v);
		this.setPos(to.x, to.y, to.z);
		if (v.lengthSqr() > 1.0E-6) {
			this.xRotO = this.getXRot();
			this.yRotO = this.getYRot();
			this.face(v);
		}
	}

	private Vec3 flyRpg(Vec3 v) {
		double speed = v.length();
		if (this.age > 2 && this.age < 14) {
			this.entityData.set(BURNING, true);
			speed = Math.min(295.0 / 20.0, speed + 0.9);
		} else if (this.age >= 14) {
			this.entityData.set(BURNING, false);
			speed *= 0.995;
		}
		// fins keep it pointing along its path; gravity bends that path a little
		return v.normalize().scale(speed).add(0, -0.012, 0);
	}

	private Vec3 flyJavelin(Vec3 v) {
		if (this.age < 5) {
			return v.add(0, -0.03, 0).scale(0.95);
		}
		this.entityData.set(BURNING, this.age < 140);
		Vec3 target = this.aimPoint();
		double speed = Math.min(this.diving ? 4.0 : 4.5, Math.max(v.length(), 0.8) + 0.25);
		if (target == null) {
			return v.normalize().scale(speed).add(0, -0.02, 0);
		}
		Vec3 here = this.position();
		double distance = new Vec3(target.x - here.x, 0, target.z - here.z).length();
		double above = here.y - target.y;
		Vec3 aim;
		if (!this.diving && this.age == 5 && distance < 25) {
			// too close for a top attack: direct attack mode
			this.diving = true;
		}
		if (!this.diving) {
			// top attack: climb steeply over the first part of the way, then come down on the target from above
			double apex = target.y + Math.min(150, Math.max(30, distance * 0.7));
			Vec3 flat = new Vec3(target.x - here.x, 0, target.z - here.z).normalize();
			Vec3 climbTo = new Vec3(here.x + flat.x * distance * 0.35, apex, here.z + flat.z * distance * 0.35);
			aim = climbTo.subtract(here);
			if (here.y > apex - 4 || distance < above * 1.3) {
				this.diving = true;
			}
		} else {
			aim = target.subtract(here);
		}
		Vec3 want = aim.normalize();
		Vec3 now = v.lengthSqr() < 1.0E-6 ? want : v.normalize();
		// it turns at most 18 degrees a tick
		double angle = Math.acos(Math.clamp(now.dot(want), -1, 1));
		double max = Math.toRadians(18);
		Vec3 dir = angle <= max ? want : slerp(now, want, max / angle);
		return dir.scale(speed);
	}

	private static Vec3 slerp(Vec3 a, Vec3 b, double t) {
		return a.scale(1 - t).add(b.scale(t)).normalize();
	}

	private @Nullable Vec3 aimPoint() {
		if (this.targetId != null && this.level() instanceof ServerLevel level) {
			Entity e = level.getEntity(this.targetId);
			if (e != null && e.isAlive()) {
				this.targetPos = e.blockPosition();
				return e.getBoundingBox().getCenter();
			}
		}
		return this.targetPos == null ? null : Vec3.atCenterOf(this.targetPos);
	}

	private @Nullable HitResult hit(ServerLevel level, Vec3 from, Vec3 to) {
		BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, CollisionContext.empty()));
		Vec3 end = block.getType() == HitResult.Type.MISS ? to : block.getLocation();
		Entity owner = this.ownerId == null ? null : level.getEntity(this.ownerId);
		HitResult best = block.getType() == HitResult.Type.MISS ? null : block;
		double bestD = best == null ? Double.MAX_VALUE : best.getLocation().distanceToSqr(from);
		for (Entity e : level.getEntities(this, new AABB(from, end).inflate(1.0), EntitySelector.NO_SPECTATORS.and(Entity::isPickable))) {
			if (e == owner && this.age < 20 || e instanceof RocketEntity) {
				continue;
			}
			Optional<Vec3> at = e.getBoundingBox().inflate(0.3).clip(from, end);
			if (at.isPresent() && at.get().distanceToSqr(from) < bestD) {
				bestD = at.get().distanceToSqr(from);
				best = new net.minecraft.world.phys.EntityHitResult(e, at.get());
			}
		}
		return best;
	}

	private void detonate(ServerLevel level, Vec3 at, @Nullable Entity direct) {
		Entity owner = this.ownerId == null ? null : level.getEntity(this.ownerId);
		if (this.kind() == GunType.Rocket.PG7V) {
			Explosives.warhead(level, at, this.getDeltaMovement().normalize(), this, owner, direct, 3.5F, 30.0F);
		} else {
			Explosives.warhead(level, at, this.getDeltaMovement().normalize(), this, owner, direct, 4.5F, 45.0F);
		}
		this.discard();
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return false;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 512.0 * 512.0;
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		this.entityData.set(KIND, input.getIntOr("kind", 0));
		this.age = input.getIntOr("age", 0);
		this.diving = input.getBooleanOr("diving", false);
		this.ownerId = input.read("owner", UUIDUtil.CODEC).orElse(null);
		this.targetId = input.read("target", UUIDUtil.CODEC).orElse(null);
		this.targetPos = input.read("target_pos", BlockPos.CODEC).orElse(null);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.putInt("kind", this.entityData.get(KIND));
		output.putInt("age", this.age);
		output.putBoolean("diving", this.diving);
		output.storeNullable("owner", UUIDUtil.CODEC, this.ownerId);
		output.storeNullable("target", UUIDUtil.CODEC, this.targetId);
		output.storeNullable("target_pos", BlockPos.CODEC, this.targetPos);
	}
}
