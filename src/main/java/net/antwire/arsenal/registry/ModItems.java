package net.antwire.arsenal.registry;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;
import net.antwire.arsenal.Arsenal;
import net.antwire.arsenal.gun.Caliber;
import net.antwire.arsenal.gun.GunItem;
import net.antwire.arsenal.gun.GunState;
import net.antwire.arsenal.gun.GunType;
import net.antwire.arsenal.item.DetonatorItem;
import net.antwire.arsenal.item.GrenadeItem;
import net.antwire.arsenal.item.KnifeItem;
import net.antwire.arsenal.item.MineDetectorItem;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.UseEffects;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

public final class ModItems {
	public static final Map<GunType, Item> GUNS = new EnumMap<>(GunType.class);
	private static final Map<Caliber, Item> AMMO = new EnumMap<>(Caliber.class);

	public static final TagKey<Item> REPAIRS_KEVLAR = TagKey.create(Registries.ITEM, Arsenal.id("repairs_kevlar"));
	public static final TagKey<Item> REPAIRS_PLATES = TagKey.create(Registries.ITEM, Arsenal.id("repairs_plates"));

	public static final ArmorMaterial KEVLAR = new ArmorMaterial(20, Map.of(ArmorType.CHESTPLATE, 5, ArmorType.HELMET, 3), 8,
		SoundEvents.ARMOR_EQUIP_LEATHER, 0.0F, 0.0F, REPAIRS_KEVLAR, asset("kevlar_vest"));
	public static final ArmorMaterial PLATES = new ArmorMaterial(26, Map.of(ArmorType.CHESTPLATE, 7), 6,
		SoundEvents.ARMOR_EQUIP_IRON, 2.0F, 0.05F, REPAIRS_PLATES, asset("plate_carrier"));
	public static final ArmorMaterial HELMET = new ArmorMaterial(22, Map.of(ArmorType.HELMET, 3), 8,
		SoundEvents.ARMOR_EQUIP_TURTLE, 1.0F, 0.0F, REPAIRS_KEVLAR, asset("combat_helmet"));

	// ammunition
	public static final Item AMMO_9MM = cartridge(Caliber.NINE_MM);
	public static final Item AMMO_50AE = cartridge(Caliber.AE50);
	public static final Item AMMO_556 = cartridge(Caliber.NATO_556);
	public static final Item AMMO_762 = cartridge(Caliber.SOVIET_762);
	public static final Item SHELL_BUCKSHOT = cartridge(Caliber.BUCKSHOT);
	public static final Item SHELL_SLUG = cartridge(Caliber.SLUG);
	public static final Item AMMO_338 = cartridge(Caliber.LAPUA_338);
	public static final Item AMMO_50BMG = cartridge(Caliber.BMG_50);
	public static final Item PG7V = register("pg7v", Item::new, new Item.Properties().stacksTo(4));
	public static final Item JAVELIN_MISSILE = register("javelin_missile", Item::new, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));

	// grenades and the like
	public static final Item FRAG_GRENADE = register("m67", p -> new GrenadeItem(GrenadeItem.Kind.FRAG, p), new Item.Properties().stacksTo(8));
	public static final Item FLASHBANG = register("m84", p -> new GrenadeItem(GrenadeItem.Kind.FLASHBANG, p), new Item.Properties().stacksTo(8));
	public static final Item SMOKE_GRENADE = register("m18", p -> new GrenadeItem(GrenadeItem.Kind.SMOKE, p), new Item.Properties().stacksTo(8));
	public static final Item DETONATOR = register("m57_detonator", DetonatorItem::new, new Item.Properties().stacksTo(1));
	public static final Item MINE_DETECTOR = register("mine_detector", MineDetectorItem::new, new Item.Properties().stacksTo(1));

	// melee and armour
	public static final Item COMBAT_KNIFE = register("combat_knife", KnifeItem::new, new Item.Properties().sword(ToolMaterial.IRON, 3.0F, -1.6F));
	public static final Item KEVLAR_VEST = register("kevlar_vest", Item::new, new Item.Properties().humanoidArmor(KEVLAR, ArmorType.CHESTPLATE));
	public static final Item PLATE_CARRIER = register("plate_carrier", Item::new, plateCarrier());
	public static final Item COMBAT_HELMET = register("combat_helmet", Item::new, new Item.Properties().humanoidArmor(HELMET, ArmorType.HELMET));

	// parts
	public static final Item KEVLAR_FABRIC = register("kevlar_fabric", Item::new, new Item.Properties());
	public static final Item CERAMIC_PLATE = register("ceramic_plate", Item::new, new Item.Properties().stacksTo(16));
	public static final Item GUN_BARREL = register("gun_barrel", Item::new, new Item.Properties());
	public static final Item GUN_RECEIVER = register("gun_receiver", Item::new, new Item.Properties());
	public static final Item POLYMER = register("polymer", Item::new, new Item.Properties());
	public static final Item SCOPE = register("scope", Item::new, new Item.Properties());
	public static final Item EXPLOSIVE_CHARGE = register("explosive_charge", Item::new, new Item.Properties());
	public static final Item BRASS_CASING = register("brass_casing", Item::new, new Item.Properties());

	static {
		for (GunType type : GunType.values()) {
			Item.Properties p = new Item.Properties().stacksTo(1)
				.rarity(type.category == GunType.Category.SNIPER || type.isLauncher() ? Rarity.UNCOMMON : Rarity.COMMON)
				.component(ModComponents.GUN, new GunState(type.isShotgun() ? 0 : type.magazine, -1, "", 0, 0))
				.component(DataComponents.USE_EFFECTS, new UseEffects(false, false, 0.55F));
			if (type.speedModifier != 0) {
				p.attributes(ItemAttributeModifiers.builder().add(Attributes.MOVEMENT_SPEED,
					new AttributeModifier(Arsenal.id("gun_weight"), type.speedModifier, AttributeModifier.Operation.ADD_MULTIPLIED_BASE), EquipmentSlotGroup.MAINHAND).build());
			}
			GUNS.put(type, register(type.id, props -> new GunItem(type, props), p));
		}
	}

	private ModItems() {
	}

	private static ResourceKey<EquipmentAsset> asset(String name) {
		return ResourceKey.create(EquipmentAssets.ROOT_ID, Arsenal.id(name));
	}

	private static Item.Properties plateCarrier() {
		Item.Properties p = new Item.Properties().humanoidArmor(PLATES, ArmorType.CHESTPLATE);
		// ceramic plates are heavy
		return p.attributes(PLATES.createAttributes(ArmorType.CHESTPLATE).withModifierAdded(Attributes.MOVEMENT_SPEED,
			new AttributeModifier(Arsenal.id("plate_weight"), -0.06, AttributeModifier.Operation.ADD_MULTIPLIED_BASE), EquipmentSlotGroup.CHEST));
	}

	private static Item cartridge(Caliber caliber) {
		Item item = register(GunItem.ammoItemFor(caliber), Item::new, new Item.Properties().stacksTo(caliber == Caliber.BMG_50 ? 32 : 64));
		AMMO.put(caliber, item);
		return item;
	}

	public static Item ammo(Caliber caliber) {
		return AMMO.get(caliber);
	}

	public static Item gun(GunType type) {
		return GUNS.get(type);
	}

	static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Arsenal.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	public static void init() {
	}
}
