package org.gtlcore.gtlcore.network.packet;

import org.gtlcore.gtlcore.common.player.NoClipManager;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;

import com.glodblock.github.glodium.network.packet.IMessage;

public class CSetNoClip implements IMessage<CSetNoClip> {

    private boolean enabled;

    public CSetNoClip() {}

    public CSetNoClip(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(enabled);
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        enabled = buf.readBoolean();
    }

    @Override
    public void onMessage(Player player) {
        NoClipManager.setEnabled(player, enabled);
    }

    @Override
    public Class<CSetNoClip> getPacketClass() {
        return CSetNoClip.class;
    }

    @Override
    public boolean isClient() {
        return false;
    }
}
