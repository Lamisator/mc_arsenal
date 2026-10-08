package net.antwire.arsenal.item;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.antwire.arsenal.block.Detonatable;
import net.antwire.arsenal.registry.ModComponents;
import net.antwire.arsenal.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * M57 firing device, the "clacker". Right click a claymore or a C4 charge to wire it up (again to unwire), right click
 * into the air to set off everything wired to it.
 */
public class DetonatorItem extends Item {
	public static final int RANGE = 256;

	public DetonatorItem(Properties properties) {
		super(properties);
	}

	public static List<GlobalPos> links(ItemStack stack) {
		List<GlobalPos> l = stack.get(ModComponents.LINKS);
		return l == null ? List.of() : l;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof Detonatable charge) || !charge.linkable()) {
			return InteractionResult.PASS;
		}
		if (level instanceof ServerLevel server && context.getPlayer() != null) {
			ItemStack stack = context.getItemInHand();
			List<GlobalPos> links = new ArrayList<>(links(stack));
			GlobalPos at = GlobalPos.of(level.dimension(), pos);
			if (links.remove(at)) {
				context.getPlayer().sendOverlayMessage(Component.translatable("message.arsenal.unlinked", links.size()));
			} else {
				links.add(at);
				charge.linked(server, pos);
				context.getPlayer().sendOverlayMessage(Component.translatable("message.arsenal.linked", links.size()));
			}
			stack.set(ModComponents.LINKS, List.copyOf(links));
			level.playSound(null, pos, ModSounds.MINE_ARM.value(), SoundSource.BLOCKS, 0.6F, 1.4F);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		List<GlobalPos> links = links(stack);
		if (level instanceof ServerLevel server) {
			level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), ModSounds.DETONATOR.value(), SoundSource.PLAYERS, 0.9F, 1.0F);
			if (links.isEmpty()) {
				player.sendOverlayMessage(Component.translatable("message.arsenal.no_links"));
				return InteractionResult.SUCCESS;
			}
			List<GlobalPos> left = new ArrayList<>();
			int fired = 0;
			for (GlobalPos link : links) {
				if (!link.dimension().equals(level.dimension()) || !link.pos().closerThan(player.blockPosition(), RANGE) || !server.isLoaded(link.pos())) {
					left.add(link);
					continue;
				}
				if (server.getBlockState(link.pos()).getBlock() instanceof Detonatable charge) {
					charge.detonate(server, link.pos(), player);
					fired++;
				}
			}
			stack.set(ModComponents.LINKS, List.copyOf(left));
			player.sendOverlayMessage(Component.translatable("message.arsenal.detonated", fired));
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
		out.accept(Component.translatable("tooltip.arsenal.detonator", links(stack).size()).withStyle(ChatFormatting.GRAY));
		out.accept(Component.translatable("tooltip.arsenal.detonator.controls").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
	}
}
