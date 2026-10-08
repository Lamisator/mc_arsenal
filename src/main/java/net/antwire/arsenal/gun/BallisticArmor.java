package net.antwire.arsenal.gun;

import java.util.EnumMap;
import java.util.Map;
import net.antwire.arsenal.registry.ModItems;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Body armour against firearms, fragments and blast. A vest only covers the torso and a helmet only the head: a shot in
 * the legs goes through whatever you wear. Even a stopped bullet hurts (blunt trauma), so nothing blocks everything.
 */
public final class BallisticArmor {
	/** Where a hit lands, and how much it hurts there. */
	public enum Zone {
		HEAD(2.0F), TORSO(1.0F), LIMBS(0.75F);

		public final float multiplier;

		Zone(float multiplier) {
			this.multiplier = multiplier;
		}
	}

	/** One piece of armour: the share of each threat it stops, and which zone it covers. */
	public enum Kind {
		// handgun, shot, rifle, magnum rifle, anti-materiel, fragment, explosion
		KEVLAR(Zone.TORSO, 0.85F, 0.85F, 0.15F, 0.05F, 0.0F, 0.65F, 0.15F, "NIJ IIIA"),
		PLATE_CARRIER(Zone.TORSO, 0.95F, 0.95F, 0.80F, 0.50F, 0.15F, 0.80F, 0.25F, "NIJ IV"),
		HELMET(Zone.HEAD, 0.80F, 0.80F, 0.15F, 0.05F, 0.0F, 0.65F, 0.10F, "NIJ IIIA");

		public final Zone zone;
		public final String rating;
		private final Map<Caliber.Threat, Float> stops = new EnumMap<>(Caliber.Threat.class);

		Kind(Zone zone, float handgun, float shot, float rifle, float magnum, float antiMateriel, float fragment, float explosion, String rating) {
			this.zone = zone;
			this.rating = rating;
			this.stops.put(Caliber.Threat.HANDGUN, handgun);
			this.stops.put(Caliber.Threat.SHOT, shot);
			this.stops.put(Caliber.Threat.RIFLE, rifle);
			this.stops.put(Caliber.Threat.MAGNUM_RIFLE, magnum);
			this.stops.put(Caliber.Threat.ANTI_MATERIEL, antiMateriel);
			this.stops.put(Caliber.Threat.FRAGMENT, fragment);
			this.stops.put(Caliber.Threat.EXPLOSION, explosion);
		}

		public float stops(Caliber.Threat threat) {
			return this.stops.getOrDefault(threat, 0.0F);
		}
	}

	private BallisticArmor() {
	}

	public static @Nullable Kind kind(ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		Item item = stack.getItem();
		if (item == ModItems.KEVLAR_VEST) {
			return Kind.KEVLAR;
		}
		if (item == ModItems.PLATE_CARRIER) {
			return Kind.PLATE_CARRIER;
		}
		if (item == ModItems.COMBAT_HELMET) {
			return Kind.HELMET;
		}
		return null;
	}

	/** Which part of {@code target} a hit at {@code at} lands on. */
	public static Zone zone(LivingEntity target, Vec3 at) {
		double feet = target.getY();
		double height = target.getBbHeight();
		double y = at.y - feet;
		if (height < 0.9) {
			// small animals: no real head or limbs to speak of
			return y > height * 0.6 && target.getEyeHeight() > height * 0.5 ? Zone.HEAD : Zone.TORSO;
		}
		if (at.y >= target.getEyeY() - 0.22) {
			return Zone.HEAD;
		}
		return y < height * 0.42 ? Zone.LIMBS : Zone.TORSO;
	}

	/** The armour covering {@code zone}, or empty. */
	public static ItemStack covering(LivingEntity target, Zone zone) {
		return switch (zone) {
			case HEAD -> target.getItemBySlot(EquipmentSlot.HEAD);
			case TORSO -> target.getItemBySlot(EquipmentSlot.CHEST);
			case LIMBS -> ItemStack.EMPTY;
		};
	}

	/**
	 * Share of a hit the armour on {@code zone} stops. Worn plates protect less: cracked ceramic at the end of its life
	 * still stops about half of what new plates do.
	 */
	public static float protection(LivingEntity target, Zone zone, Caliber.Threat threat) {
		ItemStack armor = covering(target, zone);
		Kind kind = kind(armor);
		if (kind == null || kind.zone != zone) {
			return 0.0F;
		}
		float stop = kind.stops(threat);
		if (armor.isDamageableItem() && armor.getMaxDamage() > 0) {
			float left = 1.0F - armor.getDamageValue() / (float) armor.getMaxDamage();
			stop *= 0.5F + 0.5F * left;
		}
		return stop;
	}

	/** Wear of the armour that took a hit. */
	public static void wear(LivingEntity target, Zone zone, Caliber.Threat threat) {
		ItemStack armor = covering(target, zone);
		if (kind(armor) == null) {
			return;
		}
		int wear = switch (threat) {
			case HANDGUN, SHOT, FRAGMENT -> 1;
			case RIFLE -> 3;
			case MAGNUM_RIFLE -> 6;
			case ANTI_MATERIEL -> 12;
			case EXPLOSION -> 4;
		};
		armor.hurtAndBreak(wear, target, zone == Zone.HEAD ? EquipmentSlot.HEAD : EquipmentSlot.CHEST);
	}

	/** Share of blast damage vest and helmet together keep off (used for every explosion, also vanilla TNT). */
	public static float blastProtection(LivingEntity target) {
		float stop = 0.0F;
		Kind chest = kind(target.getItemBySlot(EquipmentSlot.CHEST));
		if (chest != null && chest.zone == Zone.TORSO) {
			stop += chest.stops(Caliber.Threat.EXPLOSION);
		}
		Kind head = kind(target.getItemBySlot(EquipmentSlot.HEAD));
		if (head != null && head.zone == Zone.HEAD) {
			stop += head.stops(Caliber.Threat.EXPLOSION);
		}
		return Math.min(stop, 0.5F);
	}
}
