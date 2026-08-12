package org.gtlcore.gtlcore.integration.jade.provider;

import org.gtlcore.gtlcore.GTLCore;
import org.gtlcore.gtlcore.common.machine.trait.QuantumChestLongStorage;
import org.gtlcore.gtlcore.utils.NumberUtils;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import snownee.jade.api.Accessor;
import snownee.jade.api.view.ClientViewGroup;
import snownee.jade.api.view.IClientExtensionProvider;
import snownee.jade.api.view.IServerExtensionProvider;
import snownee.jade.api.view.ItemView;
import snownee.jade.api.view.ViewGroup;

import java.util.Collections;
import java.util.List;

public enum QuantumChestItemStorageProvider implements IServerExtensionProvider<MetaMachineBlockEntity, ItemStack>, IClientExtensionProvider<ItemStack, ItemView> {

    INSTANCE;

    private static final ResourceLocation UID = GTLCore.id("quantum_chest_item_storage");
    private static final String LONG_AMOUNT_TAG = "GTLCoreQuantumChestLongAmount";
    private static final int PRIORITY = 10;

    @Override
    public List<ViewGroup<ItemStack>> getGroups(ServerPlayer player, ServerLevel level,
                                                MetaMachineBlockEntity blockEntity, boolean showDetails) {
        if (!(blockEntity.getMetaMachine() instanceof QuantumChestLongStorage storage)) return null;

        ItemStack stack = storage.gtlcore$getStoredStack();
        long amount = storage.gtlcore$getStoredAmount();
        if (stack.isEmpty() || amount <= 0L) return Collections.emptyList();

        stack = stack.copy();
        stack.setCount(1);
        stack.getOrCreateTag().putLong(LONG_AMOUNT_TAG, amount);
        return List.of(new ViewGroup<>(List.of(stack)));
    }

    @Override
    public List<ClientViewGroup<ItemView>> getClientGroups(Accessor<?> accessor, List<ViewGroup<ItemStack>> groups) {
        return ClientViewGroup.map(groups, QuantumChestItemStorageProvider::createItemView, null);
    }

    private static ItemView createItemView(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        long amount = tag != null && tag.contains(LONG_AMOUNT_TAG, Tag.TAG_LONG) ? tag.getLong(LONG_AMOUNT_TAG) : 0L;
        ItemStack displayStack = stack.copy();
        CompoundTag displayTag = displayStack.getTag();
        if (displayTag != null) {
            displayTag.remove(LONG_AMOUNT_TAG);
            if (displayTag.isEmpty()) displayStack.setTag(null);
        }
        return new ItemView(displayStack, NumberUtils.formatLong(amount));
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public int getDefaultPriority() {
        return PRIORITY;
    }
}
