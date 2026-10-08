package net.antwire.arsenal.registry;

import net.antwire.arsenal.Arsenal;
import net.antwire.arsenal.block.AmmoCrateBlockEntity;
import net.antwire.arsenal.block.ClaymoreBlockEntity;
import net.antwire.arsenal.block.RackBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ModBlockEntities {
	public static final BlockEntityType<ClaymoreBlockEntity> CLAYMORE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Arsenal.id("claymore"),
		FabricBlockEntityTypeBuilder.create(ClaymoreBlockEntity::new, ModBlocks.CLAYMORE).build());
	public static final BlockEntityType<RackBlockEntity> RACK = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Arsenal.id("rack"),
		FabricBlockEntityTypeBuilder.create(RackBlockEntity::new, ModBlocks.WEAPON_RACK, ModBlocks.GUN_RACK).build());
	public static final BlockEntityType<AmmoCrateBlockEntity> AMMO_CRATE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Arsenal.id("ammo_crate"),
		FabricBlockEntityTypeBuilder.create(AmmoCrateBlockEntity::new, ModBlocks.AMMO_CRATE).build());

	private ModBlockEntities() {
	}

	public static void init() {
	}
}
