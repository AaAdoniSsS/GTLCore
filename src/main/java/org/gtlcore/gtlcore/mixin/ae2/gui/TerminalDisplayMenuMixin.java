package org.gtlcore.gtlcore.mixin.ae2.gui;

import org.gtlcore.gtlcore.GTLCore;
import org.gtlcore.gtlcore.integration.ae2.storage.PreciseDisplayMenu;
import org.gtlcore.gtlcore.integration.ae2.storage.TerminalCraftables;
import org.gtlcore.gtlcore.integration.ae2.storage.TerminalDisplayRead;
import org.gtlcore.gtlcore.integration.ae2.storage.TerminalInventoryChanges;
import org.gtlcore.gtlcore.integration.ae2.storage.TerminalInventoryIndex;
import org.gtlcore.gtlcore.integration.ae2.wireless.FastCellDisplayPackets;

import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.MEStorage;
import appeng.api.storage.cells.IBasicCellItem;
import appeng.menu.me.common.MEStorageMenu;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.math.BigInteger;
import java.util.*;

@Mixin(MEStorageMenu.class)
public abstract class TerminalDisplayMenuMixin implements PreciseDisplayMenu {

    @Unique
    private Map<AEKey, BigInteger> gtlcore$displayAmounts = Map.of();
    @Unique
    private Map<AEKey, BigInteger> gtlcore$pending;
    @Unique
    private boolean gtlcore$receivedDisplay;
    @Unique
    private boolean gtlcore$displayFailureLogged;

    @Unique
    private Set<AEKey> gtlcore$craftablesCache = ImmutableSet.of();
    @Unique
    private Set<AEKey> gtlcore$craftablesSource;
    @Unique
    private AEKeyType gtlcore$craftablesType;
    @Unique
    private Boolean gtlcore$nativeVisibility;
    @Unique
    private Set<AEKey> gtlcore$inventoryChanges;
    @Unique
    private TerminalInventoryChanges gtlcore$inventorySnapshot;
    @Unique
    private TerminalInventoryIndex gtlcore$inventoryIndex;
    @Shadow(remap = false)
    @Final
    private ITerminalHost host;
    @Shadow(remap = false)
    private IGridNode networkNode;

    @Shadow(remap = false)
    protected abstract boolean showsCraftables();

    @Inject(method = "broadcastChanges", at = @At("HEAD"))
    private void gtlcore$beginInventoryComparison(CallbackInfo ci) {
        gtlcore$inventoryChanges = null;
    }

    @Override
    public Map<AEKey, BigInteger> gtlcore$displayAmounts() {
        return gtlcore$displayAmounts == null ? Map.of() : gtlcore$displayAmounts;
    }

    @Override
    public void gtlcore$acceptDisplayChanges(Map<AEKey, BigInteger> changes, boolean reset, boolean complete) {
        if (gtlcore$pending == null) gtlcore$pending = new HashMap<>();
        if (reset) gtlcore$pending.clear();
        gtlcore$pending.putAll(changes);
        if (!complete) return;
        Map<AEKey, BigInteger> updated = new HashMap<>(gtlcore$displayAmounts());
        gtlcore$pending.forEach((key, amount) -> {
            if (amount.signum() == 0) updated.remove(key);
            else updated.put(key, amount);
        });
        gtlcore$pending.clear();
        if (!updated.equals(gtlcore$displayAmounts)) gtlcore$displayAmounts = Map.copyOf(updated);
        if (!gtlcore$receivedDisplay) {
            gtlcore$receivedDisplay = true;
            GTLCore.LOGGER.info("Precise terminal snapshot received: menu={}, overflowKeys={}",
                    ((MEStorageMenu) (Object) this).containerId, updated.size());
        }
    }

    @WrapOperation(method = "broadcastChanges",
                   at = @At(value = "INVOKE",
                            remap = false,
                            target = "Lappeng/api/storage/MEStorage;getAvailableStacks()Lappeng/api/stacks/KeyCounter;"))
    private KeyCounter gtlcore$collectDisplay(MEStorage storage, Operation<KeyCounter> original) {
        if (gtlcore$inventoryIndex == null || gtlcore$inventoryIndex.shouldCompact()) {
            gtlcore$inventoryIndex = new TerminalInventoryIndex();
        }
        TerminalDisplayRead.Snapshot snapshot = TerminalDisplayRead.collect(gtlcore$inventoryIndex, () -> original.call(storage));
        // Sent before AE2 builds its native inventory packet, on the same connection.
        try {
            FastCellDisplayPackets.push((MEStorageMenu) (Object) this, snapshot.exact());
        } catch (RuntimeException failure) {
            if (!gtlcore$displayFailureLogged) {
                gtlcore$displayFailureLogged = true;
                GTLCore.LOGGER.warn("Unable to synchronize precise terminal display; keeping native inventory updates", failure);
            }
        }
        return snapshot.available();
    }

