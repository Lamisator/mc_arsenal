package net.antwire.arsenal.gun;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.antwire.arsenal.block.Detonatable;
import net.antwire.arsenal.config.ArsenalConfig;
import net.antwire.arsenal.network.HitPayload;
import net.antwire.arsenal.network.ImpactPayload;
import net.antwire.arsenal.registry.ModDamageTypes;
import net.antwire.arsenal.registry.ModSounds;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Bullets in flight, simulated on the server: gravity (9.81 m/s², one block being one metre), air drag, penetration of
 * soft cover, hit zones and body armour. They are not entities; clients draw them from the shot they are told about.
 */
public final class Ballistics {
	/** 9.81 m/s² in blocks per tick². */
	public static final double GRAVITY = 9.81 / 400.0;
	private static final int MAX_AGE = 80;
	private static final Map<ServerLevel, List<Bullet>> BULLETS = new IdentityHashMap<>();

	private Ballistics() {
	}

	public static final class Bullet {
		Vec3 pos;
		Vec3 vel;
		final Caliber caliber;
		final double startSpeed;
		final float damage;
		final @Nullable Entity shooter;
		double penetration;
		int age;
		final Set<Entity> hit = new HashSet<>();
		/** Whom the shooter's rounds pass by unharmed (a soldier's own townsfolk); null = nobody. */
		java.util.function.@Nullable Predicate<Entity> spare;

		Bullet(Vec3 pos, Vec3 vel, Caliber caliber, float damage, @Nullable Entity shooter) {
			this.pos = pos;
			this.vel = vel;
			this.caliber = caliber;
			this.startSpeed = vel.length();
			this.damage = damage;
			this.shooter = shooter;
			this.penetration = caliber.penetration;
		}
	}

	public static void fire(ServerLevel level, @Nullable Entity shooter, Vec3 origin, Vec3 direction, Caliber caliber, double speedFactor, float damageFactor) {
		fire(level, shooter, origin, direction, caliber, speedFactor, damageFactor, null);
	}

	public static void fire(ServerLevel level, @Nullable Entity shooter, Vec3 origin, Vec3 direction, Caliber caliber, double speedFactor, float damageFactor,
		java.util.function.@Nullable Predicate<Entity> spare) {
		Vec3 vel = direction.normalize().scale(caliber.speed() * speedFactor);
		Bullet b = new Bullet(origin, vel, caliber, caliber.damage * damageFactor * (float) ArsenalConfig.get().damageMultiplier, shooter);
		b.spare = spare;
		BULLETS.computeIfAbsent(level, l -> new ArrayList<>()).add(b);
		// the first tick right away: at close range the bullet should not lag behind the shot
		if (!step(level, b)) {
			BULLETS.get(level).remove(b);
		}
	}

	public static void tick(ServerLevel level) {
		List<Bullet> list = BULLETS.get(level);
		if (list == null || list.isEmpty()) {
			return;
		}
		Iterator<Bullet> it = list.iterator();
		while (it.hasNext()) {
			Bullet b = it.next();
			if (b.age > 0 && !step(level, b)) {
				it.remove();
			}
			b.age++;
		}
	}

	public static void clear() {
		BULLETS.clear();
	}

