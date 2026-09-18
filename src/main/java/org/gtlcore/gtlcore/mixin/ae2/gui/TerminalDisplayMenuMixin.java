package org.gtlcore.gtlcore.mixin.ae2.gui;

import org.gtlcore.gtlcore.integration.ae2.storage.PreciseDisplayMenu;
import org.gtlcore.gtlcore.integration.ae2.storage.TerminalDisplayRead;
import org.gtlcore.gtlcore.integration.ae2.wireless.FastCellDisplayPackets;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.menu.me.common.MEStorageMenu;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.math.BigInteger;
import java.util.*;

@Mixin(MEStorageMenu.class)
public abstract class TerminalDisplayMenuMixin implements PreciseDisplayMenu {

    @Unique
    private Map<AEKey, BigInteger> gtlcore$displayAmounts = Map.of();
    @Unique
    private final Map<AEKey, BigInteger> gtlcore$pending = new HashMap<>();

    @Override
    public Map<AEKey, BigInteger> gtlcore$displayAmounts() {
        return gtlcore$displayAmounts;
    }

    @Override
    public void gtlcore$acceptDisplayChanges(Map<AEKey, BigInteger> changes, boolean complete) {
        gtlcore$pending.putAll(changes);
        if (!complete) return;
        Map<AEKey, BigInteger> updated = new HashMap<>(gtlcore$displayAmounts);
        gtlcore$pending.forEach((key, amount) -> {
            if (amount.signum() == 0) updated.remove(key);
            else updated.put(key, amount);
        });
        gtlcore$pending.clear();
        if (!updated.equals(gtlcore$displayAmounts)) gtlcore$displayAmounts = Map.copyOf(updated);
    }

    @WrapOperation(method = "broadcastChanges",
                   at = @At(value = "INVOKE",
                            remap = false,
                            target = "Lappeng/api/storage/MEStorage;getAvailableStacks()Lappeng/api/stacks/KeyCounter;"))
    private KeyCounter gtlcore$collectDisplay(MEStorage storage, Operation<KeyCounter> original) {
        KeyCounter snapshot = TerminalDisplayRead.collect(() -> original.call(storage));
        // Sent before AE2 builds its native inventory packet, on the same connection.
        FastCellDisplayPackets.push((MEStorageMenu) (Object) this, storage, snapshot);
        return snapshot;
    }
}
