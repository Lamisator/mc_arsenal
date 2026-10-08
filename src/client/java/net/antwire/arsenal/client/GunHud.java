package net.antwire.arsenal.client;

import java.util.Locale;
import net.antwire.arsenal.Arsenal;
import net.antwire.arsenal.client.fx.ClientFx;
import net.antwire.arsenal.client.fx.FlashFx;
import net.antwire.arsenal.gun.GunItem;
import net.antwire.arsenal.gun.GunServer;
import net.antwire.arsenal.gun.GunState;
import net.antwire.arsenal.gun.GunType;
import net.antwire.arsenal.item.GrenadeItem;
import net.antwire.arsenal.network.HitPayload;
import net.antwire.arsenal.registry.ModItems;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/** Ammunition counter, reload bar, hit markers, scope and launcher sights, grenade fuse, mine detector, flashbang. */
public final class GunHud {
	private static final Identifier SCOPE = Arsenal.id("textures/misc/scope.png");
	private static final Identifier PGO7 = Arsenal.id("textures/misc/pgo7.png");
	private static final Identifier CLU = Arsenal.id("textures/misc/clu.png");
	private static long hitUntil;
	private static int hitKind;

	private GunHud() {
	}

	public static void hit(int kind) {
		hitKind = kind;
		hitUntil = System.currentTimeMillis() + (kind == HitPayload.KILL ? 450 : 250);
	}

