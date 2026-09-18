package org.gtlcore.gtlcore.integration.ae2.wireless;

import org.gtlcore.gtlcore.GTLCore;
import org.gtlcore.gtlcore.integration.ae2.WirelessTerminalGridResolver;
import org.gtlcore.gtlcore.integration.ae2.crafting.IMaxFastCraftingProviderVersion;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import appeng.api.networking.IGrid;
import appeng.api.stacks.AEKey;
import appeng.menu.AEBaseMenu;
import io.netty.buffer.Unpooled;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Versioned, shared snapshots with byte and key budgets; no per-frame server queries. */
@Mod.EventBusSubscriber(modid = GTLCore.MOD_ID)
public final class JeiPatternQuery {

    private static final int CHUNK_KEYS = 512;
    private static final int CHUNK_BYTES = 32 * 1024;
    private static final int TICK_BYTES = 128 * 1024;
    private static final int TICK_KEYS = 4096;
    // A single unusually large NBT key may exceed the soft chunk target.
    private static final int MAX_PACKET_BYTES = 2 * 1024 * 1024;
    private static final MeInventoryRequestLimiter<ServerPlayer> WIRELESS_LIMITER = new MeInventoryRequestLimiter<>(2, 20);
    private static final MeInventoryRequestLimiter<ServerPlayer> TERMINAL_LIMITER = new MeInventoryRequestLimiter<>(2, 20);
    private static final Map<IGrid, Snapshot> SNAPSHOTS = new WeakHashMap<>();
    private static final Map<ServerPlayer, Map<Integer, Transfer>> TRANSFERS = new WeakHashMap<>();
    private static final ThreadPoolExecutor ENCODER = JeiPatternWorkers.worker("gtl-jei-pattern-encoder");
    private static long nextRevision;
    private static final Snapshot EMPTY = new Snapshot(-1, 0, Set.of(), CompletableFuture.completedFuture(List.of(new EncodedChunk(new byte[0], 0))));

    private JeiPatternQuery() {}

    public static int contextId(AbstractContainerMenu menu) {
        return menu instanceof AEBaseMenu ? menu.containerId : -1;
    }

