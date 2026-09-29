package org.gtlcore.gtlcore.mixin.gtmt;

import org.gtlcore.gtlcore.integration.terminal.StableBlockCandidates;
import org.gtlcore.gtlcore.mixin.gtm.api.machine.IMultiblockStateInvoker;

import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import com.hepdd.gtmthings.api.pattern.AdvancedBlockPattern;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** No world writes, inventory extraction, source ordering, or placement callbacks are replaced. */
@Mixin(value = AdvancedBlockPattern.class, remap = false)
public abstract class TerminalPatternPerformanceMixin {

    @Unique
    private final Map<Supplier<?>, Object> gtlcore$candidates = new IdentityHashMap<>();
    @Unique
    private boolean gtlcore$buildingBasis;
    @Unique
    private Direction gtlcore$front;
    @Unique
    private Direction gtlcore$up;
    @Unique
    private boolean gtlcore$flipped;
    @Unique
    private BlockPos gtlcore$x;
    @Unique
    private BlockPos gtlcore$y;
    @Unique
    private BlockPos gtlcore$z;

    @Inject(method = "autoBuild", at = @At("HEAD"))
    private void gtlcore$begin(CallbackInfo ci) {
        gtlcore$candidates.clear();
    }

    @Inject(method = "autoBuild", at = @At("RETURN"))
    private void gtlcore$end(CallbackInfo ci) {
        gtlcore$candidates.clear();
    }

    @Redirect(method = "autoBuild", at = @At(value = "INVOKE", target = "Ljava/util/function/Supplier;get()Ljava/lang/Object;"))
    private Object gtlcore$candidates(Supplier<?> supplier) {
        if (!StableBlockCandidates.contains(supplier)) return supplier.get();
        return gtlcore$candidates.computeIfAbsent(supplier, Supplier::get);
    }

    @Inject(method = "clearWorldState", at = @At("HEAD"), cancellable = true)
    private void gtlcore$clear(MultiblockState state, CallbackInfo ci) {
        ((IMultiblockStateInvoker) state).cleanState();
        ci.cancel();
    }

    @Inject(method = "updateWorldState", at = @At("HEAD"), cancellable = true)
    private void gtlcore$update(MultiblockState state, BlockPos pos, TraceabilityPredicate predicate, CallbackInfo ci) {
        ((IMultiblockStateInvoker) state).updateState(pos, predicate);
        ci.cancel();
    }

    @Inject(method = "setActualRelativeOffset", at = @At("HEAD"), cancellable = true)
    private void gtlcore$transform(int x, int y, int z, Direction front, Direction up, boolean flipped,
                                   CallbackInfoReturnable<BlockPos> cir) {
        if (gtlcore$buildingBasis) return;
        if (gtlcore$x == null || gtlcore$front != front || gtlcore$up != up || gtlcore$flipped != flipped) {
            gtlcore$buildingBasis = true;
            try {
                var original = (TerminalPatternAccessor) this;
                gtlcore$x = original.gtlcore$offset(1, 0, 0, front, up, flipped);
                gtlcore$y = original.gtlcore$offset(0, 1, 0, front, up, flipped);
                gtlcore$z = original.gtlcore$offset(0, 0, 1, front, up, flipped);
                gtlcore$front = front;
                gtlcore$up = up;
                gtlcore$flipped = flipped;
            } finally {
                gtlcore$buildingBasis = false;
            }
        }
        cir.setReturnValue(new BlockPos(
                gtlcore$x.getX() * x + gtlcore$y.getX() * y + gtlcore$z.getX() * z,
                gtlcore$x.getY() * x + gtlcore$y.getY() * y + gtlcore$z.getY() * z,
                gtlcore$x.getZ() * x + gtlcore$y.getZ() * y + gtlcore$z.getZ() * z));
    }
}
