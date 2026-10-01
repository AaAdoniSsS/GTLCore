package org.gtlcore.gtlcore.mixin.gtm.computation;

import org.gtlcore.gtlcore.api.machine.computation.CloudComputationBridge;
import org.gtlcore.gtlcore.api.machine.computation.ComputationConnections;
import org.gtlcore.gtlcore.api.machine.computation.ComputationNetwork;
import org.gtlcore.gtlcore.api.machine.computation.ComputationNode;
import org.gtlcore.gtlcore.api.machine.computation.ComputationMath;

import com.gregtechceu.gtceu.api.capability.IOpticalComputationProvider;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Pseudo
@Mixin(targets = "com.gtladd.gtladditions.api.machine.trait.CloudOpticalComputationContainer", remap = false)
public abstract class CloudComputationContainerMixin implements ComputationNode {

    @Shadow
    public int lastResearch;

    @Shadow
    private UUID getUUID() {
        throw new AssertionError();
    }

    @Override
    public List<IOpticalComputationProvider> gtlcore$computationLinks() {
        return CloudComputationBridge.providers(getUUID());
    }

    @Override
    public void gtlcore$computationTransferred(long amount) {
        lastResearch = ComputationMath.toInt(amount);
    }

    @Inject(method = "canBridge()Z", at = @At("HEAD"), cancellable = true)
    private void gtlcore$bridge(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(ComputationNetwork.canBridge(List.of((IOpticalComputationProvider) this)));
    }

    @Inject(method = "requestCWUt", at = @At("HEAD"), cancellable = true)
    private void gtlcore$request(int amount, boolean simulate, Collection<IOpticalComputationProvider> seen,
                                CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(ComputationNetwork.request((IOpticalComputationProvider) this, amount, simulate));
    }

    @Inject(method = "handleRecipeInner", at = @At("HEAD"), cancellable = true)
    private void gtlcore$legacyInput(IO io, GTRecipe recipe, List<?> left, String slot, boolean simulate,
                                    CallbackInfoReturnable<List<?>> cir) {
        cir.setReturnValue(ComputationConnections.legacyInput((IOpticalComputationProvider) this, io, recipe, left, simulate));
    }
}
