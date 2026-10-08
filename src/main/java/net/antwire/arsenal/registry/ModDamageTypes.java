package net.antwire.arsenal.registry;

import net.antwire.arsenal.Arsenal;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

public final class ModDamageTypes {
	public static final ResourceKey<DamageType> BULLET = key("bullet");
	public static final ResourceKey<DamageType> FRAGMENT = key("fragment");
	public static final ResourceKey<DamageType> BACKBLAST = key("backblast");
	public static final ResourceKey<DamageType> MINE = key("mine");

	private ModDamageTypes() {
	}

	private static ResourceKey<DamageType> key(String name) {
		return ResourceKey.create(Registries.DAMAGE_TYPE, Arsenal.id(name));
	}

	public static DamageSource source(ServerLevel level, ResourceKey<DamageType> key, @Nullable Entity direct, @Nullable Entity cause) {
		return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(key), direct, cause);
	}
}
