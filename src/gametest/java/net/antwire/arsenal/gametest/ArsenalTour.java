package net.antwire.arsenal.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.antwire.arsenal.block.MineBlock;
import net.antwire.arsenal.block.RackBlockEntity;
import net.antwire.arsenal.client.GunClient;
import net.antwire.arsenal.client.fx.FlashFx;
import net.antwire.arsenal.entity.GrenadeEntity;
import net.antwire.arsenal.entity.RocketEntity;
import net.antwire.arsenal.gun.Ballistics;
import net.antwire.arsenal.gun.Caliber;
import net.antwire.arsenal.gun.GunItem;
import net.antwire.arsenal.gun.GunServer;
import net.antwire.arsenal.gun.GunType;
import net.antwire.arsenal.item.GrenadeItem;
import net.antwire.arsenal.network.FirePayload;
import net.antwire.arsenal.registry.ModBlocks;
import net.antwire.arsenal.registry.ModComponents;
import net.antwire.arsenal.registry.ModItems;
import net.antwire.arsenal.registry.ModTab;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;

/**
 * Tour of the mod on a flat test range: every weapon in hand (hip and aimed), shots, armour, explosives, launchers,
 * racks and the HUD. Screenshots land in build/run/clientGameTest/screenshots; ballistic results are asserted and
 * printed with the prefix [arsenal-tour].
 */