	/** Moves a bullet one tick along; false once it has stopped. */
	private static boolean step(ServerLevel level, Bullet b) {
		if (b.age > MAX_AGE || b.vel.lengthSqr() < 0.04 || b.pos.y < level.getMinY() - 64) {
			return false;
		}
		Vec3 from = b.pos;
		Vec3 to = from.add(b.vel);
		if (!level.isLoaded(BlockPos.containing(to))) {
			return false;
		}
		// a tick may cross several things: entities, a pane of glass, a plank wall
		for (int guard = 0; guard < 12; guard++) {
			BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
			Vec3 blockAt = block.getType() == HitResult.Type.MISS ? to : block.getLocation();
			EntityHit entity = nearestEntity(level, b, from, blockAt);
			if (entity != null) {
				boolean through = hitEntity(level, b, entity.entity, entity.at);
				if (!through) {
					return false;
				}
				from = entity.at;
				continue;
			}
			if (block.getType() == HitResult.Type.MISS) {
				break;
			}
			BlockPos pos = block.getBlockPos();
			BlockState state = level.getBlockState(pos);
			Vec3 exit = exitPoint(level, pos, state, block.getLocation(), b.vel.normalize());
			double cost = cost(level, pos, state) * Math.max(0.15, exit.distanceTo(block.getLocation()));
			impact(level, b, pos, state, block.getLocation(), block.getDirection(), cost <= b.penetration);
			if (state.getBlock() instanceof Detonatable charge) {
				charge.detonate(level, pos, b.shooter);
			}
			if (fragile(state)) {
				if (ArsenalConfig.get().bulletsBreakGlass) {
					level.destroyBlock(pos, false);
				}
				cost = 0.05;
			}
			if (cost > b.penetration) {
				return false;
			}
			// through: what is left of its energy goes on
			double left = (b.penetration - cost) / b.penetration;
			b.penetration -= cost;
			b.vel = b.vel.scale(Math.sqrt(Math.max(0.1, left)) * (cost > 0.2 ? 0.85 : 1.0));
			from = exit.add(b.vel.normalize().scale(0.01));
			to = from.add(b.vel.scale(Math.max(0, 1 - from.distanceTo(b.pos) / Math.max(1e-6, b.vel.length()))));
		}
		b.pos = to;
		boolean water = !level.getFluidState(BlockPos.containing(to)).isEmpty();
		// water stops a bullet within a metre or two
		double drag = water ? 0.55 : b.caliber.drag;
		b.vel = b.vel.scale(1.0 - drag).add(0, -GRAVITY, 0);
		return true;
	}

	private record EntityHit(Entity entity, Vec3 at) {
	}

	private static @Nullable EntityHit nearestEntity(ServerLevel level, Bullet b, Vec3 from, Vec3 to) {
		AABB box = new AABB(from, to).inflate(0.5);
		EntityHit best = null;
		double bestD = Double.MAX_VALUE;
		for (Entity e : level.getEntities((Entity) null, box, EntitySelector.NO_SPECTATORS.and(Entity::isPickable))) {
			if (e == b.shooter || b.hit.contains(e) || (b.shooter != null && (e == b.shooter.getVehicle() || e.hasPassenger(b.shooter)))
				|| b.spare != null && b.spare.test(e)) {
				continue;
			}
			Optional<Vec3> at = e.getBoundingBox().inflate(0.05).clip(from, to);
			if (at.isPresent()) {
				double d = at.get().distanceToSqr(from);
				if (d < bestD) {
					bestD = d;
					best = new EntityHit(e, at.get());
				}
			}
		}
		return best;
	}

