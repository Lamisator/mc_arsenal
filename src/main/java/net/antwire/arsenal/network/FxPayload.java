package net.antwire.arsenal.network;

import net.antwire.arsenal.Arsenal;
import net.antwire.arsenal.gun.Ballistics;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Server to the players around: an explosion or another effect, drawn by the client. */
public record FxPayload(int kind, Vec3 at, Vec3 dir, long seed) implements CustomPacketPayload {
	public static final int GRENADE = 0;
	public static final int FLASHBANG = 1;
	public static final int SMOKE = 2;
	public static final int ROCKET = 3;
	public static final int CLAYMORE = 4;
	public static final int C4 = 5;
	public static final int MINE = 6;
	public static final int BACKBLAST = 7;
	public static final Type<FxPayload> TYPE = new Type<>(Arsenal.id("fx"));
	public static final StreamCodec<FriendlyByteBuf, FxPayload> CODEC = StreamCodec.of((buf, p) -> {
		buf.writeVarInt(p.kind);
		Vec3.STREAM_CODEC.encode(buf, p.at);
		Vec3.STREAM_CODEC.encode(buf, p.dir);
		buf.writeLong(p.seed);
	}, buf -> new FxPayload(buf.readVarInt(), Vec3.STREAM_CODEC.decode(buf), Vec3.STREAM_CODEC.decode(buf), buf.readLong()));

	public static void send(ServerLevel level, int kind, Vec3 at, Vec3 dir, long seed) {
		FxPayload p = new FxPayload(kind, at, dir, seed);
		for (ServerPlayer player : Ballistics.watching(level, at, 256)) {
			ServerPlayNetworking.send(player, p);
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
