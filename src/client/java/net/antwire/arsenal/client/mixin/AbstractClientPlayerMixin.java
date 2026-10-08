package net.antwire.arsenal.client.mixin;

import net.antwire.arsenal.client.GunClient;
import net.antwire.arsenal.gun.GunItem;
import net.antwire.arsenal.gun.GunType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Zoom when aiming: iron sights a little, optics a lot. */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
	@Inject(method = "getFieldOfViewModifier", at = @At("RETURN"), cancellable = true)
	private void arsenal$zoom(boolean firstPerson, float effectScale, CallbackInfoReturnable<Float> cir) {
		if (!firstPerson || !((Object) this instanceof LocalPlayer player)) {
			return;
		}
		GunType type = GunItem.type(player.getMainHandItem());
		if (type == null) {
			return;
		}
		float aim = GunClient.aim(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
		if (aim > 0) {
			// optics snap in once the eye is at the eyepiece; iron sights ease in
			float zoom = type.scope || type.isLauncher() ? (aim > 0.85F ? type.zoom : Mth.lerp(aim, 1.0F, 0.85F)) : Mth.lerp(aim, 1.0F, type.zoom);
			cir.setReturnValue(cir.getReturnValue() * zoom);
		}
	}
}
