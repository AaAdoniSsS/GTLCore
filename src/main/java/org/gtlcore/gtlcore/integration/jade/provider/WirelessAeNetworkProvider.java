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
    private static final String TAG_BOUND = "wireless_ae_bound";
    private static final String TAG_CONNECTED = "wireless_ae_connected";
    private static final String TAG_NETWORK_NAME = "wireless_ae_network_name";

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor blockAccessor, IPluginConfig config) {
        CompoundTag serverData = blockAccessor.getServerData();
        if (!serverData.getBoolean(TAG_PRESENT)) {
            return;
        }

        boolean connected = serverData.getBoolean(TAG_CONNECTED);
        if (serverData.getBoolean(TAG_CORE)) {
            tooltip.add(Component.translatable(connected ?
                    "tooltip.gtlcore.wireless_ae.core_connected" :
                    "tooltip.gtlcore.wireless_ae.core_disconnected",
                    serverData.getString(TAG_NETWORK_NAME))
                    .withStyle(connected ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
            return;
        }

        if (!serverData.getBoolean(TAG_BOUND)) {
            tooltip.add(Component.translatable("tooltip.gtlcore.wireless_ae.target_unbound")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }

        tooltip.add(Component.translatable(connected ?
                "tooltip.gtlcore.wireless_ae.target_connected" :
                "tooltip.gtlcore.wireless_ae.target_waiting",
                serverData.getString(TAG_NETWORK_NAME))
                .withStyle(connected ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
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
        UUID frequency = savedData.getMemberNetwork(target);
        boolean connected = false;

        if (frequency != null) {
            connected = WirelessAeNetworkRuntime.isMemberConnected(serverLevel.getServer(), frequency, target);
        } else {
            frequency = WirelessAeNetworkRuntime.findWiredNetworkFrequency(serverLevel.getServer(), target);
            connected = frequency != null;
        }

        data.putBoolean(TAG_PRESENT, true);
        data.putBoolean(TAG_CORE, false);
        data.putBoolean(TAG_BOUND, frequency != null);
        if (frequency == null) {
            data.putBoolean(TAG_CONNECTED, false);
            return;
        }

        data.putBoolean(TAG_CONNECTED, connected);
        data.putString(TAG_NETWORK_NAME, savedData.getNetworkName(frequency));
    }

    private static void appendCoreData(CompoundTag data, ServerLevel level, WirelessNetworkCoreBlockEntity core) {
        WirelessAeSavedData savedData = WirelessAeSavedData.get(level.getServer());
        UUID frequency = core.getFrequency();
        data.putBoolean(TAG_PRESENT, true);
        data.putBoolean(TAG_CORE, true);
        data.putBoolean(TAG_BOUND, true);
        data.putBoolean(TAG_CONNECTED, core.isLinkedToAeNetwork());
        data.putString(TAG_NETWORK_NAME, savedData.getNetworkName(frequency));
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
