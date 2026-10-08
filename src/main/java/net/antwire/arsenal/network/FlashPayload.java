package net.antwire.arsenal.network;

import net.antwire.arsenal.Arsenal;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to player: a flashbang went off. {@code blind} and {@code deaf} are seconds. */
public record FlashPayload(float blind, float deaf) implements CustomPacketPayload {
	public static final Type<FlashPayload> TYPE = new Type<>(Arsenal.id("flash"));
	public static final StreamCodec<FriendlyByteBuf, FlashPayload> CODEC = StreamCodec.of((buf, p) -> {
		buf.writeFloat(p.blind);
		buf.writeFloat(p.deaf);
	}, buf -> new FlashPayload(buf.readFloat(), buf.readFloat()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
