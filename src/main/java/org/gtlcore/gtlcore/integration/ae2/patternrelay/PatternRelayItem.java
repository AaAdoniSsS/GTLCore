package org.gtlcore.gtlcore.integration.ae2.patternrelay;

import org.gtlcore.gtlcore.GTLCore;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import appeng.items.parts.PartItem;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class PatternRelayItem extends PartItem<PatternRelayPart> {

    public static final ResourceLocation ACCESS_MODEL_PROPERTY = GTLCore.id("pattern_relay_access");

    public PatternRelayItem(Item.Properties properties) {
        super(properties, PatternRelayPart.class, PatternRelayPart::new);
    }

    public static float getAccessModelProperty(ItemStack stack) {
        return PatternRelayPart.isAccessMode(stack) ? 1.0F : 0.0F;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.gtlcore.me_pattern_relay.description").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.gtlcore.me_pattern_relay.wrench").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                "tooltip.gtlcore.me_pattern_relay.mode",
                Component.translatable(PatternRelayPart.getModeNameTranslationKey(stack)))
                .withStyle(ChatFormatting.AQUA));
    }
}
