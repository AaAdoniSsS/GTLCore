package org.gtlcore.gtlcore.integration.ae2.crafting;

import org.gtlcore.gtlcore.config.AE2CalculationMode;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEKey;
import appeng.crafting.CraftingPlan;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class AE2CraftingPlanCache {

    private static final int MAX_SIZE = 64;
    private static final Map<Key, Entry> CACHE = new LinkedHashMap<>(16, 0.75f, true);

    private AE2CraftingPlanCache() {}

    public static ICraftingPlan get(IGrid grid, long storageVersion, Object level, AEKey output, long requestedAmount,
                                    CalculationStrategy strategy, AE2CalculationMode mode) {
        Key key = new Key(grid, storageVersion, level, output, requestedAmount, strategy, mode);
        synchronized (CACHE) {
            Entry entry = CACHE.get(key);
            return entry == null ? null : entry.plan;
        }
    }

    public static void put(IGrid grid, long storageVersion, Object level, AEKey output, long requestedAmount,
                           CalculationStrategy strategy, AE2CalculationMode mode, ICraftingPlan plan) {
        if (!(plan instanceof CraftingPlan) || plan.simulation() || !plan.missingItems().isEmpty()) {
            return;
        }

        Key key = new Key(grid, storageVersion, level, output, requestedAmount, strategy, mode);
        synchronized (CACHE) {
            CACHE.put(key, new Entry(plan));
            while (CACHE.size() > MAX_SIZE) {
                Iterator<Key> iterator = CACHE.keySet().iterator();
                if (!iterator.hasNext()) {
                    break;
                }
                iterator.next();
                iterator.remove();
            }
        }
    }

    private record Entry(ICraftingPlan plan) {}

    private static final class Key {

        private final Object grid;
        private final long storageVersion;
        private final Object level;
        private final AEKey output;
        private final long requestedAmount;
        private final CalculationStrategy strategy;
        private final AE2CalculationMode mode;
        private final int hash;

        private Key(Object grid, long storageVersion, Object level, AEKey output, long requestedAmount,
                    CalculationStrategy strategy, AE2CalculationMode mode) {
            this.grid = grid;
            this.storageVersion = storageVersion;
            this.level = level;
            this.output = output;
            this.requestedAmount = requestedAmount;
            this.strategy = strategy;
            this.mode = mode;
            this.hash = Objects.hash(
                    System.identityHashCode(grid),
                    storageVersion,
                    level,
                    output,
                    requestedAmount,
                    strategy,
                    mode);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof Key other)) return false;
            return this.grid == other.grid && this.storageVersion == other.storageVersion &&
                    this.requestedAmount == other.requestedAmount &&
                    Objects.equals(this.level, other.level) &&
                    Objects.equals(this.output, other.output) &&
                    this.strategy == other.strategy &&
                    this.mode == other.mode;
        }

        @Override
        public int hashCode() {
            return this.hash;
        }
    }
}
