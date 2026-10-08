package net.antwire.arsenal.registry;

import java.util.function.Function;
import net.antwire.arsenal.Arsenal;
import net.antwire.arsenal.block.AmmoCrateBlock;
import net.antwire.arsenal.block.C4Block;
import net.antwire.arsenal.block.ClaymoreBlock;
import net.antwire.arsenal.block.MineBlock;
import net.antwire.arsenal.block.RackBlock;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

public final class ModBlocks {
	public static final Block CLAYMORE = register("claymore", ClaymoreBlock::new, explosive().mapColor(MapColor.COLOR_GREEN), 8);
	public static final Block AP_MINE = register("ap_mine", p -> new MineBlock(MineBlock.Kind.ANTI_PERSONNEL, p), explosive().mapColor(MapColor.COLOR_GREEN), 16);
	public static final Block AT_MINE = register("at_mine", p -> new MineBlock(MineBlock.Kind.ANTI_TANK, p), explosive().mapColor(MapColor.COLOR_GREEN), 4);
	public static final Block C4 = register("c4", C4Block::new, explosive().mapColor(MapColor.SAND), 16);
	public static final Block WEAPON_RACK = register("weapon_rack", p -> new RackBlock(RackBlock.Style.WALL, p),
		BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0F).sound(SoundType.WOOD).noOcclusion(), 64);
	public static final Block GUN_RACK = register("gun_rack", p -> new RackBlock(RackBlock.Style.FLOOR, p),
		BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(3.0F).sound(SoundType.METAL).noOcclusion(), 64);
	public static final Block AMMO_CRATE = register("ammo_crate", AmmoCrateBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).strength(2.5F).sound(SoundType.WOOD).noOcclusion(), 64);

	private ModBlocks() {
	}

	private static BlockBehaviour.Properties explosive() {
		return BlockBehaviour.Properties.of().strength(0.6F).sound(SoundType.METAL).noOcclusion().pushReaction(PushReaction.POPPED);
	}

	private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties, int stack) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Arsenal.id(name));
		Block block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Arsenal.id(name));
		BlockItem item = new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix().stacksTo(stack));
		item.registerBlocks(Item.BY_BLOCK, item);
		Registry.register(BuiltInRegistries.ITEM, itemKey, item);
		return block;
	}

	public static void init() {
	}
}
