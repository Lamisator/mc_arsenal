package net.antwire.arsenal.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.antwire.arsenal.block.RackBlock;
import net.antwire.arsenal.block.RackBlockEntity;
import net.antwire.arsenal.gun.GunItem;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** The weapons on a rack, at their true size: lying on the pegs of a wall rack, standing in a floor stand. */
public class RackRenderer implements BlockEntityRenderer<RackBlockEntity, RackRenderer.State> {
	private final ItemModelResolver items;

	public static class State extends BlockEntityRenderState {
		public ItemStackRenderState[] items = new ItemStackRenderState[0];
		public boolean[] guns = new boolean[0];
		public RackBlock.Style style = RackBlock.Style.WALL;
		public Direction facing = Direction.NORTH;
	}

	public RackRenderer(BlockEntityRendererProvider.Context context) {
		this.items = context.itemModelResolver();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(RackBlockEntity rack, State state, float partialTicks, Vec3 cameraPosition,
		ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(rack, state, partialTicks, cameraPosition, breakProgress);
		int n = rack.getContainerSize();
		state.items = new ItemStackRenderState[n];
		state.guns = new boolean[n];
		state.style = rack.getBlockState().getBlock() instanceof RackBlock b ? b.style : RackBlock.Style.WALL;
		state.facing = rack.getBlockState().getValue(RackBlock.FACING);
		for (int i = 0; i < n; i++) {
			ItemStack stack = rack.getItem(i);
			if (!stack.isEmpty()) {
				ItemStackRenderState s = new ItemStackRenderState();
				this.items.updateForTopItem(s, stack, ItemDisplayContext.FIXED, rack.getLevel(), null, (int) rack.getBlockPos().asLong() + i);
				state.items[i] = s;
				state.guns[i] = stack.getItem() instanceof GunItem;
			}
		}
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		for (int i = 0; i < state.items.length; i++) {
			ItemStackRenderState item = state.items[i];
			if (item == null) {
				continue;
			}
			poseStack.pushPose();
			poseStack.translate(0.5, 0.5, 0.5);
			poseStack.rotateDegrees(Axis.YP, -state.facing.toYRot());
			if (state.style == RackBlock.Style.WALL) {
				// lying across the pegs, three rows, close to the wall
				poseStack.translate(0.0, 0.3 - i * 0.3, 0.22);
				if (!state.guns[i]) {
					poseStack.scale(0.5F, 0.5F, 0.5F);
				}
			} else {
				// standing up, muzzle to the sky, butt on the base
				poseStack.translate(-0.4 + i * 0.2, 0.05, 0.0);
				poseStack.rotateDegrees(Axis.YP, 90);
				poseStack.rotateDegrees(Axis.ZP, 90);
				if (!state.guns[i]) {
					poseStack.scale(0.5F, 0.5F, 0.5F);
				}
			}
			item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
			poseStack.popPose();
		}
	}
}
