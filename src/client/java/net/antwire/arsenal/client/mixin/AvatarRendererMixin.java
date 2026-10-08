package net.antwire.arsenal.client.mixin;

import net.antwire.arsenal.gun.GunItem;
import net.antwire.arsenal.gun.GunType;
import net.antwire.arsenal.item.GrenadeItem;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Long guns are held at the shoulder with both hands; pistols too while aiming. */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
	@Inject(method = "getArmPose(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/client/model/HumanoidModel$ArmPose;",
		at = @At("HEAD"), cancellable = true)
	private static void arsenal$gunPose(Avatar avatar, ItemStack stack, InteractionHand hand, CallbackInfoReturnable<HumanoidModel.ArmPose> cir) {
		GunType type = GunItem.type(stack);
		if (type != null && hand == InteractionHand.MAIN_HAND) {
			boolean aiming = avatar.isUsingItem() && avatar.getUsedItemHand() == hand;
			cir.setReturnValue(type.twoHanded() || aiming ? HumanoidModel.ArmPose.CROSSBOW_HOLD : HumanoidModel.ArmPose.ITEM);
		} else if (stack.getItem() instanceof GrenadeItem && avatar.isUsingItem() && avatar.getUsedItemHand() == hand) {
			cir.setReturnValue(HumanoidModel.ArmPose.THROW_TRIDENT);
		}
	}
}
