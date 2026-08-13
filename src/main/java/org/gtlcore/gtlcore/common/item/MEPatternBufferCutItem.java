package org.gtlcore.gtlcore.common.item;

import com.gregtechceu.gtceu.api.item.ComponentItem;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class MEPatternBufferCutItem extends ComponentItem {

    public MEPatternBufferCutItem(Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        Component name = super.getName(stack);
        if (!MEPatternBufferCutBehavior.hasCutData(stack)) {
            return name;
        }
        return name.copy().append(Component.translatable("item.gtlcore.me_pattern_buffer_cut.has_content")
                .withStyle(ChatFormatting.GOLD));
    }
}
