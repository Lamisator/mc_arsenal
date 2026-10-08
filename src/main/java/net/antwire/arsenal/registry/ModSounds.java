package net.antwire.arsenal.registry;

import java.util.EnumMap;
import java.util.Map;
import net.antwire.arsenal.Arsenal;
import net.antwire.arsenal.gun.GunType;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;

public final class ModSounds {
	public static final Map<GunType, Holder.Reference<SoundEvent>> SHOTS = new EnumMap<>(GunType.class);

	public static final Holder.Reference<SoundEvent> DRY_FIRE = variable("gun.dry_fire");
	public static final Holder.Reference<SoundEvent> MAG_OUT = variable("gun.mag_out");
	public static final Holder.Reference<SoundEvent> MAG_IN = variable("gun.mag_in");
	public static final Holder.Reference<SoundEvent> CHARGE = variable("gun.charge");
	public static final Holder.Reference<SoundEvent> SLIDE = variable("gun.slide");
	public static final Holder.Reference<SoundEvent> SHELL_INSERT = variable("gun.shell_insert");
	public static final Holder.Reference<SoundEvent> PUMP = variable("gun.pump");
	public static final Holder.Reference<SoundEvent> BOLT = variable("gun.bolt");
	public static final Holder.Reference<SoundEvent> ROCKET_LOAD = variable("gun.rocket_load");
	public static final Holder.Reference<SoundEvent> MODE = variable("gun.mode");
	public static final Holder.Reference<SoundEvent> CASING = variable("gun.casing");

	public static final Holder.Reference<SoundEvent> IMPACT_HARD = variable("bullet.impact_hard");
	public static final Holder.Reference<SoundEvent> IMPACT_SOFT = variable("bullet.impact_soft");
	public static final Holder.Reference<SoundEvent> IMPACT_FLESH = variable("bullet.impact_flesh");
	public static final Holder.Reference<SoundEvent> IMPACT_METAL = variable("bullet.impact_metal");
	public static final Holder.Reference<SoundEvent> ARMOR_HIT = variable("bullet.armor_hit");
	public static final Holder.Reference<SoundEvent> CRACK = variable("bullet.crack");
	public static final Holder.Reference<SoundEvent> WHIZ = variable("bullet.whiz");

	public static final Holder.Reference<SoundEvent> GRENADE_PIN = variable("grenade.pin");
	public static final Holder.Reference<SoundEvent> GRENADE_BOUNCE = variable("grenade.bounce");
	public static final Holder.Reference<SoundEvent> EXPLOSION_GRENADE = fixed("explosion.grenade", 160);
	public static final Holder.Reference<SoundEvent> EXPLOSION_ROCKET = fixed("explosion.rocket", 200);
	public static final Holder.Reference<SoundEvent> EXPLOSION_C4 = fixed("explosion.c4", 220);
	public static final Holder.Reference<SoundEvent> FLASHBANG = fixed("flashbang.bang", 160);
	public static final Holder.Reference<SoundEvent> TINNITUS = variable("flashbang.ring");
	public static final Holder.Reference<SoundEvent> SMOKE_HISS = variable("smoke.hiss");
	public static final Holder.Reference<SoundEvent> MINE_CLICK = variable("mine.click");
	public static final Holder.Reference<SoundEvent> MINE_ARM = variable("mine.arm");
	public static final Holder.Reference<SoundEvent> DETONATOR = variable("detonator.click");
	public static final Holder.Reference<SoundEvent> DETECTOR_BEEP = variable("detector.beep");
	public static final Holder.Reference<SoundEvent> ROCKET_LOOP = fixed("rocket.loop", 96);
	public static final Holder.Reference<SoundEvent> JAVELIN_SEEK = variable("javelin.seek");
	public static final Holder.Reference<SoundEvent> JAVELIN_LOCK = variable("javelin.lock");

	static {
		for (GunType type : GunType.values()) {
			float range = switch (type.category) {
				case PISTOL, SMG -> 96;
				case SHOTGUN -> 128;
				case RIFLE, LAUNCHER -> 160;
				case SNIPER -> 256;
			};
			SHOTS.put(type, fixed((type.isLauncher() ? "launch." : "shot.") + type.id, range));
		}
	}

	private ModSounds() {
	}

	private static Holder.Reference<SoundEvent> variable(String name) {
		return Registry.registerForHolder(BuiltInRegistries.SOUND_EVENT, Arsenal.id(name), SoundEvent.createVariableRangeEvent(Arsenal.id(name)));
	}

	private static Holder.Reference<SoundEvent> fixed(String name, float range) {
		return Registry.registerForHolder(BuiltInRegistries.SOUND_EVENT, Arsenal.id(name), SoundEvent.createFixedRangeEvent(Arsenal.id(name), range));
	}

	public static void init() {
	}
}
