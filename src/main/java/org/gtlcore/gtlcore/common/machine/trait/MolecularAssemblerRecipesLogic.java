package org.gtlcore.gtlcore.common.machine.trait;

import org.gtlcore.gtlcore.api.machine.ISuspendableMachine;
import org.gtlcore.gtlcore.api.machine.multiblock.MolecularAssemblerMultiblockMachine;
import org.gtlcore.gtlcore.api.machine.multiblock.ParallelMachine;
import org.gtlcore.gtlcore.mixin.gtm.api.recipe.RecipeLogicAccessor;

import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;

import org.jetbrains.annotations.Nullable;

public class MolecularAssemblerRecipesLogic extends RecipeLogic {

    private final ParallelMachine parallel;

    public MolecularAssemblerRecipesLogic(ParallelMachine machine) {
        super((IRecipeLogicMachine) machine);
        this.parallel = machine;
    }

    @Override
    public MolecularAssemblerMultiblockMachine getMachine() {
        return (MolecularAssemblerMultiblockMachine) super.getMachine();
    }

    private @Nullable GTRecipe getRecipe() {
        var handler = getMachine().getMAHandler();
        if (handler != null) return handler.extractGTRecipe(parallel.getMaxParallel(), getMachine().getTickDuration());
        else return null;
    }

    @Override
    public void updateTickSubscription() {
        if (!this.isSuspend() && this.machine.isRecipeLogicAvailable()) {
            this.subscription = this.getMachine().subscribeServerTick(this.subscription, this::serverTick);
        } else if (this.subscription != null) {
            this.subscription.unsubscribe();
            this.subscription = null;
        }
    }

    @Override
    public void serverTick() {
        if (!this.isSuspend()) {
            if (!this.isIdle() && this.lastRecipe != null) {
                if (this.progress < this.duration) {
                    this.handleRecipeWorking();
                }
                if (this.progress >= this.duration) {
                    this.onRecipeFinish();
                }
            } else if (this.lastRecipe != null || !this.machine.keepSubscribing() || this.getMachine().getOffsetTimer() % 5L == 0L) {
                this.findAndHandleRecipe();
            }
        }

        boolean unsubscribe = false;
        if (this.isSuspend()) {
            unsubscribe = true;
        } else if (this.lastRecipe == null && this.isIdle() && !this.machine.keepSubscribing()) {
            unsubscribe = true;
        }

        if (unsubscribe && this.subscription != null) {
            this.subscription.unsubscribe();
            this.subscription = null;
        }
    }

    @Override
    public void handleRecipeWorking() {
        ++this.progress;
        ++this.totalContinuousRunningTime;
    }

    @Override
    public void findAndHandleRecipe() {
        lastRecipe = null;
        var match = getRecipe();
        if (match != null) {
            setupRecipe(match);
        }
    }

    @Override
    public void setupRecipe(GTRecipe recipe) {
        this.lastRecipe = recipe;
        this.setStatus(RecipeLogic.Status.WORKING);
        this.progress = 0;
        this.duration = recipe.duration;
        ((RecipeLogicAccessor) this).setIsActive(true);
    }

    @Override
    public void onRecipeFinish() {
        if (this.lastRecipe != null) {
            var handler = getMachine().getMAHandler();
            if (handler != null) handler.handleRecipeOutput(this.lastRecipe);

            if (this.machine instanceof ISuspendableMachine suspendableMachine && suspendableMachine.gtlcore$isSuspendAfterFinish()) {
                this.setStatus(RecipeLogic.Status.SUSPEND);
                suspendableMachine.gtlcore$setSuspendAfterFinish(false);
            } else {
                var match = getRecipe();
                if (match != null) {
                    setupRecipe(match);
                    return;
                } else {
                    lastRecipe = null;
                    this.setStatus(RecipeLogic.Status.IDLE);
                    ((RecipeLogicAccessor) this).setIsActive(false);
                }
            }
            this.progress = 0;
            this.duration = 0;
        }
    }
}
