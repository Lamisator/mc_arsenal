package net.antwire.arsenal.client.particle;

import net.antwire.arsenal.registry.ModParticles;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;

public final class ArsenalParticles {
	// life, size, growth, colours, alpha, fade in/out, friction, buoyancy, gravity, physics, fullbright, animate, jitter
	public static final ArsenalParticle.Style MUZZLE_FLASH = new ArsenalParticle.Style(1, 2, 0.06F, 0.1F, 1.3F, 0xFFF6C8, 0xFF9A30, 0.9F,
		0.0F, 0.3F, 0.6F, 0.0F, 0.0F, false, true, false, 0.1F);
	public static final ArsenalParticle.Style GUN_SMOKE = new ArsenalParticle.Style(16, 34, 0.04F, 0.07F, 4.0F, 0xD8D8D4, 0xA8A8A4, 0.18F,
		0.0F, 0.3F, 0.88F, 0.0015F, 0.0F, false, false, false, 0.1F);
	public static final ArsenalParticle.Style CASING = new ArsenalParticle.Style(40, 60, 0.014F, 0.018F, 1.0F, 0xE8C060, 0xC89C40, 1.0F,
		0.0F, 0.85F, 0.98F, 0.0F, 0.045F, true, false, false, 0.15F);
	public static final ArsenalParticle.Style SPARK = new ArsenalParticle.Style(4, 9, 0.03F, 0.06F, 0.6F, 0xFFF0B0, 0xFF6010, 1.0F,
		0.0F, 0.5F, 0.92F, 0.0F, 0.03F, true, true, false, 0.1F);
	public static final ArsenalParticle.Style BLOOD = new ArsenalParticle.Style(12, 24, 0.06F, 0.12F, 1.2F, 0x8A0A0A, 0x4A0404, 0.95F,
		0.0F, 0.6F, 0.9F, 0.0F, 0.025F, true, false, false, 0.2F);
	public static final ArsenalParticle.Style SMOKE_SCREEN = new ArsenalParticle.Style(260, 420, 1.4F, 2.2F, 2.2F, 0xE8E8E6, 0xC4C4C2, 0.92F,
		0.1F, 0.7F, 0.96F, 0.0004F, 0.0F, false, false, false, 0.06F);
	public static final ArsenalParticle.Style ROCKET_SMOKE = new ArsenalParticle.Style(50, 90, 0.25F, 0.4F, 5.0F, 0xE0DED8, 0x9A9894, 0.6F,
		0.0F, 0.4F, 0.92F, 0.001F, 0.0F, false, false, false, 0.1F);
	public static final ArsenalParticle.Style ROCKET_FLAME = new ArsenalParticle.Style(2, 5, 0.25F, 0.4F, 0.4F, 0xFFF4C0, 0xFF5A10, 0.95F,
		0.0F, 0.3F, 0.9F, 0.0F, 0.0F, false, true, false, 0.1F);
	public static final ArsenalParticle.Style FLASH = new ArsenalParticle.Style(3, 5, 3.5F, 4.5F, 1.3F, 0xFFFFFF, 0xFFFFF0, 1.0F,
		0.0F, 0.2F, 1.0F, 0.0F, 0.0F, false, true, false, 0.0F);

	private ArsenalParticles() {
	}

	public static void register() {
		ParticleProviderRegistry registry = ParticleProviderRegistry.getInstance();
		registry.register(ModParticles.MUZZLE_FLASH, s -> ArsenalParticle.provider(MUZZLE_FLASH, s));
		registry.register(ModParticles.GUN_SMOKE, s -> ArsenalParticle.provider(GUN_SMOKE, s));
		registry.register(ModParticles.CASING, s -> ArsenalParticle.provider(CASING, s));
		registry.register(ModParticles.SPARK, s -> ArsenalParticle.provider(SPARK, s));
		registry.register(ModParticles.BLOOD, s -> ArsenalParticle.provider(BLOOD, s));
		registry.register(ModParticles.SMOKE_SCREEN, s -> ArsenalParticle.provider(SMOKE_SCREEN, s));
		registry.register(ModParticles.ROCKET_SMOKE, s -> ArsenalParticle.provider(ROCKET_SMOKE, s));
		registry.register(ModParticles.ROCKET_FLAME, s -> ArsenalParticle.provider(ROCKET_FLAME, s));
		registry.register(ModParticles.FLASH, s -> ArsenalParticle.provider(FLASH, s));
	}
}
