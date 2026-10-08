package net.antwire.arsenal.gun;

import java.util.Locale;
import java.util.function.Consumer;
import net.antwire.arsenal.registry.ModComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * A firearm or launcher. Left click fires (handled by the client, checked by the server), right click aims down the
 * sights, R reloads and B switches the fire mode.
 */
public class GunItem extends Item {
	public final GunType type;

	public GunItem(GunType type, Properties properties) {
		super(properties);
		this.type = type;
	}

	public static GunState state(ItemStack stack) {
		GunState s = stack.get(ModComponents.GUN);
		return s == null ? GunState.EMPTY : s;
	}

	public static @Nullable GunType type(ItemStack stack) {
		return stack.getItem() instanceof GunItem gun ? gun.type : null;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		player.startUsingItem(hand);
		return InteractionResult.CONSUME;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity user) {
		return 72000;
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.NONE;
	}

	@Override
	public boolean canDestroyBlock(ItemStack stack, BlockState state, Level level, BlockPos pos, LivingEntity user) {
		return false;
	}

	/** The magazine count is a component that changes with every shot; don't replay the re-equip animation for it. */
	@Override
	public boolean allowComponentsUpdateAnimation(Player player, InteractionHand hand, ItemStack oldStack, ItemStack newStack) {
		return false;
	}

	@Override
	public boolean allowContinuingBlockBreaking(Player player, ItemStack oldStack, ItemStack newStack) {
		return true;
	}

	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
		GunState s = state(stack);
		// a reload only goes on while the gun is in your hand
		if (s.reloadEnd() > 0 && slot != EquipmentSlot.MAINHAND) {
			stack.set(ModComponents.GUN, s.withReload(0, 0));
		}
	}

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return true;
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		return Math.round(13.0F * state(stack).ammo() / this.type.magazine);
	}

	@Override
	public int getBarColor(ItemStack stack) {
		float f = state(stack).ammo() / (float) this.type.magazine;
		return Mth.hsvToRgb(f / 3.0F, 0.75F, 1.0F);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
		GunState s = state(stack);
		GunType t = this.type;
		String ammoName = t.caliber != null ? "item.arsenal." + ammoItemFor(t.caliber) : "item.arsenal." + (t.rocket == GunType.Rocket.PG7V ? "pg7v" : "javelin_missile");
		out.accept(Component.translatable("tooltip.arsenal.gun.ammo", s.ammo(), t.magazine, Component.translatable(ammoName)).withStyle(ChatFormatting.GRAY));
		if (!t.isLauncher()) {
			String modes = String.join(" / ", t.modes.stream().map(m -> Component.translatable("tooltip.arsenal.mode." + m.name().toLowerCase(Locale.ROOT)).getString()).toList());
			out.accept(Component.translatable("tooltip.arsenal.gun.action", Component.translatable("tooltip.arsenal.action." + t.action.name().toLowerCase(Locale.ROOT)),
				t.rpm, modes).withStyle(ChatFormatting.DARK_GRAY));
		}
		if (t.isShotgun()) {
			String shells = s.shells().isEmpty() ? "-" : new StringBuilder(s.shells()).reverse().toString();
			out.accept(Component.translatable("tooltip.arsenal.gun.shells", shells, Component.translatable(s.mode() == 1 ? "item.arsenal.shell_slug" : "item.arsenal.shell_buckshot"))
				.withStyle(ChatFormatting.DARK_GRAY));
		}
		if (t == GunType.JAVELIN) {
			out.accept(Component.translatable("tooltip.arsenal.javelin").withStyle(ChatFormatting.DARK_GRAY));
		}
		out.accept(Component.translatable("tooltip.arsenal.gun.controls").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
	}

	/** Item id of the cartridge (without namespace). */
	public static String ammoItemFor(Caliber caliber) {
		return switch (caliber) {
			case NINE_MM -> "ammo_9mm";
			case AE50 -> "ammo_50ae";
			case NATO_556 -> "ammo_556";
			case SOVIET_762 -> "ammo_762";
			case BUCKSHOT -> "shell_buckshot";
			case SLUG -> "shell_slug";
			case LAPUA_338 -> "ammo_338";
			case BMG_50 -> "ammo_50bmg";
		};
	}
}
