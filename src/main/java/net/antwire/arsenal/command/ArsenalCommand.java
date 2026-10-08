package net.antwire.arsenal.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.antwire.arsenal.gun.GunType;
import net.antwire.arsenal.registry.ModBlocks;
import net.antwire.arsenal.registry.ModItems;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

/** {@code /arsenal kit <loadout> [players]}: hands out a ready-made loadout (operators). */
public final class ArsenalCommand {
	private static final List<String> KITS = List.of("rifleman", "sniper", "breacher", "demolition", "antitank", "officer", "all");

	private ArsenalCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("arsenal")
			.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("kit")
				.then(Commands.argument("loadout", StringArgumentType.word())
					.suggests((ctx, b) -> {
						KITS.forEach(b::suggest);
						return b.buildFuture();
					})
					.executes(ctx -> give(ctx.getSource(), StringArgumentType.getString(ctx, "loadout"), List.of(ctx.getSource().getPlayerOrException())))
					.then(Commands.argument("players", EntityArgument.players())
						.executes(ctx -> give(ctx.getSource(), StringArgumentType.getString(ctx, "loadout"), EntityArgument.getPlayers(ctx, "players")))))));
	}

	private static int give(CommandSourceStack source, String kit, Collection<ServerPlayer> players) {
		List<ItemStack> items = kit(kit);
		if (items.isEmpty()) {
			source.sendFailure(Component.translatable("commands.arsenal.kit.unknown", kit, String.join(", ", KITS)));
			return 0;
		}
		for (ServerPlayer p : players) {
			for (ItemStack s : items) {
				ItemStack copy = s.copy();
				if (!p.getInventory().add(copy)) {
					p.spawnAtLocation(p.level(), copy);
				}
			}
		}
		source.sendSuccess(() -> Component.translatable("commands.arsenal.kit.given", kit, players.size()), true);
		return players.size();
	}

	private static List<ItemStack> kit(String name) {
		List<ItemStack> out = new ArrayList<>();
		switch (name) {
			case "rifleman" -> {
				add(out, ModItems.gun(GunType.M4A1), 1);
				add(out, ModItems.AMMO_556, 64, 64, 64);
				add(out, ModItems.gun(GunType.GLOCK_17), 1);
				add(out, ModItems.AMMO_9MM, 51);
				add(out, ModItems.FRAG_GRENADE, 2);
				add(out, ModItems.SMOKE_GRENADE, 1);
				armour(out);
			}
			case "sniper" -> {
				add(out, ModItems.gun(GunType.AWM), 1);
				add(out, ModItems.AMMO_338, 40);
				add(out, ModItems.gun(GunType.BARRETT_M82), 1);
				add(out, ModItems.AMMO_50BMG, 32);
				add(out, ModItems.gun(GunType.DESERT_EAGLE), 1);
				add(out, ModItems.AMMO_50AE, 28);
				add(out, ModBlocks.CLAYMORE, 2);
				add(out, ModItems.DETONATOR, 1);
				add(out, ModItems.COMBAT_KNIFE, 1);
				add(out, ModItems.KEVLAR_VEST, 1);
			}
			case "breacher" -> {
				add(out, ModItems.gun(GunType.REMINGTON_870), 1);
				add(out, ModItems.SHELL_BUCKSHOT, 48);
				add(out, ModItems.SHELL_SLUG, 16);
				add(out, ModItems.gun(GunType.MP5), 1);
				add(out, ModItems.AMMO_9MM, 64, 64);
				add(out, ModItems.FLASHBANG, 4);
				add(out, ModBlocks.C4, 4);
				add(out, ModItems.DETONATOR, 1);
				armour(out);
			}
			case "demolition" -> {
				add(out, ModBlocks.C4, 16);
				add(out, ModBlocks.CLAYMORE, 6);
				add(out, ModBlocks.AP_MINE, 12);
				add(out, ModBlocks.AT_MINE, 4);
				add(out, ModItems.DETONATOR, 1);
				add(out, ModItems.MINE_DETECTOR, 1);
				add(out, ModItems.COMBAT_KNIFE, 1);
				add(out, ModItems.FRAG_GRENADE, 4);
			}
			case "antitank" -> {
				add(out, ModItems.gun(GunType.RPG7), 1);
				add(out, ModItems.PG7V, 4, 4);
				add(out, ModItems.gun(GunType.JAVELIN), 1);
				add(out, ModItems.JAVELIN_MISSILE, 1, 1, 1);
				add(out, ModItems.gun(GunType.AK47), 1);
				add(out, ModItems.AMMO_762, 64, 64);
				armour(out);
			}
			case "officer" -> {
				add(out, ModItems.gun(GunType.GLOCK_17), 1);
				add(out, ModItems.AMMO_9MM, 64);
				add(out, ModItems.COMBAT_KNIFE, 1);
				add(out, ModItems.KEVLAR_VEST, 1);
			}
			case "all" -> {
				for (GunType t : GunType.values()) {
					add(out, ModItems.gun(t), 1);
				}
				add(out, ModItems.AMMO_9MM, 64);
				add(out, ModItems.AMMO_50AE, 64);
				add(out, ModItems.AMMO_556, 64);
				add(out, ModItems.AMMO_762, 64);
				add(out, ModItems.SHELL_BUCKSHOT, 64);
				add(out, ModItems.SHELL_SLUG, 64);
				add(out, ModItems.AMMO_338, 64);
				add(out, ModItems.AMMO_50BMG, 32);
				add(out, ModItems.PG7V, 4);
				add(out, ModItems.JAVELIN_MISSILE, 1, 1);
				add(out, ModItems.FRAG_GRENADE, 8);
				add(out, ModItems.FLASHBANG, 8);
				add(out, ModItems.SMOKE_GRENADE, 8);
				add(out, ModItems.COMBAT_KNIFE, 1);
				armour(out);
			}
			default -> {
			}
		}
		return out;
	}

	private static void armour(List<ItemStack> out) {
		add(out, ModItems.PLATE_CARRIER, 1);
		add(out, ModItems.COMBAT_HELMET, 1);
		add(out, ModItems.COMBAT_KNIFE, 1);
	}

	private static void add(List<ItemStack> out, ItemLike item, int... counts) {
		Item i = item.asItem();
		for (int c : counts) {
			out.add(new ItemStack(i, c));
		}
	}
}
