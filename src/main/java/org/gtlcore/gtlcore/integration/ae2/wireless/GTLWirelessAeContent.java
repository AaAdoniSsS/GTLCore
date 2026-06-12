package org.gtlcore.gtlcore.integration.ae2.wireless;

import org.gtlcore.gtlcore.GTLCore;
import org.gtlcore.gtlcore.common.data.GTLCreativeModeTabs;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class GTLWirelessAeContent {

    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, GTLCore.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, GTLCore.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, GTLCore.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(ForgeRegistries.MENU_TYPES, GTLCore.MOD_ID);

    public static final RegistryObject<Block> WIRELESS_NETWORK_CORE = BLOCKS.register(
            "wireless_network_core",
            WirelessNetworkCoreBlock::new);

    public static final RegistryObject<Item> WIRELESS_NETWORK_CORE_ITEM = ITEMS.register(
            "wireless_network_core",
            () -> new BlockItem(WIRELESS_NETWORK_CORE.get(), new Item.Properties()));

    public static final RegistryObject<Block> WIRELESS_NETWORK_BOOKMARK = BLOCKS.register(
            "wireless_network_bookmark",
            WirelessNetworkBookmarkBlock::new);

    public static final RegistryObject<Item> WIRELESS_NETWORK_BOOKMARK_ITEM = ITEMS.register(
            "wireless_network_bookmark",
            () -> new BlockItem(WIRELESS_NETWORK_BOOKMARK.get(), new Item.Properties()));

    public static final RegistryObject<BlockEntityType<WirelessNetworkCoreBlockEntity>> WIRELESS_NETWORK_CORE_BE = BLOCK_ENTITY_TYPES.register(
            "wireless_network_core",
            () -> BlockEntityType.Builder.of(
                    WirelessNetworkCoreBlockEntity::new,
                    WIRELESS_NETWORK_CORE.get())
                    .build(null));

    public static final RegistryObject<BlockEntityType<WirelessNetworkBookmarkBlockEntity>> WIRELESS_NETWORK_BOOKMARK_BE = BLOCK_ENTITY_TYPES.register(
            "wireless_network_bookmark",
            () -> BlockEntityType.Builder.of(
                    WirelessNetworkBookmarkBlockEntity::new,
                    WIRELESS_NETWORK_BOOKMARK.get())
                    .build(null));

    public static final RegistryObject<MenuType<WirelessNetworkCoreMenu>> WIRELESS_NETWORK_CORE_MENU = MENU_TYPES.register(
            "wireless_network_core",
            () -> IForgeMenuType.create(WirelessNetworkCoreMenu::new));

    public static final RegistryObject<MenuType<WirelessNetworkBookmarkMenu>> WIRELESS_NETWORK_BOOKMARK_MENU = MENU_TYPES.register(
            "wireless_network_bookmark",
            () -> IForgeMenuType.create(WirelessNetworkBookmarkMenu::new));

    public static final RegistryObject<MenuType<WirelessAeTargetMenu>> WIRELESS_AE_TARGET_MENU = MENU_TYPES.register(
            "wireless_ae_target",
            () -> IForgeMenuType.create(WirelessAeTargetMenu::new));

    private GTLWirelessAeContent() {}

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITY_TYPES.register(modBus);
        MENU_TYPES.register(modBus);
        modBus.addListener(GTLWirelessAeContent::addCreativeTabItems);
    }

    private static void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {
        if (CreativeModeTabs.FUNCTIONAL_BLOCKS.equals(event.getTabKey()) || GTLCreativeModeTabs.GTL_CORE.getKey().equals(event.getTabKey())) {
            event.accept(WIRELESS_NETWORK_CORE_ITEM);
            event.accept(WIRELESS_NETWORK_BOOKMARK_ITEM);
        }
    }
}
