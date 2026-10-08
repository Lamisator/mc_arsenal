package net.antwire.arsenal.client.mixin;

import net.antwire.arsenal.client.GunClient;
import net.antwire.arsenal.gun.GunItem;
import net.antwire.arsenal.gun.GunType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Looking through a scope, the mouse turns the view as much slower as the picture is magnified. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@ModifyArgs(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
	private void arsenal$scopeSensitivity(Args args) {
		if (this.minecraft.player == null || !this.minecraft.options.getCameraType().isFirstPerson()) {
			return;
		}
		GunType type = GunItem.type(this.minecraft.player.getMainHandItem());
		float aim = GunClient.aim(1.0F);
		if (type != null && aim > 0.5F && type.zoom < 0.9F) {
			double f = Math.max(0.15, type.zoom * 1.2);
			args.set(0, (double) args.get(0) * f);
			args.set(1, (double) args.get(1) * f);
		}
	}
}
