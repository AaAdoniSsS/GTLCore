package org.gtlcore.gtlcore.client.preview;

/** Pure Java checks; the client renderer and real inventory routes also require in-game validation. */
public final class MultiblockPreviewTest {

    public static void main(String[] args) throws Exception {
        int assertions = PreviewControlsRegression.run() + PreviewPerformanceRegression.run();
        System.out.println("Multiblock preview regression assertions=" + assertions);
    }
}
