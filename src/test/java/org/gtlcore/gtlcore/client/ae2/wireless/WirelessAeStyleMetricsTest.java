package org.gtlcore.gtlcore.client.ae2.wireless;

public final class WirelessAeStyleMetricsTest {

    private WirelessAeStyleMetricsTest() {}

    public static void main(String[] args) {
        fillsPanelBackgroundOverflowWithoutTilingTextureEdge();
        exposesScrollbarTrackBoundsOnlyWhenScrollbarIsNeeded();
    }

    private static void fillsPanelBackgroundOverflowWithoutTilingTextureEdge() {
        int textureSize = 256;

        expectEquals(256, WirelessAeStyleMetrics.panelBackgroundTextureLength(260, textureSize), "texture length");
        expectEquals(4, WirelessAeStyleMetrics.panelBackgroundOverflowLength(260, textureSize), "overflow length");
        expectEquals(172, WirelessAeStyleMetrics.panelBackgroundTextureLength(172, textureSize), "short texture length");
        expectEquals(0, WirelessAeStyleMetrics.panelBackgroundOverflowLength(172, textureSize), "short overflow length");
    }

    private static void exposesScrollbarTrackBoundsOnlyWhenScrollbarIsNeeded() {
        WirelessAeStyleMetrics.Bounds bounds = WirelessAeStyleMetrics.scrollbarTrackBounds(
                20, 40, 6, 102, 5, 4);

        expectEquals(new WirelessAeStyleMetrics.Bounds(20, 40, 6, 102), bounds, "scrollbar track");
        expectEquals(null, WirelessAeStyleMetrics.scrollbarTrackBounds(20, 40, 6, 102, 4, 4),
                "hidden scrollbar track");
    }

    private static void expectEquals(Object expected, Object actual, String label) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(label + ": expected " + expected + " but got " + actual);
        }
    }
}
