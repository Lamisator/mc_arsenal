package net.antwire.arsenal.network;

import net.antwire.arsenal.Arsenal;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to shooter: you hit someone. Draws the hit marker. */
public record HitPayload(int kind) implements CustomPacketPayload {
	public static final int BODY = 0;
	public static final int HEAD = 1;
	public static final int ARMOR = 2;
	public static final int KILL = 3;
	public static final Type<HitPayload> TYPE = new Type<>(Arsenal.id("hit"));
	public static final StreamCodec<FriendlyByteBuf, HitPayload> CODEC = StreamCodec.of((buf, p) -> buf.writeVarInt(p.kind), buf -> new HitPayload(buf.readVarInt()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
