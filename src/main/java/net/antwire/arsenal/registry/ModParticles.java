package net.antwire.arsenal.registry;

import net.antwire.arsenal.Arsenal;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

public final class ModParticles {
	/** Bright star at the muzzle, one or two ticks. */
	public static final SimpleParticleType MUZZLE_FLASH = register("muzzle_flash");
	/** Small grey puff after a shot. */
	public static final SimpleParticleType GUN_SMOKE = register("gun_smoke");
	/** Spent brass flying out of the ejection port. */
	public static final SimpleParticleType CASING = register("casing");
	/** Sparks of a bullet striking stone or metal. */
	public static final SimpleParticleType SPARK = register("spark");
	/** Blood from a hit. */
	public static final SimpleParticleType BLOOD = register("blood");
	/** Thick, slow smoke of a smoke grenade. */
	public static final SimpleParticleType SMOKE_SCREEN = register("smoke_screen");
	/** Exhaust smoke behind a rocket. */
	public static final SimpleParticleType ROCKET_SMOKE = register("rocket_smoke");
	/** Full-bright rocket motor flame. */
	public static final SimpleParticleType ROCKET_FLAME = register("rocket_flame");
	/** The white burst of a flashbang. */
	public static final SimpleParticleType FLASH = register("flash");

	private ModParticles() {
	}

	private static SimpleParticleType register(String name) {
		return Registry.register(BuiltInRegistries.PARTICLE_TYPE, Arsenal.id(name), FabricParticleTypes.simple(true));
	}

	public static void init() {
	}
}
