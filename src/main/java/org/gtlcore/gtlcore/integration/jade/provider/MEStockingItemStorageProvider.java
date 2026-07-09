package org.gtlcore.gtlcore.integration.jade.provider;

import org.gtlcore.gtlcore.GTLCore;
import org.gtlcore.gtlcore.integration.jade.MEStockingItemJadeHelper;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;

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

import java.util.List;

public enum MEStockingItemStorageProvider implements IServerExtensionProvider<MetaMachineBlockEntity, ItemStack>, IClientExtensionProvider<ItemStack, ItemView> {

    INSTANCE;

    private static final ResourceLocation UID = GTLCore.id("me_stocking_item_storage");
    private static final int PRIORITY = -10;

    @Override
    public List<ViewGroup<ItemStack>> getGroups(ServerPlayer player, ServerLevel level,
                                                MetaMachineBlockEntity blockEntity, boolean showDetails) {
        return MEStockingItemJadeHelper.createGroups(blockEntity.getMetaMachine());
    }

    @Override
    public List<ClientViewGroup<ItemView>> getClientGroups(Accessor<?> accessor, List<ViewGroup<ItemStack>> groups) {
        return MEStockingItemJadeHelper.createClientGroups(groups);
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
