package net.antwire.arsenal.registry;

import net.antwire.arsenal.Arsenal;
import net.antwire.arsenal.gun.GunType;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public final class ModTab {
	public static final CreativeModeTab TAB = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, Arsenal.id("arsenal"),
		FabricCreativeModeTab.builder()
			.title(Component.translatable("itemGroup.arsenal"))
			.icon(() -> new ItemStack(ModItems.gun(GunType.M4A1)))
			.displayItems((params, output) -> {
				for (GunType type : GunType.values()) {
					output.accept(ModItems.gun(type));
				}
				output.accept(ModItems.AMMO_9MM);
				output.accept(ModItems.AMMO_50AE);
				output.accept(ModItems.AMMO_556);
				output.accept(ModItems.AMMO_762);
				output.accept(ModItems.SHELL_BUCKSHOT);
				output.accept(ModItems.SHELL_SLUG);
				output.accept(ModItems.AMMO_338);
				output.accept(ModItems.AMMO_50BMG);
				output.accept(ModItems.PG7V);
				output.accept(ModItems.JAVELIN_MISSILE);
				output.accept(ModItems.FRAG_GRENADE);
				output.accept(ModItems.FLASHBANG);
				output.accept(ModItems.SMOKE_GRENADE);
				output.accept(ModBlocks.CLAYMORE);
				output.accept(ModBlocks.AP_MINE);
				output.accept(ModBlocks.AT_MINE);
				output.accept(ModBlocks.C4);
				output.accept(ModItems.DETONATOR);
				output.accept(ModItems.MINE_DETECTOR);
				output.accept(ModItems.COMBAT_KNIFE);
				output.accept(ModItems.COMBAT_HELMET);
				output.accept(ModItems.KEVLAR_VEST);
				output.accept(ModItems.PLATE_CARRIER);
				output.accept(ModBlocks.WEAPON_RACK);
				output.accept(ModBlocks.GUN_RACK);
				output.accept(ModBlocks.AMMO_CRATE);
				output.accept(ModItems.GUN_BARREL);
				output.accept(ModItems.GUN_RECEIVER);
				output.accept(ModItems.POLYMER);
				output.accept(ModItems.SCOPE);
				output.accept(ModItems.BRASS_CASING);
				output.accept(ModItems.EXPLOSIVE_CHARGE);
				output.accept(ModItems.KEVLAR_FABRIC);
				output.accept(ModItems.CERAMIC_PLATE);
			})
			.build());

	private ModTab() {
	}

	public static void init() {
	}
}
