package org.gtlcore.gtlcore.mixin.ae2.crafting;

import org.gtlcore.gtlcore.integration.ae2.crafting.CraftingTemplateHelper;
import org.gtlcore.gtlcore.utils.NumberUtils;

import net.minecraft.world.level.Level;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.execution.InputTemplate;
import appeng.crafting.inv.ICraftingInventory;
import com.google.common.collect.Iterables;
import org.spongepowered.asm.mixin.*;

@Mixin(CraftingCpuHelper.class)
public abstract class CraftingCpuHelperMixin {

    /**
     * @author .
     * @reason 提升性能
     */
    @Overwrite(remap = false)
    public static Iterable<InputTemplate> getValidItemTemplates(ICraftingInventory inv,
                                                                IPatternDetails.IInput input, Level level) {
        var substitutes = CraftingTemplateHelper.loadRawCandidates(inv, input.getPossibleInputs());
        return Iterables.filter(substitutes, stack -> input.isValid(stack.key(), level));
    }

    /**
     * @author .
     * @reason Keep grouped template extraction representable for very large crafting requests.
     */
    @Overwrite(remap = false)
    public static long extractTemplates(ICraftingInventory inv, InputTemplate template, long requestedAmount) {
        long requestedItems = NumberUtils.saturatedMultiply(template.amount(), requestedAmount);
        long availableItems = inv.extract(template.key(), requestedItems, Actionable.SIMULATE);
        if (availableItems == 0) {
            return 0;
        }

        long availableTemplates = availableItems / template.amount();
        long extractedItems = NumberUtils.saturatedMultiply(template.amount(), availableTemplates);
        if (extractedItems == 0) {
            return 0;
        }

        long extracted = inv.extract(template.key(), extractedItems, Actionable.MODULATE);
        if (extracted != extractedItems) {
            throw new IllegalStateException("Failed to correctly extract whole number. Invalid simulation!");
        }
        return availableTemplates;
    }
}
