package org.gtlcore.gtlcore.integration.jade.provider;

import org.gtlcore.gtlcore.GTLCore;
import org.gtlcore.gtlcore.integration.ae2.wireless.WirelessAeNetworkRuntime;
import org.gtlcore.gtlcore.integration.ae2.wireless.WirelessAeSavedData;
import org.gtlcore.gtlcore.integration.ae2.wireless.WirelessNetworkCoreBlockEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.util.UUID;

public class WirelessAeNetworkProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {

    private static final ResourceLocation UID = GTLCore.id("wireless_ae_network_provider");
    private static final String TAG_PRESENT = "gtlcore_wireless_ae";
    private static final String TAG_CORE = "wireless_ae_core";
    private static final String TAG_NETWORK_NAME = "wireless_ae_network_name";

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor blockAccessor, IPluginConfig config) {
        CompoundTag serverData = blockAccessor.getServerData();
        if (!serverData.getBoolean(TAG_PRESENT)) {
            return;
        }

        if (serverData.getBoolean(TAG_CORE)) {
            tooltip.add(Component.translatable(
                    "tooltip.gtlcore.wireless_ae.core_connected",
                    serverData.getString(TAG_NETWORK_NAME))
                    .withStyle(ChatFormatting.GREEN));
            return;
        }

        tooltip.add(Component.translatable(
                "tooltip.gtlcore.wireless_ae.target_connected",
                serverData.getString(TAG_NETWORK_NAME))
                .withStyle(ChatFormatting.GREEN));
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor blockAccessor) {
        Level level = blockAccessor.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        BlockEntity blockEntity = blockAccessor.getBlockEntity();
        if (blockEntity instanceof WirelessNetworkCoreBlockEntity core) {
            appendCoreData(data, serverLevel, core);
            return;
        }

        BlockPos targetPos = WirelessAeNetworkRuntime.resolveWirelessTargetPos(serverLevel, blockAccessor.getPosition());
        if (!WirelessAeNetworkRuntime.isWirelessMeTarget(serverLevel, targetPos)) {
            return;
        }

        BlockHitResult hitResult = blockAccessor.getHitResult();
        Vec3 hitLocation = hitResult == null ? null : hitResult.getLocation();
        WirelessAeSavedData.MemberKey target = WirelessAeNetworkRuntime.resolveWirelessTarget(
                serverLevel,
                blockAccessor.getPosition(),
                blockAccessor.getSide(),
                hitLocation);
        WirelessAeSavedData savedData = WirelessAeSavedData.get(serverLevel.getServer());
        UUID frequency = WirelessAeNetworkRuntime.findConnectedNetworkFrequency(serverLevel.getServer(), target);
        if (frequency == null) {
            return;
        }

        data.putBoolean(TAG_PRESENT, true);
        data.putBoolean(TAG_CORE, false);
        data.putString(TAG_NETWORK_NAME, savedData.getNetworkName(frequency));
    }

    private static void appendCoreData(CompoundTag data, ServerLevel level, WirelessNetworkCoreBlockEntity core) {
        if (!core.isLinkedToAeNetwork()) {
            return;
        }

        WirelessAeSavedData savedData = WirelessAeSavedData.get(level.getServer());
        UUID frequency = core.getFrequency();
        data.putBoolean(TAG_PRESENT, true);
        data.putBoolean(TAG_CORE, true);
        data.putString(TAG_NETWORK_NAME, savedData.getNetworkName(frequency));
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
