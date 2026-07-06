package org.gtlcore.gtlcore.client.ae2.wireless;

final class WirelessAeStyleMetrics {

    private WirelessAeStyleMetrics() {}

    static int panelBackgroundTextureLength(int totalLength, int textureLength) {
        if (totalLength <= 0 || textureLength <= 0) {
            return 0;
        }
        return Math.min(totalLength, textureLength);
    }

    static int panelBackgroundOverflowLength(int totalLength, int textureLength) {
        if (totalLength <= 0 || textureLength <= 0) {
            return 0;
        }
        return Math.max(0, totalLength - textureLength);
    }

    static Bounds scrollbarTrackBounds(int x, int y, int width, int height, int totalRows, int visibleRows) {
        if (width <= 0 || height <= 0 || totalRows <= Math.max(0, visibleRows)) {
            return null;
        }
        return new Bounds(x, y, width, height);
    }

    record Bounds(int x, int y, int width, int height) {

        int right() {
            return x + width;
        }

        int bottom() {
            return y + height;
        }
    }
}
