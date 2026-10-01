package org.gtlcore.gtlcore.client.preview;

/** Shared by world and JEI previews; several visible widgets cannot multiply the frame budget. */
public final class PreviewFrameBudget {

    private static long remaining;
    private static long frame;

    private PreviewFrameBudget() {}

    public static void beginFrame() {
        frame++;
        remaining = PreviewSettings.frameBudgetMs() * 1_000_000L;
    }

    public static long frame() {
        return frame;
    }

    public static Slice slice() {
        return new Slice();
    }

    public static final class Slice implements AutoCloseable {

        private final long start = System.nanoTime();
        private final long deadline = start + remaining;

        public boolean available() {
            return System.nanoTime() < deadline;
        }

        @Override
        public void close() {
            remaining = Math.max(0, remaining - (System.nanoTime() - start));
        }
    }
}
