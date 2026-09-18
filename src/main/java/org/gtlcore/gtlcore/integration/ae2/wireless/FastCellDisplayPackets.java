package org.gtlcore.gtlcore.integration.ae2.wireless;

import org.gtlcore.gtlcore.integration.ae2.storage.PreciseInventoryDisplayService;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import appeng.api.stacks.AEKey;
import appeng.menu.me.common.MEStorageMenu;

import java.math.BigInteger;
import java.util.*;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public final class FastCellDisplayPackets {

    public static final int MAX_KEYS = 256;
    private static final Map<MEStorageMenu, Map<AEKey, BigInteger>> PREVIOUS = new WeakHashMap<>();

    private FastCellDisplayPackets() {}

    public static void register(SimpleChannel channel, IntSupplier ids) {
        channel.registerMessage(ids.getAsInt(), Response.class, Response::encode, Response::decode, Response::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void push(MEStorageMenu menu, appeng.api.storage.MEStorage storage, appeng.api.stacks.KeyCounter snapshot) {
        if (!(menu.getPlayer() instanceof ServerPlayer player) || !menu.isValidMenu()) return;
        List<AEKey> keys = new ArrayList<>();
        for (var entry : snapshot) {
            if (entry.getLongValue() == Long.MAX_VALUE && WirelessAeKeyPacketCodec.supports(entry.getKey()) &&
                    menu.isKeyVisible(entry.getKey()))
                keys.add(entry.getKey());
        }
        Map<AEKey, BigInteger> amounts = keys.isEmpty() ? Map.of() : PreciseInventoryDisplayService.query(storage, keys);
        Map<AEKey, BigInteger> previous = PREVIOUS.getOrDefault(menu, Map.of());
        if (amounts.equals(previous)) return;
        Map<AEKey, BigInteger> changes = new LinkedHashMap<>();
        amounts.forEach((key, amount) -> {
            if (!amount.equals(previous.get(key))) changes.put(key, amount);
        });
        for (AEKey key : previous.keySet()) if (!amounts.containsKey(key)) changes.put(key, BigInteger.ZERO);
        PREVIOUS.put(menu, Map.copyOf(amounts));
        Map<AEKey, BigInteger> chunk = new LinkedHashMap<>();
        for (var entry : changes.entrySet()) {
            if (chunk.size() == MAX_KEYS) {
                WirelessAePackets.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                        new Response(menu.containerId, false, Map.copyOf(chunk)));
                chunk.clear();
            }
            chunk.put(entry.getKey(), entry.getValue());
        }
        WirelessAePackets.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new Response(menu.containerId, true, Map.copyOf(chunk)));
    }

    public record Response(int containerId, boolean complete, Map<AEKey, BigInteger> amounts) {

        private static void encode(Response packet, FriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.containerId);
            buffer.writeBoolean(packet.complete);
            buffer.writeVarInt(packet.amounts.size());
            packet.amounts.forEach((key, amount) -> {
                WirelessAeKeyPacketCodec.write(buffer, key);
                buffer.writeByteArray(amount.toByteArray());
            });
        }

        private static Response decode(FriendlyByteBuf buffer) {
            int containerId = buffer.readVarInt();
            boolean complete = buffer.readBoolean();
            int size = buffer.readVarInt();
            if (size < 0 || size > MAX_KEYS) throw new IllegalArgumentException("Too many display amounts");
            Map<AEKey, BigInteger> amounts = new HashMap<>();
            for (int i = 0; i < size; i++) {
                AEKey key = WirelessAeKeyPacketCodec.read(buffer);
                BigInteger amount = new BigInteger(buffer.readByteArray(32));
                if (amount.signum() < 0) throw new IllegalArgumentException("Negative display amount");
                amounts.put(key, amount);
            }
            return new Response(containerId, complete, amounts);
        }

        private static void handle(Response packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> receive(packet)));
            context.setPacketHandled(true);
        }
    }

    private static void receive(Response packet) {
        var minecraft = net.minecraft.client.Minecraft.getInstance();
        if (minecraft.player != null && minecraft.player.containerMenu.containerId == packet.containerId &&
                minecraft.player.containerMenu instanceof org.gtlcore.gtlcore.integration.ae2.storage.PreciseDisplayMenu menu) {
            menu.gtlcore$acceptDisplayChanges(packet.amounts(), packet.complete());
        }
    }
}
