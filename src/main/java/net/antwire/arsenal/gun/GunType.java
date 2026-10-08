package net.antwire.arsenal.gun;

import java.util.EnumSet;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Every firearm and launcher with its real-world figures, scaled to Minecraft where it has to be. */
public enum GunType {
	GLOCK_17("glock17", "Glock 17", Category.PISTOL, Caliber.NINE_MM, null, 17, 600, Action.SEMI_AUTO, EnumSet.of(FireMode.SEMI),
		1.8F, 0.5F, 2.4F, 0.7F, 32, 0.85F, false, 1.0, 1.0F, 0.0F),
	DESERT_EAGLE("deagle", "Desert Eagle", Category.PISTOL, Caliber.AE50, null, 7, 240, Action.SEMI_AUTO, EnumSet.of(FireMode.SEMI),
		2.4F, 0.6F, 7.5F, 1.6F, 38, 0.85F, false, 1.0, 1.0F, 0.0F),
	MP5("mp5", "MP5A3", Category.SMG, Caliber.NINE_MM, null, 30, 800, Action.SEMI_AUTO, EnumSet.of(FireMode.SEMI, FireMode.BURST, FireMode.AUTO),
		2.2F, 0.7F, 1.5F, 0.6F, 44, 0.8F, false, 1.07, 1.0F, -0.02F),
	M4A1("m4a1", "M4A1", Category.RIFLE, Caliber.NATO_556, null, 30, 800, Action.SEMI_AUTO, EnumSet.of(FireMode.SEMI, FireMode.AUTO),
		2.6F, 0.25F, 2.1F, 0.8F, 48, 0.6F, false, 1.0, 1.0F, -0.04F),
	AK47("ak47", "AK-47", Category.RIFLE, Caliber.SOVIET_762, null, 30, 600, Action.SEMI_AUTO, EnumSet.of(FireMode.AUTO, FireMode.SEMI),
		3.0F, 0.45F, 3.1F, 1.3F, 50, 0.75F, false, 1.0, 1.0F, -0.05F),
	REMINGTON_870("r870", "Remington 870", Category.SHOTGUN, Caliber.BUCKSHOT, null, 6, 75, Action.PUMP, EnumSet.of(FireMode.SEMI),
		4.2F, 3.2F, 7.0F, 1.6F, 12, 0.85F, false, 1.0, 1.0F, -0.04F),
	BENELLI_M4("m4super90", "Benelli M4", Category.SHOTGUN, Caliber.BUCKSHOT, null, 7, 300, Action.SEMI_AUTO, EnumSet.of(FireMode.SEMI),
		3.8F, 3.0F, 6.0F, 1.4F, 11, 0.85F, false, 1.0, 1.0F, -0.05F),
	AWM("awm", "AWM", Category.SNIPER, Caliber.LAPUA_338, null, 5, 45, Action.BOLT, EnumSet.of(FireMode.SEMI),
		4.0F, 0.02F, 9.0F, 2.0F, 70, 0.1F, true, 1.0, 1.0F, -0.10F),
	BARRETT_M82("m82", "Barrett M82A1", Category.SNIPER, Caliber.BMG_50, null, 10, 150, Action.SEMI_AUTO, EnumSet.of(FireMode.SEMI),
		5.0F, 0.04F, 12.0F, 3.0F, 80, 0.1F, true, 1.0, 1.0F, -0.15F),
	RPG7("rpg7", "RPG-7", Category.LAUNCHER, null, Rocket.PG7V, 1, 30, Action.SINGLE, EnumSet.of(FireMode.SEMI),
		1.5F, 0.4F, 4.0F, 1.0F, 60, 0.45F, false, 1.0, 1.0F, -0.12F),
	JAVELIN("javelin", "FGM-148 Javelin", Category.LAUNCHER, null, Rocket.JAVELIN, 1, 20, Action.SINGLE, EnumSet.of(FireMode.SEMI),
		1.5F, 0.4F, 2.5F, 0.5F, 90, 0.25F, false, 1.0, 1.0F, -0.18F);

	public final String id;
	public final String displayName;
	public final Category category;
	/** Cartridge for firearms; null for launchers. */
	public final @Nullable Caliber caliber;
	/** Projectile for launchers; null for firearms. */
	public final @Nullable Rocket rocket;
	public final int magazine;
	/** Rounds per minute (cyclic rate, or how fast the action can be worked). */
	public final int rpm;
	public final Action action;
	public final Set<FireMode> modes;
	/** Cone half-angle in degrees from the hip and aimed down the sights. */
	public final float hipSpread;
	public final float aimSpread;
	/** Muzzle climb and sideways kick per shot, degrees. */
	public final float recoilUp;
	public final float recoilSide;
	/** Ticks for a full reload (shotguns: per shell). */
	public final int reloadTicks;
	/** Field of view while aiming, as a fraction of normal (0.1 = 10x scope). */
	public final float zoom;
	/** Aims through a telescopic sight drawn over the screen. */
	public final boolean scope;
	/** Barrel length: multiplies the cartridge's muzzle velocity. */
	public final double velocityFactor;
	public final float damageFactor;
	/** Movement speed while held, as a fraction (-0.1 = 10 % slower). */
	public final float speedModifier;

	GunType(String id, String displayName, Category category, @Nullable Caliber caliber, @Nullable Rocket rocket, int magazine, int rpm, Action action,
		Set<FireMode> modes, float hipSpread, float aimSpread, float recoilUp, float recoilSide, int reloadTicks, float zoom, boolean scope,
		double velocityFactor, float damageFactor, float speedModifier) {
		this.id = id;
		this.displayName = displayName;
		this.category = category;
		this.caliber = caliber;
		this.rocket = rocket;
		this.magazine = magazine;
		this.rpm = rpm;
		this.action = action;
		this.modes = modes;
		this.hipSpread = hipSpread;
		this.aimSpread = aimSpread;
		this.recoilUp = recoilUp;
		this.recoilSide = recoilSide;
		this.reloadTicks = reloadTicks;
		this.zoom = zoom;
		this.scope = scope;
		this.velocityFactor = velocityFactor;
		this.damageFactor = damageFactor;
		this.speedModifier = speedModifier;
	}

	/** Ticks between two shots. */
	public double interval() {
		return 1200.0 / this.rpm;
	}

	public boolean isShotgun() {
		return this.category == Category.SHOTGUN;
	}

	public boolean isLauncher() {
		return this.category == Category.LAUNCHER;
	}

	/** Shotguns load shell by shell into the tube. */
	public boolean loadsSingly() {
		return this.isShotgun();
	}

	/** Held with both hands at the shoulder all the time (pistols only when aiming). */
	public boolean twoHanded() {
		return this.category != Category.PISTOL;
	}

	public FireMode defaultMode() {
		return this.modes.contains(FireMode.AUTO) && this.category != Category.SMG ? FireMode.AUTO : this.modes.iterator().next();
	}

	public static GunType byOrdinal(int i) {
		GunType[] all = values();
		return all[Math.floorMod(i, all.length)];
	}

	public enum Category {
		PISTOL, SMG, RIFLE, SHOTGUN, SNIPER, LAUNCHER
	}

	public enum Action {
		SEMI_AUTO, PUMP, BOLT, SINGLE
	}

	public enum FireMode {
		SEMI, BURST, AUTO
	}

	public enum Rocket {
		PG7V, JAVELIN
	}
}