    public static void register(SimpleChannel channel, IntSupplier ids) {
        channel.registerMessage(ids.getAsInt(), Request.class, Request::write, Request::read, Request::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        channel.registerMessage(ids.getAsInt(), Response.class, Response::write, Response::read, Response::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    private static IGrid findGrid(ServerPlayer player) {
        if (player.containerMenu instanceof AEBaseMenu menu) {
            var host = ((org.gtlcore.gtlcore.mixin.ae2.gui.AEBaseMenuPatternAccessor) menu).gtlcore$getPatternActionHost();
            var node = host == null ? null : host.getActionableNode();
            if (node != null && node.isActive()) return node.getGrid();
        }
        return WirelessTerminalGridResolver.find(player, player.serverLevel());
    }

    private static Snapshot snapshot(IGrid grid) {
        if (grid == null) return EMPTY;
        var service = grid.getCraftingService();
        long version = ((IMaxFastCraftingProviderVersion) service).gtlcore$getMaxFastCraftingProviderVersionTick();
        Snapshot previous = SNAPSHOTS.get(grid);
        if (previous != null && previous.version == version && !previous.chunks.isCompletedExceptionally()) return previous;
        // AE returns a detached set already; avoid another complete hash/copy pass.
        Set<AEKey> keys = service.getCraftables(WirelessAeKeyPacketCodec::supports);
        // Provider changes that do not change outputs must not trigger another full download.
        Snapshot result = previous != null && !previous.chunks.isCompletedExceptionally() && previous.keys.equals(keys) ?
                new Snapshot(version, previous.revision, previous.keys, previous.chunks) :
                new Snapshot(version, ++nextRevision, keys, encodeAsync(keys));
        SNAPSHOTS.put(grid, result);
        return result;
    }

    private static CompletableFuture<List<EncodedChunk>> encodeAsync(Set<AEKey> keys) {
        try {
            return CompletableFuture.supplyAsync(() -> {

                List<EncodedChunk> chunks = encode(keys);

                return chunks;
            }, ENCODER);
        } catch (RejectedExecutionException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    private static List<EncodedChunk> encode(Set<AEKey> keys) {
        List<EncodedChunk> chunks = new ArrayList<>();
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        FriendlyByteBuf keyBuffer = new FriendlyByteBuf(Unpooled.buffer());
        int count = 0;
        try {
            for (AEKey key : keys) {
                keyBuffer.clear();
                WirelessAeKeyPacketCodec.write(keyBuffer, key);
                if (keyBuffer.readableBytes() > MAX_PACKET_BYTES) throw new IllegalArgumentException("Pattern key exceeds packet limit");
                if (count > 0 && (count == CHUNK_KEYS || buffer.readableBytes() + keyBuffer.readableBytes() > CHUNK_BYTES)) {
                    chunks.add(copyChunk(buffer, count));
                    buffer.clear();
                    count = 0;
                }
                buffer.writeBytes(keyBuffer);
                count++;
            }
            if (count > 0 || chunks.isEmpty()) chunks.add(copyChunk(buffer, count));
        } finally {
            buffer.release();
            keyBuffer.release();
        }
        return List.copyOf(chunks);
    }

    private static EncodedChunk copyChunk(FriendlyByteBuf buffer, int count) {
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.getBytes(buffer.readerIndex(), bytes);
        return new EncodedChunk(bytes, count);
    }

    private record EncodedChunk(byte[] bytes, int keys) {}

    private record Snapshot(long version, long revision, Set<AEKey> keys, CompletableFuture<List<EncodedChunk>> chunks) {}

    private static final class Transfer {

        final Request request;
        final Snapshot snapshot;
        int chunk;

        Transfer(Request request, Snapshot snapshot) {
            this.request = request;
            this.snapshot = snapshot;
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        var transfers = TRANSFERS.get(player);
        if (transfers == null) return;
        int activeContext = contextId(player.containerMenu);
        transfers.keySet().removeIf(id -> id != -1 && id != activeContext);
        int bytes = 0, keys = 0, packets = 0;
        while (!transfers.isEmpty()) {
            Transfer wireless = transfers.get(-1), active = transfers.get(activeContext);
            Transfer transfer = active != null && (wireless == null || ((player.tickCount + packets) & 1) == 0) ? active : wireless;
            if (!transfer.snapshot.chunks.isDone()) break;
            if (transfer.snapshot.chunks.isCompletedExceptionally()) {
                transfers.remove(transfer.request.menuId);
                send(player, new Response(transfer.request.id, -1, 0, -1, 0, new byte[0]));
                continue;
            }
            List<EncodedChunk> encoded = transfer.snapshot.chunks.join();
            EncodedChunk chunk = encoded.get(transfer.chunk);
            if (packets > 0 && (bytes + chunk.bytes.length > TICK_BYTES || keys + chunk.keys > TICK_KEYS)) break;
            send(player, new Response(transfer.request.id, transfer.snapshot.revision, transfer.chunk,
                    encoded.size(), chunk.keys, chunk.bytes));
            bytes += chunk.bytes.length;
            keys += chunk.keys;
            packets++;
            if (++transfer.chunk == encoded.size()) {
                transfers.remove(transfer.request.menuId);

            }
        }
        if (transfers.isEmpty()) TRANSFERS.remove(player);
    }

    private static void send(ServerPlayer player, Response response) {
        WirelessAePackets.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), response);
    }

    @SubscribeEvent
    public static void stop(ServerStoppedEvent event) {
        TRANSFERS.clear();
        SNAPSHOTS.values().forEach(snapshot -> snapshot.chunks.cancel(false));
        SNAPSHOTS.clear();
        ENCODER.getQueue().clear();
    }

    public record Request(long id, int menuId, long knownRevision) {

        private static void write(Request p, FriendlyByteBuf b) {
            b.writeLong(p.id);
            b.writeVarInt(p.menuId);
            b.writeLong(p.knownRevision);
        }

        private static Request read(FriendlyByteBuf b) {
            return new Request(b.readLong(), b.readVarInt(), b.readLong());
        }

        private static void handle(Request p, Supplier<NetworkEvent.Context> supplier) {
            var context = supplier.get();
            context.enqueueWork(() -> {
                var player = context.getSender();
                if (player == null || (p.menuId != -1 && contextId(player.containerMenu) != p.menuId)) return;
                var limiter = p.menuId == -1 ? WIRELESS_LIMITER : TERMINAL_LIMITER;
                if (!limiter.tryAcquire(player, player.serverLevel().getGameTime())) {
                    send(player, new Response(p.id, p.knownRevision, 0, -1, 0, new byte[0]));
                    return;
                }
                var grid = p.menuId == -1 ? WirelessTerminalGridResolver.find(player, player.serverLevel()) : findGrid(player);
                Snapshot snapshot = snapshot(grid);

                if (snapshot.revision == p.knownRevision) send(player, new Response(p.id, snapshot.revision, 0, 0, 0, new byte[0]));
                else TRANSFERS.computeIfAbsent(player, ignored -> new HashMap<>()).put(p.menuId, new Transfer(p, snapshot));
            });
            context.setPacketHandled(true);
        }
    }

    /** Negative/zero chunks are retry/unchanged acknowledgements. Payload is encoded once per network revision. */
    public record Response(long id, long revision, int chunk, int chunks, int keyCount, byte[] data) {

        private static void write(Response p, FriendlyByteBuf b) {
            b.writeLong(p.id);
            b.writeLong(p.revision);
            b.writeVarInt(p.chunk);
            b.writeVarInt(p.chunks);
            b.writeVarInt(p.keyCount);
            b.writeByteArray(p.data);
        }

        private static Response read(FriendlyByteBuf b) {
            long id = b.readLong(), revision = b.readLong();
            int chunk = b.readVarInt(), chunks = b.readVarInt(), count = b.readVarInt();
            byte[] data = b.readByteArray(MAX_PACKET_BYTES);
            if (chunks < -1 || chunk < 0 || (chunks > 0 && chunk >= chunks) || count < 0 || count > CHUNK_KEYS || (chunks <= 0 && (count != 0 || chunk != 0 || data.length != 0))) throw new IllegalArgumentException("Invalid pattern snapshot chunk");
            return new Response(id, revision, chunk, chunks, count, data);
        }

        public void addKeysTo(Set<AEKey> keys) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
            try {
                for (int i = 0; i < keyCount; i++) keys.add(WirelessAeKeyPacketCodec.read(buffer));
                if (buffer.isReadable()) throw new IllegalArgumentException("Trailing pattern snapshot data");
            } finally {
                buffer.release();
            }
        }

        private static void handle(Response p, Supplier<NetworkEvent.Context> supplier) {
            var context = supplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> org.gtlcore.gtlcore.client.ae2.JeiPatternStatus.receive(p)));
            context.setPacketHandled(true);
        }
    }
}
