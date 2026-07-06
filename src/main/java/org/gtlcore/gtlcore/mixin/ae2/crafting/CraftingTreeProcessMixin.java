package org.gtlcore.gtlcore.mixin.ae2.crafting;

import org.gtlcore.gtlcore.integration.ae2.crafting.ICraftingCalculation;
import org.gtlcore.gtlcore.integration.ae2.crafting.ICraftingTreeNode;
import org.gtlcore.gtlcore.integration.ae2.crafting.ICraftingTreeProcess;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftBranchFailure;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.CraftingTreeProcess;
import appeng.crafting.inv.CraftingSimulationState;
import lombok.Getter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.Map;

@SuppressWarnings("AddedMixinMembersNamePattern")
@Mixin(CraftingTreeProcess.class)
public abstract class CraftingTreeProcessMixin implements ICraftingTreeProcess {

    @Shadow(remap = false)
    @Final
    private appeng.crafting.CraftingCalculation job;
    @Shadow(remap = false)
    private boolean containerItems;
    @Shadow(remap = false)
    @Final
    private Map<CraftingTreeNode, Long> nodes;
    @Getter
    @Shadow(remap = false)
    @Final
    IPatternDetails details;
    @Shadow(remap = false)
    boolean possible;
    @Shadow(remap = false)
    private boolean limitQty;

    @Override
    @Unique
    public void adaptiveRequest(CraftingSimulationState inv, long times) throws CraftBranchFailure, InterruptedException {
        ((ICraftingCalculation) this.job).gtlcore$handlePausing();

        var containerItems = this.containerItems ? new KeyCounter() : null;

        for (var entry : this.nodes.entrySet()) {
            ((ICraftingTreeNode) entry.getKey()).adaptiveRequest(inv, entry.getValue() * times, containerItems);
        }

        onSucceed(inv, times, containerItems);
        ((ICraftingCalculation) this.job).gtlcore$clearTemplateCache();
    }

    @Override
    @Unique
    public void fastRequest(CraftingSimulationState inv, long times) throws CraftBranchFailure, InterruptedException {
        ((ICraftingCalculation) this.job).gtlcore$handlePausing();

        var containerItems = this.containerItems ? new KeyCounter() : null;

        for (var entry : this.nodes.entrySet()) {
            ((ICraftingTreeNode) entry.getKey()).fastRequest(inv, entry.getValue() * times, containerItems);
        }

        onSucceed(inv, times, containerItems);
    }

    @Override
    @Unique
    public void ultraFastRequest(CraftingSimulationState inv, long times) throws CraftBranchFailure, InterruptedException {
        ((ICraftingCalculation) this.job).gtlcore$handlePausing();

        var containerItems = this.containerItems ? new KeyCounter() : null;

        for (var entry : this.nodes.entrySet()) {
            ((ICraftingTreeNode) entry.getKey()).ultraFastRequest(inv, entry.getValue() * times, containerItems);
        }

        onSucceed(inv, times, containerItems);
    }

    @Unique
    private void onSucceed(CraftingSimulationState inv, long times, KeyCounter containerItems) {
        if (containerItems != null) {
            for (var stack : containerItems) {
                inv.insert(stack.getKey(), stack.getLongValue(), Actionable.MODULATE);
                inv.addStackBytes(stack.getKey(), stack.getLongValue(), 1);
            }
        }

        for (var out : this.details.getOutputs()) {
            inv.insert(out.what(), out.amount() * times, Actionable.MODULATE);
        }

        inv.addCrafting(details, times);
        inv.addBytes(times);
    }

    @Override
    @Unique
    public boolean getPossible() {
        return this.possible;
    }

    @Override
    @Unique
    public void setPossible(boolean b) {
        this.possible = b;
    }

    @Override
    @Unique
    public long getOutputCountTest(AEKey what) {
        long tot = 0L;

        for (GenericStack is : this.details.getOutputs()) {
            if (what.matches(is)) {
                tot += is.amount();
            }
        }

        return tot;
    }

    @Override
    @Unique
    public boolean limitsQuantityTest() {
        return this.limitQty;
    }

    @Override
    @Unique
    public void gtlcore$resetAdaptiveState() {
        this.possible = true;
        for (var node : this.nodes.keySet()) {
            ((ICraftingTreeNode) node).gtlcore$resetAdaptiveState();
        }
    }
}
