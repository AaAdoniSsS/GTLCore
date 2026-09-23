package org.gtlcore.test;

import org.gtlcore.gtlcore.integration.ae2.graph.*;
import org.gtlcore.gtlcore.integration.ae2.graph.core.*;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.*;
import java.lang.reflect.*;
import java.util.*;

/** Optional, isolated ADD fixture. Reflection keeps the test mod usable without ADD installed. */
public final class GraphAddonProbe {
    private static final BlockPos BUFFER = new BlockPos(66,65,0);
    public static void effectivePatterns(Level level, AEKey input, AEKey product) throws Exception {
        Class<?> helper = Class.forName("com.gtladd.gtladditions.integration.ae2.MEBufferPatternHelperExtensions");
        Method rewrite = helper.getDeclaredMethod("rewriteForgeOfTheAntichristPattern", IPatternDetails.class, Level.class, int.class);
        rewrite.setAccessible(true);
        Class<?> rules = Class.forName("com.gtladd.gtladditions.utils.RecipeCalculationHelper");
        Object instance = rules.getField("INSTANCE").get(null);
        var containers = (Set<Item>) rules.getMethod("getRECIPE_CYCLE_CONTAINER_ITEMS").invoke(instance);
        check(!containers.isEmpty(), "Actual ADD container set is empty");
        var container = containers.stream().map(AEItemKey::of).filter(key -> key.toString().startsWith("kubejs:"))
                .findFirst().orElseThrow(() -> new AssertionError("Install actual pack container item registrations; barrier fallback is not a valid fixture"));
        var raw = PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeProcessingPattern(
                new GenericStack[]{new GenericStack(input, 3_000_000_000L), new GenericStack(container, 1)},
                new GenericStack[]{new GenericStack(product, 1), new GenericStack(container, 1)}), level);
        var four = (IPatternDetails) rewrite.invoke(null, raw, level, 4);
        var eight = (IPatternDetails) rewrite.invoke(null, raw, level, 8);
        var recipe = normalize(four, level);
        check(recipe.inputs().get(input) == 3_000_000_000L, "FOA ordinary input was incorrectly multiplied or truncated");
        check(recipe.inputs().get(container) == 4L && recipe.outputs().get(container) == 1L, "FOA container consumption/return changed");
        check(recipe.outputs().get(product) == 4L, "FOA NBT output or multiplier lost");
        check(!PatternFingerprint.of(four).equals(PatternFingerprint.of(eight)), "FOA modes shared one fingerprint");
        var stock = Map.<AEKey,Long>of(input, 30_000_000_000L, container, 10L);
        var work = new GraphPlanningWork<>(new GraphCompiler<>(List.of(recipe)), product, 8, stock, true, true,
                new PlanningBudget(5000, 1_000_000, () -> false));
        while (!work.step()) {}
        check(work.result().feasible(), "Final effective FOA pattern did not plan");
        PlanVerifier.verify(work.result());
        check(work.result().patternTimes().get(recipe.id()) == 2L, "Graph planned raw output instead of effective output");
        System.out.println("[Graph ADD] PASS effective FOA=4/8, container input=4 return=1, NBT identity, input=3000000000, runs=2 container=" + container);
    }

    public static void configure(Level level, int multiplier, AEKey input, AEKey output) throws Exception {
        var machine = (MEPatternBufferPartMachine) MetaMachine.getMachine(level, BUFFER);
        check(machine != null && machine.getClass().getName().contains("MESuperPatternBuffer"), "Not an ADD super buffer");
        machine.getClass().getMethod("setFOAModeEnabled", boolean.class).invoke(machine, true);
        machine.getClass().getMethod("setFOAPatternOutputMultiplier", int.class).invoke(machine, multiplier);
        machine.getTerminalPatternInventory().setItemDirect(0, PatternDetailsHelper.encodeProcessingPattern(
                new GenericStack[]{new GenericStack(input, 1)}, new GenericStack[]{new GenericStack(output, 1)}));
        var patterns = machine.getAvailablePatterns();
        check(patterns.size() == 1, "Buffer failed to advertise its effective pattern");
        check(normalize(patterns.get(0), level).outputs().get(output) == multiplier, "getRealPattern bypassed FOA mode");
        System.out.println("[Graph ADD] actual buffer advertises FOA=" + multiplier + " fingerprint=" + PatternFingerprint.of(patterns.get(0)));
    }

    public static void inspectPlan(appeng.api.networking.crafting.ICraftingPlan value, int multiplier, AEKey input) {
        var plan = ((AeGraphPlan)value).graph();
        check(plan.feasible(), "Real provider plan not feasible");
        check(plan.initial().get(input) == 8 / multiplier, "Planning used wrong effective input total");
        PlanVerifier.verify(plan);
        System.out.println("[Graph ADD] PASS real provider plan multiplier=" + multiplier + " input=" + plan.initial().get(input) + " target=" + plan.amount());
    }

    private static GraphRecipe<AEKey> normalize(IPatternDetails pattern, Level level) throws Exception {
        Method normalize = GtlPatternCatalog.class.getDeclaredMethod("normalize", IPatternDetails.class, String.class, KeyCounter.class, Level.class);
        normalize.setAccessible(true);
        return ((List<GraphRecipe<AEKey>>) normalize.invoke(null, pattern, PatternFingerprint.of(pattern), new KeyCounter(), level)).get(0);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
