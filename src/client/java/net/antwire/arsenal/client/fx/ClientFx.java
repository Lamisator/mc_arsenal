package net.antwire.arsenal.client.fx;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import net.antwire.arsenal.block.C4Block;
import net.antwire.arsenal.block.ClaymoreBlock;
import net.antwire.arsenal.block.MineBlock;
import net.antwire.arsenal.client.GunClient;
import net.antwire.arsenal.client.GunGeometry;
import net.antwire.arsenal.entity.GrenadeEntity;
import net.antwire.arsenal.entity.RocketEntity;
import net.antwire.arsenal.explosive.Explosives;
import net.antwire.arsenal.gun.Caliber;
import net.antwire.arsenal.gun.GunType;
import net.antwire.arsenal.gun.Spread;
import net.antwire.arsenal.item.MineDetectorItem;
import net.antwire.arsenal.network.FxPayload;
import net.antwire.arsenal.network.ImpactPayload;
import net.antwire.arsenal.network.ShotPayload;
import net.antwire.arsenal.registry.ModItems;
import net.antwire.arsenal.registry.ModParticles;
import net.antwire.arsenal.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Everything the client draws and plays around shooting and explosions. */
public final class ClientFx {
	private static final Set<Integer> ROCKETS_HEARD = new HashSet<>();
	private static int detectorTimer;
	public static double detectorDistance = -1;

	private ClientFx() {
	}

	// ---------------------------------------------------------------- shots

	/** Where the muzzle of a gun held by {@code shooter} is, in the world. */
	public static Vec3 muzzle(Entity shooter, GunType type, float yaw, float pitch, boolean firstPerson, float aim) {
		GunGeometry g = GunGeometry.of(type);
		Vec3 look = Spread.look(yaw, pitch);
		Vec3 up = Spread.look(yaw, pitch - 90);
		Vec3 right = look.cross(up).normalize();
		Vec3 eye = shooter.getEyePosition();
		if (firstPerson) {
			// the same place the first-person renderer draws the gun (at its reduced scale)
			boolean pistol = type.category == GunType.Category.PISTOL;
			double scale = pistol ? 0.7 : 0.6;
			double gripZ = Mth.lerp(aim, pistol ? 0.36 : 0.34, 0.08 + g.eyeRelief * scale);
			double gripX = Mth.lerp(aim, pistol ? 0.15 : 0.2, 0.0);
			double gripY = Mth.lerp(aim, pistol ? -0.2 : -0.21, -g.sight * scale);
			return eye.add(look.scale(gripZ + g.muzzle * scale)).add(right.scale(gripX)).add(up.scale(gripY + g.bore * scale));
		}
		return eye.add(look.scale(0.25 + g.muzzle)).add(right.scale(0.32)).add(up.scale(-0.32));
	}

	public static void localShot(Minecraft mc, LocalPlayer player, GunType type, @Nullable Caliber caliber, float yaw, float pitch, long seed, boolean aiming,
		float movement) {
		boolean firstPerson = mc.options.getCameraType().isFirstPerson();
		mc.level.playLocalSound(player.getX(), player.getEyeY(), player.getZ(), ModSounds.SHOTS.get(type).value(), SoundSource.PLAYERS,
			type.category == GunType.Category.SNIPER ? 2.5F : 1.6F, 0.95F + mc.level.getRandom().nextFloat() * 0.1F, false);
		Vec3 muzzle = muzzle(player, type, yaw, pitch, firstPerson, GunClient.aim(1.0F));
		shotEffects(mc.level, player, type, caliber, muzzle, yaw, pitch, seed, aiming, movement, true, firstPerson);
	}

	public static void remoteShot(Minecraft mc, ShotPayload p) {
		Entity shooter = mc.level.getEntity(p.shooter());
		if (shooter == null) {
			return;
		}
		GunType type = GunType.byOrdinal(p.gun());
		Caliber caliber = p.caliber() < 0 ? null : Caliber.byOrdinal(p.caliber());
		Vec3 muzzle = muzzle(shooter, type, p.yaw(), p.pitch(), false, 0);
		shotEffects(mc.level, shooter, type, caliber, muzzle, p.yaw(), p.pitch(), p.seed(), p.aiming(), p.movement(), false, false);
	}

