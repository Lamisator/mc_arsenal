package net.antwire.arsenal.client;

import java.util.Random;
import net.antwire.arsenal.client.fx.ClientFx;
import net.antwire.arsenal.gun.Caliber;
import net.antwire.arsenal.gun.GunItem;
import net.antwire.arsenal.gun.GunState;
import net.antwire.arsenal.gun.GunType;
import net.antwire.arsenal.network.ActionPayload;
import net.antwire.arsenal.network.FirePayload;
import net.antwire.arsenal.registry.ModSounds;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The shooter's side: trigger, rate of fire, recoil, aiming down the sights and the Javelin's seeker. Shots are
 * predicted here (flash, sound, tracers, kick) and checked by the server.
 */
public final class GunClient {
	public static final int LOCK_TICKS = 40;
	private static final Random RANDOM = new Random();
	public static KeyMapping reloadKey;
	public static KeyMapping modeKey;

	private static long ticks;
	private static double nextShot;
	private static int burstLeft;
	private static float aim;
	private static float aimO;
	private static float kick;
	private static float kickO;
	private static float pendingPitch;
	private static float pendingYaw;
	private static int predictedShots;
	private static int lastAmmo = -1;
	private static @Nullable ItemStack lastStack;

	// Javelin command launch unit
	private static int seekEntity = -1;
	private static @Nullable BlockPos seekPos;
	private static int seekTicks;
	private static int toneTimer;

	private GunClient() {
	}

	public static @Nullable GunType held(LocalPlayer player) {
		return GunItem.type(player.getMainHandItem());
	}

	public static boolean aiming(LocalPlayer player) {
		return player.isUsingItem() && held(player) != null && player.getUseItem() == player.getMainHandItem();
	}

	public static float aim(float partial) {
		return Mth.lerp(partial, aimO, aim);
	}

	public static float kick(float partial) {
		return Mth.lerp(partial, kickO, kick);
	}

	public static int predictedAmmo(ItemStack stack) {
		return Math.max(0, GunItem.state(stack).ammo() - (stack == lastStack ? predictedShots : 0));
	}

	public static boolean locked() {
		return seekTicks >= LOCK_TICKS;
	}

	public static float lockProgress() {
		return Math.min(1.0F, seekTicks / (float) LOCK_TICKS);
	}

	public static int seekEntity() {
		return seekEntity;
	}

	public static @Nullable BlockPos seekPos() {
		return seekPos;
	}

