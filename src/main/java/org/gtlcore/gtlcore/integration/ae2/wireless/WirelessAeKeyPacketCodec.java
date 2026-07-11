package org.gtlcore.gtlcore.integration.ae2.wireless;

import net.minecraft.network.FriendlyByteBuf;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;

final class WirelessAeKeyPacketCodec {

    private static final int ITEM_KEY_TYPE = 0;
    private static final int FLUID_KEY_TYPE = 1;

    private WirelessAeKeyPacketCodec() {}

    static boolean supports(AEKey key) {
        return key instanceof AEItemKey || key instanceof AEFluidKey;
    }

    static void write(FriendlyByteBuf buffer, AEKey key) {
        if (key instanceof AEItemKey itemKey) {
            buffer.writeByte(ITEM_KEY_TYPE);
            itemKey.writeToPacket(buffer);
        } else if (key instanceof AEFluidKey fluidKey) {
            buffer.writeByte(FLUID_KEY_TYPE);
            fluidKey.writeToPacket(buffer);
        } else {
            throw new IllegalArgumentException("Unsupported wireless AE key: " + key);
        }
    }

    static AEKey read(FriendlyByteBuf buffer) {
        return switch (buffer.readUnsignedByte()) {
            case ITEM_KEY_TYPE -> AEItemKey.fromPacket(buffer);
            case FLUID_KEY_TYPE -> AEFluidKey.fromPacket(buffer);
            default -> throw new IllegalArgumentException("Unsupported wireless AE key type");
        };
    }
}