	public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.gui.hud.isHidden()) {
			flash(g);
			return;
		}
		float partial = delta.getGameTimeDeltaPartialTick(false);
		ItemStack stack = player.getMainHandItem();
		GunType type = GunItem.type(stack);
		boolean firstPerson = mc.options.getCameraType().isFirstPerson();
		if (type != null && firstPerson) {
			float aim = GunClient.aim(partial);
			if (aim > 0.85F) {
				if (type.scope) {
					optic(g, SCOPE, 1.0F);
				} else if (type == GunType.RPG7) {
					optic(g, PGO7, 0.9F);
				} else if (type == GunType.JAVELIN) {
					clu(g, mc);
				}
			}
		}
		if (type != null) {
			ammo(g, mc, player, stack, type);
		}
		if (player.isUsingItem() && player.getUseItem().getItem() instanceof GrenadeItem grenade) {
			fuse(g, mc, player, grenade);
		}
		if (System.currentTimeMillis() < hitUntil) {
			hitMarker(g);
		}
		if (ClientFx.detectorDistance >= 0) {
			int x = g.guiWidth() / 2 + 14;
			int y = g.guiHeight() / 2 + 10;
			g.text(mc.font, String.format(Locale.ROOT, "MINE %.1f m", ClientFx.detectorDistance), x, y, 0xFFFF5040, true);
		}
		flash(g);
	}

	private static void flash(GuiGraphicsExtractor g) {
		float white = FlashFx.whiteness();
		if (white > 0.002F) {
			g.fill(0, 0, g.guiWidth(), g.guiHeight(), (int) (white * 255) << 24 | 0xFFFFFF);
		}
	}

	/** A round sight picture: the texture holds the reticle, everything outside it is black. */
	private static void optic(GuiGraphicsExtractor g, Identifier texture, float scale) {
		int size = (int) (Math.min(g.guiWidth(), g.guiHeight()) * scale);
		int left = (g.guiWidth() - size) / 2;
		int top = (g.guiHeight() - size) / 2;
		g.blit(RenderPipelines.GUI_TEXTURED, texture, left, top, 0.0F, 0.0F, size, size, size, size);
		g.fill(0, 0, g.guiWidth(), top, 0xFF000000);
		g.fill(0, top + size, g.guiWidth(), g.guiHeight(), 0xFF000000);
		g.fill(0, top, left, top + size, 0xFF000000);
		g.fill(left + size, top, g.guiWidth(), top + size, 0xFF000000);
	}

	/** The Javelin's command launch unit: green picture, track gates closing in on the target until it locks. */
	private static void clu(GuiGraphicsExtractor g, Minecraft mc) {
		int w = g.guiWidth();
		int h = g.guiHeight();
		g.fill(0, 0, w, h, 0x3010FF30);
		optic(g, CLU, 0.95F);
		int cx = w / 2;
		int cy = h / 2;
		float p = GunClient.lockProgress();
		boolean locked = GunClient.locked();
		int color = locked ? 0xFFFF4040 : 0xFF40FF40;
		int box = (int) Mth.lerp(p, 60, 12);
		int bx = cx;
		int by = cy;
		Entity target = GunClient.seekTarget(mc);
		if (target != null) {
			int[] screen = project(mc, target.getBoundingBox().getCenter(), w, h);
			if (screen != null) {
				bx = screen[0];
				by = screen[1];
			}
		}
		if (GunClient.lockProgress() > 0) {
			corner(g, bx - box, by - box, 1, 1, color);
			corner(g, bx + box, by - box, -1, 1, color);
			corner(g, bx - box, by + box, 1, -1, color);
			corner(g, bx + box, by + box, -1, -1, color);
		}
		Font font = mc.font;
		g.text(font, "TOP", cx - 90, cy - 70, 0xFF40FF40, false);
		g.text(font, locked ? "LOCK" : p > 0 ? "SEEK" : "DAY", cx + 66, cy - 70, color, false);
		g.text(font, "CLU  4x", cx - 90, cy + 62, 0xFF40FF40, false);
	}

	private static void corner(GuiGraphicsExtractor g, int x, int y, int dx, int dy, int color) {
		g.fill(Math.min(x, x + 6 * dx), y, Math.max(x, x + 6 * dx) + 1, y + 1, color);
		g.fill(x, Math.min(y, y + 6 * dy), x + 1, Math.max(y, y + 6 * dy) + 1, color);
	}

	/** Screen position of a world point, or null if behind the camera. */
	private static int[] project(Minecraft mc, Vec3 world, int w, int h) {
		var camera = mc.gameRenderer.mainCamera();
		Vec3 rel = world.subtract(camera.position());
		Vector4f v = new Vector4f((float) rel.x, (float) rel.y, (float) rel.z, 1.0F);
		Matrix4f view = new Matrix4f().rotation(camera.rotation().conjugate(new org.joml.Quaternionf()));
		view.transform(v);
		if (v.z >= -0.05F) {
			return null;
		}
		double fov = mc.options.fov().get() * GunType.JAVELIN.zoom;
		double f = 1.0 / Math.tan(Math.toRadians(fov) / 2);
		double aspect = (double) w / h;
		double sx = (v.x / -v.z) * f / aspect;
		double sy = (v.y / -v.z) * f;
		return new int[] {(int) (w / 2.0 + sx * w / 2.0), (int) (h / 2.0 - sy * h / 2.0)};
	}

	private static void ammo(GuiGraphicsExtractor g, Minecraft mc, LocalPlayer player, ItemStack stack, GunType type) {
		Font font = mc.font;
		GunState s = GunItem.state(stack);
		int ammo = GunClient.predictedAmmo(stack);
		int right = g.guiWidth() - 8;
		int bottom = g.guiHeight() - 8;
		String big = ammo + "";
		String mag = " / " + type.magazine;
		int reserve = reserve(player, type);
		String name = stack.getHoverName().getString();
		String mode = type.isShotgun() ? Component.translatable(s.mode() == 1 ? "hud.arsenal.slug" : "hud.arsenal.buck").getString()
			: type.isLauncher() ? "" : Component.translatable("tooltip.arsenal.mode." + s.fireMode(type).name().toLowerCase(Locale.ROOT)).getString();
		int color = ammo == 0 ? 0xFFFF5050 : ammo <= Math.max(1, type.magazine / 4) ? 0xFFFFC040 : 0xFFFFFFFF;
		g.pose().pushMatrix();
		g.pose().translate(right, bottom);
		g.pose().scale(2.0F, 2.0F);
		int bw = font.width(big);
		g.text(font, big, -bw - font.width(mag) / 2 - 1, -9, color, true);
		g.pose().popMatrix();
		g.text(font, mag, right - font.width(mag), bottom - 9, 0xFFC0C0C0, true);
		String line = name + (mode.isEmpty() ? "" : "  " + mode) + "  +" + reserve;
		g.text(font, line, right - font.width(line), bottom - 30, 0xFFE0E0E0, true);
		long now = mc.level.getGameTime();
		if (s.reloading(now) && s.reloadTicks() > 0) {
			float f = 1.0F - (s.reloadEnd() - now - mc.getDeltaTracker().getGameTimeDeltaPartialTick(false)) / s.reloadTicks();
			int bw2 = 80;
			int x = g.guiWidth() / 2 - bw2 / 2;
			int y = g.guiHeight() / 2 + 18;
			g.fill(x - 1, y - 1, x + bw2 + 1, y + 4, 0x90000000);
			g.fill(x, y, x + (int) (bw2 * Mth.clamp(f, 0, 1)), y + 3, 0xFFE0E0E0);
			g.centeredText(font, Component.translatable("hud.arsenal.reloading"), g.guiWidth() / 2, y + 6, 0xFFE0E0E0);
		} else if (ammo == 0 && reserve > 0) {
			g.centeredText(font, Component.translatable("hud.arsenal.press_reload", GunClient.reloadKey.getTranslatedKeyMessage()), g.guiWidth() / 2,
				g.guiHeight() / 2 + 18, 0xFFFFC040);
		}
	}

	private static int reserve(LocalPlayer player, GunType type) {
		if (type.isShotgun()) {
			return GunServer.count(player.getInventory(), ModItems.SHELL_BUCKSHOT) + GunServer.count(player.getInventory(), ModItems.SHELL_SLUG);
		}
		Item ammo = GunServer.ammoItem(type);
		return ammo == null ? 0 : GunServer.count(player.getInventory(), ammo);
	}

	private static void fuse(GuiGraphicsExtractor g, Minecraft mc, LocalPlayer player, GrenadeItem grenade) {
		int held = player.getTicksUsingItem();
		float left = Math.max(0, (grenade.kind.fuse - held) / 20.0F);
		int x = g.guiWidth() / 2;
		int y = g.guiHeight() / 2 + 18;
		int w = 60;
		float f = left / (grenade.kind.fuse / 20.0F);
		g.fill(x - w / 2 - 1, y - 1, x + w / 2 + 1, y + 4, 0x90000000);
		g.fill(x - w / 2, y, x - w / 2 + (int) (w * f), y + 3, f < 0.3F ? 0xFFFF4040 : 0xFFFFC040);
		g.centeredText(mc.font, String.format(Locale.ROOT, "%.1f s", left), x, y + 6, 0xFFFFFFFF);
	}

	private static void hitMarker(GuiGraphicsExtractor g) {
		int cx = g.guiWidth() / 2;
		int cy = g.guiHeight() / 2;
		int color = switch (hitKind) {
			case HitPayload.HEAD -> 0xFFFF6060;
			case HitPayload.KILL -> 0xFFFF2020;
			case HitPayload.ARMOR -> 0xFF90A0B0;
			default -> 0xFFFFFFFF;
		};
		int r = hitKind == HitPayload.KILL ? 9 : 7;
		for (int i = 3; i < r; i++) {
			g.fill(cx - i, cy - i, cx - i + 1, cy - i + 1, color);
			g.fill(cx + i, cy - i, cx + i + 1, cy - i + 1, color);
			g.fill(cx - i, cy + i, cx - i + 1, cy + i + 1, color);
			g.fill(cx + i, cy + i, cx + i + 1, cy + i + 1, color);
		}
	}
}
