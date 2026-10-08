package net.antwire.arsenal.registry;

import java.util.List;
import net.antwire.arsenal.Arsenal;
import net.antwire.arsenal.gun.GunState;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;

public final class ModComponents {
	public static final DataComponentType<GunState> GUN = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Arsenal.id("gun"),
		DataComponentType.<GunState>builder().persistent(GunState.CODEC).networkSynchronized(GunState.STREAM_CODEC).build());
	/** Charges a firing device is wired to. */
	public static final DataComponentType<List<GlobalPos>> LINKS = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Arsenal.id("links"),
		DataComponentType.<List<GlobalPos>>builder().persistent(GlobalPos.CODEC.listOf())
			.networkSynchronized(GlobalPos.STREAM_CODEC.apply(ByteBufCodecs.list())).build());

	private ModComponents() {
	}

	public static void init() {
	}
}
