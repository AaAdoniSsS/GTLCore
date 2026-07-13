package org.gtlcore.gtlcore.mixin.ae2.logic;

import org.gtlcore.gtlcore.integration.ae2.crafting.IPatternProviderAutoExpand;
import org.gtlcore.gtlcore.utils.NumberUtils;

import com.gregtechceu.gtceu.common.data.GTItems;

import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.helpers.patternprovider.PatternProviderTarget;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author EasterFG on 2024/10/13
 */
@Mixin(PatternProviderLogic.class)
public abstract class PatternProviderLogicMixin implements IPatternProviderAutoExpand {

    @Shadow(remap = false)
    @Final
    private PatternProviderLogicHost host;

    @Shadow(remap = false)
    @Final
    private Set<AEKey> patternInputs;

    @Shadow(remap = false)
    private Set<Direction> getActiveSides() {
        throw new AssertionError();
    }

    @Shadow(remap = false)
    private PatternProviderTarget findAdapter(Direction direction) {
        throw new AssertionError();
    }

    @Shadow(remap = false)
    private boolean isBlocking() {
        throw new AssertionError();
    }

    @Inject(method = "updatePatterns", at = @At("TAIL"), remap = false)
    public void updatePatternsHook(CallbackInfo ci) {
        patternInputs.remove(AEItemKey.of(GTItems.INTEGRATED_CIRCUIT.get()));
    }

    @Inject(method = "adapterAcceptsAll", at = @At("HEAD"), remap = false, cancellable = true)
    private void gtlcore$requireFullTargetCapacity(PatternProviderTarget target, KeyCounter[] inputHolder,
                                                   CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(gtlcore$targetAcceptsAll(target, gtlcore$toInputCounter(inputHolder), 1));
    }

    @Override
    public long gtlcore$getMaxPatternOperations(IPatternDetails pattern, long requestedOperations) {
        if (requestedOperations <= 1 || !pattern.supportsPushInputsToExternalInventory()) {
            return requestedOperations;
        }

        var blockEntity = host.getBlockEntity();
        Level level = blockEntity.getLevel();
        if (level == null) {
            return requestedOperations;
        }

        var baseInputs = gtlcore$toInputCounter(pattern);
        long maxOperations = 0;
        boolean hasAdapter = false;

        for (var direction : getActiveSides()) {
            var targetPosition = blockEntity.getBlockPos().relative(direction);
            var targetBlockEntity = level.getBlockEntity(targetPosition);
            var machine = ICraftingMachine.of(level, targetPosition, direction.getOpposite(), targetBlockEntity);
            if (machine != null && machine.acceptsPlans()) {
                return requestedOperations;
            }

            var target = findAdapter(direction);
            if (target == null || (isBlocking() && target.containsPatternInput(patternInputs))) {
                continue;
            }
            hasAdapter = true;
            maxOperations = Math.max(maxOperations,
                    gtlcore$findMaxOperations(target, baseInputs, requestedOperations));
        }

        if (!hasAdapter) {
            return requestedOperations;
        }
        return maxOperations;
    }

    private long gtlcore$findMaxOperations(PatternProviderTarget target, KeyCounter baseInputs,
                                           long requestedOperations) {
        if (!gtlcore$targetAcceptsAll(target, baseInputs, requestedOperations)) {
            // Target acceptance is monotonic with batch size, so find the largest feasible batch.
            long low = 0;
            long high = requestedOperations - 1;
            while (low < high) {
                long middle = low + ((high - low + 1) >>> 1);
                if (gtlcore$targetAcceptsAll(target, baseInputs, middle)) {
                    low = middle;
                } else {
                    high = middle - 1;
                }
            }
            return low;
        }
        return requestedOperations;
    }

    private boolean gtlcore$targetAcceptsAll(PatternProviderTarget target, KeyCounter baseInputs,
                                             long operations) {
        Map<AEKeyType, Long> requiredByType = new IdentityHashMap<>();
        Map<AEKeyType, Long> capacityByType = new IdentityHashMap<>();
        for (var input : baseInputs) {
            long amount = NumberUtils.saturatedMultiply(input.getLongValue(), operations);
            if (amount <= 0) {
                continue;
            }

            AEKeyType type = input.getKey().getType();
            long available = target.insert(input.getKey(), Long.MAX_VALUE, Actionable.SIMULATE);
            requiredByType.merge(type, amount, NumberUtils::saturatedAdd);
            capacityByType.merge(type, available, Long::min);
        }

        // SIMULATE calls do not reserve shared slots. Use the smallest per-key capacity
        // as a conservative shared-capacity bound so one input cannot consume the batch.
        for (var entry : requiredByType.entrySet()) {
            if (entry.getValue() > capacityByType.getOrDefault(entry.getKey(), 0L)) {
                return false;
            }
        }
        return true;
    }

    private KeyCounter gtlcore$toInputCounter(KeyCounter[] inputHolder) {
        var combinedInputs = new KeyCounter();
        for (var inputList : inputHolder) {
            for (var input : inputList) {
                combinedInputs.add(input.getKey(), input.getLongValue());
            }
        }
        return combinedInputs;
    }

    private KeyCounter gtlcore$toInputCounter(IPatternDetails pattern) {
        var baseInputs = new KeyCounter();
        for (var input : pattern.getInputs()) {
            var possibleInputs = input.getPossibleInputs();
            if (possibleInputs.length == 0) {
                continue;
            }
            baseInputs.add(possibleInputs[0].what(), input.getMultiplier());
        }
        return baseInputs;
    }
}