    @WrapOperation(method = "broadcastChanges",
                   at = @At(value = "INVOKE",
                            target = "Lappeng/menu/me/common/MEStorageMenu;getCraftablesFromGrid()Ljava/util/Set;",
                            remap = false))
    private Set<AEKey> gtlcore$cacheCraftables(MEStorageMenu menu, Operation<Set<AEKey>> original) {
        if (gtlcore$nativeVisibility == null) {
            try {
                gtlcore$nativeVisibility = menu.getClass().getMethod("isKeyVisible", AEKey.class)
                        .getDeclaringClass() == MEStorageMenu.class;
            } catch (ReflectiveOperationException failure) {
                gtlcore$nativeVisibility = false;
            }
        }
        // An addon may implement a dynamic visibility predicate. Preserve its native refresh behavior.
        if (!gtlcore$nativeVisibility) return ImmutableSet.copyOf(original.call(menu));
        IGridNode node = networkNode;
        if (node == null && host instanceof IActionHost actionHost) node = actionHost.getActionableNode();
        if (!showsCraftables() || node == null || !node.isActive()) return ImmutableSet.of();
        if (!(node.getGrid().getCraftingService() instanceof TerminalCraftables source)) {
            return ImmutableSet.copyOf(original.call(menu));
        }
        Set<AEKey> snapshot = source.gtlcore$terminalCraftables();
        AEKeyType type = null;
        if (host instanceof ItemMenuHost itemHost && itemHost.getItemStack().getItem() instanceof IBasicCellItem cell) {
            type = cell.getKeyType();
        }
        if (snapshot != gtlcore$craftablesSource || type != gtlcore$craftablesType) {
            if (type == null) gtlcore$craftablesCache = snapshot;
            else {
                var builder = ImmutableSet.<AEKey>builder();
                for (AEKey key : snapshot) if (menu.isKeyVisible(key)) builder.add(key);
                gtlcore$craftablesCache = builder.build();
            }
            gtlcore$craftablesSource = snapshot;
            gtlcore$craftablesType = type;
        }
        return gtlcore$craftablesCache;
    }

    @WrapOperation(method = "broadcastChanges",
                   at = @At(value = "INVOKE",
                            remap = false,
                            target = "Lcom/google/common/collect/Sets;difference(Ljava/util/Set;Ljava/util/Set;)Lcom/google/common/collect/Sets$SetView;"))
    private Sets.SetView<AEKey> gtlcore$craftableChanges(Set<AEKey> first, Set<AEKey> second,
                                                         Operation<Sets.SetView<AEKey>> original) {
        return first == second ? Sets.difference(Set.of(), Set.of()) : original.call(first, second);
    }

    @WrapOperation(method = "broadcastChanges",
                   at = @At(value = "INVOKE",
                            remap = false,
                            target = "Lappeng/api/stacks/KeyCounter;removeAll(Lappeng/api/stacks/KeyCounter;)V"))
    private void gtlcore$compareInventory(KeyCounter previous, KeyCounter current, Operation<Void> original) {
        if (gtlcore$inventoryIndex != null) {
            Set<AEKey> indexed = gtlcore$inventoryIndex.changesFor(previous, current);
            if (indexed != null) {
                gtlcore$inventoryChanges = indexed;
                return;
            }
        }
        if (gtlcore$inventorySnapshot == null) gtlcore$inventorySnapshot = new TerminalInventoryChanges();
        gtlcore$inventoryChanges = gtlcore$inventorySnapshot.between(previous, current);
    }

    @WrapOperation(method = "broadcastChanges",
                   at = @At(value = "INVOKE",
                            remap = false,
                            target = "Lappeng/api/stacks/KeyCounter;removeZeros()V"))
    private void gtlcore$skipDifferenceCleanup(KeyCounter counter, Operation<Void> original) {
        if (gtlcore$inventoryChanges == null) original.call(counter);
    }

    @WrapOperation(method = "broadcastChanges",
                   at = @At(value = "INVOKE",
                            remap = false,
                            target = "Lappeng/api/stacks/KeyCounter;keySet()Ljava/util/Set;"))
    private Set<AEKey> gtlcore$changedInventoryKeys(KeyCounter counter, Operation<Set<AEKey>> original) {
        Set<AEKey> changes = gtlcore$inventoryChanges;
        gtlcore$inventoryChanges = null;
        return changes == null ? original.call(counter) : changes;
    }
}
