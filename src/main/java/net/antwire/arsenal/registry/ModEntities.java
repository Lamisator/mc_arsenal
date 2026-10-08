package net.antwire.arsenal.registry;

import net.antwire.arsenal.Arsenal;
import net.antwire.arsenal.entity.GrenadeEntity;
import net.antwire.arsenal.entity.RocketEntity;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
	public static final EntityType<GrenadeEntity> GRENADE = register("grenade",
		EntityType.Builder.<GrenadeEntity>of(GrenadeEntity::new, MobCategory.MISC).sized(0.25F, 0.25F).clientTrackingRange(8).updateInterval(1).noLootTable());
	public static final EntityType<RocketEntity> ROCKET = register("rocket",
		EntityType.Builder.<RocketEntity>of(RocketEntity::new, MobCategory.MISC).sized(0.3F, 0.3F).clientTrackingRange(16).updateInterval(1).fireImmune()
			.noLootTable());

	private ModEntities() {
	}

	private static <T extends Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Arsenal.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	public static void init() {
	}
}
