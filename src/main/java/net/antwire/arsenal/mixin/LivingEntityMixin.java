package net.antwire.arsenal.mixin;

import net.antwire.arsenal.gun.BallisticArmor;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Body armour also takes some of the blast of any explosion, TNT included. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@Inject(method = "getDamageAfterArmorAbsorb", at = @At("RETURN"), cancellable = true)
	private void arsenal$blastProtection(DamageSource source, float damage, CallbackInfoReturnable<Float> cir) {
		if (source.is(DamageTypeTags.IS_EXPLOSION)) {
			float stop = BallisticArmor.blastProtection((LivingEntity) (Object) this);
			if (stop > 0) {
				cir.setReturnValue(cir.getReturnValue() * (1.0F - stop));
			}
		}
	}
}