	private static void shotEffects(ClientLevel level, Entity shooter, GunType type, @Nullable Caliber caliber, Vec3 muzzle, float yaw, float pitch, long seed,
		boolean aiming, float movement, boolean local, boolean firstPerson) {
		Vec3 look = Spread.look(yaw, pitch);
		Vec3 up = Spread.look(yaw, pitch - 90);
		Vec3 right = look.cross(up).normalize();
		Random r = new Random(seed ^ 0x5DEECE66DL);
		if (type.isLauncher()) {
			for (int i = 0; i < 14; i++) {
				Vec3 back = look.reverse().scale(0.3 + r.nextDouble() * 0.5).add(r.nextGaussian() * 0.06, r.nextGaussian() * 0.06, r.nextGaussian() * 0.06);
				level.addParticle(ModParticles.ROCKET_SMOKE, muzzle.x - look.x * 1.2, muzzle.y - look.y * 1.2, muzzle.z - look.z * 1.2, back.x, back.y, back.z);
			}
			level.addParticle(ModParticles.MUZZLE_FLASH, muzzle.x, muzzle.y, muzzle.z, 0, 0, 0);
			return;
		}
		if (!firstPerson || !type.scope || GunClient.aim(1.0F) < 0.85F) {
			int flashes = type.isShotgun() || type.category == GunType.Category.SNIPER ? 3 : 1;
			for (int i = 0; i < flashes; i++) {
				Vec3 f = muzzle.add(look.scale(i * 0.12));
				level.addParticle(ModParticles.MUZZLE_FLASH, f.x, f.y, f.z, look.x * 0.02, look.y * 0.02, look.z * 0.02);
			}
		}
		for (int i = 0; i < (type.isShotgun() || type.category == GunType.Category.SNIPER ? 2 : 1); i++) {
			level.addParticle(ModParticles.GUN_SMOKE, muzzle.x, muzzle.y, muzzle.z, look.x * 0.05 + r.nextGaussian() * 0.01, 0.01 + r.nextDouble() * 0.01,
				look.z * 0.05 + r.nextGaussian() * 0.01);
		}
		// spent brass out of the right side (a pump gun throws its hull when pumped; close enough)
		if (type.action != GunType.Action.BOLT) {
			Vec3 port = firstPerson ? muzzle.subtract(look.scale(GunGeometry.of(type).muzzle * 0.55)).add(right.scale(0.02))
				: shooter.getEyePosition().add(look.scale(0.3)).add(right.scale(0.3)).add(up.scale(-0.2));
			Vec3 v = right.scale(0.12 + r.nextDouble() * 0.05).add(up.scale(0.1 + r.nextDouble() * 0.05)).add(look.scale(-0.03));
			level.addParticle(ModParticles.CASING, port.x, port.y, port.z, v.x, v.y, v.z);
		}
		if (caliber != null) {
			List<Vec3> dirs = Spread.directions(type, caliber, yaw, pitch, aiming, movement, seed);
			Vec3 eye = shooter.getEyePosition();
			for (Vec3 d : dirs) {
				ClientBullets.add(eye, muzzle, d.scale(caliber.speed() * type.velocityFactor), caliber, local, shooter.getId());
			}
		}
	}

	// ---------------------------------------------------------------- impacts

	public static void impact(Minecraft mc, ImpactPayload p) {
		ClientLevel level = mc.level;
		Vec3 at = p.at();
		Vec3 n = p.normal();
		Random r = new Random();
		BlockState state = Block.stateById(p.state());
		switch (p.kind()) {
			case ImpactPayload.FLESH -> {
				if (p.flags() == 1) {
					burst(level, ModParticles.SPARK, at, n, 3, 0.15, r);
				} else {
					burst(level, ModParticles.BLOOD, at, n, 7, 0.12, r);
				}
			}
			case ImpactPayload.SPARK -> burst(level, ModParticles.SPARK, at, n, 6, 0.2, r);
			default -> {
				if (!state.isAir()) {
					burst(level, new BlockParticleOption(ParticleTypes.BLOCK, state), at, n, p.kind() == ImpactPayload.GLASS ? 12 : 6, 0.15, r);
				}
				if (p.kind() == ImpactPayload.STONE || p.kind() == ImpactPayload.METAL) {
					burst(level, ModParticles.SPARK, at, n, p.kind() == ImpactPayload.METAL ? 7 : 2, 0.2, r);
				}
				if (p.kind() != ImpactPayload.GLASS) {
					level.addParticle(ModParticles.GUN_SMOKE, at.x + n.x * 0.05, at.y + n.y * 0.05, at.z + n.z * 0.05, n.x * 0.03, n.y * 0.03, n.z * 0.03);
					ClientBullets.hole(at, n, p.kind() == ImpactPayload.METAL);
				}
			}
		}
	}

