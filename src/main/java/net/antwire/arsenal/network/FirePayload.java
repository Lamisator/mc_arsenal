package net.antwire.arsenal.network;

import net.antwire.arsenal.Arsenal;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: one trigger pull (one round, or one shell of pellets). The client's view and seed decide where it
 * goes; the server checks the rate of fire and the ammunition. A Javelin also sends what it has locked on to.
 */
public record FirePayload(float yaw, float pitch, long seed, boolean aiming, float movement, int lockEntity, boolean hasLockPos, BlockPos lockPos)
	implements CustomPacketPayload {
	public static final Type<FirePayload> TYPE = new Type<>(Arsenal.id("fire"));
	public static final StreamCodec<FriendlyByteBuf, FirePayload> CODEC = StreamCodec.of((buf, p) -> {
		buf.writeFloat(p.yaw);
		buf.writeFloat(p.pitch);
		buf.writeLong(p.seed);
		buf.writeBoolean(p.aiming);
		buf.writeFloat(p.movement);
		buf.writeVarInt(p.lockEntity);
		buf.writeBoolean(p.hasLockPos);
		buf.writeBlockPos(p.lockPos);
	}, buf -> new FirePayload(buf.readFloat(), buf.readFloat(), buf.readLong(), buf.readBoolean(), buf.readFloat(), buf.readVarInt(), buf.readBoolean(),
		buf.readBlockPos()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