	/** Applies the hit; true if the bullet goes on through the target. */
	private static boolean hitEntity(ServerLevel level, Bullet b, Entity target, Vec3 at) {
		b.hit.add(target);
		double v = b.vel.length() / b.startSpeed;
		float damage = (float) (b.damage * v * v);
		ServerPlayer shooter = b.shooter instanceof ServerPlayer p ? p : null;
		if (target instanceof LivingEntity living) {
			BallisticArmor.Zone zone = BallisticArmor.zone(living, at);
			float stop = BallisticArmor.protection(living, zone, b.caliber.threat);
			damage *= zone.multiplier * (1.0F - stop);
			if (stop > 0) {
				BallisticArmor.wear(living, zone, b.caliber.threat);
				level.playSound(null, at.x, at.y, at.z, ModSounds.ARMOR_HIT.value(), SoundSource.PLAYERS, 1.0F, 0.9F + level.getRandom().nextFloat() * 0.2F);
			} else {
				level.playSound(null, at.x, at.y, at.z, ModSounds.IMPACT_FLESH.value(), SoundSource.PLAYERS, 0.8F, 0.9F + level.getRandom().nextFloat() * 0.2F);
			}
			boolean wasAlive = living.isAlive();
			living.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.BULLET, b.shooter, b.shooter), damage);
			ImpactPayload.send(level, at, b.vel.normalize().reverse(), ImpactPayload.FLESH, 0, stop > 0.5F ? 1 : 0);
			if (shooter != null && shooter != target) {
				int kind = !living.isAlive() && wasAlive ? HitPayload.KILL : zone == BallisticArmor.Zone.HEAD ? HitPayload.HEAD : stop > 0.5F ? HitPayload.ARMOR : HitPayload.BODY;
				ServerPlayNetworking.send(shooter, new HitPayload(kind));
			}
		} else {
			target.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.BULLET, b.shooter, b.shooter), damage);
			ImpactPayload.send(level, at, b.vel.normalize().reverse(), ImpactPayload.SPARK, 0, 0);
		}
		// only heavy rifle rounds go on through a body
		if (b.caliber.penetration >= 4.0) {
			b.penetration *= 0.6;
			b.vel = b.vel.scale(0.75);
			return true;
		}
		return false;
	}

	private static void impact(ServerLevel level, Bullet b, BlockPos pos, BlockState state, Vec3 at, Direction face, boolean through) {
		SoundType sound = state.getSoundType();
		Holder<SoundEvent> event;
		int kind;
		if (sound == SoundType.METAL || sound == SoundType.ANVIL || sound == SoundType.CHAIN || sound == SoundType.COPPER || sound == SoundType.NETHERITE_BLOCK
			|| sound == SoundType.IRON) {
			event = ModSounds.IMPACT_METAL;
			kind = ImpactPayload.METAL;
		} else if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
			event = ModSounds.IMPACT_HARD;
			kind = ImpactPayload.STONE;
		} else {
			event = ModSounds.IMPACT_SOFT;
			kind = ImpactPayload.SOFT;
		}
		if (fragile(state)) {
			kind = ImpactPayload.GLASS;
		}
		level.playSound(null, at.x, at.y, at.z, event.value(), SoundSource.BLOCKS, 0.7F, 0.85F + level.getRandom().nextFloat() * 0.3F);
		ImpactPayload.send(level, at, Vec3.atLowerCornerOf(face.getUnitVec3i()), kind, Block.getId(state), through ? 1 : 0);
	}

	/** Leaves, glass, panes and the like: no real cover, and glass breaks. */
	public static boolean fragile(BlockState state) {
		return state.is(BlockTags.LEAVES) || state.is(Blocks.GLASS) || state.is(Blocks.GLASS_PANE) || state.is(BlockTags.IMPERMEABLE)
			|| state.is(Blocks.TINTED_GLASS) || state.is(Blocks.ICE) || state.is(Blocks.GLOWSTONE) || state.getSoundType() == SoundType.GLASS;
	}

	/** Penetration a metre of this block costs. */
	public static double cost(ServerLevel level, BlockPos pos, BlockState state) {
		float hardness = state.getDestroySpeed(level, pos);
		if (hardness < 0 || hardness >= 40) {
			return 1000;
		}
		if (fragile(state)) {
			return 0.1;
		}
		SoundType sound = state.getSoundType();
		if (state.is(BlockTags.WOOL) || state.is(BlockTags.WOOL_CARPETS) || state.is(Blocks.HAY_BLOCK) || sound == SoundType.SNOW || state.is(Blocks.SPONGE)) {
			return 0.5;
		}
		if (sound == SoundType.METAL || sound == SoundType.ANVIL || sound == SoundType.IRON || sound == SoundType.NETHERITE_BLOCK || sound == SoundType.COPPER) {
			return 12;
		}
		if (state.is(BlockTags.MINEABLE_WITH_AXE)) {
			return 1.0;
		}
		if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
			return 2.5;
		}
		if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
			return Math.clamp(hardness * 2.4, 3.0, 14.0);
		}
		return Math.max(0.3, hardness * 1.5);
	}

	/** Where a straight line entering {@code pos} at {@code at} comes out of the block's shape again. */
	private static Vec3 exitPoint(ServerLevel level, BlockPos pos, BlockState state, Vec3 at, Vec3 dir) {
		VoxelShape shape = state.getCollisionShape(level, pos);
		Vec3 p = at;
		for (int i = 0; i < 40; i++) {
			p = p.add(dir.scale(0.05));
			BlockPos inside = BlockPos.containing(p);
			if (!inside.equals(pos)) {
				return p;
			}
			Vec3 local = p.subtract(pos.getX(), pos.getY(), pos.getZ());
			boolean in = false;
			for (AABB box : shape.toAabbs()) {
				if (box.contains(local)) {
					in = true;
					break;
				}
			}
			if (!in) {
				return p;
			}
		}
		return p;
	}

	/** Players near enough to see a shot or impact. */
	public static Iterable<ServerPlayer> watching(ServerLevel level, Vec3 at, double range) {
		List<ServerPlayer> out = new ArrayList<>();
		for (ServerPlayer p : PlayerLookup.level(level)) {
			if (p.position().distanceToSqr(at) < range * range) {
				out.add(p);
			}
		}
		return out;
	}
}
