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

/** Server to the players around: a bullet struck something here. Dust, sparks, blood and a bullet hole. */
public record ImpactPayload(Vec3 at, Vec3 normal, int kind, int state, int flags) implements CustomPacketPayload {
	public static final int SOFT = 0;
	public static final int STONE = 1;
	public static final int METAL = 2;
	public static final int GLASS = 3;
	public static final int FLESH = 4;
	public static final int SPARK = 5;
	public static final Type<ImpactPayload> TYPE = new Type<>(Arsenal.id("impact"));
	public static final StreamCodec<FriendlyByteBuf, ImpactPayload> CODEC = StreamCodec.of((buf, p) -> {
		Vec3.STREAM_CODEC.encode(buf, p.at);
		Vec3.STREAM_CODEC.encode(buf, p.normal);
		buf.writeVarInt(p.kind);
		buf.writeVarInt(p.state);
		buf.writeVarInt(p.flags);
	}, buf -> new ImpactPayload(Vec3.STREAM_CODEC.decode(buf), Vec3.STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

	public static void send(ServerLevel level, Vec3 at, Vec3 normal, int kind, int state, int flags) {
		ImpactPayload p = new ImpactPayload(at, normal, kind, state, flags);
		for (ServerPlayer player : Ballistics.watching(level, at, 128)) {
			ServerPlayNetworking.send(player, p);
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
