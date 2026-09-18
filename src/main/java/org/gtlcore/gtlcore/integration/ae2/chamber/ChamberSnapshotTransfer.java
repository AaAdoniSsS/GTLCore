package org.gtlcore.gtlcore.integration.ae2.chamber;

import org.gtlcore.gtlcore.GTLCore;
import org.gtlcore.gtlcore.integration.ae2.wireless.WirelessAePackets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import io.netty.buffer.Unpooled;
import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

import java.util.function.Consumer;

/** Bounded transport; clients publish a snapshot only after every chunk has arrived. */
public final class ChamberSnapshotTransfer {

    public static final int MAX_CHUNK_BYTES = 128 * 1024;
    public static final int MAX_SNAPSHOT_BYTES = 16 * 1024 * 1024;

    private ChamberSnapshotTransfer() {}

    public static boolean send(ServerPlayer player, int containerId, boolean contents, long revision,
                               Consumer<FriendlyByteBuf> encoder) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer(4096, MAX_SNAPSHOT_BYTES));
        SyncEvent event = new SyncEvent();
        event.begin();
        try {
            // Encode completely before sending: an oversized snapshot never leaves a partial transfer.
            encoder.accept(buffer);
            int total = buffer.readableBytes();
            for (int offset = 0; offset < total;) {
                byte[] chunk = new byte[Math.min(MAX_CHUNK_BYTES, total - offset)];
                buffer.readBytes(chunk);
                WirelessAePackets.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                        new WirelessAePackets.SyncMEChamberSnapshotPacket(
                                containerId, contents, revision, total, offset, chunk));
                offset += chunk.length;
                event.chunks++;
            }
            event.bytes = total;
            event.contents = contents;
            event.containerId = containerId;
            event.commit();
            return true;
        } catch (IndexOutOfBoundsException | IllegalArgumentException failure) {
            GTLCore.LOGGER.warn("Cannot encode chamber snapshot for menu {}", containerId, failure);
            player.displayClientMessage(Component.translatable("message.gtlcore.chamber_snapshot_too_large"), false);
            player.closeContainer();
            return false;
        } finally {
            buffer.release();
        }
    }

    public static final class Receiver {

        private byte[] pending;
        private long revision;
        private int received;

        public void accept(long incomingRevision, int total, int offset, byte[] chunk,
                           Consumer<FriendlyByteBuf> decoder) {
            if (total <= 0 || total > MAX_SNAPSHOT_BYTES || offset < 0 || chunk.length == 0 ||
                    chunk.length > MAX_CHUNK_BYTES || offset > total - chunk.length) {
                clear();
                return;
            }
            if (offset == 0) {
                pending = new byte[total];
                revision = incomingRevision;
                received = 0;
            }
            if (pending == null || revision != incomingRevision || pending.length != total || received != offset) {
                clear();
                return;
            }
            System.arraycopy(chunk, 0, pending, offset, chunk.length);
            received += chunk.length;
            if (received == total) {
                FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(pending));
                clear();
                try {
                    decoder.accept(buffer);
                } finally {
                    buffer.release();
                }
            }
        }

        private void clear() {
            pending = null;
            received = 0;
        }
    }

    @Name("gtlcore.ChamberSnapshot")
    @Label("Chamber snapshot transfer")
    @Category("GTLCore")
    @StackTrace(false)
    public static final class SyncEvent extends Event {

        public int containerId;
        public boolean contents;
        public int bytes;
        public int chunks;
    }
}
