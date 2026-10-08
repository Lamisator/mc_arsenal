package net.antwire.arsenal.item;

import java.util.function.Consumer;
import net.antwire.arsenal.entity.GrenadeEntity;
import net.antwire.arsenal.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Hold right click: the pin is out and the fuse burns. Let go to throw (sneak for a short underhand lob). Hold on too
 * long and it goes off in your hand.
 */
public class GrenadeItem extends Item {
	public final Kind kind;

	public enum Kind {
		FRAG("m67", 90), FLASHBANG("m84", 30), SMOKE("m18", 30);

		public final String id;
		/** Ticks from pulling the pin to detonation. */
		public final int fuse;

		Kind(String id, int fuse) {
			this.id = id;
			this.fuse = fuse;
		}

		public static Kind byOrdinal(int i) {
			return values()[Math.floorMod(i, values().length)];
		}
	}

	public GrenadeItem(Kind kind, Properties properties) {
		super(properties);
		this.kind = kind;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		player.startUsingItem(hand);
		level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), ModSounds.GRENADE_PIN.value(), SoundSource.PLAYERS, 0.8F, 1.0F);
		return InteractionResult.CONSUME;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity user) {
		return 72000;
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.TRIDENT;
	}

	@Override
	public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
		int held = this.getUseDuration(stack, entity) - remaining;
		if (held >= this.kind.fuse && level instanceof ServerLevel server) {
			// cooked off in the hand
			this.release(server, entity, stack, 0, 0.0F);
			entity.stopUsingItem();
		}
	}

	@Override
	public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remaining) {
		if (level instanceof ServerLevel server) {
			int held = this.getUseDuration(stack, entity) - remaining;
			float power = entity.isShiftKeyDown() ? 0.45F : 1.15F;
			this.release(server, entity, stack, Math.max(1, this.kind.fuse - held), power);
		}
		return true;
	}

	private void release(ServerLevel level, LivingEntity entity, ItemStack stack, int fuse, float power) {
		Vec3 look = entity.getLookAngle();
		Vec3 at = entity.getEyePosition().add(look.scale(0.4)).add(0, -0.15, 0);
		GrenadeEntity grenade = new GrenadeEntity(level, this.kind, entity, at, look.scale(power).add(0, power * 0.18, 0).add(entity.getDeltaMovement()), fuse);
		level.addFreshEntity(grenade);
		if (!(entity instanceof Player p && p.hasInfiniteMaterials())) {
			stack.shrink(1);
		}
		if (entity instanceof Player p) {
			p.getCooldowns().addCooldown(stack, 10);
		}
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
		out.accept(Component.translatable("tooltip.arsenal.grenade." + this.kind.id).withStyle(ChatFormatting.GRAY));
		out.accept(Component.translatable("tooltip.arsenal.grenade.fuse", String.format(java.util.Locale.ROOT, "%.1f", this.kind.fuse / 20.0)).withStyle(ChatFormatting.DARK_GRAY));
		out.accept(Component.translatable("tooltip.arsenal.grenade.controls").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
	}
}