public class ArsenalTour implements FabricClientGameTest {
	private static final String SCENES = System.getProperty("arsenal.scenes", "all");
	private final List<String> failures = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext context) {
		context.getInput().resizeWindow(1280, 720);
		try (TestSingleplayerContext sp = context.worldBuilder()
			.setUseConsistentSettings(true)
			.adjustSettings(s -> {
				s.getNormalPresetList().stream().filter(e -> e.preset() != null && e.preset().is(WorldPresets.FLAT)).findFirst().ifPresent(s::setWorldType);
				s.setSeed("arsenal-tour");
			})
			.create()) {
			sp.getConnection().waitForChunksRender();
			for (String cmd : List.of("time set 6000", "weather clear", "gamerule advance_time false", "gamerule advance_weather false",
				"gamerule spawn_mobs false", "gamemode creative @a", "kill @e[type=!player]")) {
				sp.getServer().runCommand(cmd);
			}
			context.runOnClient(mc -> mc.options.renderDistance().set(12));
			context.waitTicks(40);

			if (run("inventory")) {
				this.inventory(context);
			}
			if (run("hands")) {
				this.hands(context, sp);
			}
			if (run("armor")) {
				this.armor(context, sp);
			}
			if (run("fire")) {
				this.fire(context, sp);
			}
			if (run("explosives")) {
				this.explosives(context, sp);
			}
			if (run("launchers")) {
				this.launchers(context, sp);
			}
			if (run("racks")) {
				this.racks(context, sp);
			}
			if (run("hud")) {
				this.hud(context, sp);
			}
		}
		if (!this.failures.isEmpty()) {
			throw new AssertionError("Arsenal tour failures:\n" + String.join("\n", this.failures));
		}
	}

	private static boolean run(String name) {
		return SCENES.equals("all") || SCENES.contains(name);
	}

	private void log(String s) {
		System.out.println("[arsenal-tour] " + s);
	}

	private void check(boolean ok, String what) {
		this.log((ok ? "OK   " : "FAIL ") + what);
		if (!ok) {
			this.failures.add(what);
		}
	}

	// ---------------------------------------------------------------- helpers

	private void tp(ClientGameTestContext context, TestSingleplayerContext sp, double x, double y, double z, float yaw, float pitch) {
		sp.getServer().runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", x, y, z, yaw, pitch));
		sp.getServer().runOnServer(server -> {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				p.getAbilities().flying = true;
				p.onUpdateAbilities();
				p.setDeltaMovement(Vec3.ZERO);
			}
		});
		context.runOnClient(mc -> {
			if (mc.player != null) {
				mc.player.getAbilities().flying = true;
				mc.player.setDeltaMovement(Vec3.ZERO);
			}
		});
		context.waitTicks(5);
		try {
			sp.getConnection().waitForChunksRender(false, 400);
		} catch (AssertionError e) {
			this.log("chunks still rendering");
		}
	}

	private void look(ClientGameTestContext context, TestSingleplayerContext sp, double x, double y, double z, Vec3 target) {
		double dx = target.x - x;
		double dy = target.y - (y + 1.62);
		double dz = target.z - z;
		float yaw = (float) -Math.toDegrees(Math.atan2(dx, dz));
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		this.tp(context, sp, x, y, z, yaw, pitch);
	}

	private void hold(TestSingleplayerContext sp, ItemStack stack) {
		sp.getServer().runOnServer(server -> {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				p.getInventory().setSelectedSlot(0);
				p.getInventory().setItem(0, stack.copy());
			}
		});
	}

	private void clear(TestSingleplayerContext sp) {
		sp.getServer().runCommand("kill @e[type=!player]");
		sp.getServer().runCommand("clear @a");
	}

	private void shot(ClientGameTestContext context, String name) {
		context.runOnClient(mc -> mc.gui.hud.getChat().clearMessages(false));
		context.takeScreenshot(name);
	}

	private int ground(TestSingleplayerContext sp) {
		return sp.getServer().computeOnServer(server -> server.overworld().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, 0, 0));
	}

	private Zombie dummy(ServerLevel level, double x, double y, double z, float yaw) {
		Zombie z0 = net.minecraft.world.entity.EntityTypes.HUSK.create(level, EntitySpawnReason.COMMAND);
		z0.setNoAi(true);
		z0.setPersistenceRequired();
		z0.setPos(x, y, z);
		z0.setYRot(yaw);
		z0.yBodyRot = yaw;
		z0.yHeadRot = yaw;
		level.addFreshEntity(z0);
		return z0;
	}

	// ---------------------------------------------------------------- scenes

	private void inventory(ClientGameTestContext context) {
		context.runOnClient(mc -> {
			try {
				java.lang.reflect.Field f = net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen.class.getDeclaredField("selectedTab");
				f.setAccessible(true);
				f.set(null, ModTab.TAB);
			} catch (ReflectiveOperationException e) {
				throw new RuntimeException(e);
			}
			mc.gui.setScreen(new net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen(mc.player, mc.player.connection.enabledFeatures(), true));
		});
		context.waitTicks(5);
		context.runOnClient(mc -> {
			var screen = (net.fabricmc.fabric.api.client.creativetab.v1.FabricCreativeModeInventoryScreen) mc.gui.screen();
			screen.switchToPage(screen.getPage(ModTab.TAB));
		});
		context.waitTicks(5);
		context.runOnClient(mc -> {
			try {
				java.lang.reflect.Method m = net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen.class.getDeclaredMethod("selectTab",
					net.minecraft.world.item.CreativeModeTab.class);
				m.setAccessible(true);
				m.invoke(mc.gui.screen(), ModTab.TAB);
			} catch (ReflectiveOperationException e) {
				throw new RuntimeException(e);
			}
		});
		context.getInput().setCursorPos(5, 5);
		context.waitTicks(5);
		this.shot(context, "inventory");
		context.runOnClient(mc -> mc.gui.setScreen(null));
	}

	/** Every weapon at the hip and aimed, and some in third person. */
	private void hands(ClientGameTestContext context, TestSingleplayerContext sp) {
		int g = this.ground(sp);
		this.clear(sp);
		sp.getServer().runOnServer(server -> {
			ServerLevel level = server.overworld();
			for (int i = 0; i < 4; i++) {
				this.dummy(level, 0.5 + (i - 1.5) * 3, g, 20.5, 180);
			}
			for (int x = -6; x <= 6; x++) {
				for (int y = 0; y < 4; y++) {
					level.setBlockAndUpdate(new BlockPos(x, g + y, 24), (x + y) % 2 == 0 ? Blocks.TARGET.defaultBlockState() : Blocks.HAY_BLOCK.defaultBlockState());
				}
			}
		});
		this.tp(context, sp, 0.5, g, 0.5, 0, 0);
		for (GunType type : GunType.values()) {
			this.hold(sp, new ItemStack(ModItems.gun(type)));
			context.waitTicks(10);
			this.shot(context, "hand_" + type.id);
			context.getInput().holdKey(o -> o.keyUse);
			context.waitTicks(14);
			this.shot(context, "aim_" + type.id);
			context.getInput().releaseKey(o -> o.keyUse);
			context.waitTicks(4);
		}
		// mannequins (player models) holding the guns, seen from the side
		sp.getServer().runOnServer(server -> {
			ServerLevel level = server.overworld();
			GunType[] types = {GunType.M4A1, GunType.GLOCK_17, GunType.AWM, GunType.RPG7, GunType.JAVELIN, GunType.REMINGTON_870, GunType.BARRETT_M82,
				GunType.AK47};
			for (int i = 0; i < types.length; i++) {
				net.minecraft.world.entity.decoration.Mannequin m = net.minecraft.world.entity.EntityTypes.MANNEQUIN.create(level, EntitySpawnReason.COMMAND);
				m.absSnapTo(-20.5 + (i % 4) * 3, g, 10.5 + (i / 4) * 5, 90, 0);
				m.setYHeadRot(90);
				m.yBodyRot = 90;
				m.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.gun(types[i])));
				level.addFreshEntity(m);
			}
		});
		this.look(context, sp, -16.0, g + 0.5, 4.5, new Vec3(-16.0, g + 1.2, 13));
		context.waitTicks(15);
		this.shot(context, "third_side");
		this.look(context, sp, -26.0, g + 0.8, 12.5, new Vec3(-16.0, g + 1.2, 12.5));
		context.waitTicks(10);
		this.shot(context, "third_front");
		this.tp(context, sp, 0.5, g, 0.5, 0, 0);
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
		this.hold(sp, new ItemStack(ModItems.gun(GunType.AK47)));
		sp.getServer().runOnServer(server -> {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.PLATE_CARRIER));
				p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.COMBAT_HELMET));
			}
		});
		context.waitTicks(8);
		this.shot(context, "third_armor_plate_carrier");
		sp.getServer().runOnServer(server -> {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.KEVLAR_VEST));
			}
		});
		context.waitTicks(8);
		this.shot(context, "third_armor_kevlar");
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		sp.getServer().runOnServer(server -> {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				p.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
				p.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
			}
		});
	}

	/** Damage of a 5.56 and other rounds into torso, legs and head, with and without armour. */
	private void armor(ClientGameTestContext context, TestSingleplayerContext sp) {
		int g = this.ground(sp);
		this.clear(sp);
		this.tp(context, sp, 0.5, g, -5.5, 0, 0);
		float bare = this.damage(sp, g, Caliber.NATO_556, ItemStack.EMPTY, ItemStack.EMPTY, 1.2);
		float kevlar = this.damage(sp, g, Caliber.NATO_556, new ItemStack(ModItems.KEVLAR_VEST), ItemStack.EMPTY, 1.2);
		float plates = this.damage(sp, g, Caliber.NATO_556, new ItemStack(ModItems.PLATE_CARRIER), ItemStack.EMPTY, 1.2);
		float pistolKevlar = this.damage(sp, g, Caliber.NINE_MM, new ItemStack(ModItems.KEVLAR_VEST), ItemStack.EMPTY, 1.2);
		float pistolBare = this.damage(sp, g, Caliber.NINE_MM, ItemStack.EMPTY, ItemStack.EMPTY, 1.2);
		float legs = this.damage(sp, g, Caliber.NATO_556, new ItemStack(ModItems.PLATE_CARRIER), ItemStack.EMPTY, 0.4);
		float head = this.damage(sp, g, Caliber.NINE_MM, ItemStack.EMPTY, ItemStack.EMPTY, 1.75);
		float helmet = this.damage(sp, g, Caliber.NINE_MM, ItemStack.EMPTY, new ItemStack(ModItems.COMBAT_HELMET), 1.75);
		float bmg = this.damage(sp, g, Caliber.BMG_50, new ItemStack(ModItems.PLATE_CARRIER), ItemStack.EMPTY, 1.2);
		this.log(String.format(Locale.ROOT, "5.56 torso: bare %.1f, kevlar %.1f, plates %.1f; 9mm torso: bare %.1f, kevlar %.1f; 5.56 legs with plates %.1f; "
			+ "9mm head %.1f, with helmet %.1f; .50 BMG into plates %.1f", bare, kevlar, plates, pistolBare, pistolKevlar, legs, head, helmet, bmg));
		this.check(bare > 6.5 && bare < 9, "5.56 torso hit does about 8");
		this.check(kevlar > bare * 0.7, "a soft vest hardly slows a rifle round");
		this.check(plates < bare * 0.35, "ceramic plates stop most of a rifle round");
		this.check(pistolKevlar < pistolBare * 0.3, "a Kevlar vest stops a 9 mm");
		this.check(legs > bare * 0.6, "a plate carrier does not protect the legs");
		this.check(head > pistolBare * 1.7, "headshots hurt about twice as much");
		this.check(helmet < head * 0.35, "a helmet stops a 9 mm to the head");
		this.check(bmg >= 19.9F, "a .50 BMG goes through plates (kills the dummy)");
	}

	/** Fires one round into a fresh dummy at 12 m, aiming at {@code height} above its feet, and returns the damage. */
	private float damage(TestSingleplayerContext sp, int g, Caliber caliber, ItemStack chest, ItemStack head, double height) {
		return sp.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			level.getEntitiesOfClass(Zombie.class, new net.minecraft.world.phys.AABB(-20, g - 5, -20, 20, g + 20, 40)).forEach(e -> e.discard());
			Zombie z = this.dummy(level, 0.5, g, 12.5, 180);
			z.setItemSlot(EquipmentSlot.CHEST, chest.copy());
			z.setItemSlot(EquipmentSlot.HEAD, head.copy());
			z.setHealth(z.getMaxHealth());
			ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
			Vec3 from = new Vec3(0.5, g + height, -2.5);
			Vec3 to = new Vec3(0.5, g + height, 12.5);
			float before = z.getHealth();
			Ballistics.fire(level, p, from, to.subtract(from), caliber, 1.0, 1.0F);
			for (int i = 0; i < 4; i++) {
				Ballistics.tick(level);
			}
			float lost = before - (z.isAlive() ? z.getHealth() : 0);
			if (!z.isAlive() && lost < before) {
				lost = before;
			}
			z.discard();
			return lost;
		});
	}

	/** Automatic fire with the real input: hold the trigger with an M4 and watch the magazine, tracers and holes. */
	private void fire(ClientGameTestContext context, TestSingleplayerContext sp) {
		int g = this.ground(sp);
		this.clear(sp);
		sp.getServer().runOnServer(server -> {
			ServerLevel level = server.overworld();
			for (int x = -5; x <= 5; x++) {
				for (int y = 0; y < 5; y++) {
					BlockState s = x < -2 ? Blocks.STONE.defaultBlockState() : x < 1 ? Blocks.OAK_PLANKS.defaultBlockState() : x < 3 ? Blocks.GLASS.defaultBlockState()
						: Blocks.IRON_BLOCK.defaultBlockState();
					level.setBlockAndUpdate(new BlockPos(x, g + y, 30), s);
				}
			}
			this.dummy(level, 3.5, g, 26.5, 180);
			this.dummy(level, -2.5, g, 26.5, 180);
		});
		this.look(context, sp, 0.5, g, 0.5, new Vec3(0.5, g + 1.6, 30));
		this.hold(sp, new ItemStack(ModItems.gun(GunType.M4A1)));
		context.waitTicks(10);
		context.getInput().holdKey(o -> o.keyAttack);
		context.waitTicks(4);
		this.shot(context, "fire_m4_burst");
		context.waitTicks(10);
		context.getInput().releaseKey(o -> o.keyAttack);
		context.waitTicks(10);
		int ammo = sp.getServer().computeOnServer(server -> GunItem.state(server.getPlayerList().getPlayers().getFirst().getMainHandItem()).ammo());
		this.log("M4 magazine after 14 ticks of fire: " + ammo);
		this.check(ammo < 30 && ammo > 10, "holding the trigger fires the M4 automatically (" + ammo + " left)");
		this.shot(context, "fire_m4_holes");
		// reload
		context.getInput().pressKey(GunClient.reloadKey);
		context.waitTicks(20);
		this.shot(context, "fire_m4_reloading");
		context.waitTicks(60);
		int after = sp.getServer().computeOnServer(server -> GunItem.state(server.getPlayerList().getPlayers().getFirst().getMainHandItem()).ammo());
		this.check(after == 30, "R reloads the magazine (" + after + ")");
		// shotgun into the dummies
		this.hold(sp, new ItemStack(ModItems.gun(GunType.REMINGTON_870)));
		sp.getServer().runOnServer(server -> {
			ItemStack s = server.getPlayerList().getPlayers().getFirst().getMainHandItem();
			s.set(ModComponents.GUN, GunItem.state(s).withShells("BBBBBB"));
		});
		this.look(context, sp, 3.5, g, 18.5, new Vec3(3.5, g + 1.2, 26.5));
		context.waitTicks(10);
		context.getInput().pressKey(o -> o.keyAttack);
		context.waitTicks(2);
		this.shot(context, "fire_shotgun");
		context.waitTicks(20);
		// the sniper's view
		this.hold(sp, new ItemStack(ModItems.gun(GunType.AWM)));
		this.look(context, sp, 0.5, g + 2, -60.5, new Vec3(-2.5, g + 1.6, 26.5));
		context.waitTicks(10);
		context.getInput().holdKey(o -> o.keyUse);
		context.waitTicks(16);
		this.shot(context, "scope_awm");
		context.getInput().pressKey(o -> o.keyAttack);
		context.waitTicks(2);
		this.shot(context, "scope_awm_fired");
		context.waitTicks(10);
		context.getInput().releaseKey(o -> o.keyUse);
	}

	private void explosives(ClientGameTestContext context, TestSingleplayerContext sp) {
		int g = this.ground(sp);
		this.clear(sp);
		// a ring of dummies round a frag grenade
		List<Integer> ids = sp.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			List<Integer> out = new ArrayList<>();
			for (int i = 0; i < 6; i++) {
				double a = i * Math.PI / 3;
				out.add(this.dummy(level, 40.5 + Math.cos(a) * (2 + i), g, 0.5 + Math.sin(a) * (2 + i), 0).getId());
			}
			GrenadeEntity grenade = new GrenadeEntity(level, GrenadeItem.Kind.FRAG, server.getPlayerList().getPlayers().getFirst(), new Vec3(40.5, g + 0.5, 0.5),
				Vec3.ZERO, 30);
			level.addFreshEntity(grenade);
			return out;
		});
		this.look(context, sp, 40.5, g + 4, -14.5, new Vec3(40.5, g, 0.5));
		context.waitTicks(31);
		this.shot(context, "frag_grenade");
		context.waitTicks(10);
		int hurt = sp.getServer().computeOnServer(server -> {
			int n = 0;
			for (int id : ids) {
				var e = server.overworld().getEntity(id);
				if (e == null || !e.isAlive() || ((LivingEntity) e).getHealth() < ((LivingEntity) e).getMaxHealth()) {
					n++;
				}
			}
			return n;
		});
		this.check(hurt >= 4, "a frag grenade hurts most of the dummies around it (" + hurt + "/6)");

		// anti-personnel mine under a dummy
		boolean mine = sp.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			BlockPos p = new BlockPos(60, g, 0);
			level.setBlockAndUpdate(p, ModBlocks.AP_MINE.defaultBlockState().setValue(MineBlock.ARMED, true));
			Zombie z = this.dummy(level, 60.5, g + 2, 0.5, 0);
			z.setNoAi(false);
			return z.isAlive();
		});
		context.waitTicks(30);
		boolean mineGone = sp.getServer().computeOnServer(server -> !server.overworld().getBlockState(new BlockPos(60, g, 0)).is(ModBlocks.AP_MINE));
		this.check(mine && mineGone, "a mine goes off when stepped on");

		// claymore fired by the clacker into a row of dummies
		this.clear(sp);
		sp.getServer().runOnServer(server -> {
			ServerLevel level = server.overworld();
			BlockPos c = new BlockPos(80, g, 0);
			level.setBlockAndUpdate(c, ModBlocks.CLAYMORE.defaultBlockState().setValue(net.antwire.arsenal.block.ClaymoreBlock.FACING, Direction.SOUTH));
			for (int i = 0; i < 5; i++) {
				this.dummy(level, 78.5 + i, g, 8.5 + (i % 2) * 3, 0);
			}
			ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
			ItemStack det = new ItemStack(ModItems.DETONATOR);
			det.set(ModComponents.LINKS, List.of(GlobalPos.of(level.dimension(), c)));
			p.getInventory().setSelectedSlot(0);
			p.getInventory().setItem(0, det);
		});
		this.look(context, sp, 88.5, g + 3, -6.5, new Vec3(80.5, g, 6));
		context.waitTicks(10);
		this.shot(context, "claymore_before");
		sp.getServer().runOnServer(server -> {
			ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
			p.getMainHandItem().use(p.level(), p, InteractionHand.MAIN_HAND);
		});
		context.waitTicks(2);
		this.shot(context, "claymore_fired");
		context.waitTicks(20);
		int dead = sp.getServer().computeOnServer(server -> 5 - server.overworld().getEntitiesOfClass(Zombie.class,
			new net.minecraft.world.phys.AABB(70, g - 2, -2, 90, g + 5, 20), LivingEntity::isAlive).size());
		this.check(dead >= 2, "a claymore cuts down the dummies in its fan (" + dead + "/5 dead)");

		// C4 on a wall
		sp.getServer().runOnServer(server -> {
			ServerLevel level = server.overworld();
			for (int x = 95; x <= 105; x++) {
				for (int y = 0; y < 6; y++) {
					level.setBlockAndUpdate(new BlockPos(x, g + y, 10), Blocks.STONE_BRICKS.defaultBlockState());
				}
			}
			level.setBlockAndUpdate(new BlockPos(100, g + 2, 9), ModBlocks.C4.defaultBlockState().setValue(net.antwire.arsenal.block.C4Block.FACING, Direction.NORTH));
		});
		this.look(context, sp, 100.5, g + 1, -2.5, new Vec3(100.5, g + 2, 10));
		context.waitTicks(10);
		this.shot(context, "c4_before");
		sp.getServer().runOnServer(server -> ((net.antwire.arsenal.block.C4Block) ModBlocks.C4).detonate(server.overworld(), new BlockPos(100, g + 2, 9), null));
		context.waitTicks(30);
		this.shot(context, "c4_after");
		boolean breach = sp.getServer().computeOnServer(server -> server.overworld().getBlockState(new BlockPos(100, g + 2, 10)).isAir());
		this.check(breach, "C4 breaches a stone brick wall");

		// flashbang right in front of the player
		this.clear(sp);
		this.tp(context, sp, 120.5, g, 0.5, 0, 10);
		sp.getServer().runOnServer(server -> {
			ServerLevel level = server.overworld();
			level.addFreshEntity(new GrenadeEntity(level, GrenadeItem.Kind.FLASHBANG, null, new Vec3(120.5, g + 0.5, 4.5), Vec3.ZERO, 10));
		});
		context.waitTicks(14);
		float white = context.computeOnClient(mc -> FlashFx.whiteness());
		this.shot(context, "flashbang");
		this.check(white > 0.8F, "a flashbang in view whites out the screen (" + white + ")");
		context.waitTicks(80);
		this.shot(context, "flashbang_fading");
		context.runOnClient(mc -> FlashFx.reset());

		// smoke
		sp.getServer().runOnServer(server -> {
			ServerLevel level = server.overworld();
			level.addFreshEntity(new GrenadeEntity(level, GrenadeItem.Kind.SMOKE, null, new Vec3(140.5, g + 0.5, 6.5), Vec3.ZERO, 5));
			this.dummy(level, 140.5, g, 9.5, 180);
		});
		this.look(context, sp, 140.5, g, -4.5, new Vec3(140.5, g + 1, 6.5));
		context.waitTicks(100);
		this.shot(context, "smoke_grenade");
	}

	private void launchers(ClientGameTestContext context, TestSingleplayerContext sp) {
		int g = this.ground(sp);
		this.clear(sp);
		sp.getServer().runOnServer(server -> {
			ServerLevel level = server.overworld();
			for (int x = -4; x <= 4; x++) {
				for (int y = 0; y < 6; y++) {
					level.setBlockAndUpdate(new BlockPos(x, g + y, -60), Blocks.STONE_BRICKS.defaultBlockState());
				}
			}
		});
		this.look(context, sp, 3.5, g, -20.5, new Vec3(0.5, g + 2, -60));
		this.hold(sp, new ItemStack(ModItems.gun(GunType.RPG7)));
		context.waitTicks(10);
		context.getInput().pressKey(o -> o.keyAttack);
		context.waitTicks(3);
		this.shot(context, "rpg_launch");
		context.waitTicks(10);
		this.shot(context, "rpg_hit");
		context.waitTicks(20);
		boolean hole = sp.getServer().computeOnServer(server -> {
			int air = 0;
			for (int x = -4; x <= 4; x++) {
				for (int y = 0; y < 6; y++) {
					if (server.overworld().getBlockState(new BlockPos(x, g + y, -60)).isAir()) {
						air++;
					}
				}
			}
			return air > 2;
		});
		this.check(hole, "an RPG-7 rocket blows a hole in a wall");
		this.shot(context, "rpg_after");
		boolean empty = sp.getServer().computeOnServer(server -> GunItem.state(server.getPlayerList().getPlayers().getFirst().getMainHandItem()).ammo() == 0);
		this.check(empty, "the RPG is empty after the shot");

		// Javelin: lock with the real seeker, then watch the top attack
		this.clear(sp);
		int target = sp.getServer().computeOnServer(server -> this.dummy(server.overworld(), 200.5, g, 60.5, 180).getId());
		this.look(context, sp, 200.5, g, 0.5, new Vec3(200.5, g + 1, 60.5));
		context.runOnClient(mc -> this.log("target on client: " + mc.level.getEntity(target)));
		this.hold(sp, new ItemStack(ModItems.gun(GunType.JAVELIN)));
		context.waitTicks(10);
		context.getInput().holdKey(o -> o.keyUse);
		context.waitTicks(20);
		this.shot(context, "javelin_seek");
		context.waitTicks(40);
		boolean locked = context.computeOnClient(mc -> GunClient.locked());
		this.check(locked, "the Javelin locks after two seconds on target");
		this.shot(context, "javelin_locked");
		context.runOnClient(mc -> this.log("javelin locked on entity " + GunClient.seekEntity() + " / block " + GunClient.seekPos()));
		context.getInput().pressKey(o -> o.keyAttack);
		context.waitTicks(4);
		sp.getServer().runOnServer(server -> this.log("after javelin shot: ammo " + GunItem.state(server.getPlayerList().getPlayers().getFirst().getMainHandItem()).ammo()
			+ ", rockets " + server.overworld().getEntitiesOfClass(RocketEntity.class, new net.minecraft.world.phys.AABB(-300, -100, -300, 300, 400, 300)).size()));
		context.getInput().releaseKey(o -> o.keyUse);
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		context.waitTicks(30);
		this.shot(context, "javelin_climb");
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		this.look(context, sp, 230.5, g + 10, 40.5, new Vec3(200.5, g + 1, 60.5));
		boolean hit = false;
		for (int i = 0; i < 30 && !hit; i++) {
			context.waitTicks(10);
			hit = sp.getServer().computeOnServer(server -> {
				var e = server.overworld().getEntity(target);
				for (RocketEntity r : server.overworld().getEntitiesOfClass(RocketEntity.class, new net.minecraft.world.phys.AABB(-300, -100, -300, 300, 400, 300))) {
					this.log(String.format(Locale.ROOT, "javelin at %.1f %.1f %.1f", r.getX(), r.getY(), r.getZ()));
				}
				return e == null || !e.isAlive();
			});
			if (i == 3) {
				this.shot(context, "javelin_dive");
			}
		}
		this.check(hit, "the Javelin finds and kills its target");
		this.shot(context, "javelin_impact");
	}

	private void racks(ClientGameTestContext context, TestSingleplayerContext sp) {
		int g = this.ground(sp);
		this.clear(sp);
		sp.getServer().runOnServer(server -> {
			ServerLevel level = server.overworld();
			for (int x = -3; x <= 3; x++) {
				for (int y = 0; y < 4; y++) {
					level.setBlockAndUpdate(new BlockPos(x, g + y, -100), Blocks.SMOOTH_STONE.defaultBlockState());
				}
			}
			GunType[][] wall = {{GunType.M4A1, GunType.AK47, GunType.MP5}, {GunType.AWM, GunType.BARRETT_M82, GunType.REMINGTON_870},
				{GunType.GLOCK_17, GunType.DESERT_EAGLE, GunType.BENELLI_M4}};
			for (int i = 0; i < 3; i++) {
				BlockPos p = new BlockPos(-2 + i * 2, g + 1, -99);
				level.setBlockAndUpdate(p, ModBlocks.WEAPON_RACK.defaultBlockState().setValue(net.antwire.arsenal.block.RackBlock.FACING, Direction.SOUTH));
				if (level.getBlockEntity(p) instanceof RackBlockEntity rack) {
					for (int s = 0; s < 3; s++) {
						rack.setItem(s, new ItemStack(ModItems.gun(wall[i][s])));
					}
				}
			}
			BlockPos stand = new BlockPos(0, g, -96);
			level.setBlockAndUpdate(stand, ModBlocks.GUN_RACK.defaultBlockState().setValue(net.antwire.arsenal.block.RackBlock.FACING, Direction.SOUTH));
			if (level.getBlockEntity(stand) instanceof RackBlockEntity rack) {
				GunType[] up = {GunType.M4A1, GunType.AK47, GunType.REMINGTON_870, GunType.AWM, GunType.RPG7};
				for (int s = 0; s < 5; s++) {
					rack.setItem(s, new ItemStack(ModItems.gun(up[s])));
				}
			}
			level.setBlockAndUpdate(new BlockPos(2, g, -96), ModBlocks.AMMO_CRATE.defaultBlockState());
			level.setBlockAndUpdate(new BlockPos(-2, g, -96), ModBlocks.CLAYMORE.defaultBlockState());
			level.setBlockAndUpdate(new BlockPos(-3, g, -95), ModBlocks.AP_MINE.defaultBlockState());
			level.setBlockAndUpdate(new BlockPos(3, g, -95), ModBlocks.AT_MINE.defaultBlockState());
			level.setBlockAndUpdate(new BlockPos(1, g, -95), ModBlocks.C4.defaultBlockState());
		});
		this.look(context, sp, 0.5, g, -91.5, new Vec3(0.5, g + 1, -98));
		context.waitTicks(10);
		this.shot(context, "racks");
		this.look(context, sp, 0.5, g, -97.5, new Vec3(0.5, g + 1.5, -99));
		context.waitTicks(10);
		this.shot(context, "racks_close");
	}

	private void hud(ClientGameTestContext context, TestSingleplayerContext sp) {
		int g = this.ground(sp);
		this.clear(sp);
		this.tp(context, sp, 0.5, g, 0.5, 0, 0);
		ItemStack m82 = new ItemStack(ModItems.gun(GunType.BARRETT_M82));
		this.hold(sp, m82);
		sp.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst().getInventory().setItem(1, new ItemStack(ModItems.AMMO_50BMG, 32)));
		context.waitTicks(10);
		this.shot(context, "hud_m82");
		sp.getServer().runOnServer(server -> {
			ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
			GunServer.fire(p, new FirePayload(p.getYRot(), p.getXRot(), 1L, false, 0, -1, false, BlockPos.ZERO));
		});
		context.waitTicks(5);
		this.hold(sp, new ItemStack(ModItems.FRAG_GRENADE, 4));
		context.waitTicks(5);
		context.getInput().holdKey(o -> o.keyUse);
		context.waitTicks(30);
		this.shot(context, "hud_grenade_cook");
		context.getInput().releaseKey(o -> o.keyUse);
		context.waitTicks(100);
		this.hold(sp, new ItemStack(ModItems.MINE_DETECTOR));
		sp.getServer().runOnServer(server -> server.overworld().setBlockAndUpdate(new BlockPos(2, g, 3), ModBlocks.AP_MINE.defaultBlockState()));
		context.waitTicks(20);
		this.shot(context, "hud_mine_detector");
	}
}
