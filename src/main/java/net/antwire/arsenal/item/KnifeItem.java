package net.antwire.arsenal.item;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.phys.Vec3;

/** A fighting knife: quick, and from behind a stab does twice the damage. It also defuses mines (sneak + right click). */
public class KnifeItem extends Item {
	public KnifeItem(Properties properties) {
		super(properties);
	}

	@Override
	public float getAttackDamageBonus(Entity victim, float damage, DamageSource source) {
		if (source.getEntity() instanceof LivingEntity attacker && victim instanceof LivingEntity target && behind(attacker, target)) {
			return damage;
		}
		return 0.0F;
	}

	/** Whether {@code attacker} stands behind {@code target}, out of its sight. */
	public static boolean behind(LivingEntity attacker, LivingEntity target) {
		Vec3 facing = Vec3.directionFromRotation(0, target.yBodyRot);
		Vec3 toAttacker = attacker.position().subtract(target.position()).multiply(1, 0, 1);
		if (toAttacker.lengthSqr() < 1.0E-4) {
			return false;
		}
		return facing.dot(toAttacker.normalize()) < -0.5;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
		out.accept(Component.translatable("tooltip.arsenal.knife").withStyle(ChatFormatting.GRAY));
	}
}
