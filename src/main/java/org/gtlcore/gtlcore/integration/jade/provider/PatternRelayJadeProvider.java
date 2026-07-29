package org.gtlcore.gtlcore.integration.jade.provider;

import org.gtlcore.gtlcore.GTLCore;
import org.gtlcore.gtlcore.integration.ae2.patternrelay.PatternRelayPart;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.util.SettingsFrom;
import org.jetbrains.annotations.Nullable;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.IElement;
import snownee.jade.api.ui.IElementHelper;

public final class PatternRelayJadeProvider implements IBlockComponentProvider {

    private static final ResourceLocation UID = GTLCore.id("me_pattern_relay_mode");
    private static final String MODE_TOOLTIP_KEY = "tooltip.gtlcore.me_pattern_relay.mode";

    @Override
    public IElement getIcon(BlockAccessor accessor, IPluginConfig config, IElement defaultIcon) {
        PatternRelayPart relay = getTargetedRelay(accessor);
        if (relay == null) {
            return defaultIcon;
        }

        ItemStack stack = new ItemStack(relay.getPartItem());
        CompoundTag settings = new CompoundTag();
        relay.exportSettings(SettingsFrom.DISMANTLE_ITEM, settings);
        if (!settings.isEmpty()) {
            stack.setTag(settings);
        }
        return IElementHelper.get().item(stack);
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        PatternRelayPart relay = getTargetedRelay(accessor);
        if (relay != null) {
            tooltip.add(Component.translatable(
                    MODE_TOOLTIP_KEY,
                    Component.translatable(relay.getModeNameTranslationKey()))
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    private static @Nullable PatternRelayPart getTargetedRelay(BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof CableBusBlockEntity cableBus)) {
            return null;
        }

        BlockHitResult hitResult = accessor.getHitResult();
        if (hitResult == null) {
            return null;
        }

        Vec3 hitInBlock = hitResult.getLocation().subtract(Vec3.atLowerCornerOf(accessor.getPosition()));
        return cableBus.getCableBus().selectPartLocal(hitInBlock).part instanceof PatternRelayPart relay ? relay : null;
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
