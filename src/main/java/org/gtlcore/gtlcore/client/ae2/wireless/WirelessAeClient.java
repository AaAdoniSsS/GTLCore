package org.gtlcore.gtlcore.client.ae2.wireless;

import org.gtlcore.gtlcore.integration.ae2.wireless.GTLWirelessAeContent;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

public final class WirelessAeClient {

    private WirelessAeClient() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(WirelessAeClient::onClientSetup);
        modBus.addListener(WirelessNetworkCoreRenderer::registerAdditionalModels);
        WirelessAeScreenHooks.register();
    }

    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(GTLWirelessAeContent.WIRELESS_NETWORK_BOOKMARK.get(), RenderType.cutout());
            MenuScreens.register(GTLWirelessAeContent.WIRELESS_NETWORK_CORE_MENU.get(), WirelessNetworkCoreScreen::new);
            MenuScreens.register(GTLWirelessAeContent.WIRELESS_NETWORK_BOOKMARK_MENU.get(), WirelessNetworkBookmarkScreen::new);
            MenuScreens.register(GTLWirelessAeContent.WIRELESS_AE_TARGET_MENU.get(), WirelessAeTargetScreen::new);
        });
    }
}
