package org.gtlcore.gtlcore.integration.jei;

import org.gtlcore.gtlcore.GTLCore;
import org.gtlcore.gtlcore.common.data.machines.MultiBlockMachineA;

import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.item.IntCircuitBehaviour;

import com.lowdragmc.lowdraglib.LDLib;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

@JeiPlugin
public class GTLJEIPlugin implements IModPlugin {

    @Override
    public @NotNull ResourceLocation getPluginUid() {
        return GTLCore.id("jei_plugin");
    }

    @Override
    public void registerRecipeCatalysts(@NotNull IRecipeCatalystRegistration registration) {
        if (LDLib.isReiLoaded() || LDLib.isEmiLoaded()) return;
        registration.addRecipeCatalyst(MultiBlockMachineA.ADVANCED_MULTI_SMELTER.asStack(), RecipeTypes.SMELTING);
        registration.addRecipeCatalyst(MultiBlockMachineA.DIMENSIONALLY_TRANSCENDENT_STEAM_OVEN.asStack(), RecipeTypes.SMELTING);
    }

    @Override
    public void registerItemSubtypes(@NotNull ISubtypeRegistration registration) {
        if (LDLib.isReiLoaded() || LDLib.isEmiLoaded()) return;
        registration.useNbtForSubtypes(GTItems.TURBINE_ROTOR.asItem());
        registration.useNbtForSubtypes(GTItems.INTEGRATED_CIRCUIT.asItem());
    }

    @Override
    public void onRuntimeAvailable(@NotNull IJeiRuntime runtime) {
        JeiMeInventoryTooltip.onRuntimeAvailable(runtime);
        var list = new ArrayList<ItemStack>();
        for (int i = 1; i <= 32; i++) {
            list.add(IntCircuitBehaviour.stack(i));
        }
        runtime.getIngredientManager().addIngredientsAtRuntime(VanillaTypes.ITEM_STACK, list);
    }

    @Override
    public void onRuntimeUnavailable() {
        JeiMeInventoryTooltip.onRuntimeUnavailable();
    }
}
