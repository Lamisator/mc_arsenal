package net.antwire.arsenal.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.antwire.arsenal.entity.RocketEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;

/** Draws a rocket with its item model, nose along the flight path. */
public class RocketRenderer extends EntityRenderer<RocketEntity, RocketRenderer.State> {
	private final ItemModelResolver items;

	public static class State extends EntityRenderState {
		public final ItemStackRenderState item = new ItemStackRenderState();
		public float yaw;
		public float pitch;
	}

	public RocketRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.items = context.getItemModelResolver();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(RocketEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		this.items.updateForNonLiving(state.item, entity.getItem(), ItemDisplayContext.NONE, entity);
		state.yaw = Mth.rotLerp(partialTicks, entity.yRotO, entity.getYRot());
		state.pitch = Mth.lerp(partialTicks, entity.xRotO, entity.getXRot());
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.rotateDegrees(Axis.YP, state.yaw + 180.0F);
		poseStack.rotateDegrees(Axis.XP, -state.pitch);
		state.item.submit(poseStack, collector, 0xF000F0, OverlayTexture.NO_OVERLAY, state.outlineColor);
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}
}
