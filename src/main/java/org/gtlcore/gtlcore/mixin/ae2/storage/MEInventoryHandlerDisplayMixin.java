package org.gtlcore.gtlcore.mixin.ae2.storage;

import org.gtlcore.gtlcore.integration.ae2.storage.TerminalDisplayRead;
import org.gtlcore.gtlcore.integration.ae2.storage.TerminalFilterRules;

import appeng.api.config.IncludeExclude;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.me.storage.MEInventoryHandler;
import appeng.util.prioritylist.IPartitionList;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Collections;
import java.util.Iterator;

/** Keep the native recursion/visibility checks; only preserve precision when copying accepted keys. */
@Mixin(MEInventoryHandler.class)
public abstract class MEInventoryHandlerDisplayMixin {

    @Shadow(remap = false)
    private IPartitionList partitionList;
    @Shadow(remap = false)
    private IncludeExclude partitionListMode;

    @Shadow(remap = false)
    protected abstract boolean canExtract(AEKey key);

    @WrapOperation(method = "getAvailableStacks",
                   remap = false,
                   at = @At(value = "INVOKE", target = "Lappeng/api/stacks/KeyCounter;iterator()Ljava/util/Iterator;"))
    private Iterator<Object2LongMap.Entry<AEKey>> gtlcore$filterDense(KeyCounter source,
                                                                      Operation<Iterator<Object2LongMap.Entry<AEKey>>> original,
                                                                      @Local(argsOnly = true) KeyCounter out) {
        if (TerminalDisplayRead.tracks(out) && TerminalDisplayRead.dense(out) != null && TerminalDisplayRead.dense(source) != null) {
            Object rules = TerminalFilterRules.capture(this, partitionList, partitionListMode);
            if (TerminalDisplayRead.filter(out, source, this, rules, this::canExtract)) return Collections.emptyIterator();
        }
        return original.call(source);
    }

    @WrapOperation(method = "getAvailableStacks",
                   remap = false,
                   at = @At(value = "INVOKE", target = "Lappeng/api/storage/MEStorage;getAvailableStacks()Lappeng/api/stacks/KeyCounter;"))
    private KeyCounter gtlcore$captureFilteredSource(MEStorage storage, Operation<KeyCounter> original) {
        KeyCounter counter = original.call(storage);
        TerminalDisplayRead.filteredSource(this, counter);
        return counter;
    }

    @WrapOperation(method = "getAvailableStacks",
                   remap = false,
                   at = @At(value = "INVOKE", target = "Lappeng/api/stacks/KeyCounter;add(Lappeng/api/stacks/AEKey;J)V"))
    private void gtlcore$copyFilteredAmount(KeyCounter out, AEKey key, long amount, Operation<Void> original) {
        if (TerminalDisplayRead.tracks(out)) TerminalDisplayRead.addFiltered(out, this, key, amount);
        else original.call(out, key, amount);
    }
}
