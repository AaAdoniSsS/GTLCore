package org.gtlcore.gtlcore.api.recipe;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

public final class RecipeMultiplierTracker {

    public static final Multipliers DEFAULT = new Multipliers(1, 1);

    private static final Map<MetaMachine, Multipliers> MULTIPLIERS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final ThreadLocal<CaptureContext> CAPTURE_CONTEXT = new ThreadLocal<>();

    private RecipeMultiplierTracker() {}

    public static void begin(MetaMachine machine, GTRecipe baseRecipe) {
        CAPTURE_CONTEXT.set(new CaptureContext(machine, baseRecipe));
    }

    public static void captureBeforeOverclock(MetaMachine machine, GTRecipe recipe) {
        CaptureContext context = contextFor(machine);
        if (context != null && context.captured == null) {
            context.captured = calculate(context.baseRecipe, recipe);
        }
    }

    public static void captureReduction(MetaMachine machine, GTRecipe before,
                                        double energyMultiplier, double durationMultiplier) {
        CaptureContext context = contextFor(machine);
        if (context != null) {
            Multipliers beforeReduction = context.captured == null ?
                    calculate(context.baseRecipe, before) : context.captured;
            context.captured = multiply(beforeReduction, energyMultiplier, durationMultiplier);
            return;
        }
        MULTIPLIERS.put(machine, multiply(DEFAULT, energyMultiplier, durationMultiplier));
    }

    public static void finish(MetaMachine machine, boolean commit) {
        CaptureContext context = contextFor(machine);
        try {
            if (commit && context != null) {
                MULTIPLIERS.put(machine, context.captured == null ? DEFAULT : context.captured);
            }
        } finally {
            CAPTURE_CONTEXT.remove();
        }
    }

    public static Optional<Multipliers> get(MetaMachine machine) {
        return Optional.ofNullable(MULTIPLIERS.get(machine));
    }

    private static CaptureContext contextFor(MetaMachine machine) {
        CaptureContext context = CAPTURE_CONTEXT.get();
        return context != null && context.machine == machine ? context : null;
    }

    private static Multipliers calculate(GTRecipe baseRecipe, GTRecipe modifiedRecipe) {
        double energyMultiplier = 1;
        double baseEUt = Math.abs((double) RecipeHelper.getInputEUt(baseRecipe));
        if (baseEUt > 0) {
            double modifiedEUt = Math.abs((double) RecipeHelper.getInputEUt(modifiedRecipe));
            long parallels = Math.max(1, IGTRecipe.of(modifiedRecipe).getRealParallels());
            energyMultiplier = modifiedEUt / baseEUt / parallels;
        }

        double durationMultiplier = baseRecipe.duration > 0 ?
                (double) modifiedRecipe.duration / baseRecipe.duration :
                1;
        return new Multipliers(finiteOrOne(energyMultiplier), finiteOrOne(durationMultiplier));
    }

    private static double finiteOrOne(double value) {
        return Double.isFinite(value) && value >= 0 ? value : 1;
    }

    private static Multipliers multiply(Multipliers multipliers, double energy, double duration) {
        return new Multipliers(
                multipliers.energy * finiteOrOne(energy),
                multipliers.duration * finiteOrOne(duration));
    }

    public record Multipliers(double energy, double duration) {}

    private static final class CaptureContext {

        private final MetaMachine machine;
        private final GTRecipe baseRecipe;
        private Multipliers captured;

        private CaptureContext(MetaMachine machine, GTRecipe baseRecipe) {
            this.machine = machine;
            this.baseRecipe = baseRecipe;
        }
    }
}
