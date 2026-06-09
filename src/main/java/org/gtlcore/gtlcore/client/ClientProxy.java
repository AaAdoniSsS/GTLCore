package org.gtlcore.gtlcore.client;

import org.gtlcore.gtlcore.common.CommonProxy;
import org.gtlcore.gtlcore.client.ae2.wireless.WirelessAeClient;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@OnlyIn(Dist.CLIENT)
public class ClientProxy extends CommonProxy {

    public ClientProxy() {
        super();
        init();
    }

    public static void init() {
        CraftingUnitModelProvider.initCraftingUnitModels();
        WirelessAeClient.register(FMLJavaModLoadingContext.get().getModEventBus());
    }
}
