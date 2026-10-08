package net.antwire.arsenal.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.antwire.arsenal.client.GunClient;
import net.antwire.arsenal.client.GunGeometry;
import net.antwire.arsenal.gun.GunItem;
import net.antwire.arsenal.gun.GunState;
import net.antwire.arsenal.gun.GunType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A gun in first person: held at the hip to the right, brought up to the eye when aiming (the sights on the line of
 * sight), kicked back by every shot and lowered while reloading. Through a scope the gun is not drawn at all.
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class FirstPersonRendererMixin {
	@Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
	private void arsenal$gun(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state, float partialTicks, float xRot, InteractionHand hand,
		float attack, ItemStack itemStack, float inverseArmHeight, PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, CallbackInfo ci) {
		GunType type = GunItem.type(itemStack);
		if (type == null || hand != InteractionHand.MAIN_HAND) {
			return;
		}
		ci.cancel();
		AvatarRenderState avatar = playerState.avatarRenderState;
		if (state.isScoping || avatar == null) {
			return;
		}
		float aim = GunClient.aim(partialTicks);
		if ((type.scope || type.isLauncher()) && aim > 0.85F) {
			return;
		}
		int invert = avatar.mainArm == HumanoidArm.RIGHT ? 1 : -1;
		GunGeometry g = GunGeometry.of(type);
		boolean pistol = type.category == GunType.Category.PISTOL;
		// the gun is drawn at a fraction of its size and correspondingly closer: same picture, less clipping
		float scale = pistol ? 0.7F : 0.6F;
		float ease = aim * aim * (3 - 2 * aim);
		// the grip: at the hip to the right, or with the rear sight just in front of the eye
		float hx = invert * (pistol ? 0.15F : 0.2F);
		float hy = (pistol ? -0.2F : -0.21F) + inverseArmHeight * -0.5F;
		float hz = pistol ? -0.36F : -0.34F;
		float ax = 0.0F;
		float ay = (float) -g.sight * scale;
		float az = (float) -(0.08 + g.eyeRelief * scale);
		poseStack.pushPose();
		poseStack.translate(Mth.lerp(ease, hx, ax), Mth.lerp(ease, hy, ay), Mth.lerp(ease, hz, az));
		// from the hip the muzzle points in towards the crosshair
		poseStack.rotateDegrees(Axis.YP, invert * 2.5F * (1 - ease));
		poseStack.rotateDegrees(Axis.XP, 1.5F * (1 - ease));
		Minecraft mc = Minecraft.getInstance();
		GunState s = GunItem.state(itemStack);
		if (mc.level != null && s.reloadTicks() > 0 && s.reloading(mc.level.getGameTime())) {
			float f = 1.0F - (s.reloadEnd() - mc.level.getGameTime() - partialTicks) / s.reloadTicks();
			float dip = Mth.sin(Mth.clamp(f, 0, 1) * Mth.PI);
			poseStack.translate(0, -0.12F * dip, 0.05F * dip);
			poseStack.rotateDegrees(Axis.XP, -28.0F * dip);
			poseStack.rotateDegrees(Axis.ZP, invert * 18.0F * dip);
		}
		float kick = GunClient.kick(partialTicks);
		if (kick > 0) {
			poseStack.translate(0, 0.01F * kick, 0.045F * kick * (type.category == GunType.Category.PISTOL ? 0.6F : 1.0F));
			poseStack.rotateDegrees(Axis.XP, 3.5F * kick);
		}
		poseStack.scale(scale, scale, scale);
		state.mainHandRenderState.submit(poseStack, collector, lightCoords, OverlayTexture.NO_OVERLAY, 0);
		poseStack.popPose();
	}
}