	private static void burst(ClientLevel level, ParticleOptions particle, Vec3 at, Vec3 n, int count, double speed, Random r) {
		for (int i = 0; i < count; i++) {
			Vec3 v = n.scale(speed * (0.4 + r.nextDouble())).add(r.nextGaussian() * speed * 0.5, r.nextGaussian() * speed * 0.5 + speed * 0.3, r.nextGaussian() * speed * 0.5);
			level.addParticle(particle, at.x + n.x * 0.03, at.y + n.y * 0.03, at.z + n.z * 0.03, v.x, v.y, v.z);
		}
	}

	// ---------------------------------------------------------------- explosions

	public static void fx(Minecraft mc, FxPayload p) {
		ClientLevel level = mc.level;
		Vec3 at = p.at();
		Random r = new Random(p.seed());
		switch (p.kind()) {
			case FxPayload.GRENADE -> {
				fragments(level, at, Explosives.SPHERE, p.seed(), 70, 1.2);
				dust(level, at, 24, 0.35, r);
			}
			case FxPayload.CLAYMORE -> {
				fragments(level, at, Explosives.fan(p.dir()), p.seed(), 120, 1.8);
				dust(level, at, 20, 0.3, r);
			}
			case FxPayload.FLASHBANG -> {
				level.addParticle(ModParticles.FLASH, at.x, at.y, at.z, 0, 0, 0);
				for (int i = 0; i < 24; i++) {
					level.addParticle(ModParticles.SPARK, at.x, at.y, at.z, r.nextGaussian() * 0.25, r.nextDouble() * 0.3, r.nextGaussian() * 0.25);
				}
				for (int i = 0; i < 6; i++) {
					level.addParticle(ModParticles.GUN_SMOKE, at.x, at.y, at.z, r.nextGaussian() * 0.05, 0.03, r.nextGaussian() * 0.05);
				}
			}
			case FxPayload.ROCKET, FxPayload.C4 -> {
				level.addParticle(ModParticles.FLASH, at.x, at.y, at.z, 0, 0, 0);
				for (int i = 0; i < 40; i++) {
					level.addParticle(ModParticles.SPARK, at.x, at.y, at.z, r.nextGaussian() * 0.5, r.nextDouble() * 0.6, r.nextGaussian() * 0.5);
				}
				for (int i = 0; i < 24; i++) {
					level.addParticle(ModParticles.ROCKET_SMOKE, at.x + r.nextGaussian(), at.y + r.nextDouble(), at.z + r.nextGaussian(), r.nextGaussian() * 0.12,
						r.nextDouble() * 0.15, r.nextGaussian() * 0.12);
				}
				dust(level, at, 30, 0.5, r);
			}
			case FxPayload.MINE -> {
				dust(level, at, 40, 0.45, r);
				for (int i = 0; i < 10; i++) {
					level.addParticle(ModParticles.ROCKET_SMOKE, at.x, at.y, at.z, r.nextGaussian() * 0.05, 0.2 + r.nextDouble() * 0.2, r.nextGaussian() * 0.05);
				}
			}
			case FxPayload.BACKBLAST -> {
				Vec3 d = p.dir();
				for (int i = 0; i < 30; i++) {
					double s = 0.3 + r.nextDouble() * 0.6;
					level.addParticle(ModParticles.ROCKET_SMOKE, at.x, at.y, at.z, d.x * s + r.nextGaussian() * 0.08, d.y * s + r.nextGaussian() * 0.08,
						d.z * s + r.nextGaussian() * 0.08);
				}
				for (int i = 0; i < 8; i++) {
					level.addParticle(ModParticles.ROCKET_FLAME, at.x, at.y, at.z, d.x * 0.4, d.y * 0.4, d.z * 0.4);
				}
			}
			default -> {
			}
		}
	}

	private static void fragments(ClientLevel level, Vec3 at, Explosives.Pattern pattern, long seed, int count, double speed) {
		Random r = new Random(seed);
		for (int i = 0; i < count; i++) {
			Vec3 d = pattern.direction(r).scale(speed * (0.6 + r.nextDouble() * 0.6));
			r.nextDouble();
			level.addParticle(ModParticles.SPARK, at.x, at.y, at.z, d.x, d.y, d.z);
		}
	}

