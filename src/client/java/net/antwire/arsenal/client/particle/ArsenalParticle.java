package net.antwire.arsenal.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/** One configurable billboard particle for every effect of the mod; the {@link Style} says how it behaves. */
public class ArsenalParticle extends SingleQuadParticle {
	private final Style style;
	private final SpriteSet sprites;
	private final float startSize;
	private final float endSize;
	private float jitter = 1.0F;

	/** Colours are 0xRRGGBB; sizes in blocks; gravity in blocks per tick². */
	public record Style(int minLife, int maxLife, float minSize, float maxSize, float growth, int startColor, int endColor, float alpha, float fadeInFraction,
		float fadeOutStart, float friction, float buoyancy, float gravity, boolean physics, boolean fullBright, boolean animateSprite, float colorJitter) {
	}

	public ArsenalParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, Style style, SpriteSet sprites, RandomSource random) {
		super(level, x, y, z, sprites.get(random));
		this.style = style;
		this.sprites = sprites;
		this.xd = xd;
		this.yd = yd;
		this.zd = zd;
		this.hasPhysics = style.physics();
		this.friction = style.friction();
		this.gravity = 0.0F;
		this.lifetime = style.minLife() + (style.maxLife() > style.minLife() ? random.nextInt(style.maxLife() - style.minLife()) : 0);
		this.startSize = Mth.lerp(random.nextFloat(), style.minSize(), style.maxSize());
		this.endSize = this.startSize * style.growth();
		this.quadSize = this.startSize;
		this.alpha = style.fadeInFraction() > 0 ? 0.0F : style.alpha();
		this.roll = random.nextFloat() * Mth.TWO_PI;
		this.oRoll = this.roll;
		this.jitter = 1.0F + (random.nextFloat() - 0.5F) * style.colorJitter();
		this.applyColor(0.0F);
		if (style.animateSprite()) {
			this.setSpriteFromAge(sprites);
		}
	}

	private void applyColor(float t) {
		int a = this.style.startColor();
		int b = this.style.endColor();
		float r = Mth.lerp(t, (a >> 16 & 255) / 255.0F, (b >> 16 & 255) / 255.0F) * this.jitter;
		float g = Mth.lerp(t, (a >> 8 & 255) / 255.0F, (b >> 8 & 255) / 255.0F) * this.jitter;
		float bl = Mth.lerp(t, (a & 255) / 255.0F, (b & 255) / 255.0F) * this.jitter;
		this.setColor(Mth.clamp(r, 0, 1), Mth.clamp(g, 0, 1), Mth.clamp(bl, 0, 1));
	}

	@Override
	public void tick() {
		this.xo = this.x;
		this.yo = this.y;
		this.zo = this.z;
		this.oRoll = this.roll;
		if (this.age++ >= this.lifetime) {
			this.remove();
			return;
		}
		float t = (float) this.age / this.lifetime;
		this.yd += this.style.buoyancy() - this.style.gravity();
		this.move(this.xd, this.yd, this.zd);
		if (this.onGround && this.style.physics()) {
			this.xd *= 0.6;
			this.zd *= 0.6;
		}
		this.xd *= this.friction;
		this.yd *= this.friction;
		this.zd *= this.friction;
		if (this.style.physics()) {
			this.roll += (float) (Math.sqrt(this.xd * this.xd + this.zd * this.zd) * 3.0);
		}
		float grow = 1.0F - (1.0F - t) * (1.0F - t);
		this.quadSize = Mth.lerp(grow, this.startSize, this.endSize);
		this.applyColor(Math.min(1.0F, t * 1.4F));
		float fadeIn = this.style.fadeInFraction() > 0 ? Math.min(1.0F, t / this.style.fadeInFraction()) : 1.0F;
		float fadeOut = t > this.style.fadeOutStart() ? 1.0F - (t - this.style.fadeOutStart()) / (1.0F - this.style.fadeOutStart()) : 1.0F;
		this.alpha = this.style.alpha() * fadeIn * Mth.clamp(fadeOut, 0.0F, 1.0F);
		if (this.style.animateSprite()) {
			this.setSpriteFromAge(this.sprites);
		}
	}

	@Override
	protected int getLightCoords(float a) {
		if (this.style.fullBright()) {
			return 0xF000F0;
		}
		int here = super.getLightCoords(a);
		return LightCoordsUtil.pack(Math.max(LightCoordsUtil.block(here), 0), LightCoordsUtil.sky(here));
	}

	@Override
	protected SingleQuadParticle.Layer getLayer() {
		return SingleQuadParticle.Layer.TRANSLUCENT;
	}

	public static ParticleProvider<SimpleParticleType> provider(Style style, SpriteSet sprites) {
		return (options, level, x, y, z, xd, yd, zd, random) -> new ArsenalParticle(level, x, y, z, xd, yd, zd, style, sprites, random);
	}
}
