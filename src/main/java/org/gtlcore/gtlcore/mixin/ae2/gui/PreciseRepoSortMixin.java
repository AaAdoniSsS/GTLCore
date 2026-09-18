package org.gtlcore.gtlcore.mixin.ae2.gui;

import org.gtlcore.gtlcore.client.ae2.PreciseRepoAmounts;

import appeng.api.config.SortDir;
import appeng.api.config.SortOrder;
import appeng.api.stacks.AEKey;
import appeng.client.gui.me.common.Repo;
import appeng.client.gui.widgets.ISortSource;
import appeng.menu.me.common.GridInventoryEntry;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.math.BigInteger;
import java.util.*;

@Mixin(Repo.class)
public abstract class PreciseRepoSortMixin implements PreciseRepoAmounts {

    @Shadow(remap = false)
    @Final
    private ISortSource sortSrc;

    @Shadow(remap = false)
    public abstract Set<GridInventoryEntry> getAllEntries();

    @Shadow(remap = false)
    public abstract void updateView();

    @Unique
    private Map<AEKey, BigInteger> gtlcore$exact = Map.of();

    @Override
    public void gtlcore$setPreciseAmounts(Map<AEKey, BigInteger> amounts) {
        if (gtlcore$exact.equals(amounts)) return;
        gtlcore$exact = Map.copyOf(amounts);
        if (sortSrc.getSortBy() == SortOrder.AMOUNT) updateView();
    }

    @Inject(method = "getComparator", at = @At("HEAD"), cancellable = true, remap = false)
    private void gtlcore$preciseComparator(SortOrder order, SortDir direction,
                                           CallbackInfoReturnable<Comparator<? super GridInventoryEntry>> cir) {
        if (order != SortOrder.AMOUNT) return;
        // Use a common integer denominator: exact even for custom key types with non-decimal units.
        BigInteger denominator = BigInteger.ONE;
        Map<Integer, BigInteger> factors = new HashMap<>();
        for (GridInventoryEntry entry : getAllEntries()) {
            int unit = entry.getWhat().getAmountPerUnit();
            if (!factors.containsKey(unit)) {
                BigInteger value = BigInteger.valueOf(unit);
                denominator = denominator.divide(denominator.gcd(value)).multiply(value);
                factors.put(unit, value);
            }
        }
        final BigInteger common = denominator;
        factors.replaceAll((unit, value) -> common.divide(value));
        Map<AEKey, BigInteger> values = new HashMap<>();
        for (GridInventoryEntry entry : getAllEntries()) {
            AEKey key = entry.getWhat();
            BigInteger amount = gtlcore$exact.get(key);
            if (amount == null) amount = BigInteger.valueOf(entry.getStoredAmount());
            values.put(key, amount.multiply(factors.get(key.getAmountPerUnit())));
        }
        Comparator<GridInventoryEntry> comparator = Comparator.comparing(entry -> values.get(entry.getWhat()));
        cir.setReturnValue(direction == SortDir.ASCENDING ? comparator : comparator.reversed());
    }
}
