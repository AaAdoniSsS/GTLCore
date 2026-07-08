package org.gtlcore.gtlcore.mixin.ae2.crafting;

import org.gtlcore.gtlcore.integration.ae2.crafting.ICraftingCalculation;
import org.gtlcore.gtlcore.integration.ae2.crafting.ICraftingTreeNode;
import org.gtlcore.gtlcore.integration.ae2.crafting.ICraftingTreeProcess;
import org.gtlcore.gtlcore.utils.NumberUtils;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftBranchFailure;
import appeng.crafting.CraftingCalculation;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.CraftingTreeProcess;
import appeng.crafting.inv.CraftingSimulationState;
import lombok.Getter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
    @Unique
    private AEKey gTLCore$cachedOutputKey;
    @Unique
    private long gTLCore$cachedOutputCount;
    @Unique
    private CraftingTreeNode[] gTLCore$childNodes;
    @Unique
    private long[] gTLCore$childMultipliers;
    @Unique
    private GenericStack[] gTLCore$outputs;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void gTLCore$initCachedData(ICraftingService cc, CraftingCalculation job, IPatternDetails details,
                                        CraftingTreeNode parent, CallbackInfo ci) {
        gTLCore$cacheChildRequests();
        this.gTLCore$outputs = details.getOutputs();
    }

    @Override
    @Unique
    public void fastRequest(CraftingSimulationState inv, long times) throws CraftBranchFailure, InterruptedException {
        ICraftingCalculation calculation = (ICraftingCalculation) this.job;
        calculation.gtlcore$handlePausing();
        calculation.gtlcore$recordCraftingLogProcessRequest(this.limitQty, times, this.gTLCore$childNodes.length);

        var containerItems = this.containerItems ? new KeyCounter() : null;

        for (int i = 0; i < this.gTLCore$childNodes.length; i++) {
            ((ICraftingTreeNode) this.gTLCore$childNodes[i]).fastRequest(inv,
                    NumberUtils.saturatedMultiply(this.gTLCore$childMultipliers[i], times), containerItems);
        }

        onSucceed(inv, times, containerItems);
        calculation.gtlcore$clearTemplateCache();
    }

    @Override
    @Unique
    public void ultraFastRequest(CraftingSimulationState inv, long times) throws CraftBranchFailure, InterruptedException {
        ICraftingCalculation calculation = (ICraftingCalculation) this.job;
        calculation.gtlcore$handlePausing();
        calculation.gtlcore$recordCraftingLogProcessRequest(this.limitQty, times, this.gTLCore$childNodes.length);

        gTLCore$runUltraFastRequest(inv, times, calculation);
    }

    @Override
    @Unique
    public void maxFastRequest(CraftingSimulationState inv, long times) throws CraftBranchFailure, InterruptedException {
        ICraftingCalculation calculation = (ICraftingCalculation) this.job;
        calculation.gtlcore$handlePausing();
        calculation.gtlcore$recordCraftingLogProcessRequest(this.limitQty, times, this.gTLCore$childNodes.length);

        var containerItems = this.containerItems ? new KeyCounter() : null;

        for (int i = 0; i < this.gTLCore$childNodes.length; i++) {
            ((ICraftingTreeNode) this.gTLCore$childNodes[i]).maxFastRequest(inv,
                    NumberUtils.saturatedMultiply(this.gTLCore$childMultipliers[i], times), containerItems);
        }

        onSucceed(inv, times, containerItems);
        calculation.gtlcore$clearTemplateCache();
    }

    @Unique
    private void gTLCore$runUltraFastRequest(CraftingSimulationState inv, long times,
                                             ICraftingCalculation calculation)
                                                                               throws CraftBranchFailure,
                                                                               InterruptedException {
        var containerItems = this.containerItems ? new KeyCounter() : null;

        for (int i = 0; i < this.gTLCore$childNodes.length; i++) {
            ((ICraftingTreeNode) this.gTLCore$childNodes[i]).ultraFastRequest(inv,
                    NumberUtils.saturatedMultiply(this.gTLCore$childMultipliers[i], times), containerItems);
        }

        onSucceed(inv, times, containerItems);
        calculation.gtlcore$clearTemplateCache();
    }

    @Unique
    private void onSucceed(CraftingSimulationState inv, long times, KeyCounter containerItems) {
        if (containerItems != null) {
            for (var stack : containerItems) {
                inv.insert(stack.getKey(), stack.getLongValue(), Actionable.MODULATE);
                inv.addStackBytes(stack.getKey(), stack.getLongValue(), 1);
            }
        }

        GenericStack[] outputs = gTLCore$getOutputs();
        for (var out : outputs) {
            inv.insert(out.what(), NumberUtils.saturatedMultiply(out.amount(), times), Actionable.MODULATE);
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
        if (Objects.equals(this.gTLCore$cachedOutputKey, what)) {
            return this.gTLCore$cachedOutputCount;
        }

        long tot = 0L;

        for (GenericStack is : gTLCore$getOutputs()) {
            if (what.matches(is)) {
                tot = NumberUtils.saturatedAdd(tot, is.amount());
            }
        }

        this.gTLCore$cachedOutputKey = what;
        this.gTLCore$cachedOutputCount = tot;
        return tot;
    }

    @Override
    @Unique
    public boolean limitsQuantityTest() {
        return this.limitQty;
    }

    @Unique
    private void gTLCore$cacheChildRequests() {
        if (this.gTLCore$childNodes != null) {
            return;
        }

        List<Object> mergeKeys = new ArrayList<>(this.nodes.size());
        List<CraftingTreeNode> childNodes = new ArrayList<>(this.nodes.size());
        List<Long> childMultipliers = new ArrayList<>(this.nodes.size());

        for (var entry : this.nodes.entrySet()) {
            ICraftingTreeNode childNode = (ICraftingTreeNode) entry.getKey();
            Object mergeKey = childNode.gtlcore$getRequestMergeKey();
            int index = mergeKeys.indexOf(mergeKey);
            if (index >= 0) {
                childMultipliers.set(index, NumberUtils.saturatedAdd(childMultipliers.get(index), entry.getValue()));
            } else {
                mergeKeys.add(mergeKey);
                childNodes.add(entry.getKey());
                childMultipliers.add(entry.getValue());
            }
        }

        this.gTLCore$childNodes = childNodes.toArray(new CraftingTreeNode[0]);
        this.gTLCore$childMultipliers = new long[childMultipliers.size()];
        for (int i = 0; i < childMultipliers.size(); i++) {
            this.gTLCore$childMultipliers[i] = childMultipliers.get(i);
        }
    }

    @Unique
    private GenericStack[] gTLCore$getOutputs() {
        if (this.gTLCore$outputs == null) {
            this.gTLCore$outputs = this.details.getOutputs();
        }
        return this.gTLCore$outputs;
    }

    @Override
    @Unique
    public void gtlcore$resetFastState() {
        this.possible = true;
        gTLCore$cacheChildRequests();
        for (CraftingTreeNode node : this.gTLCore$childNodes) {
            ((ICraftingTreeNode) node).gtlcore$resetFastState();
        }
    }
}
