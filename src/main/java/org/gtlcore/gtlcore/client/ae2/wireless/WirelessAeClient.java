package org.gtlcore.gtlcore.client.ae2.wireless;

import org.gtlcore.gtlcore.integration.ae2.wireless.GTLWirelessAeContent;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import appeng.api.util.AEColor;
import appeng.client.render.StaticItemColor;
import appeng.init.client.InitScreens;

public final class WirelessAeClient {

    private WirelessAeClient() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(WirelessAeClient::onClientSetup);
        modBus.addListener(WirelessAeClient::onRegisterItemColors);
        modBus.addListener(WirelessNetworkCoreRenderer::registerAdditionalModels);
        WirelessAeScreenHooks.register();
    }

    private static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        event.register(
                new StaticItemColor(AEColor.TRANSPARENT),
                GTLWirelessAeContent.THROUGHPUT_MONITOR_TERMINAL.get());
    }

    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(GTLWirelessAeContent.WIRELESS_NETWORK_BOOKMARK.get(), RenderType.cutout());
            MenuScreens.register(GTLWirelessAeContent.WIRELESS_NETWORK_CORE_MENU.get(), WirelessNetworkCoreScreen::new);
            MenuScreens.register(GTLWirelessAeContent.WIRELESS_NETWORK_BOOKMARK_MENU.get(), WirelessNetworkBookmarkScreen::new);
            MenuScreens.register(GTLWirelessAeContent.WIRELESS_AE_TARGET_MENU.get(), WirelessAeTargetScreen::new);
            MenuScreens.register(GTLWirelessAeContent.PATTERN_QUICK_UPLOAD_SELECTION_MENU.get(), PatternQuickUploadSelectionScreen::new);
            InitScreens.register(
                    GTLWirelessAeContent.TAG_VIEW_CELL_MENU.get(),
                    TagViewCellScreen::new,
                    "/screens/tag_view_cell.json");
            MenuScreens.register(GTLWirelessAeContent.THROUGHPUT_MONITOR_TERMINAL_MENU.get(), ThroughputMonitorTerminalScreen::new);
            MenuScreens.register(GTLWirelessAeContent.WIRELESS_THROUGHPUT_MONITOR_TERMINAL_MENU.get(), ThroughputMonitorTerminalScreen::new);
        });
    }
}
