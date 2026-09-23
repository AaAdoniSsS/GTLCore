package org.gtlcore.gtlcore.integration.ae2.graph;

import org.gtlcore.gtlcore.integration.ae2.graph.core.GraphRecipe;
import org.gtlcore.gtlcore.integration.ae2.graph.core.PlanningBudget;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.pattern.AEProcessingPattern;

import java.util.List;

final class ExactProcessingCaptureTest {

    static void run() throws Exception {
        var inventory = new KeyCounter();
        AEKey input = null;
        for (int index = 0; index < 8192; index++) {
            var tag = new CompoundTag();
            tag.putInt("variant", index);
            var key = AEItemKey.of(Items.PAPER, tag);
            if (index == 4096) input = key;
            inventory.add(key, 3_000_000_000L);
        }
        var tag = new CompoundTag();
        var in = new ListTag();
        var out = new ListTag();
        in.add(GenericStack.writeTag(new GenericStack(input, 3_000_000_000L)));
        out.add(GenericStack.writeTag(new GenericStack(AEItemKey.of(Items.BOOK), 7)));
        tag.put("in", in);
        tag.put("out", out);
        var pattern = new AEProcessingPattern(AEItemKey.of(Items.PAPER, tag));
        IPatternDetails generic = new IPatternDetails() {

            @Override
            public AEItemKey getDefinition() {
                return pattern.getDefinition();
            }

            @Override
            public IInput[] getInputs() {
                return pattern.getInputs();
            }

            @Override
            public GenericStack[] getOutputs() {
                return pattern.getOutputs();
            }

            @Override
            public boolean supportsPushInputsToExternalInventory() {
                return true;
            }
        };
        var normalize = GtlPatternCatalog.class.getDeclaredMethod("normalize", IPatternDetails.class, String.class,
                KeyCounter.class, Level.class, PlanningBudget.class);
        normalize.setAccessible(true);
        String binding = PatternFingerprint.of(pattern);
        var exactBudget = new PlanningBudget(0, 128, () -> false);
        @SuppressWarnings("unchecked")
        var exact = (List<GraphRecipe<AEKey>>) normalize.invoke(null, pattern, binding, inventory, null, exactBudget);
        var genericBudget = new PlanningBudget(0, 100_000, () -> false);
        @SuppressWarnings("unchecked")
        var full = (List<GraphRecipe<AEKey>>) normalize.invoke(null, generic, binding, inventory, null, genericBudget);
        if (!exact.equals(full) || exact.size() != 1 || exact.get(0).inputs().get(input) != 3_000_000_000L)
            throw new AssertionError("Exact processing capture differs from full template validation");
        if (genericBudget.nodes() < 8192 || exactBudget.nodes() >= 128)
            throw new AssertionError("Exact path still scans unrelated NBT variants or wrapper skipped generic path");
        System.out.println("Exact processing capture: 8192 stock variants, identical full-path IDs/slots/long amounts; work=" +
                genericBudget.nodes() + " -> " + exactBudget.nodes() + "; custom wrappers retain validation");
    }
}
