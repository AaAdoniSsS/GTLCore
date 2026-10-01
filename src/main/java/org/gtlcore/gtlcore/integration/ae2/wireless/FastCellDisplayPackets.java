package org.gtlcore.gtlcore.integration.ae2.wireless;

import org.gtlcore.gtlcore.GTLCore;

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
    private static final int MAX_AMOUNT_BYTES = 1024;
    private static final Map<MEStorageMenu, Map<AEKey, BigInteger>> PREVIOUS = new WeakHashMap<>();
    private static final Set<MEStorageMenu> READY = Collections.newSetFromMap(new WeakHashMap<>());

    private FastCellDisplayPackets() {}

    public static void register(SimpleChannel channel, IntSupplier ids) {
        channel.registerMessage(ids.getAsInt(), Response.class, Response::encode, Response::decode, Response::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        channel.registerMessage(ids.getAsInt(), Ready.class,
                (packet, buffer) -> buffer.writeVarInt(packet.containerId()),
                buffer -> new Ready(buffer.readVarInt()), Ready::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    public static void ready(MEStorageMenu menu) {
        WirelessAePackets.CHANNEL.sendToServer(new Ready(menu.containerId));
    }

    private record Ready(int containerId) {

        private static void handle(Ready packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player != null && player.containerMenu instanceof MEStorageMenu menu &&
                        menu.containerId == packet.containerId() && menu.isValidMenu()) {
                    // Duplicate readiness messages must not repeatedly force full snapshots.
                    READY.add(menu);
                }
            });
            context.setPacketHandled(true);
        }
    }

    /** Send only the precision already collected by the native inventory read; never query storage. */
    public static void push(MEStorageMenu menu, Map<AEKey, BigInteger> exact) {
        if (!(menu.getPlayer() instanceof ServerPlayer player) || !menu.isValidMenu() || !READY.contains(menu)) return;
        if (!PREVIOUS.containsKey(menu)) {
            GTLCore.LOGGER.info("Precise terminal sync ready: menu={}, overflowKeys={}", menu.containerId, exact.size());
            PREVIOUS.put(menu, Map.of());
        }
        Map<AEKey, BigInteger> previous = PREVIOUS.getOrDefault(menu, Map.of());
        Map<AEKey, BigInteger> changes = null;
        for (var entry : exact.entrySet()) {
            AEKey key = entry.getKey();
            BigInteger amount = entry.getValue();
            if (canDisplay(menu, key, amount) && !amount.equals(previous.get(key))) {
                if (changes == null) changes = new LinkedHashMap<>();
                changes.put(key, amount);
            }
        }
        for (AEKey key : previous.keySet()) {
            BigInteger amount = exact.get(key);
            if (amount == null || !canDisplay(menu, key, amount)) {
                if (changes == null) changes = new LinkedHashMap<>();
                changes.put(key, BigInteger.ZERO);
            }
        }
        if (changes == null) return;
        Map<AEKey, BigInteger> chunk = new LinkedHashMap<>();
        boolean first = true;
        for (var entry : changes.entrySet()) {
            if (chunk.size() == MAX_KEYS) {
                WirelessAePackets.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                        new Response(menu.containerId, first, false, Map.copyOf(chunk)));
                first = false;
                chunk.clear();
            }
            chunk.put(entry.getKey(), entry.getValue());
        }
        WirelessAePackets.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new Response(menu.containerId, first, true, Map.copyOf(chunk)));
        Map<AEKey, BigInteger> updated = new HashMap<>(previous);
        changes.forEach((key, amount) -> {
            if (amount.signum() == 0) updated.remove(key);
            else updated.put(key, amount);
        });
        PREVIOUS.put(menu, updated);
    }

    private static boolean canDisplay(MEStorageMenu menu, AEKey key, BigInteger amount) {
        return WirelessAeKeyPacketCodec.supports(key) && menu.isKeyVisible(key) &&
                amount.bitLength() < MAX_AMOUNT_BYTES * Byte.SIZE;
    }

    public record Response(int containerId, boolean reset, boolean complete, Map<AEKey, BigInteger> amounts) {

        private static void encode(Response packet, FriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.containerId);
            buffer.writeBoolean(packet.reset);
            buffer.writeBoolean(packet.complete);
            buffer.writeVarInt(packet.amounts.size());
            packet.amounts.forEach((key, amount) -> {
                WirelessAeKeyPacketCodec.write(buffer, key);
                buffer.writeByteArray(amount.toByteArray());
            });
        }

        private static Response decode(FriendlyByteBuf buffer) {
            int containerId = buffer.readVarInt();
            boolean reset = buffer.readBoolean();
            boolean complete = buffer.readBoolean();
            int size = buffer.readVarInt();
            if (size < 0 || size > MAX_KEYS) throw new IllegalArgumentException("Too many display amounts");
            Map<AEKey, BigInteger> amounts = new HashMap<>();
            for (int i = 0; i < size; i++) {
                AEKey key = WirelessAeKeyPacketCodec.read(buffer);
                BigInteger amount = new BigInteger(buffer.readByteArray(MAX_AMOUNT_BYTES));
                if (amount.signum() < 0) throw new IllegalArgumentException("Negative display amount");
                amounts.put(key, amount);
            }
            return new Response(containerId, reset, complete, amounts);
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
            menu.gtlcore$acceptDisplayChanges(packet.amounts(), packet.reset(), packet.complete());
        }
    }
}
