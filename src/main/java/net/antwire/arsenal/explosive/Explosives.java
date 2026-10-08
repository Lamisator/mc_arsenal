package net.antwire.arsenal.explosive;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.antwire.arsenal.block.Detonatable;
import net.antwire.arsenal.config.ArsenalConfig;
import net.antwire.arsenal.gun.BallisticArmor;
import net.antwire.arsenal.gun.Ballistics;
import net.antwire.arsenal.gun.Caliber;
import net.antwire.arsenal.network.FlashPayload;
import net.antwire.arsenal.network.FxPayload;
import net.antwire.arsenal.registry.ModDamageTypes;
import net.antwire.arsenal.registry.ModSounds;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ExplosionParticleInfo;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;

/** Everything that goes bang: grenades, claymores, C4, mines and rocket warheads. */
public final class Explosives {
	private static final WeightedList<ExplosionParticleInfo> BLOCK_PARTICLES = WeightedList.<ExplosionParticleInfo>builder()
		.add(new ExplosionParticleInfo(ParticleTypes.POOF, 0.5F, 1.0F))
		.add(new ExplosionParticleInfo(ParticleTypes.LARGE_SMOKE, 1.0F, 1.0F))
		.build();

	private record Chain(ServerLevel level, BlockPos pos, BlockState state, @Nullable Entity cause, long when) {
	}

	private static final List<Chain> CHAIN = new ArrayList<>();

	private Explosives() {
	}

	/** Another charge caught in a blast goes off a moment later. */
	public static void chain(ServerLevel level, BlockPos pos, BlockState state, @Nullable Entity cause) {
		for (Chain c : CHAIN) {
			if (c.pos.equals(pos) && c.level == level) {
				return;
			}
		}
		CHAIN.add(new Chain(level, pos.immutable(), state, cause, level.getGameTime() + 2 + level.getRandom().nextInt(5)));
	}

	public static void tick() {
		if (CHAIN.isEmpty()) {
			return;
		}
		List<Chain> due = new ArrayList<>();
		CHAIN.removeIf(c -> {
			if (c.level.getGameTime() >= c.when) {
				due.add(c);
				return true;
			}
			return false;
		});
		for (Chain c : due) {
			if (c.state.getBlock() instanceof Detonatable charge) {
				charge.chainDetonate(c.level, c.pos, c.state, c.cause);
			}
		}
	}

	public static void clearPending() {
		CHAIN.clear();
	}

	public static void blast(ServerLevel level, @Nullable Entity source, @Nullable Entity owner, Vec3 at, float power, boolean terrain, Holder<SoundEvent> sound) {
		// charges within reach of the blast go off too, whether or not the blast breaks blocks
		int r = (int) Math.ceil(power * 1.5);
		BlockPos center = BlockPos.containing(at);
		for (BlockPos p : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
			if (p.distToCenterSqr(at) <= r * r && !p.equals(center)) {
				BlockState state = level.getBlockState(p);
				if (state.getBlock() instanceof Detonatable) {
					chain(level, p, state, owner);
				}
			}
		}
		level.explode(source, level.damageSources().explosion(source, owner), null, at.x, at.y, at.z, power, false,
			terrain ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE, ParticleTypes.EXPLOSION, ParticleTypes.EXPLOSION_EMITTER,
			BLOCK_PARTICLES, sound);
	}

	// ---------------------------------------------------------------- fragments

	/** Direction of fragment {@code i}: client and server draw them from the same seed. */
	public interface Pattern {
		Vec3 direction(Random random);
	}

	/** A sphere, skimming the ground a little more than going up: a grenade lying on the floor. */
	public static final Pattern SPHERE = r -> {
		double yaw = r.nextDouble() * Math.PI * 2;
		double pitch = Math.asin(r.nextDouble() * 1.4 - 0.4);
		return new Vec3(Math.cos(yaw) * Math.cos(pitch), Math.sin(pitch), Math.sin(yaw) * Math.cos(pitch));
	};

	/** A claymore's fan: 60° wide, about 18° high, facing {@code dir}. */
	public static Pattern fan(Vec3 dir) {
		Vec3 flat = new Vec3(dir.x, 0, dir.z).normalize();
		double base = Math.atan2(flat.z, flat.x);
		return r -> {
			double yaw = base + (r.nextDouble() - 0.5) * Math.toRadians(60);
			double pitch = Math.toRadians((r.nextDouble() - 0.35) * 18);
			return new Vec3(Math.cos(yaw) * Math.cos(pitch), Math.sin(pitch), Math.sin(yaw) * Math.cos(pitch));
		};
	}

