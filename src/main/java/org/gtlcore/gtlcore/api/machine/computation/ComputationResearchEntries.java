package org.gtlcore.gtlcore.api.machine.computation;

import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Builder entries have an int ABI. Their unique data stack retains the long values until recipe generation. */
public final class ComputationResearchEntries {

    private static final Map<ItemStack, Amounts> ENTRIES = Collections.synchronizedMap(new WeakHashMap<>());

    private ComputationResearchEntries() {}

    public static void remember(ItemStack data, long rate, long total) {
        ENTRIES.put(data, new Amounts(rate, total));
    }

    public static Amounts get(ItemStack data) {
        return ENTRIES.get(data);
    }

    public record Amounts(long rate, long total) {}
}
