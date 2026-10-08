package net.antwire.arsenal.client.mixin;

import net.antwire.arsenal.gun.GunItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** With a gun in hand the attack button is the trigger: no punching, no digging. */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Shadow
	public @Nullable LocalPlayer player;

	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void arsenal$trigger(CallbackInfoReturnable<Boolean> cir) {
		if (this.player != null && GunItem.type(this.player.getMainHandItem()) != null) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
	private void arsenal$noDigging(boolean down, CallbackInfo ci) {
		if (this.player != null && GunItem.type(this.player.getMainHandItem()) != null) {
			ci.cancel();
		}
	}
}
