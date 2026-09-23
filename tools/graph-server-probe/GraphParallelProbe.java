package org.gtlcore.test;

import appeng.api.crafting.IPatternDetails;
import appeng.blockentity.crafting.MolecularAssemblerBlockEntity;
import appeng.hooks.ticking.TickHandler;
import java.util.*;

public final class GraphParallelProbe {
    private static String label;
    private static long start, first, firstEnd;
    private static int accepts, peakSameTick, sameTick, peakBusy;
    private static final Set<Object> machines = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Map<String, Set<Object>> byRecipe = new LinkedHashMap<>();

    public static void begin(String name) {
        label = name; start = TickHandler.instance().getCurrentTick(); first = -1; firstEnd = -1;
        accepts = peakSameTick = sameTick = peakBusy = 0; machines.clear(); byRecipe.clear();
    }
    public static void accepted(Object machine, IPatternDetails pattern) {
        if (label == null) return;
        long tick = TickHandler.instance().getCurrentTick();
        if (first < 0) first = tick;
        if (firstEnd != tick) { firstEnd = tick; sameTick = 0; }
        peakSameTick = Math.max(peakSameTick, ++sameTick);
        accepts++; machines.add(machine);
        byRecipe.computeIfAbsent(pattern.getOutputs()[0].what().toString(), k -> Collections.newSetFromMap(new IdentityHashMap<>())).add(machine);
        int busy = 0;
        for (Object value : machines) if (((MolecularAssemblerBlockEntity)value).getCurrentPattern() != null) busy++;
        peakBusy = Math.max(peakBusy, busy);
    }
    public static String finish() {
        String text = "[Graph Parallel] label=" + label + " ticks=" + (TickHandler.instance().getCurrentTick() - start) +
                " dispatches=" + accepts + " machines=" + machines.size() + " peak_same_tick=" + peakSameTick + " peak_busy=" + peakBusy +
                " per_recipe_machines=" + byRecipe.entrySet().stream().map(e -> e.getKey() + ':' + e.getValue().size()).toList();
        label = null;
        System.out.println(text);
        return text;
    }
}