	private static void dust(ClientLevel level, Vec3 at, int count, double speed, Random r) {
		BlockState ground = level.getBlockState(BlockPos.containing(at).below());
		if (ground.isAir()) {
			ground = level.getBlockState(BlockPos.containing(at).below(2));
		}
		if (ground.isAir()) {
			return;
		}
		BlockParticleOption particle = new BlockParticleOption(ParticleTypes.BLOCK, ground);
		for (int i = 0; i < count; i++) {
			double a = r.nextDouble() * Math.PI * 2;
			double s = speed * (0.3 + r.nextDouble());
			level.addParticle(particle, at.x, at.y + 0.1, at.z, Math.cos(a) * s, 0.2 + r.nextDouble() * speed, Math.sin(a) * s);
		}
	}

	// ---------------------------------------------------------------- per tick

	/** Smoke grenades, rocket trails, the mine detector. */
	public static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		LocalPlayer player = mc.player;
		if (level == null || player == null) {
			ROCKETS_HEARD.clear();
			return;
		}
		Random r = new Random();
		Set<Integer> alive = new HashSet<>();
		for (Entity e : level.entitiesForRendering()) {
			if (e instanceof GrenadeEntity g && g.smoke() > 0) {
				int left = g.smoke();
				// a thick cloud quickly, then a steady stream for the rest of the burn
				int n = left > GrenadeEntity.SMOKE_TICKS - 60 ? 3 : left > 200 ? 1 : (left % 3 == 0 ? 1 : 0);
				for (int i = 0; i < n; i++) {
					level.addParticle(ModParticles.SMOKE_SCREEN, g.getX(), g.getY() + 0.2, g.getZ(), r.nextGaussian() * 0.06, 0.03 + r.nextDouble() * 0.03,
						r.nextGaussian() * 0.06);
				}
			} else if (e instanceof RocketEntity rocket) {
				alive.add(rocket.getId());
				if (ROCKETS_HEARD.add(rocket.getId())) {
					mc.getSoundManager().play(new RocketSound(rocket));
				}
				Vec3 v = rocket.getDeltaMovement();
				Vec3 tail = rocket.position().subtract(v.normalize().scale(0.6));
				if (rocket.burning()) {
					level.addParticle(ModParticles.ROCKET_FLAME, tail.x, tail.y, tail.z, -v.x * 0.05, -v.y * 0.05, -v.z * 0.05);
					int steps = (int) Math.min(8, v.length() / 1.2) + 1;
					for (int i = 0; i < steps; i++) {
						Vec3 p = tail.subtract(v.scale(i / (double) steps));
						level.addParticle(ModParticles.ROCKET_SMOKE, p.x, p.y, p.z, r.nextGaussian() * 0.01, r.nextGaussian() * 0.01, r.nextGaussian() * 0.01);
					}
				} else if (rocket.tickCount % 2 == 0) {
					level.addParticle(ModParticles.GUN_SMOKE, tail.x, tail.y, tail.z, 0, 0, 0);
				}
			}
		}
		ROCKETS_HEARD.retainAll(alive);
		detector(mc, level, player);
	}

	private static void detector(Minecraft mc, ClientLevel level, LocalPlayer player) {
		if (!player.getMainHandItem().is(ModItems.MINE_DETECTOR) && !player.getOffhandItem().is(ModItems.MINE_DETECTOR)) {
			detectorDistance = -1;
			return;
		}
		if (player.tickCount % 5 == 0) {
			int r = MineDetectorItem.RANGE;
			BlockPos center = player.blockPosition();
			double best = -1;
			for (BlockPos p : BlockPos.betweenClosed(center.offset(-r, -3, -r), center.offset(r, 2, r))) {
				Block b = level.getBlockState(p).getBlock();
				if (b instanceof MineBlock || b instanceof ClaymoreBlock || b instanceof C4Block) {
					double d = Math.sqrt(p.distToCenterSqr(player.getX(), player.getY(), player.getZ()));
					if (d <= r && (best < 0 || d < best)) {
						best = d;
					}
				}
			}
			detectorDistance = best;
		}
		if (detectorDistance >= 0 && --detectorTimer <= 0) {
			float closeness = (float) (1.0 - detectorDistance / MineDetectorItem.RANGE);
			level.playLocalSound(player.getX(), player.getY(), player.getZ(), ModSounds.DETECTOR_BEEP.value(), SoundSource.PLAYERS, 0.5F, 0.8F + closeness * 0.9F,
				false);
			detectorTimer = (int) Math.max(2, 4 + detectorDistance * 4);
		}
	}

	public static boolean isLiving(@Nullable Entity e) {
		return e instanceof LivingEntity;
	}
}
