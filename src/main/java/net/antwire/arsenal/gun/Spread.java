package net.antwire.arsenal.gun;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Where the rounds of one trigger pull go. The client picks the seed, so its tracers fly where the server's bullets do.
 */
public final class Spread {
	private Spread() {
	}

	public static Vec3 look(float yaw, float pitch) {
		float f = Mth.cos(-yaw * Mth.DEG_TO_RAD - Mth.PI);
		float g = Mth.sin(-yaw * Mth.DEG_TO_RAD - Mth.PI);
		float h = -Mth.cos(-pitch * Mth.DEG_TO_RAD);
		float i = Mth.sin(-pitch * Mth.DEG_TO_RAD);
		return new Vec3(g * h, i, f * h);
	}

	/**
	 * @param movement extra inaccuracy from running or jumping, 0..1
	 */
	public static float cone(GunType type, boolean aiming, float movement) {
		float base = aiming ? type.aimSpread : type.hipSpread;
		return base * (1.0F + movement * (aiming ? 4.0F : 1.5F));
	}

	public static List<Vec3> directions(GunType type, Caliber caliber, float yaw, float pitch, boolean aiming, float movement, long seed) {
		Random random = new Random(seed);
		Vec3 forward = look(yaw, pitch);
		Vec3 up = look(yaw, pitch - 90);
		Vec3 right = forward.cross(up).normalize();
		// a slug flies like a bullet; the cone of a shotgun is the pattern its pellets spread into
		float cone = caliber == Caliber.SLUG ? (aiming ? 0.4F : 1.6F) * (1.0F + movement) : cone(type, aiming, movement);
		int count = caliber.pellets;
		List<Vec3> out = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			double r = Math.sqrt(random.nextDouble()) * Math.toRadians(cone);
			double a = random.nextDouble() * Math.PI * 2;
			double x = Math.cos(a) * Math.tan(r);
			double y = Math.sin(a) * Math.tan(r);
			out.add(forward.add(right.scale(x)).add(up.scale(y)).normalize());
		}
		return out;
	}
}
