package net.antwire.arsenal.network;

import net.antwire.arsenal.Arsenal;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: reload, or switch the fire mode (shotguns: the shell to load next). */
public record ActionPayload(int action) implements CustomPacketPayload {
	public static final int RELOAD = 0;
	public static final int MODE = 1;
	public static final Type<ActionPayload> TYPE = new Type<>(Arsenal.id("action"));
	public static final StreamCodec<FriendlyByteBuf, ActionPayload> CODEC = StreamCodec.of((buf, p) -> buf.writeVarInt(p.action),
		buf -> new ActionPayload(buf.readVarInt()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
