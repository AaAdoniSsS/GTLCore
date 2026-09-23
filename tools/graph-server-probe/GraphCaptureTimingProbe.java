package org.gtlcore.test;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.locks.LockSupport;

/** Optional isolated-server load and full-tick timing; never included in Core. */
@Mod.EventBusSubscriber(modid = "gtlgraphprobe")
public final class GraphCaptureTimingProbe {
    private static volatile int loadMillis;
    private static int loadTicks;
    private static long tickStarted;
    private static Window current;

    public static void load(int milliseconds) {
        if (milliseconds != 0 && milliseconds != 20 && milliseconds != 60)
            throw new IllegalArgumentException("Unsupported test load");
        loadMillis = milliseconds;
        loadTicks = 600;
        System.out.println("[Graph Capture] artificial main-thread load=" + milliseconds + " ms/tick, auto-clears after 600 ticks");
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void start(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        tickStarted = System.nanoTime();
        int millis = loadMillis;
        if (millis != 0) {
            long deadline = tickStarted + millis * 1_000_000L;
            long remaining;
            while ((remaining = deadline - System.nanoTime()) > 0) LockSupport.parkNanos(remaining);
            if (--loadTicks <= 0) loadMillis = 0;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static synchronized void endTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && current != null && tickStarted >= current.started)
            current.ticks.add(System.nanoTime() - tickStarted);
    }

    public static synchronized Window begin(String engine) {
        if (current != null) throw new IllegalStateException("Concurrent timing windows");
        return current = new Window(engine, loadMillis);
    }

    public static synchronized void finish(Window window) {
        if (current != window) return;
        current = null;
        Collections.sort(window.ticks);
        int count = window.ticks.size();
        long max = count == 0 ? 0 : window.ticks.get(count - 1);
        long p95 = count == 0 ? 0 : window.ticks.get((int) Math.ceil(count * .95) - 1);
        long over = window.ticks.stream().filter(nanos -> nanos > 50_000_000L).count();
        System.out.printf(Locale.ROOT,
                "[Graph Capture] engine=%s load_ms=%d full_ticks=%d tick_p95_ms=%.4f tick_max_ms=%.4f over_50ms=%d%n",
                window.engine, window.load, count, p95 / 1_000_000.0, max / 1_000_000.0, over);
    }

    public static final class Window {
        private final String engine;
        private final int load;
        private final long started = System.nanoTime();
        private final ArrayList<Long> ticks = new ArrayList<>();

        private Window(String engine, int load) {
            this.engine = engine;
            this.load = load;
        }
    }
}
