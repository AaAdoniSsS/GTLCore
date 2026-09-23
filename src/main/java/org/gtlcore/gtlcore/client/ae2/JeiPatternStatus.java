package org.gtlcore.gtlcore.client.ae2;

import org.gtlcore.gtlcore.GTLCore;
import org.gtlcore.gtlcore.integration.ae2.wireless.JeiPatternQuery;
import org.gtlcore.gtlcore.integration.ae2.wireless.JeiPatternWorkers;
import org.gtlcore.gtlcore.integration.ae2.wireless.WirelessAePackets;

import com.lowdragmc.lowdraglib.utils.LocalizationUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Mod;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;

@Mod.EventBusSubscriber(modid = GTLCore.MOD_ID, value = Dist.CLIENT)
public final class JeiPatternStatus {

    // The wireless cache and its in-flight download survive all screen/menu changes.
    private static final Cache WIRELESS = new Cache(-1);
    private static long sequence, ticks;
    private static final ThreadPoolExecutor DECODER = JeiPatternWorkers.worker("gtl-jei-pattern-decoder");

    private JeiPatternStatus() {}

    private static final class Cache {

        final int context;
        Set<AEKey> craftable = new HashSet<>();
        CompletableFuture<Set<AEKey>> decoding;
        volatile long decodeGeneration;
        boolean publishing;
        long decodedBytes;
        long requestId, sentAt = -100, revision = -1;
        long requestedRevision;
        Set<AEKey> requestedSnapshot;
        boolean pending;
        int nextChunk;

        Cache(int context) {
            this.context = context;
        }

        void tick() {
            if (publishing) return;
            if ((!pending && ticks - sentAt >= 20) || (pending && ticks - sentAt >= 100)) {
                pending = true;
                nextChunk = 0;
                // Allocate a receive set only if the server actually sends a new snapshot.
                decoding = CompletableFuture.completedFuture(new HashSet<>());
                decodeGeneration++;
                decodedBytes = 0;
                sentAt = ticks;
                requestId = ++sequence;
                requestedRevision = revision;
                requestedSnapshot = craftable;
                // Reuse a completed wireless snapshot after the server confirms the same network/version.
                WirelessAePackets.CHANNEL.sendToServer(new JeiPatternQuery.Request(requestId, context, requestedRevision));
            }
        }

        void receive(JeiPatternQuery.Response packet) {
            if (packet.id() != requestId || !pending) return;
            sentAt = ticks;
            if (packet.chunks() == -1) {
                // Explicit retry response avoids the old five-second pending timeout.
                pending = false;
                requestedSnapshot = null;
                sentAt = ticks - 10;
                return;
            }
            if (packet.chunks() == 0) {
                if (packet.revision() == requestedRevision) {
                    craftable = requestedSnapshot;
                    revision = requestedRevision;
                }
                requestedSnapshot = null;
                pending = false;

                return;
            }
            if (packet.chunk() != nextChunk) return;

            decodedBytes += packet.data().length;
            if (decodedBytes > 64L * 1024 * 1024) {
                decodeGeneration++;
                pending = false;
                publishing = false;
                decoding = null;
                sentAt = ticks + 100;
                GTLCore.LOGGER.warn("JEI pattern snapshot exceeds 64 MiB receive limit; request={}", requestId);
                return;
            }
            long generation = decodeGeneration;
            long id = requestId;
            decoding = decoding.thenApplyAsync(keys -> {
                if (decodeGeneration != generation) throw new CancellationException();
                packet.addKeysTo(keys);

                return keys;
            }, DECODER);
            boolean last = ++nextChunk == packet.chunks();
            if (last) publishing = true;
            decoding.whenComplete((keys, failure) -> {
                if (!last && failure == null) return;
                Minecraft.getInstance().execute(() -> {
                    if (requestId != id || decodeGeneration != generation) return;
                    if (failure != null) {
                        decodeGeneration++;
                        pending = false;
                        publishing = false;
                        sentAt = ticks;
                        return;
                    }
                    // Publication is a single pointer replacement on the client thread.
                    craftable = keys;
                    decoding = null;
                    revision = packet.revision();
                    requestedSnapshot = null;
                    pending = false;
                    publishing = false;

                });
            });
        }

        void clear() {
            craftable = new HashSet<>();
            decoding = null;
            decodeGeneration++;
            publishing = false;
            requestedSnapshot = null;
            requestId = ++sequence;
            sentAt = -100;
            revision = -1;
            pending = false;
            nextChunk = 0;
        }
    }

    private static Cache displayedCache() {
        // All JEI surfaces use the preloaded wireless snapshot. Opening an AE terminal
        // must never switch to a cold cache and make the labels appear one by one.
        return WIRELESS;
    }

    public static void draw(GuiGraphics graphics, Object ingredient, int x, int y) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        Cache cache = displayedCache();
        if (cache.revision >= 0 && cache.craftable.isEmpty()) return;
        AEKey key = ingredient instanceof ItemStack stack && !stack.isEmpty() ? AEItemKey.of(stack) :
                ingredient instanceof FluidStack fluid && !fluid.isEmpty() ? AEFluidKey.of(fluid) : null;
        boolean found = key != null && cache.craftable.contains(key);

        if (!found) return;
        String label = LocalizationUtils.format("gtlcore.jei.pattern_status.craftable");
        int labelWidth = mc.font.width(label);
        float scale = Math.min(0.5F, 16F / labelWidth);
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(x + 16 - labelWidth * scale, y + 16 - mc.font.lineHeight * scale, 300);
            graphics.pose().scale(scale, scale, 1);
            graphics.drawString(mc.font, label, 0, 0, 0xFF55FF55, true);
        } finally {
            graphics.pose().popPose();
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ticks++;

        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        // Always preload the carried terminal, including while an AE terminal is open.
        WIRELESS.tick();
        // WIRELESS is the sole shared snapshot for every JEI surface.
    }

    public static void receive(JeiPatternQuery.Response packet) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        WIRELESS.receive(packet);
    }

    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            WIRELESS.clear();
        }
    }
}
