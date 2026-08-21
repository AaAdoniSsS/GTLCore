package org.gtlcore.gtlcore.mixin.gtm.api.recipe;

import org.gtlcore.gtlcore.api.recipe.IRecipeChanceDisplay;

import com.gregtechceu.gtceu.api.recipe.chance.boost.ChanceBoostFunction;
import com.gregtechceu.gtceu.api.recipe.chance.logic.ChanceLogic;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import com.lowdragmc.lowdraglib.utils.LocalizationUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Content.class)
public abstract class ContentMixin implements IRecipeChanceDisplay {

    @Shadow(remap = false)
    public int chance;
    @Shadow(remap = false)
    public int maxChance;
    @Shadow(remap = false)
    public int tierChanceBoost;
    @Unique
    private boolean gtlcore$hasChanceDisplayTiers;
    @Unique
    private int gtlcore$recipeTier;
    @Unique
    private int gtlcore$selectedTier;

    @Override
    public void gtlcore$setChanceDisplayTiers(int recipeTier, int selectedTier) {
        gtlcore$hasChanceDisplayTiers = true;
        gtlcore$recipeTier = recipeTier;
        gtlcore$selectedTier = selectedTier;
    }

    @Override
    public int gtlcore$getDisplayChance() {
        return Math.max(0, gtlcore$getUnclampedDisplayChance());
    }

    @Override
    public int gtlcore$getUnclampedDisplayChance() {
        if (!gtlcore$hasChanceDisplayTiers) return chance;
        return ChanceBoostFunction.OVERCLOCK.getBoostedChance((Content) (Object) this, gtlcore$recipeTier,
                gtlcore$selectedTier);
    }

    @Override
    public int gtlcore$getChanceBoostTierCount() {
        if (tierChanceBoost == 0) return 0;
        return Math.max(0, (gtlcore$getUnclampedDisplayChance() - chance) / tierChanceBoost);
    }

    /**
     * @author .
     * @reason 修改显示不消耗字符位置
     */
    @OnlyIn(Dist.CLIENT)
    @Overwrite(remap = false)
    public void drawChance(GuiGraphics graphics, float x, float y, int width, int height) {
        if (this.chance != ChanceLogic.getMaxChancedValue() || this.tierChanceBoost != 0) {
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, 400.0F);
            graphics.pose().scale(0.5F, 0.5F, 1.0F);
            float chance = 100.0F * (float) gtlcore$getDisplayChance() / (float) this.maxChance;
            String percent = FormattingUtil.formatPercent(chance);
            String s = chance == 0.0F ? LocalizationUtils.format("gtceu.gui.content.chance_0_short") : percent + "%";
            int color = chance == 0.0F ? 16711680 : 16776960;
            Font fontRenderer = Minecraft.getInstance().font;
            int xDraw = (int) ((x + (float) width / 3.0F) * 2.0F - (float) fontRenderer.width(s) + 23.0F) - (chance == 0.0F ? 10 : 0);
            int yDraw = (int) ((y + (float) height / 3.0F + 6.0F) * 2.0F - (float) height) - (chance == 0.0F ? 3 : 0);
            graphics.drawString(fontRenderer, s, xDraw, yDraw, color, true);
            graphics.pose().popPose();
        }
    }
}
