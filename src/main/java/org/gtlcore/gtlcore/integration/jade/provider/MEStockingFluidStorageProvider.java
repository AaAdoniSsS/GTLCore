package org.gtlcore.gtlcore.integration.jade.provider;

import org.gtlcore.gtlcore.GTLCore;
import org.gtlcore.gtlcore.integration.jade.MEStockingFluidJadeHelper;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import snownee.jade.api.Accessor;
import snownee.jade.api.view.ClientViewGroup;
import snownee.jade.api.view.FluidView;
import snownee.jade.api.view.IClientExtensionProvider;
import snownee.jade.api.view.IServerExtensionProvider;
import snownee.jade.api.view.ViewGroup;

import java.util.List;

public enum MEStockingFluidStorageProvider implements IServerExtensionProvider<MetaMachineBlockEntity, CompoundTag>, IClientExtensionProvider<CompoundTag, FluidView> {

    INSTANCE;

    private static final ResourceLocation UID = GTLCore.id("me_stocking_fluid_storage");
    private static final int PRIORITY = -10;

    @Override
    public List<ViewGroup<CompoundTag>> getGroups(ServerPlayer player, ServerLevel level,
                                                  MetaMachineBlockEntity blockEntity, boolean showDetails) {
        return MEStockingFluidJadeHelper.createGroups(blockEntity.getMetaMachine());
    }

    @Override
    public List<ClientViewGroup<FluidView>> getClientGroups(Accessor<?> accessor,
                                                            List<ViewGroup<CompoundTag>> groups) {
        return MEStockingFluidJadeHelper.createClientGroups(groups);
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