	/** Start of every client tick, before vanilla handles the mouse buttons. */
	public static void tick(Minecraft mc) {
		ticks++;
		aimO = aim;
		kickO = kick;
		kick *= 0.55F;
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null) {
			return;
		}
		ItemStack stack = player.getMainHandItem();
		GunType type = GunItem.type(stack);
		boolean aiming = type != null && aiming(player);
		float speed = type == null ? 0.25F : switch (type.category) {
			case PISTOL -> 0.34F;
			case SMG -> 0.28F;
			case RIFLE, SHOTGUN -> 0.22F;
			case SNIPER -> 0.16F;
			case LAUNCHER -> 0.14F;
		};
		aim = Mth.approach(aim, aiming ? 1.0F : 0.0F, speed);
		applyRecoil(player);
		if (type == null) {
			burstLeft = 0;
			seekTicks = 0;
			return;
		}
		GunState state = GunItem.state(stack);
		if (stack != lastStack || state.ammo() != lastAmmo) {
			lastStack = stack;
			lastAmmo = state.ammo();
			predictedShots = 0;
		}
		if (type == GunType.JAVELIN) {
			seek(mc, player, aiming);
		} else {
			seekTicks = 0;
		}
		if (mc.gui.screen() != null) {
			burstLeft = 0;
			return;
		}
		int clicks = 0;
		while (mc.options.keyAttack.consumeClick()) {
			clicks++;
		}
		boolean down = mc.options.keyAttack.isDown();
		GunType.FireMode mode = state.fireMode(type);
		if (nextShot < ticks - 1) {
			nextShot = ticks - 1;
		}
		switch (mode) {
			case SEMI -> {
				if (clicks > 0 && ticks >= nextShot) {
					fire(mc, player, stack, type, state, true);
				}
			}
			case BURST -> {
				if (clicks > 0 && burstLeft == 0) {
					burstLeft = 3;
				}
				while (burstLeft > 0 && ticks >= nextShot) {
					burstLeft--;
					if (!fire(mc, player, stack, type, state, burstLeft == 2)) {
						burstLeft = 0;
					}
				}
			}
			case AUTO -> {
				int guard = 0;
				while ((down || clicks > 0) && ticks >= nextShot && guard++ < 3) {
					clicks = 0;
					if (!fire(mc, player, stack, type, state, guard == 1)) {
						break;
					}
				}
			}
		}
	}

	/** End of the client tick: the reload and fire-mode keys. */
	public static void keys(Minecraft mc) {
		if (mc.player == null) {
			return;
		}
		while (reloadKey.consumeClick()) {
			if (held(mc.player) != null) {
				ClientPlayNetworking.send(new ActionPayload(ActionPayload.RELOAD));
			}
		}
		while (modeKey.consumeClick()) {
			if (held(mc.player) != null) {
				ClientPlayNetworking.send(new ActionPayload(ActionPayload.MODE));
			}
		}
	}

	private static boolean fire(Minecraft mc, LocalPlayer player, ItemStack stack, GunType type, GunState state, boolean click) {
		long now = mc.level.getGameTime();
		if (state.reloading(now) && !type.loadsSingly()) {
			return false;
		}
		if (predictedAmmo(stack) <= 0) {
			if (click) {
				mc.level.playLocalSound(player.getX(), player.getEyeY(), player.getZ(), ModSounds.DRY_FIRE.value(), SoundSource.PLAYERS, 0.6F, 1.0F, false);
			}
			nextShot = ticks + 5;
			return false;
		}
		boolean aiming = aiming(player);
		int lockEntity = -1;
		boolean hasLockPos = false;
		BlockPos lockPos = BlockPos.ZERO;
		if (type == GunType.JAVELIN) {
			if (!locked() || !aiming) {
				if (click) {
					mc.level.playLocalSound(player.getX(), player.getEyeY(), player.getZ(), ModSounds.JAVELIN_SEEK.value(), SoundSource.PLAYERS, 0.6F, 0.6F, false);
				}
				nextShot = ticks + 5;
				return false;
			}
			lockEntity = seekEntity;
			if (seekEntity < 0 && seekPos != null) {
				hasLockPos = true;
				lockPos = seekPos;
			}
			seekTicks = 0;
		}
		nextShot = Math.max(nextShot, ticks) + type.interval();
		predictedShots++;
		long seed = RANDOM.nextLong();
		float yaw = player.getYRot();
		float pitch = player.getXRot();
		float movement = movement(player);
		ClientPlayNetworking.send(new FirePayload(yaw, pitch, seed, aiming, movement, lockEntity, hasLockPos, lockPos));
		Caliber caliber = type.isShotgun() ? state.nextShell() : type.caliber;
		ClientFx.localShot(mc, player, type, caliber, yaw, pitch, seed, aiming, movement);
		// recoil: the muzzle climbs and wanders; aimed and crouched it is easier to hold
		float control = (aiming ? 0.7F : 1.0F) * (player.isCrouching() ? 0.75F : 1.0F);
		pendingPitch += type.recoilUp * control * (0.85F + RANDOM.nextFloat() * 0.3F);
		pendingYaw += (float) (RANDOM.nextGaussian() * type.recoilSide * control);
		kick = Math.min(1.5F, kick + (type.category == GunType.Category.SNIPER || type.isShotgun() ? 1.0F : 0.6F));
		return true;
	}

	/** How much running or jumping spoils the aim, 0..1. */
	private static float movement(LocalPlayer player) {
		Vec3 v = player.getDeltaMovement();
		float m = (float) Math.min(1.0, Math.sqrt(v.x * v.x + v.z * v.z) / 0.22);
		if (!player.onGround()) {
			m = Math.max(m, 0.8F);
		}
		if (player.isCrouching()) {
			m *= 0.5F;
		}
		return m;
	}

	/** Spreads the recoil over two ticks so the camera kicks instead of jumping. */
	private static void applyRecoil(LocalPlayer player) {
		if (Math.abs(pendingPitch) < 0.01F && Math.abs(pendingYaw) < 0.01F) {
			return;
		}
		float p = pendingPitch * 0.6F;
		float y = pendingYaw * 0.6F;
		pendingPitch -= p;
		pendingYaw -= y;
		player.setXRot(Mth.clamp(player.getXRot() - p, -90.0F, 90.0F));
		player.setYRot(player.getYRot() + y);
	}

	/** The Javelin's seeker: hold the crosshair on a target for two seconds to lock. */
	private static void seek(Minecraft mc, LocalPlayer player, boolean aiming) {
		if (!aiming || GunItem.state(player.getMainHandItem()).ammo() <= 0) {
			seekTicks = 0;
			seekEntity = -1;
			seekPos = null;
			return;
		}
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getLookAngle().scale(400));
		BlockHitResult block = mc.level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		Vec3 stop = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
		EntityHitResult entity = ProjectileUtil.getEntityHitResult(player, eye, stop, new AABB(eye, stop).inflate(2),
			e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() || e instanceof net.minecraft.world.entity.vehicle.VehicleEntity, 400 * 400);
		int id = -1;
		BlockPos pos = null;
		if (entity != null) {
			id = entity.getEntity().getId();
		} else if (block.getType() != HitResult.Type.MISS) {
			pos = block.getBlockPos();
		}
		boolean same = id >= 0 ? id == seekEntity : pos != null && seekPos != null && seekEntity < 0 && pos.closerThan(seekPos, 3);
		if (id < 0 && pos == null) {
			seekTicks = 0;
		} else if (same) {
			seekTicks++;
		} else {
			seekTicks = 1;
		}
		seekEntity = id;
		seekPos = pos;
		if (--toneTimer <= 0 && seekTicks > 0) {
			boolean lock = locked();
			mc.level.playLocalSound(player.getX(), player.getEyeY(), player.getZ(), (lock ? ModSounds.JAVELIN_LOCK : ModSounds.JAVELIN_SEEK).value(), SoundSource.PLAYERS,
				0.5F, 1.0F, false);
			toneTimer = lock ? 6 : 10;
		}
	}

	/** Entity the seeker is on (client side), for the overlay. */
	public static @Nullable Entity seekTarget(Minecraft mc) {
		return seekEntity >= 0 && mc.level != null ? mc.level.getEntity(seekEntity) : null;
	}

	public static void reset() {
		aim = aimO = 0;
		kick = kickO = 0;
		pendingPitch = pendingYaw = 0;
		burstLeft = 0;
		seekTicks = 0;
		lastStack = null;
	}
}
