package net.antwire.arsenal.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.antwire.arsenal.Arsenal;
import net.antwire.arsenal.gun.Ballistics;
import net.antwire.arsenal.gun.Caliber;
import net.antwire.arsenal.registry.ModSounds;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Vector3f;

/** Bullets as the client sees them: short glowing streaks, the crack of a near miss, and the holes they leave. */
public final class ClientBullets {
	private static final RenderType TRACER = RenderTypes.entityTranslucentEmissive(Arsenal.id("textures/misc/tracer.png"));
	private static final RenderType HOLE = RenderTypes.entityTranslucent(Arsenal.id("textures/misc/bullet_hole.png"));
	private static final RenderType MARK = RenderTypes.entityTranslucent(Arsenal.id("textures/misc/bullet_mark.png"));
	private static final List<Bullet> BULLETS = new ArrayList<>();
	private static final ArrayDeque<Hole> HOLES = new ArrayDeque<>();
	private static final int MAX_HOLES = 400;
	private static final int HOLE_TICKS = 20 * 60;

	private static final class Bullet {
		Vec3 start;
		Vec3 pos;
		Vec3 prev;
		Vec3 vel;
		final Caliber caliber;
		final boolean local;
		final int shooter;
		int age;
		boolean dead;
		boolean heard;
		/** The visible streak starts at the muzzle and catches up with the line of the bullet. */
		Vec3 offset;

		Bullet(Vec3 eye, Vec3 muzzle, Vec3 vel, Caliber caliber, boolean local, int shooter) {
			this.start = muzzle;
			this.pos = eye;
			this.prev = eye;
			this.vel = vel;
			this.caliber = caliber;
			this.local = local;
			this.shooter = shooter;
			this.offset = muzzle.subtract(eye);
		}
	}

	private record Hole(Vec3 at, Vec3 normal, BlockPos block, long born, float spin, boolean metal) {
	}

	private ClientBullets() {
	}

	public static void add(Vec3 eye, Vec3 muzzle, Vec3 vel, Caliber caliber, boolean local, int shooter) {
		if (BULLETS.size() < 600) {
			BULLETS.add(new Bullet(eye, muzzle, vel, caliber, local, shooter));
		}
	}

