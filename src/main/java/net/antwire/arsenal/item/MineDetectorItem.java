package net.antwire.arsenal.item;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** AN/PSS-14 mine detector. Held in the hand it beeps near buried mines, faster the closer they are (client side). */
public class MineDetectorItem extends Item {
	public static final int RANGE = 6;

	public MineDetectorItem(Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
		out.accept(Component.translatable("tooltip.arsenal.mine_detector", RANGE).withStyle(ChatFormatting.GRAY));
	}
}
