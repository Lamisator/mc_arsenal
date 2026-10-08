package net.antwire.arsenal.network;

import net.antwire.arsenal.Arsenal;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to the players around: someone fired. They draw the muzzle flash, the tracers and the brass from it. */
public record ShotPayload(int shooter, int gun, int caliber, float yaw, float pitch, long seed, boolean aiming, float movement) implements CustomPacketPayload {
	public static final Type<ShotPayload> TYPE = new Type<>(Arsenal.id("shot"));
	public static final StreamCodec<FriendlyByteBuf, ShotPayload> CODEC = StreamCodec.of((buf, p) -> {
		buf.writeVarInt(p.shooter);
		buf.writeVarInt(p.gun);
		buf.writeVarInt(p.caliber);
		buf.writeFloat(p.yaw);
		buf.writeFloat(p.pitch);
		buf.writeLong(p.seed);
		buf.writeBoolean(p.aiming);
		buf.writeFloat(p.movement);
	}, buf -> new ShotPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readLong(), buf.readBoolean(),
		buf.readFloat()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
