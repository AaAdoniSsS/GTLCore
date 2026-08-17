package org.gtlcore.gtlcore.mixin.jei;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.forge.ForgeTypes;
import mezz.jei.api.runtime.IClickableIngredient;
import mezz.jei.api.runtime.IJeiRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Pseudo
@Mixin(targets = "dev.ftb.mods.ftblibrary.integration.forge.JEIIntegration", remap = false)
public abstract class FtbJeiIntegrationMixin {

    @Shadow
    public static IJeiRuntime runtime;

    @Inject(method = "getClickableIngredientUnderMouse", at = @At("RETURN"), cancellable = true, remap = false)
    private void gtlcore$useContainedFluidAsClickableIngredient(
                                                                double mouseX, double mouseY,
                                                                CallbackInfoReturnable<Optional<IClickableIngredient<?>>> cir) {
        Optional<IClickableIngredient<?>> clickable = cir.getReturnValue();
        if (clickable.isEmpty() || runtime == null) {
            return;
        }
        Optional<ItemStack> item = clickable.get().getTypedIngredient().getIngredient(VanillaTypes.ITEM_STACK);
        if (item.isEmpty()) {
            return;
        }
        Optional<FluidStack> containedFluid = FluidUtil.getFluidContained(item.get());
        if (containedFluid.isEmpty()) {
            return;
        }
        runtime.getIngredientManager()
                .createClickableIngredient(
                        ForgeTypes.FLUID_STACK, containedFluid.get(), clickable.get().getArea(), false)
                .ifPresent(fluidClickable -> cir.setReturnValue(Optional.of(fluidClickable)));
    }

    @Inject(method = "getClickableIngredientUnderMouse", at = @At("HEAD"), cancellable = true, remap = false)
    private void gtlcore$useArchitecturyFluidAsClickableIngredient(
                                                                   double mouseX, double mouseY,
                                                                   CallbackInfoReturnable<Optional<IClickableIngredient<?>>> cir) {
        if (runtime == null) {
            return;
        }
        Object positionedIngredient = gtlcore$getFtbIngredient(Minecraft.getInstance().screen);
        if (positionedIngredient == null) {
            return;
        }
        try {
            Object ingredient = positionedIngredient.getClass().getMethod("ingredient").invoke(positionedIngredient);
            if (!(ingredient instanceof dev.architectury.fluid.FluidStack architecturyFluid)) {
                return;
            }
            long amount = architecturyFluid.getAmount();
            if (amount <= 0 || amount > Integer.MAX_VALUE) {
                return;
            }
            FluidStack forgeFluid = new FluidStack(
                    architecturyFluid.getFluid(), (int) amount, architecturyFluid.getTag());
            Rect2i area = (Rect2i) positionedIngredient.getClass().getMethod("area").invoke(positionedIngredient);
            runtime.getIngredientManager()
                    .createClickableIngredient(ForgeTypes.FLUID_STACK, forgeFluid, area, false)
                    .ifPresent(fluidClickable -> cir.setReturnValue(Optional.of(fluidClickable)));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            // FTB is optional; leave its original handler untouched when its UI changes.
        }
    }

    private static Object gtlcore$getFtbIngredient(Screen screen) {
        if (screen == null) {
            return null;
        }
        try {
            Class<?> wrapperType = Class.forName("dev.ftb.mods.ftblibrary.ui.IScreenWrapper");
            if (!wrapperType.isInstance(screen)) {
                return null;
            }
            Object gui = wrapperType.getMethod("getGui").invoke(screen);
            Object result = gui.getClass().getMethod("getIngredientUnderMouse").invoke(gui);
            if (result instanceof Optional<?> optional) {
                return optional.orElse(null);
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
        return null;
    }
}