	public static void hole(Vec3 at, Vec3 normal, boolean metal) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		BlockPos block = BlockPos.containing(at.subtract(normal.scale(0.05)));
		HOLES.addLast(new Hole(at.add(normal.scale(0.004 + HOLES.size() % 7 * 0.0003)), normal, block, level.getGameTime(), (float) (Math.random() * Math.PI * 2), metal));
		while (HOLES.size() > MAX_HOLES) {
			HOLES.removeFirst();
		}
	}

	public static void clear() {
		BULLETS.clear();
		HOLES.clear();
	}

	public static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level == null) {
			clear();
			return;
		}
		Vec3 ear = mc.gameRenderer.mainCamera().position();
		Iterator<Bullet> it = BULLETS.iterator();
		while (it.hasNext()) {
			Bullet b = it.next();
			if (b.dead || ++b.age > 60) {
				it.remove();
				continue;
			}
			b.prev = b.pos;
			Vec3 next = b.pos.add(b.vel);
			BlockHitResult hit = level.clip(new ClipContext(b.pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
			if (hit.getType() != HitResult.Type.MISS && !Ballistics.fragile(level.getBlockState(hit.getBlockPos()))) {
				next = hit.getLocation();
				b.dead = true;
			}
			if (!b.local && !b.heard && b.shooter != (mc.player == null ? -1 : mc.player.getId())) {
				double d = distanceToSegment(ear, b.pos, next);
				if (d < 3.5) {
					b.heard = true;
					boolean supersonic = b.vel.length() * 20 > 343;
					Vec3 at = closest(ear, b.pos, next);
					level.playLocalSound(at.x, at.y, at.z, (supersonic ? ModSounds.CRACK : ModSounds.WHIZ).value(), SoundSource.PLAYERS,
						supersonic ? 1.0F : 0.7F, 0.9F + level.getRandom().nextFloat() * 0.2F, false);
				}
			}
			b.pos = next;
			b.offset = b.offset.scale(0.35);
			b.vel = b.vel.scale(1.0 - b.caliber.drag).add(0, -Ballistics.GRAVITY, 0);
		}
		long now = level.getGameTime();
		HOLES.removeIf(h -> now - h.born > HOLE_TICKS || level.getBlockState(h.block).isAir());
	}

	private static Vec3 closest(Vec3 p, Vec3 a, Vec3 b) {
		Vec3 ab = b.subtract(a);
		double len = ab.lengthSqr();
		double t = len < 1.0E-9 ? 0 : Math.clamp(p.subtract(a).dot(ab) / len, 0, 1);
		return a.add(ab.scale(t));
	}

	private static double distanceToSegment(Vec3 p, Vec3 a, Vec3 b) {
		return closest(p, a, b).distanceTo(p);
	}

	public static void render(LevelRenderContext context) {
		if (BULLETS.isEmpty() && HOLES.isEmpty()) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		CameraRenderState camera = context.levelState().cameraRenderState;
		Vec3 cam = camera.pos;
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		PoseStack poseStack = context.poseStack();
		poseStack.pushPose();
		poseStack.translate(-cam.x, -cam.y, -cam.z);
		for (Bullet b : BULLETS) {
			Vec3 head = b.prev.lerp(b.pos, partial).add(b.offset.scale(Math.pow(0.35, partial)));
			Vec3 dir = b.vel.normalize();
			double speed = b.vel.length();
			double len = Math.min(Math.min(1.5 + speed * 0.25, 9.0), head.distanceTo(b.start));
			if (len < 0.2) {
				continue;
			}
			Vec3 tail = head.subtract(dir.scale(len));
			float width = b.caliber.threat == Caliber.Threat.MAGNUM_RIFLE || b.caliber.threat == Caliber.Threat.ANTI_MATERIEL ? 0.035F : 0.022F;
			int alpha = b.local ? 150 : 190;
			int color = alpha << 24 | 0xFFE8A0;
			context.submitNodeCollector().submitCustomGeometry(poseStack, TRACER, (pose, vc) -> ribbon(pose, vc, tail, head, cam, width, color));
		}
		ClientLevel level = mc.level;
		long now = level == null ? 0 : level.getGameTime();
		for (Hole h : HOLES) {
			if (h.at.distanceToSqr(cam) > 48 * 48) {
				continue;
			}
			float age = (now - h.born) / (float) HOLE_TICKS;
			int a = (int) (255 * (age > 0.85F ? (1 - age) / 0.15F : 1));
			int light = level == null ? 0xF000F0 : net.minecraft.util.LightCoordsUtil.getLightCoords(level, BlockPos.containing(h.at.add(h.normal.scale(0.2))));
			context.submitNodeCollector().submitCustomGeometry(poseStack, h.metal ? MARK : HOLE, (pose, vc) -> decal(pose, vc, h, a, light));
		}
		poseStack.popPose();
	}

	private static void ribbon(PoseStack.Pose pose, VertexConsumer vc, Vec3 tail, Vec3 head, Vec3 cam, float width, int color) {
		Vec3 along = head.subtract(tail);
		Vec3 toCam = cam.subtract(head);
		Vec3 side = along.cross(toCam);
		if (side.lengthSqr() < 1.0E-9) {
			return;
		}
		side = side.normalize().scale(width);
		int faint = color & 0x00FFFFFF;
		vertex(pose, vc, tail.add(side), faint, 0, 0, 0xF000F0);
		vertex(pose, vc, tail.subtract(side), faint, 0, 1, 0xF000F0);
		vertex(pose, vc, head.subtract(side), color, 1, 1, 0xF000F0);
		vertex(pose, vc, head.add(side), color, 1, 0, 0xF000F0);
	}

	private static void decal(PoseStack.Pose pose, VertexConsumer vc, Hole h, int alpha, int light) {
		Vec3 n = h.normal;
		Vec3 ref = Math.abs(n.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
		Vec3 u = n.cross(ref).normalize();
		Vec3 v = n.cross(u).normalize();
		double c = Math.cos(h.spin), s = Math.sin(h.spin);
		Vec3 a = u.scale(c).add(v.scale(s)).scale(0.07);
		Vec3 b = v.scale(c).subtract(u.scale(s)).scale(0.07);
		int color = alpha << 24 | 0xFFFFFF;
		vertex(pose, vc, h.at.subtract(a).subtract(b), color, 0, 0, light, n);
		vertex(pose, vc, h.at.add(a).subtract(b), color, 1, 0, light, n);
		vertex(pose, vc, h.at.add(a).add(b), color, 1, 1, light, n);
		vertex(pose, vc, h.at.subtract(a).add(b), color, 0, 1, light, n);
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer vc, Vec3 p, int color, float u, float v, int light) {
		vertex(pose, vc, p, color, u, v, light, new Vec3(0, 1, 0));
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer vc, Vec3 p, int color, float u, float v, int light, Vec3 n) {
		vc.addVertex(pose, (float) p.x, (float) p.y, (float) p.z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
			.setNormal(pose, (float) n.x, (float) n.y, (float) n.z);
	}

	static Vector3f unused() {
		return new Vector3f();
	}
}
