package org.gtlcore.gtlcore.integration.ae2.pattern;

import org.gtlcore.gtlcore.integration.ae2.wireless.GTLWirelessAeContent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class PatternQuickUploadSelectionMenu extends AbstractContainerMenu {

    private ItemStack patternStack;
    private ItemStack cancelStack;
    private final List<Entry> entries;
    private boolean selected;
    private boolean uploaded;

    public PatternQuickUploadSelectionMenu(int containerId, Inventory inventory, FriendlyByteBuf data) {
        super(GTLWirelessAeContent.PATTERN_QUICK_UPLOAD_SELECTION_MENU.get(), containerId);
        this.patternStack = data.readItem();
        this.cancelStack = data.readItem();
        int size = data.readVarInt();
        this.entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            this.entries.add(new Entry(
                    ResourceKey.create(Registries.DIMENSION, data.readResourceLocation()),
                    data.readBlockPos(),
                    data.readComponent(),
                    data.readResourceLocation(),
                    data.readComponent()));
        }
    }

    public PatternQuickUploadSelectionMenu(int containerId, Inventory inventory, ItemStack patternStack,
                                           ItemStack cancelStack, List<Entry> entries) {
        super(GTLWirelessAeContent.PATTERN_QUICK_UPLOAD_SELECTION_MENU.get(), containerId);
        this.patternStack = patternStack.copy();
        this.cancelStack = cancelStack.copy();
        this.entries = List.copyOf(entries);
    }

    public static void open(ServerPlayer player, ItemStack patternStack, ItemStack cancelStack,
                            List<PatternQuickUploadService.Target> targets) {
        List<Entry> entries = targets.stream()
                .map(target -> new Entry(
                        target.levelKey(),
                        target.bufferPos(),
                        target.targetName(),
                        target.recipeTypeId(),
                        target.recipeTypeName()))
                .toList();

        NetworkHooks.openScreen(
                player,
                new MenuProvider() {

                    @Override
                    public @NotNull Component getDisplayName() {
                        return Component.translatable("screen.gtlcore.pattern_quick_upload_select");
                    }

                    @Override
                    public AbstractContainerMenu createMenu(int containerId, @NotNull Inventory inventory,
                                                            @NotNull Player menuPlayer) {
                        return new PatternQuickUploadSelectionMenu(containerId, inventory, patternStack, cancelStack, entries);
                    }
                },
                buffer -> write(buffer, patternStack, cancelStack, entries));
    }

    public void select(ServerPlayer player, int index) {
        if (index < 0 || index >= entries.size() || patternStack.isEmpty()) {
            return;
        }
        selected = true;
        Entry entry = entries.get(index);
        if (PatternQuickUploadService.insertIntoTarget(player, patternStack, new PatternQuickUploadService.Target(
                entry.levelKey(),
                entry.bufferPos(),
                entry.targetName(),
                entry.recipeTypeId(),
                entry.recipeTypeName()))) {
            uploaded = true;
            patternStack = ItemStack.EMPTY;
            cancelStack = ItemStack.EMPTY;
            player.displayClientMessage(Component.translatable("message.gtlcore.pattern_quick_upload_inserted", entry.targetName()), true);
            player.closeContainer();
        } else {
            player.displayClientMessage(Component.translatable("message.gtlcore.pattern_quick_upload_insert_failed"), true);
        }
    }

    public List<Entry> getEntries() {
        return entries;
    }

    public ItemStack getPatternStack() {
        return patternStack;
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return !player.isRemoved();
    }

    @Override
    public void removed(@NotNull Player player) {
        super.removed(player);
        if (!player.level().isClientSide && !uploaded && !patternStack.isEmpty()) {
            player.getInventory().placeItemBackInInventory(selected ? patternStack : cancelStack);
            patternStack = ItemStack.EMPTY;
            cancelStack = ItemStack.EMPTY;
        }
    }

    private static void write(FriendlyByteBuf buffer, ItemStack patternStack, ItemStack cancelStack,
                              List<Entry> entries) {
        buffer.writeItem(patternStack);
        buffer.writeItem(cancelStack);
        buffer.writeVarInt(entries.size());
        for (Entry entry : entries) {
            buffer.writeResourceLocation(entry.levelKey().location());
            buffer.writeBlockPos(entry.bufferPos());
            buffer.writeComponent(entry.targetName());
            buffer.writeResourceLocation(entry.recipeTypeId());
            buffer.writeComponent(entry.recipeTypeName());
        }
    }

    public record Entry(ResourceKey<Level> levelKey, BlockPos bufferPos, Component targetName, ResourceLocation recipeTypeId,
                        Component recipeTypeName) {}
}
