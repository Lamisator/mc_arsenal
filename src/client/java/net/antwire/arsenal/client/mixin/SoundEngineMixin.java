package net.antwire.arsenal.client.mixin;

import net.antwire.arsenal.client.fx.FlashFx;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** After a flashbang the world is muffled while the ears ring. */
@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {
	@Inject(method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F", at = @At("RETURN"), cancellable = true)
	private void arsenal$deafened(SoundInstance instance, CallbackInfoReturnable<Float> cir) {
		float hearing = FlashFx.hearing();
		if (hearing < 1.0F && !FlashFx.isRinging(instance)) {
			cir.setReturnValue(cir.getReturnValue() * hearing);
		}
	}
}
