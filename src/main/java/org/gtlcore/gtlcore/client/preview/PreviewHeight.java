package org.gtlcore.gtlcore.client.preview;

/** Bounds of occupied positions, independent of camera, visible layer and render masks. */
public final class PreviewHeight {

    private final Bounds host = new Bounds();
    private final Bounds combined = new Bounds();

    public void include(int x, int y, int z, boolean moduleOnly) {
        combined.include(x, y, z);
        if (!moduleOnly) host.include(x, y, z);
    }

    public int aboveBottom(int controllerY, boolean modules) {
        Bounds bounds = modules ? combined : host;
        long distance = (long) controllerY - bounds.minY;
        return bounds.empty || distance < 0 || distance > Integer.MAX_VALUE ? -1 : (int) distance;
    }

    public Dimensions dimensions(boolean modules) {
        Bounds bounds = modules ? combined : host;
        return bounds.empty ? Dimensions.UNKNOWN : new Dimensions(
                (long) bounds.maxX - bounds.minX + 1,
                (long) bounds.maxZ - bounds.minZ + 1,
                (long) bounds.maxY - bounds.minY + 1);
    }

    public record Dimensions(long width, long depth, long height) {

        public static final Dimensions UNKNOWN = new Dimensions(0, 0, 0);

        public boolean known() {
            return width > 0 && depth > 0 && height > 0;
        }
    }

    private static final class Bounds {

        private int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        private int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        private boolean empty = true;

        void include(int x, int y, int z) {
            empty = false;
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }
    }
}
