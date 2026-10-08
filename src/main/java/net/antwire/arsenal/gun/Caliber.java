package net.antwire.arsenal.gun;

/**
 * A cartridge: what comes out of the barrel. Damage is the hit at the muzzle velocity; it falls with the square of the
 * remaining velocity (the bullet's energy). One block is one metre, so velocities are real values in m/s.
 */
public enum Caliber {
	// id, damage, muzzle velocity m/s, drag per tick, penetration, pellets, class, tracer
	NINE_MM("9mm", 6.0F, 375, 0.016, 1.0, 1, Threat.HANDGUN),
	AE50("50ae", 11.0F, 470, 0.015, 1.6, 1, Threat.HANDGUN),
	NATO_556("556", 8.0F, 910, 0.010, 2.4, 1, Threat.RIFLE),
	SOVIET_762("762", 9.5F, 715, 0.011, 2.8, 1, Threat.RIFLE),
	BUCKSHOT("buckshot", 2.6F, 400, 0.030, 0.4, 9, Threat.SHOT),
	SLUG("slug", 14.0F, 470, 0.020, 1.5, 1, Threat.SHOT),
	LAPUA_338("338", 24.0F, 915, 0.006, 4.5, 1, Threat.MAGNUM_RIFLE),
	BMG_50("50bmg", 34.0F, 853, 0.005, 9.0, 1, Threat.ANTI_MATERIEL);

	public final String id;
	public final float damage;
	public final double velocity;
	/** Fraction of its speed the bullet loses every tick in air. */
	public final double drag;
	/** How much material it gets through: one "point" is about a plank. */
	public final double penetration;
	public final int pellets;
	public final Threat threat;

	Caliber(String id, float damage, double velocity, double drag, double penetration, int pellets, Threat threat) {
		this.id = id;
		this.damage = damage;
		this.velocity = velocity;
		this.drag = drag;
		this.penetration = penetration;
		this.pellets = pellets;
		this.threat = threat;
	}

	/** Blocks per tick. */
	public double speed() {
		return this.velocity / 20.0;
	}

	public static Caliber byOrdinal(int i) {
		Caliber[] all = values();
		return all[Math.floorMod(i, all.length)];
	}

	/** What body armour has to stop: armour is rated against these. */
	public enum Threat {
		HANDGUN, SHOT, RIFLE, MAGNUM_RIFLE, ANTI_MATERIEL, FRAGMENT, EXPLOSION
	}
}
