package org.gtlcore.gtlcore.integration.ae2.crafting;

import org.gtlcore.gtlcore.api.machine.trait.AECraft.IMECraftIOPart;
import org.gtlcore.gtlcore.api.machine.trait.MEPart.IMEPatternPartMachine;
import org.gtlcore.gtlcore.config.ConfigHolder;

import appeng.api.networking.crafting.ICraftingProvider;
import appeng.helpers.patternprovider.PatternProviderLogic;

public final class CraftingPatternAutoExpand {

    private CraftingPatternAutoExpand() {}

    public static boolean canAutoExpand(boolean processingPattern, ICraftingProvider provider) {
        if (!processingPattern) {
            return false;
        }
        if (provider instanceof IMEPatternPartMachine || provider instanceof IMECraftIOPart) {
            return true;
        }
        return isPatternProviderAutoExpandEnabled() && provider instanceof PatternProviderLogic;
    }

    private static boolean isPatternProviderAutoExpandEnabled() {
        return ConfigHolder.INSTANCE != null && ConfigHolder.INSTANCE.enableAe2PatternProviderAutoExpand;
    }
}
