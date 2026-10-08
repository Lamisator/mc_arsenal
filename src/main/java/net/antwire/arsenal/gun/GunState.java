package net.antwire.arsenal.gun;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What a gun carries: rounds in the magazine (or tube), the selected fire mode, for shotguns the shells in loading
 * order ('B' buckshot, 'S' slug; the last one is fired first), and a running reload.
 */
public record GunState(int ammo, int mode, String shells, long reloadEnd, int reloadTicks) {
	public static final GunState EMPTY = new GunState(0, -1, "", 0, 0);

	public static final Codec<GunState> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.INT.optionalFieldOf("ammo", 0).forGetter(GunState::ammo),
		Codec.INT.optionalFieldOf("mode", -1).forGetter(GunState::mode),
		Codec.STRING.optionalFieldOf("shells", "").forGetter(GunState::shells),
		Codec.LONG.optionalFieldOf("reload_end", 0L).forGetter(GunState::reloadEnd),
		Codec.INT.optionalFieldOf("reload_ticks", 0).forGetter(GunState::reloadTicks)
	).apply(i, GunState::new));

	public static final StreamCodec<ByteBuf, GunState> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, GunState::ammo,
		ByteBufCodecs.VAR_INT, GunState::mode,
		ByteBufCodecs.STRING_UTF8, GunState::shells,
		ByteBufCodecs.VAR_LONG, GunState::reloadEnd,
		ByteBufCodecs.VAR_INT, GunState::reloadTicks,
		GunState::new);

	public GunType.FireMode fireMode(GunType type) {
		GunType.FireMode[] all = GunType.FireMode.values();
		if (this.mode < 0 || this.mode >= all.length || !type.modes.contains(all[this.mode])) {
			return type.defaultMode();
		}
		return all[this.mode];
	}

	public boolean reloading(long now) {
		return this.reloadEnd > now;
	}

	public GunState withAmmo(int ammo) {
		return new GunState(ammo, this.mode, this.shells, this.reloadEnd, this.reloadTicks);
	}

	public GunState withMode(int mode) {
		return new GunState(this.ammo, mode, this.shells, this.reloadEnd, this.reloadTicks);
	}

	public GunState withShells(String shells) {
		return new GunState(shells.length(), this.mode, shells, this.reloadEnd, this.reloadTicks);
	}

	public GunState withReload(long end, int ticks) {
		return new GunState(this.ammo, this.mode, this.shells, end, ticks);
	}

	/** The shell that comes next out of a shotgun's tube. */
	public Caliber nextShell() {
		return !this.shells.isEmpty() && this.shells.charAt(this.shells.length() - 1) == 'S' ? Caliber.SLUG : Caliber.BUCKSHOT;
	}
}
