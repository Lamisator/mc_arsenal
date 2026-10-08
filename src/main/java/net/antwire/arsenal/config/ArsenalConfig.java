package net.antwire.arsenal.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import net.antwire.arsenal.Arsenal;
import net.fabricmc.loader.api.FabricLoader;

/** config/arsenal.json */
public class ArsenalConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("arsenal.json");
	private static ArsenalConfig instance = new ArsenalConfig();

	public static ArsenalConfig get() {
		return instance;
	}

	/** All bullet damage is multiplied by this. */
	public double damageMultiplier = 1.0;
	/** Bullets shatter glass, panes and ice. */
	public boolean bulletsBreakGlass = true;
	/** Hand grenades damage blocks. */
	public boolean grenadeBlockDamage = false;
	/** RPG and Javelin warheads damage blocks. */
	public boolean rocketBlockDamage = true;
	/** C4 charges damage blocks (that is what they are for). */
	public boolean c4BlockDamage = true;
	/** Anti-tank mines damage blocks. */
	public boolean mineBlockDamage = true;
	/** A claymore's sensor never fires at the player who placed it. */
	public boolean claymoreSparesOwner = true;
	/** An RPG's backblast hurts whoever stands behind the shooter. */
	public boolean backblast = true;
	/** Seconds a flashbang blinds at most (looking straight at it, close by). */
	public double flashbangSeconds = 6.0;
	/** Seconds the ears ring at most. */
	public double deafSeconds = 8.0;

	public static void load() {
		ArsenalConfig loaded = null;
		if (Files.exists(PATH)) {
			try (Reader r = Files.newBufferedReader(PATH)) {
				loaded = GSON.fromJson(r, ArsenalConfig.class);
			} catch (Exception e) {
				Arsenal.LOGGER.error("Could not read {}", PATH, e);
			}
		}
		instance = loaded == null ? new ArsenalConfig() : loaded;
		instance.damageMultiplier = Math.clamp(instance.damageMultiplier, 0.0, 100.0);
		instance.flashbangSeconds = Math.clamp(instance.flashbangSeconds, 0.0, 60.0);
		instance.deafSeconds = Math.clamp(instance.deafSeconds, 0.0, 60.0);
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer w = Files.newBufferedWriter(PATH)) {
				GSON.toJson(instance, w);
			}
		} catch (Exception e) {
			Arsenal.LOGGER.error("Could not write {}", PATH, e);
		}
	}
}
