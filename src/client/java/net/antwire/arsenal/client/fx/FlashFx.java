package net.antwire.arsenal.client.fx;

import net.antwire.arsenal.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;

/** Being on the wrong end of a flashbang: a white screen that slowly clears, and ringing ears that muffle the world. */
public final class FlashFx {
	private static long blindStart;
	private static float blindSeconds;
	private static long deafStart;
	private static float deafSeconds;
	private static SoundInstance ringing;

	private FlashFx() {
	}

	public static void start(float blind, float deaf) {
		long now = System.currentTimeMillis();
		if (blind > remaining(blindStart, blindSeconds, now)) {
			blindStart = now;
			blindSeconds = blind;
		}
		if (deaf > remaining(deafStart, deafSeconds, now)) {
			deafStart = now;
			deafSeconds = deaf;
			Minecraft mc = Minecraft.getInstance();
			if (ringing != null) {
				mc.getSoundManager().stop(ringing);
			}
			ringing = SimpleSoundInstance.forUI(ModSounds.TINNITUS.value(), 1.0F, Math.min(1.0F, 0.3F + deaf / 8.0F));
			mc.getSoundManager().play(ringing);
		}
	}

	private static float remaining(long start, float seconds, long now) {
		return Math.max(0, seconds - (now - start) / 1000.0F);
	}

	/** Opacity of the white-out, 0..1. */
	public static float whiteness() {
		if (blindSeconds <= 0) {
			return 0;
		}
		float t = (System.currentTimeMillis() - blindStart) / 1000.0F;
		if (t > blindSeconds) {
			return 0;
		}
		float hold = blindSeconds * 0.35F;
		if (t < hold) {
			return 1.0F;
		}
		float f = 1.0F - (t - hold) / (blindSeconds - hold);
		return f * f;
	}

	/** How loud everything else still sounds, 0..1. */
	public static float hearing() {
		if (deafSeconds <= 0) {
			return 1.0F;
		}
		float t = (System.currentTimeMillis() - deafStart) / 1000.0F;
		if (t > deafSeconds) {
			return 1.0F;
		}
		float f = t / deafSeconds;
		return 0.1F + 0.9F * f * f;
	}

	public static boolean isRinging(SoundInstance sound) {
		return sound == ringing;
	}

	public static void reset() {
		blindSeconds = 0;
		deafSeconds = 0;
	}
}
