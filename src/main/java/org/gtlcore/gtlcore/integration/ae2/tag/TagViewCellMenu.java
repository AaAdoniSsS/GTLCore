package org.gtlcore.gtlcore.integration.ae2.tag;

import org.gtlcore.gtlcore.integration.ae2.wireless.GTLWirelessAeContent;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkHooks;

import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.menu.AEBaseMenu;
import appeng.menu.locator.MenuLocator;
import appeng.menu.locator.MenuLocators;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class TagViewCellMenu extends AEBaseMenu {

    private static final String SAVE_FILTER_ACTION = "save_tag_filter";

    private final @Nullable ItemMenuHost host;
    private final String initialWhitelist;
    private final String initialBlacklist;

    public static TagViewCellMenu createClientMenu(int containerId, Inventory inventory, FriendlyByteBuf data) {
        MenuLocator locator = MenuLocators.readFromPacket(data);
        ItemMenuHost host = locator.locate(inventory.player, ItemMenuHost.class);
        return new TagViewCellMenu(
                GTLWirelessAeContent.TAG_VIEW_CELL_MENU.get(),
                containerId,
                inventory,
                host,
                locator);
    }

    private TagViewCellMenu(MenuType<?> menuType, int containerId, Inventory inventory,
                            @Nullable ItemMenuHost host, @Nullable MenuLocator locator) {
        super(menuType, containerId, inventory, host);
        this.host = host;
        ItemStack stack = host == null ? ItemStack.EMPTY : host.getItemStack();
        this.initialWhitelist = TagViewCellItem.getWhitelist(stack);
        this.initialBlacklist = TagViewCellItem.getBlacklist(stack);
        registerClientAction(SAVE_FILTER_ACTION, FilterConfig.class, this::applyFilter);
        if (locator != null) {
            setLocator(locator);
        }
    }

    public static boolean open(Player player, MenuLocator locator, boolean returningFromSubmenu) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        ItemMenuHost host = locator.locate(player, ItemMenuHost.class);
        if (host == null || !TagViewCellItem.isTagViewCell(host.getItemStack())) {
            return false;
        }

        NetworkHooks.openScreen(
                serverPlayer,
                new MenuProvider() {

                    @Override
                    public @NotNull Component getDisplayName() {
                        return Component.translatable("screen.gtlcore.tag_view_cell");
                    }

                    @Override
                    public AbstractContainerMenu createMenu(int containerId, @NotNull Inventory inventory,
                                                            @NotNull Player menuPlayer) {
                        return new TagViewCellMenu(
                                GTLWirelessAeContent.TAG_VIEW_CELL_MENU.get(),
                                containerId,
                                inventory,
                                host,
                                locator);
                    }
                },
                buffer -> MenuLocators.writeToPacket(buffer, locator));
        return true;
    }

    public String getInitialWhitelist() {
        return initialWhitelist;
    }

    public String getInitialBlacklist() {
        return initialBlacklist;
    }

    public void saveFilter(String whitelist, String blacklist) {
        sendClientAction(
                SAVE_FILTER_ACTION,
                new FilterConfig(
                        TagViewCellItem.normalizeExpression(whitelist),
                        TagViewCellItem.normalizeExpression(blacklist)));
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return host != null && TagViewCellItem.isTagViewCell(host.getItemStack()) && super.stillValid(player);
    }

    private void applyFilter(FilterConfig config) {
        if (host == null || !TagViewCellItem.isTagViewCell(host.getItemStack())) {
            return;
        }
        TagViewCellItem.setExpressions(host.getItemStack(), config.whitelist, config.blacklist);
        host.getPlayer().getInventory().setChanged();
    }

    public record FilterConfig(String whitelist, String blacklist) {}
}
