package net.antwire.arsenal.gun;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.antwire.arsenal.entity.RocketEntity;
import net.antwire.arsenal.explosive.Explosives;
import net.antwire.arsenal.network.ActionPayload;
import net.antwire.arsenal.network.FirePayload;
import net.antwire.arsenal.network.ShotPayload;
import net.antwire.arsenal.registry.ModComponents;
import net.antwire.arsenal.registry.ModItems;
import net.antwire.arsenal.registry.ModSounds;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Server side of shooting: rate of fire, ammunition, reloads, fire modes. */
public final class GunServer {
	private static final Map<UUID, Double> NEXT_SHOT = new HashMap<>();
	private static final List<Delayed> DELAYED = new ArrayList<>();

	private record Delayed(ServerLevel level, Entity at, Holder<SoundEvent> sound, float pitch, long when) {
	}

	private GunServer() {
	}

	public static void fire(ServerPlayer player, FirePayload p) {
		ItemStack stack = player.getMainHandItem();
		GunType type = GunItem.type(stack);
		if (type == null || player.isSpectator() || !player.isAlive()) {
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		GunState s = GunItem.state(stack);
		if (s.reloading(now)) {
			if (!type.loadsSingly()) {
				return;
			}
			// a shotgun can fire between two shells
			s = s.withReload(0, 0);
		}
		double next = NEXT_SHOT.getOrDefault(player.getUUID(), 0.0);
		// one tick of leeway for the client's timing
		if (now + 1.0 < next) {
			return;
		}
		if (s.ammo() <= 0) {
			stack.set(ModComponents.GUN, s);
			level.playSound(player, player.getX(), player.getEyeY(), player.getZ(), ModSounds.DRY_FIRE.value(), SoundSource.PLAYERS, 0.6F, 1.0F);
			NEXT_SHOT.put(player.getUUID(), now + 4.0);
			return;
		}
		NEXT_SHOT.put(player.getUUID(), Math.max(next, now - 1.0) + type.interval());

		float yaw = p.yaw();
		float pitch = p.pitch();
		// the client's view may lead the server's by a few ticks, not by a turn
		if (Math.abs(Mth.wrapDegrees(yaw - player.getYRot())) > 40 || Math.abs(pitch - player.getXRot()) > 40) {
			yaw = player.getYRot();
			pitch = player.getXRot();
		}
		Caliber caliber = type.isShotgun() ? s.nextShell() : type.caliber;
		if (type.isShotgun()) {
			s = s.withShells(s.shells().substring(0, s.shells().length() - 1));
		} else {
			s = s.withAmmo(s.ammo() - 1);
		}
		stack.set(ModComponents.GUN, s);
		showLoaded(stack, type, s.ammo() > 0);

		Vec3 eye = player.getEyePosition();
		if (type.isLauncher()) {
			if (type == GunType.JAVELIN && p.lockEntity() < 0 && !p.hasLockPos()) {
				// refused: the missile needs a lock (the client should not have let it go)
				stack.set(ModComponents.GUN, s.withAmmo(s.ammo() + 1));
				showLoaded(stack, type, true);
				return;
			}
			Vec3 dir = Spread.look(yaw, pitch);
			Entity target = p.lockEntity() >= 0 ? level.getEntity(p.lockEntity()) : null;
			RocketEntity rocket = type == GunType.RPG7 ? RocketEntity.rpg(level, player, eye.add(dir.scale(0.8)), dir)
				: RocketEntity.javelin(level, player, eye.add(dir.scale(0.8)), dir, target, p.hasLockPos() ? p.lockPos() : null);
			level.addFreshEntity(rocket);
			Explosives.backblast(level, player, eye, dir);
		} else if (caliber != null) {
			for (Vec3 dir : Spread.directions(type, caliber, yaw, pitch, p.aiming(), Math.clamp(p.movement(), 0, 1), p.seed())) {
				Ballistics.fire(level, player, eye, dir, caliber, type.velocityFactor, type.damageFactor);
			}
		}
		level.playSound(player, player.getX(), player.getEyeY(), player.getZ(), ModSounds.SHOTS.get(type).value(), SoundSource.PLAYERS,
			type.category == GunType.Category.SNIPER ? 6.0F : 4.0F, 0.95F + level.getRandom().nextFloat() * 0.1F);
		level.gameEvent(player, GameEvent.PROJECTILE_SHOOT, eye);
		ShotPayload shot = new ShotPayload(player.getId(), type.ordinal(), caliber == null ? -1 : caliber.ordinal(), yaw, pitch, p.seed(), p.aiming(), p.movement());
		for (ServerPlayer other : Ballistics.watching(level, eye, 256)) {
			if (other != player) {
				ServerPlayNetworking.send(other, shot);
			}
		}
		// working the action: the pump or the bolt after the shot
		if (type.action == GunType.Action.PUMP && s.ammo() > 0) {
			later(level, player, ModSounds.PUMP, 1.0F, now + 6);
		} else if (type.action == GunType.Action.BOLT && s.ammo() > 0) {
			later(level, player, ModSounds.BOLT, 1.0F, now + 9);
		}
		if (s.ammo() == 0 && type.action != GunType.Action.SINGLE) {
			level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), ModSounds.DRY_FIRE.value(), SoundSource.PLAYERS, 0.25F, 1.4F);
		}
	}

	public static void action(ServerPlayer player, ActionPayload p) {
		ItemStack stack = player.getMainHandItem();
		GunType type = GunItem.type(stack);
		if (type == null) {
			return;
		}
		if (p.action() == ActionPayload.RELOAD) {
			startReload(player, stack, type);
		} else if (p.action() == ActionPayload.MODE) {
			switchMode(player, stack, type);
		}
	}

	private static void switchMode(ServerPlayer player, ItemStack stack, GunType type) {
		GunState s = GunItem.state(stack);
		if (type.isShotgun()) {
			int shell = s.mode() == 1 ? 0 : 1;
			stack.set(ModComponents.GUN, s.withMode(shell));
			player.sendOverlayMessage(Component.translatable("message.arsenal.load_next", Component.translatable(shell == 1 ? "item.arsenal.shell_slug" : "item.arsenal.shell_buckshot")));
		} else {
			if (type.modes.size() < 2) {
				return;
			}
			GunType.FireMode[] all = GunType.FireMode.values();
			GunType.FireMode current = s.fireMode(type);
			GunType.FireMode next = current;
			for (int i = 1; i <= all.length; i++) {
				GunType.FireMode m = all[(current.ordinal() + i) % all.length];
				if (type.modes.contains(m)) {
					next = m;
					break;
				}
			}
			stack.set(ModComponents.GUN, s.withMode(next.ordinal()));
			player.sendOverlayMessage(Component.translatable("tooltip.arsenal.mode." + next.name().toLowerCase(java.util.Locale.ROOT)));
		}
		player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), ModSounds.MODE.value(), SoundSource.PLAYERS, 0.6F, 1.0F);
	}

	private static void startReload(ServerPlayer player, ItemStack stack, GunType type) {
		GunState s = GunItem.state(stack);
		long now = player.level().getGameTime();
		if (s.reloading(now) || s.ammo() >= type.magazine) {
			return;
		}
		Item ammo = type.isShotgun() ? preferredShell(player, s) : ammoItem(type);
		if (ammo == null || !player.hasInfiniteMaterials() && count(player.getInventory(), ammo) == 0) {
			player.sendOverlayMessage(Component.translatable("message.arsenal.no_ammo"));
			return;
		}
		int ticks = (int) Math.round(type.reloadTicks * reloadSpeed(type, s));
		stack.set(ModComponents.GUN, s.withReload(now + ticks, ticks));
		Holder<SoundEvent> sound = switch (type.category) {
			case SHOTGUN -> ModSounds.SHELL_INSERT;
			case LAUNCHER -> ModSounds.ROCKET_LOAD;
			default -> ModSounds.MAG_OUT;
		};
		if (!type.isShotgun()) {
			player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), sound.value(), SoundSource.PLAYERS, 0.8F, 1.0F);
		}
	}

	/** An empty magazine takes longer: the bolt has to be released or the slide racked. */
	private static double reloadSpeed(GunType type, GunState s) {
		return !type.isShotgun() && !type.isLauncher() && s.ammo() == 0 ? 1.25 : 1.0;
	}

	/** Finishes reloads that are due, and plays delayed sounds. */
	public static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ItemStack stack = player.getMainHandItem();
			GunType type = GunItem.type(stack);
			if (type == null) {
				continue;
			}
			GunState s = GunItem.state(stack);
			long now = player.level().getGameTime();
			if (s.reloadEnd() > 0 && now >= s.reloadEnd()) {
				finishReload(player, stack, type, s, now);
			}
		}
		Iterator<Delayed> it = DELAYED.iterator();
		while (it.hasNext()) {
			Delayed d = it.next();
			if (d.level.getGameTime() >= d.when) {
				if (d.at.isAlive()) {
					d.level.playSound(null, d.at.getX(), d.at.getEyeY(), d.at.getZ(), d.sound.value(), SoundSource.PLAYERS, 0.8F, d.pitch);
				}
				it.remove();
			}
		}
	}

	private static void finishReload(ServerPlayer player, ItemStack stack, GunType type, GunState s, long now) {
		boolean creative = player.hasInfiniteMaterials();
		ServerLevel level = player.level();
		if (type.isShotgun()) {
			Item shell = preferredShell(player, s);
			if (shell == null || !creative && !take(player.getInventory(), shell, 1)) {
				stack.set(ModComponents.GUN, s.withReload(0, 0));
				return;
			}
			String shells = s.shells() + (shell == ModItems.SHELL_SLUG ? "S" : "B");
			s = s.withShells(shells);
			level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), ModSounds.SHELL_INSERT.value(), SoundSource.PLAYERS, 0.8F,
				0.95F + level.getRandom().nextFloat() * 0.1F);
			Item more = preferredShell(player, s);
			if (s.ammo() < type.magazine && more != null && (creative || count(player.getInventory(), more) > 0)) {
				stack.set(ModComponents.GUN, s.withReload(now + type.reloadTicks, type.reloadTicks));
			} else {
				stack.set(ModComponents.GUN, s.withReload(0, 0));
				later(level, player, ModSounds.PUMP, 1.0F, now + 4);
			}
			return;
		}
		Item ammo = ammoItem(type);
		int want = type.magazine - s.ammo();
		int got = creative ? want : takeUpTo(player.getInventory(), ammo, want);
		boolean wasEmpty = s.ammo() == 0;
		stack.set(ModComponents.GUN, s.withAmmo(s.ammo() + got).withReload(0, 0));
		showLoaded(stack, type, s.ammo() + got > 0);
		if (got > 0 && !type.isLauncher()) {
			level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), ModSounds.MAG_IN.value(), SoundSource.PLAYERS, 0.8F, 1.0F);
			if (wasEmpty) {
				later(level, player, type.category == GunType.Category.PISTOL ? ModSounds.SLIDE : type.action == GunType.Action.BOLT ? ModSounds.BOLT : ModSounds.CHARGE,
					1.0F, now + 5);
			}
		}
	}

	/** An RPG shows the rocket in its muzzle only while loaded (flag 0 of the custom model data means empty). */
	private static void showLoaded(ItemStack stack, GunType type, boolean loaded) {
		if (type != GunType.RPG7) {
			return;
		}
		if (loaded) {
			stack.remove(DataComponents.CUSTOM_MODEL_DATA);
		} else {
			stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(true), List.of(), List.of()));
		}
	}

	private static void later(ServerLevel level, Entity at, Holder<SoundEvent> sound, float pitch, long when) {
		DELAYED.add(new Delayed(level, at, sound, pitch, when));
	}

	public static @Nullable Item ammoItem(GunType type) {
		if (type.rocket != null) {
			return type.rocket == GunType.Rocket.PG7V ? ModItems.PG7V : ModItems.JAVELIN_MISSILE;
		}
		return type.caliber == null ? null : ModItems.ammo(type.caliber);
	}

	/** The shell the player asked for, or the other kind if they have none of it. */
	private static @Nullable Item preferredShell(ServerPlayer player, GunState s) {
		Item want = s.mode() == 1 ? ModItems.SHELL_SLUG : ModItems.SHELL_BUCKSHOT;
		Item other = want == ModItems.SHELL_SLUG ? ModItems.SHELL_BUCKSHOT : ModItems.SHELL_SLUG;
		if (player.hasInfiniteMaterials() || count(player.getInventory(), want) > 0) {
			return want;
		}
		return count(player.getInventory(), other) > 0 ? other : null;
	}

	public static int count(Inventory inv, Item item) {
		int n = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack st = inv.getItem(i);
			if (st.is(item)) {
				n += st.getCount();
			}
		}
		return n;
	}

	private static boolean take(Inventory inv, Item item, int n) {
		return takeUpTo(inv, item, n) == n;
	}

	private static int takeUpTo(Inventory inv, Item item, int n) {
		int got = 0;
		for (int i = 0; i < inv.getContainerSize() && got < n; i++) {
			ItemStack st = inv.getItem(i);
			if (st.is(item)) {
				int t = Math.min(n - got, st.getCount());
				st.shrink(t);
				got += t;
			}
		}
		return got;
	}

	public static void forget(ServerPlayer player) {
		NEXT_SHOT.remove(player.getUUID());
	}

	public static void clear() {
		NEXT_SHOT.clear();
		DELAYED.clear();
	}
}
