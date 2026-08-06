package org.gtlcore.gtlcore.client.gtmt;

import org.gtlcore.gtlcore.client.renderer.BlockHighlightHandler;
import org.gtlcore.gtlcore.integration.gtmt.WirelessEnergyLocator;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class WirelessEnergyLocatorClient {

    private WirelessEnergyLocatorClient() {}

    public static void highlight(WirelessEnergyLocator.Target target) {
        BlockHighlightHandler.highlight(
                target.position(),
                target.dimension(),
                System.currentTimeMillis() + WirelessEnergyLocator.HIGHLIGHT_DURATION_MILLIS);
    }
}