	/**
	 * Flings {@code count} fragments from {@code center}. Each one is a ray to {@code range}: it hurts the first body it
	 * meets (armour helps a lot against fragments) and shatters glass.
	 */
	public static void fragments(ServerLevel level, Vec3 center, Pattern pattern, long seed, int count, double range, float maxDamage, @Nullable Entity source,
		@Nullable Entity owner) {
		Random random = new Random(seed);
		for (int i = 0; i < count; i++) {
			Vec3 dir = pattern.direction(random);
			double reach = range * (0.6 + 0.4 * random.nextDouble());
			Vec3 end = center.add(dir.scale(reach));
			BlockHitResult hit = level.clip(new ClipContext(center, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
			Vec3 stop = hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
			if (hit.getType() != HitResult.Type.MISS && ArsenalConfig.get().bulletsBreakGlass && Ballistics.fragile(level.getBlockState(hit.getBlockPos()))
				&& !level.getBlockState(hit.getBlockPos()).isAir()) {
				level.destroyBlock(hit.getBlockPos(), false);
			}
			LivingEntity victim = null;
			Vec3 victimAt = null;
			double best = Double.MAX_VALUE;
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, stop).inflate(0.4), LivingEntity::isAlive)) {
				var at = e.getBoundingBox().inflate(0.1).clip(center, stop);
				if (at.isPresent() && at.get().distanceToSqr(center) < best) {
					best = at.get().distanceToSqr(center);
					victim = e;
					victimAt = at.get();
				}
			}
			if (victim != null) {
				double d = Math.sqrt(best);
				float damage = (float) (maxDamage * Math.max(0.15, 1.0 - d / range));
				BallisticArmor.Zone zone = BallisticArmor.zone(victim, victimAt);
				float stopShare = BallisticArmor.protection(victim, zone, Caliber.Threat.FRAGMENT);
				if (stopShare > 0) {
					BallisticArmor.wear(victim, zone, Caliber.Threat.FRAGMENT);
				}
				victim.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.FRAGMENT, source, owner), damage * zone.multiplier * (1.0F - stopShare));
			}
		}
	}

	// ---------------------------------------------------------------- the charges

	/** M67: 180 g of Composition B in a steel body. Lethal within about 5 m, fragments out to 15 m. */
	public static void fragGrenade(ServerLevel level, Vec3 at, @Nullable Entity source, @Nullable Entity owner) {
		long seed = level.getRandom().nextLong();
		Vec3 center = at.add(0, 0.25, 0);
		blast(level, source, owner, at, 2.0F, ArsenalConfig.get().grenadeBlockDamage, ModSounds.EXPLOSION_GRENADE);
		fragments(level, center, SPHERE, seed, 180, 15, 10.0F, source, owner);
		FxPayload.send(level, FxPayload.GRENADE, center, Vec3.ZERO, seed);
	}

	/**
	 * M84 stun grenade: 170 dB and six million candela. Blinds whoever looks at it, deafens everyone near, stuns mobs.
	 * Walls and turning away help against the flash, not against the bang.
	 */
	public static void flashbang(ServerLevel level, Vec3 at, @Nullable Entity owner) {
		Vec3 center = at.add(0, 0.3, 0);
		level.playSound(null, center.x, center.y, center.z, ModSounds.FLASHBANG.value(), SoundSource.PLAYERS, 6.0F, 1.0F);
		FxPayload.send(level, FxPayload.FLASHBANG, center, Vec3.ZERO, 0);
		ArsenalConfig cfg = ArsenalConfig.get();
		for (ServerPlayer player : PlayerLookup.around(level, center, 24)) {
			if (player.isSpectator()) {
				continue;
			}
			Vec3 eye = player.getEyePosition();
			double d = eye.distanceTo(center);
			boolean seen = clear(level, center, eye);
			Vec3 to = center.subtract(eye).normalize();
			double facing = player.getLookAngle().dot(to);
			double look = facing > 0.5 ? 1.0 : facing > 0.0 ? 0.65 : facing > -0.5 ? 0.35 : 0.15;
			double near = Math.clamp(1.0 - d / 20.0, 0.0, 1.0);
			float blind = seen ? (float) (cfg.flashbangSeconds * look * Math.sqrt(near)) : 0.0F;
			float deaf = (float) (cfg.deafSeconds * Math.clamp(1.0 - d / 24.0, 0.0, 1.0) * (seen ? 1.0 : 0.7));
			if (blind > 0.05F || deaf > 0.05F) {
				ServerPlayNetworking.send(player, new FlashPayload(blind, deaf));
			}
			if (blind > 1.0F) {
				player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, (int) (blind * 20), 1));
			}
		}
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(12), LivingEntity::isAlive)) {
			double d = e.getEyePosition().distanceTo(center);
			if (d < 1.5) {
				e.hurtServer(level, level.damageSources().explosion(null, owner), 2.0F);
			}
			if (e instanceof Mob mob && clear(level, center, e.getEyePosition())) {
				int ticks = (int) (20 * cfg.flashbangSeconds * Math.clamp(1.0 - d / 12.0, 0.2, 1.0));
				mob.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, ticks, 0));
				mob.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, 2));
				mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 1));
				mob.setTarget(null);
				mob.getNavigation().stop();
			}
		}
	}

	/** M18A1 claymore: 700 steel balls in a 60° fan, deadly to 50 m. */
	public static void claymore(ServerLevel level, BlockPos pos, Vec3 facing, @Nullable Entity owner) {
		Vec3 center = Vec3.atBottomCenterOf(pos).add(0, 0.35, 0).add(facing.scale(0.3));
		long seed = level.getRandom().nextLong();
		level.removeBlock(pos, false);
		blast(level, null, owner, center, 1.6F, false, ModSounds.EXPLOSION_GRENADE);
		fragments(level, center, fan(facing), seed, 260, 45, 18.0F, null, owner);
		FxPayload.send(level, FxPayload.CLAYMORE, center, facing, seed);
	}

	/** M112 block of C4: 0.57 kg, made to cut steel and break walls. */
	public static void c4(ServerLevel level, BlockPos pos, @Nullable Entity owner) {
		Vec3 center = Vec3.atCenterOf(pos);
		level.removeBlock(pos, false);
		blast(level, null, owner, center, 5.0F, ArsenalConfig.get().c4BlockDamage, ModSounds.EXPLOSION_C4);
		FxPayload.send(level, FxPayload.C4, center, Vec3.ZERO, 0);
	}

	/** Anti-personnel blast mine: takes the leg of whoever steps on it, armour or not. */
	public static void apMine(ServerLevel level, BlockPos pos, @Nullable Entity victim) {
		Vec3 center = Vec3.atBottomCenterOf(pos).add(0, 0.1, 0);
		level.removeBlock(pos, false);
		if (victim != null && victim.isAlive()) {
			victim.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.MINE, null, null), 16.0F);
		}
		blast(level, null, null, center, 1.4F, false, ModSounds.EXPLOSION_GRENADE);
		FxPayload.send(level, FxPayload.MINE, center, Vec3.ZERO, 0);
	}

	/** TM-62 anti-tank mine: 7 kg of TNT under a vehicle. */
	public static void atMine(ServerLevel level, BlockPos pos, @Nullable Entity victim) {
		Vec3 center = Vec3.atBottomCenterOf(pos).add(0, 0.2, 0);
		level.removeBlock(pos, false);
		if (victim != null && victim.isAlive()) {
			victim.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.MINE, null, null), 40.0F);
		}
		blast(level, null, null, center, 6.0F, ArsenalConfig.get().mineBlockDamage, ModSounds.EXPLOSION_C4);
		FxPayload.send(level, FxPayload.C4, center, Vec3.ZERO, 0);
	}

	/** A rocket's warhead: HEAT, so most of it goes into what it hits, the rest is blast. */
	public static void warhead(ServerLevel level, Vec3 at, Vec3 dir, @Nullable Entity rocket, @Nullable Entity owner, @Nullable Entity direct, float power,
		float directDamage) {
		if (direct != null && direct.isAlive()) {
			direct.hurtServer(level, level.damageSources().explosion(rocket, owner), directDamage);
		}
		blast(level, rocket, owner, at, power, ArsenalConfig.get().rocketBlockDamage, ModSounds.EXPLOSION_ROCKET);
		FxPayload.send(level, FxPayload.ROCKET, at, dir, 0);
	}

	/**
	 * The backblast of a recoilless launcher: a jet of hot gas out of the back, dangerous for 5 m. With a wall right
	 * behind, it comes back at the shooter.
	 */
	public static void backblast(ServerLevel level, LivingEntity shooter, Vec3 eye, Vec3 dir) {
		Vec3 back = dir.reverse();
		Vec3 start = eye.add(back.scale(0.6)).add(0, -0.2, 0);
		FxPayload.send(level, FxPayload.BACKBLAST, start, back, 0);
		if (!ArsenalConfig.get().backblast) {
			return;
		}
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(start, start).inflate(6), LivingEntity::isAlive)) {
			if (e == shooter) {
				continue;
			}
			Vec3 to = e.getBoundingBox().getCenter().subtract(start);
			double d = to.length();
			if (d < 6 && to.normalize().dot(back) > 0.8 && clear(level, start, e.getBoundingBox().getCenter())) {
				e.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.BACKBLAST, shooter, shooter), (float) (12 * (1 - d / 6) + 2));
				e.igniteForSeconds(2);
				e.push(back.x * 0.8, 0.3, back.z * 0.8);
			}
		}
		BlockHitResult wall = level.clip(new ClipContext(start, start.add(back.scale(1.5)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
		if (wall.getType() != HitResult.Type.MISS) {
			shooter.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.BACKBLAST, shooter, shooter), 6.0F);
		}
	}

	/** Nothing solid between {@code a} and {@code b}. */
	public static boolean clear(ServerLevel level, Vec3 a, Vec3 b) {
		return level.clip(new ClipContext(a, b, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, CollisionContext.empty())).getType() == HitResult.Type.MISS;
	}
}
