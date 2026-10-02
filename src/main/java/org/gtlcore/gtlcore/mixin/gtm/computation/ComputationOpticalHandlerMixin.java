package org.gtlcore.gtlcore.mixin.gtm.computation;

import org.gtlcore.gtlcore.api.machine.computation.ComputationMath;
import org.gtlcore.gtlcore.api.machine.computation.ComputationNetwork;
import org.gtlcore.gtlcore.api.machine.computation.ComputationNode;
import org.gtlcore.gtlcore.api.machine.computation.ComputationVisualState;

import com.gregtechceu.gtceu.api.capability.IOpticalComputationProvider;
import com.gregtechceu.gtceu.common.blockentity.OpticalPipeBlockEntity;
import com.gregtechceu.gtceu.common.pipelike.optical.OpticalNetHandler;
import com.gregtechceu.gtceu.common.pipelike.optical.OpticalPipeNet;

import net.minecraft.world.level.Level;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Mixin(OpticalNetHandler.class)
public abstract class ComputationOpticalHandlerMixin implements ComputationNode {

    @Shadow(remap = false)
    private OpticalPipeNet net;
    @Shadow(remap = false)
    @Final
    private Level world;
    @Shadow(remap = false)
    @Final
    private OpticalPipeBlockEntity pipe;

    @Shadow(remap = false)
    private IOpticalComputationProvider getComputationProvider(Collection<IOpticalComputationProvider> seen) {
        throw new AssertionError();
    }

    @Shadow(remap = false)
    private void setPipesActive() {
        throw new AssertionError();
    }

    @Override
    public boolean gtlcore$computationOnline() {
        return net != null && net.isValid() && pipe != null && !pipe.isInValid() &&
                world.hasChunkAt(pipe.getPipePos());
    }

    @Override
    public List<IOpticalComputationProvider> gtlcore$computationLinks() {
        return ComputationNetwork.link(getComputationProvider(new ArrayList<>()));
    }

    @Override
    public void gtlcore$computationTransferred(long amount) {
        setPipesActive();
    }

    @Inject(method = "setPipesActive", at = @At("HEAD"), cancellable = true, remap = false)
    private void gtlcore$throttleLights(CallbackInfo ci) {
        if (net == null || !((ComputationVisualState) net).gtlcore$refreshComputationLight(ComputationNetwork.tick(world))) ci.cancel();
    }

    @Inject(method = "requestCWUt", at = @At("HEAD"), cancellable = true, remap = false)
    private void gtlcore$request(int amount, boolean simulate, Collection<IOpticalComputationProvider> seen,
                                 CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(ComputationNetwork.request((IOpticalComputationProvider) this, amount, simulate));
    }

    @Inject(method = "getMaxCWUt", at = @At("HEAD"), cancellable = true, remap = false)
    private void gtlcore$capacity(Collection<IOpticalComputationProvider> seen, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(ComputationMath.toInt(ComputationNetwork.capacity(List.of((IOpticalComputationProvider) this))));
    }

    @Inject(method = "canBridge", at = @At("HEAD"), cancellable = true, remap = false)
    private void gtlcore$bridge(Collection<IOpticalComputationProvider> seen, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(ComputationNetwork.canBridge(List.of((IOpticalComputationProvider) this)));
    }

    @Inject(method = "updateNetwork", at = @At("RETURN"), remap = false)
    private void gtlcore$reroute(OpticalPipeNet net, CallbackInfo ci) {
        ComputationNetwork.invalidate();
    }
}
