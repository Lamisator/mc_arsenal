package net.antwire.arsenal.api;

import net.antwire.arsenal.gun.Ballistics;
import net.antwire.arsenal.gun.Caliber;
import net.antwire.arsenal.gun.GunType;
import net.antwire.arsenal.gun.Spread;
import net.antwire.arsenal.network.ShotPayload;
import net.antwire.arsenal.registry.ModItems;
import net.antwire.arsenal.registry.ModSounds;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * For other mods: lets any living entity (a guard, a soldier) fire an Arsenal gun with real bullets, muzzle flash and
 * sound. The caller keeps track of the magazine and reloads; guns are named by their item id ("m4a1", "ak47", ...).
 */
public final class ArsenalApi {
	private ArsenalApi() {
	}

	private static @Nullable GunType gun(String id) {
		for (GunType t : GunType.values()) {
			if (t.id.equals(id)) {
				return t;
			}
		}
		return null;
	}

	/** The gun as an item (empty if there is no such gun). */
	public static ItemStack stack(String id) {
		GunType t = gun(id);
		return t == null ? ItemStack.EMPTY : new ItemStack(ModItems.gun(t));
	}

	public static int magazine(String id) {
		GunType t = gun(id);
		return t == null ? 0 : t.magazine;
	}

	/** Ticks between two shots at the gun's rate of fire. */
	public static double interval(String id) {
		GunType t = gun(id);
		return t == null ? 20 : t.interval();
	}

	public static int reloadTicks(String id) {
		GunType t = gun(id);
		return t == null ? 40 : t.reloadTicks;
	}

	/**
	 * Fires one round (or one load of buckshot) from the shooter's eyes towards yaw/pitch. {@code inaccuracy} 0..1
	 * widens the cone from the gun's aimed spread to several times that (a nervous shooter, a moving one).
	 */
	public static boolean fire(LivingEntity shooter, String id, float yaw, float pitch, float inaccuracy) {
		GunType type = gun(id);
		if (type == null || type.isLauncher() || !(shooter.level() instanceof ServerLevel level)) {
			return false;
		}
		Caliber caliber = type.isShotgun() ? Caliber.BUCKSHOT : type.caliber;
		long seed = level.getRandom().nextLong();
		float movement = Math.clamp(inaccuracy, 0, 1);
		Vec3 eye = shooter.getEyePosition();
		for (Vec3 dir : Spread.directions(type, caliber, yaw, pitch, true, movement, seed)) {
			Ballistics.fire(level, shooter, eye, dir, caliber, type.velocityFactor, type.damageFactor);
		}
		level.playSound(null, shooter.getX(), shooter.getEyeY(), shooter.getZ(), ModSounds.SHOTS.get(type).value(), SoundSource.HOSTILE,
			type.category == GunType.Category.SNIPER ? 6.0F : 4.0F, 0.95F + level.getRandom().nextFloat() * 0.1F);
		level.gameEvent(shooter, GameEvent.PROJECTILE_SHOOT, eye);
		ShotPayload shot = new ShotPayload(shooter.getId(), type.ordinal(), caliber.ordinal(), yaw, pitch, seed, true, movement);
		for (ServerPlayer p : Ballistics.watching(level, eye, 256)) {
			ServerPlayNetworking.send(p, shot);
		}
		return true;
	}

	/** The sounds of a magazine change, spread over the reload. */
	public static void reloadSound(LivingEntity shooter, boolean magazineIn) {
		shooter.level().playSound(null, shooter.getX(), shooter.getEyeY(), shooter.getZ(),
			(magazineIn ? ModSounds.MAG_IN : ModSounds.MAG_OUT).value(), SoundSource.HOSTILE, 0.8F, 1.0F);
	}
}
