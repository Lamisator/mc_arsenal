package net.antwire.arsenal.client;

import net.antwire.arsenal.gun.GunType;

/**
 * Real dimensions of each gun model, in metres from the grip (where the right hand holds it): written by
 * tools/gen_assets.py together with the models, so that sights and muzzles line up with what is drawn.
 */
public final class GunGeometry {
	/** Height of the line of sight above the grip. */
	public final double sight;
	/** How far the rear sight (or the eyepiece) is behind the grip; negative means ahead of it. */
	public final double eyeRelief;
	/** Muzzle: forward of the grip, and height of the bore above it. */
	public final double muzzle;
	public final double bore;

	private GunGeometry(double sight, double eyeRelief, double muzzle, double bore) {
		this.sight = sight;
		this.eyeRelief = eyeRelief;
		this.muzzle = muzzle;
		this.bore = bore;
	}

	public static GunGeometry of(GunType type) {
		return switch (type) {
			case GLOCK_17 -> new GunGeometry(0.0380, 0.0100, 0.1710, 0.0190);
			case DESERT_EAGLE -> new GunGeometry(0.0490, 0.0180, 0.2650, 0.0280);
			case MP5 -> new GunGeometry(0.0640, 0.1400, 0.3450, 0.0180);
			case M4A1 -> new GunGeometry(0.0720, 0.0750, 0.4040, 0.0290);
			case AK47 -> new GunGeometry(0.0650, 0.0900, 0.5000, 0.0280);
			case REMINGTON_870 -> new GunGeometry(0.0480, 0.1200, 0.7600, 0.0340);
			case BENELLI_M4 -> new GunGeometry(0.0700, 0.1300, 0.4700, 0.0380);
			case AWM -> new GunGeometry(0.0880, 0.2200, 0.7700, 0.0460);
			case BARRETT_M82 -> new GunGeometry(0.1180, 0.2800, 0.9500, 0.0540);
			case RPG7 -> new GunGeometry(0.0520, 0.1200, 0.6900, 0.0200);
			case JAVELIN -> new GunGeometry(0.1150, 0.1000, 0.6200, 0.0700);
		};
	}
}
