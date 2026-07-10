package org.gtlcore.gtlcore.integration.jei;

import org.gtlcore.gtlcore.client.ae2.MeInventoryAmountClient;

import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import com.mojang.datafixers.util.Either;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.forge.ForgeTypes;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IJeiRuntime;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public final class JeiMeInventoryTooltip {

    private static final TooltipListener LISTENER = new TooltipListener();
    private static @Nullable IJeiRuntime runtime;
    private static boolean registered;

    private JeiMeInventoryTooltip() {}

    public static void onRuntimeAvailable(IJeiRuntime runtime) {
        JeiMeInventoryTooltip.runtime = runtime;
        if (!registered) {
            MinecraftForge.EVENT_BUS.register(LISTENER);
            registered = true;
        }
    }

    public static void onRuntimeUnavailable() {
        runtime = null;
        if (registered) {
            MinecraftForge.EVENT_BUS.unregister(LISTENER);
            registered = false;
        }
    }

    private static @Nullable AEKey getHoveredKey(IJeiRuntime runtime) {
        Optional<ITypedIngredient<?>> hovered = runtime.getIngredientListOverlay().getIngredientUnderMouse();
        if (hovered.isEmpty()) {
            hovered = runtime.getBookmarkOverlay().getIngredientUnderMouse();
        }
        if (hovered.isPresent()) {
            return toKey(hovered.get());
        }

        Optional<ItemStack> item = runtime.getRecipesGui().getIngredientUnderMouse(VanillaTypes.ITEM_STACK);
        if (item.isPresent() && !item.get().isEmpty()) {
            return AEItemKey.of(item.get());
        }
        Optional<FluidStack> fluid = runtime.getRecipesGui().getIngredientUnderMouse(ForgeTypes.FLUID_STACK);
        if (fluid.isPresent() && !fluid.get().isEmpty()) {
            return AEFluidKey.of(fluid.get());
        }
        return null;
    }

    private static @Nullable AEKey toKey(ITypedIngredient<?> ingredient) {
        Optional<ItemStack> item = ingredient.getIngredient(VanillaTypes.ITEM_STACK);
        if (item.isPresent() && !item.get().isEmpty()) {
            return AEItemKey.of(item.get());
        }
        Optional<FluidStack> fluid = ingredient.getIngredient(ForgeTypes.FLUID_STACK);
        if (fluid.isPresent() && !fluid.get().isEmpty()) {
            return AEFluidKey.of(fluid.get());
        }
        return null;
    }

    private static final class TooltipListener {

        @SubscribeEvent
        public void gatherTooltip(RenderTooltipEvent.GatherComponents event) {
            IJeiRuntime runtime = JeiMeInventoryTooltip.runtime;
            if (runtime == null) {
                return;
            }
            AEKey key = getHoveredKey(runtime);
            if (key == null) {
                return;
            }
            MeInventoryAmountClient.getTooltip(key).ifPresent(component -> event.getTooltipElements()
                    .add(Either.<FormattedText, TooltipComponent>left(component)));
        }
    }
}
