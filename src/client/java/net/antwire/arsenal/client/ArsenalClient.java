package net.antwire.arsenal.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.antwire.arsenal.Arsenal;
import net.antwire.arsenal.client.fx.ClientBullets;
import net.antwire.arsenal.client.fx.ClientFx;
import net.antwire.arsenal.client.fx.FlashFx;
import net.antwire.arsenal.client.particle.ArsenalParticles;
import net.antwire.arsenal.client.render.RackRenderer;
import net.antwire.arsenal.client.render.RocketRenderer;
import net.antwire.arsenal.network.FlashPayload;
import net.antwire.arsenal.network.FxPayload;
import net.antwire.arsenal.network.HitPayload;
import net.antwire.arsenal.network.ImpactPayload;
import net.antwire.arsenal.network.ShotPayload;
import net.antwire.arsenal.registry.ModBlockEntities;
import net.antwire.arsenal.registry.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;

public class ArsenalClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRenderers.register(ModEntities.GRENADE, ctx -> new ThrownItemRenderer<>(ctx, 0.7F, false));
		EntityRenderers.register(ModEntities.ROCKET, RocketRenderer::new);
		BlockEntityRenderers.register(ModBlockEntities.RACK, RackRenderer::new);
		ArsenalParticles.register();

		ClientPlayNetworking.registerGlobalReceiver(ShotPayload.TYPE, (p, ctx) -> ClientFx.remoteShot(ctx.client(), p));
		ClientPlayNetworking.registerGlobalReceiver(ImpactPayload.TYPE, (p, ctx) -> ClientFx.impact(ctx.client(), p));
		ClientPlayNetworking.registerGlobalReceiver(HitPayload.TYPE, (p, ctx) -> GunHud.hit(p.kind()));
		ClientPlayNetworking.registerGlobalReceiver(FlashPayload.TYPE, (p, ctx) -> FlashFx.start(p.blind(), p.deaf()));
		ClientPlayNetworking.registerGlobalReceiver(FxPayload.TYPE, (p, ctx) -> ClientFx.fx(ctx.client(), p));

		KeyMapping.Category category = KeyMapping.Category.register(Arsenal.id("arsenal"));
		GunClient.reloadKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.arsenal.reload", InputConstants.KEY_R, category));
		GunClient.modeKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.arsenal.fire_mode", InputConstants.KEY_B, category));

		ClientTickEvents.START_CLIENT_TICK.register(GunClient::tick);
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			GunClient.keys(mc);
			ClientBullets.tick(mc);
			ClientFx.tick(mc);
		});
		HudElementRegistry.addLast(Arsenal.id("gun_hud"), GunHud::render);
		LevelRenderEvents.COLLECT_SUBMITS.register(ClientBullets::render);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ClientBullets.clear();
			FlashFx.reset();
			GunClient.reset();
		});
	}
}
