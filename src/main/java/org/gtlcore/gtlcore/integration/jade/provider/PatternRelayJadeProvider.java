package org.gtlcore.gtlcore.integration.jade.provider;

import org.gtlcore.gtlcore.GTLCore;
import org.gtlcore.gtlcore.integration.ae2.patternrelay.PatternRelayPart;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import appeng.blockentity.networking.CableBusBlockEntity;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public final class PatternRelayJadeProvider implements IBlockComponentProvider {

    private static final ResourceLocation UID = GTLCore.id("me_pattern_relay_mode");
    private static final String MODE_TOOLTIP_KEY = "tooltip.gtlcore.me_pattern_relay.mode";

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!(accessor.getBlockEntity() instanceof CableBusBlockEntity cableBus)) {
            return;
        }

        BlockHitResult hitResult = accessor.getHitResult();
        if (hitResult == null) {
            return;
        }

        Vec3 hitInBlock = hitResult.getLocation().subtract(Vec3.atLowerCornerOf(accessor.getPosition()));
        if (cableBus.getCableBus().selectPartLocal(hitInBlock).part instanceof PatternRelayPart relay) {
            tooltip.add(Component.translatable(
                    MODE_TOOLTIP_KEY,
                    Component.translatable(relay.getModeNameTranslationKey()))
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
