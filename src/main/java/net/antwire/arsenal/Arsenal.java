package net.antwire.arsenal;

import net.antwire.arsenal.command.ArsenalCommand;
import net.antwire.arsenal.config.ArsenalConfig;
import net.antwire.arsenal.explosive.Explosives;
import net.antwire.arsenal.gun.Ballistics;
import net.antwire.arsenal.gun.GunServer;
import net.antwire.arsenal.network.ActionPayload;
import net.antwire.arsenal.network.FirePayload;
import net.antwire.arsenal.network.FlashPayload;
import net.antwire.arsenal.network.FxPayload;
import net.antwire.arsenal.network.HitPayload;
import net.antwire.arsenal.network.ImpactPayload;
import net.antwire.arsenal.network.ShotPayload;
import net.antwire.arsenal.registry.ModBlockEntities;
import net.antwire.arsenal.registry.ModBlocks;
import net.antwire.arsenal.registry.ModComponents;
import net.antwire.arsenal.registry.ModEntities;
import net.antwire.arsenal.registry.ModItems;
import net.antwire.arsenal.registry.ModParticles;
import net.antwire.arsenal.registry.ModSounds;
import net.antwire.arsenal.registry.ModTab;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Arsenal: modern small arms, launchers, grenades, mines and body armour.
 */
public class Arsenal implements ModInitializer {
	public static final String MOD_ID = "arsenal";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		ArsenalConfig.load();
		ModComponents.init();
		ModSounds.init();
		ModParticles.init();
		ModBlocks.init();
		ModItems.init();
		ModBlockEntities.init();
		ModEntities.init();
		ModTab.init();

		PayloadTypeRegistry.serverboundPlay().register(FirePayload.TYPE, FirePayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ActionPayload.TYPE, ActionPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ShotPayload.TYPE, ShotPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ImpactPayload.TYPE, ImpactPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(HitPayload.TYPE, HitPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(FlashPayload.TYPE, FlashPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(FxPayload.TYPE, FxPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(FirePayload.TYPE, (payload, context) -> GunServer.fire(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(ActionPayload.TYPE, (payload, context) -> GunServer.action(context.player(), payload));

		ServerTickEvents.END_LEVEL_TICK.register(Ballistics::tick);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			GunServer.tick(server);
			Explosives.tick();
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> GunServer.forget(handler.player));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			Ballistics.clear();
			GunServer.clear();
			Explosives.clearPending();
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> ArsenalCommand.register(dispatcher));
		LOGGER.info("Arsenal loaded: {} weapons", net.antwire.arsenal.gun.GunType.values().length);
	}
}
