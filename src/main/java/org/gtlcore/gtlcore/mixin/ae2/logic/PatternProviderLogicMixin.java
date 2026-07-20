package org.gtlcore.gtlcore.mixin.ae2.logic;

import org.gtlcore.gtlcore.api.crafting.IAutoExpandSettings;
import org.gtlcore.gtlcore.config.ConfigHolder;
import org.gtlcore.gtlcore.integration.ae2.AEUtils;
import org.gtlcore.gtlcore.integration.ae2.compat.MAE2Compat;
import org.gtlcore.gtlcore.integration.ae2.crafting.IPatternProviderAutoExpand;
import org.gtlcore.gtlcore.utils.NumberUtils;

import com.gregtechceu.gtceu.common.data.GTItems;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.networking.IManagedGridNode;
import appeng.api.parts.IPartHost;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.InterfaceLogicHost;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.helpers.patternprovider.PatternProviderTarget;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author EasterFG on 2024/10/13
 */
@Mixin(PatternProviderLogic.class)
public abstract class PatternProviderLogicMixin implements IAutoExpandSettings, IPatternProviderAutoExpand {

    private static final String AUTO_EXPAND_KEY = "gtlcore:auto_expand";

    @Shadow(remap = false)
    @Final
    private PatternProviderLogicHost host;

    @Shadow(remap = false)
    @Final
    private Set<AEKey> patternInputs;

    @Shadow(remap = false)
    private Set<Direction> getActiveSides() {
        throw new AssertionError();
    }

    @Shadow(remap = false)
    private PatternProviderTarget findAdapter(Direction direction) {
        throw new AssertionError();
    }

    @Shadow(remap = false)
    private boolean isBlocking() {
        throw new AssertionError();
    }

    @Shadow(remap = false)
    public abstract void saveChanges();

    @Unique
    private boolean gtlcore$autoExpand = false;

    @Override
    public boolean isPatternAutoExpand() {
        return gtlcore$autoExpand;
    }

    @Override
    public void setPatternAutoExpand(boolean enabled) {
        this.gtlcore$autoExpand = enabled;
    }

    @Inject(method = "writeToNBT", at = @At("TAIL"), remap = false)
    private void gtlcore$writeAutoExpand(CompoundTag tag, CallbackInfo ci) {
        tag.putBoolean(AUTO_EXPAND_KEY, gtlcore$autoExpand);
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"), remap = false)
    private void gtlcore$readAutoExpand(CompoundTag tag, CallbackInfo ci) {
        if (tag.contains(AUTO_EXPAND_KEY)) {
            gtlcore$autoExpand = tag.getBoolean(AUTO_EXPAND_KEY);
        } else {
            gtlcore$autoExpand = isPatternProviderAutoExpandEnabledByDefault();
        }
    }

    @Inject(method = "<init>(Lappeng/api/networking/IManagedGridNode;Lappeng/helpers/patternprovider/PatternProviderLogicHost;)V",
            at = @At("RETURN"),
            remap = false)
    private void gtlcore$initAutoExpandDefault(IManagedGridNode node, PatternProviderLogicHost host, CallbackInfo ci) {
        this.gtlcore$autoExpand = isPatternProviderAutoExpandEnabledByDefault();
    }

    @Inject(method = "<init>(Lappeng/api/networking/IManagedGridNode;Lappeng/helpers/patternprovider/PatternProviderLogicHost;I)V",
            at = @At("RETURN"),
            remap = false)
    private void gtlcore$initAutoExpandDefault(IManagedGridNode node, PatternProviderLogicHost host, int slots,
                                               CallbackInfo ci) {
        this.gtlcore$autoExpand = isPatternProviderAutoExpandEnabledByDefault();
    }

    @Unique
    private static boolean isPatternProviderAutoExpandEnabledByDefault() {
        return ConfigHolder.INSTANCE != null && ConfigHolder.INSTANCE.ae2PatternProviderAutoExpandDefault;
    }

    @Inject(method = "exportSettings", at = @At("TAIL"), remap = false)
    private void gtlcore$exportAutoExpand(CompoundTag output, CallbackInfo ci) {
        output.putBoolean(AUTO_EXPAND_KEY, gtlcore$autoExpand);
    }

    @Inject(method = "importSettings", at = @At("TAIL"), remap = false)
    private void gtlcore$importAutoExpand(CompoundTag input, Player player, CallbackInfo ci) {
        if (input.contains(AUTO_EXPAND_KEY)) {
            gtlcore$autoExpand = input.getBoolean(AUTO_EXPAND_KEY);
            saveChanges();
        }
    }

    @Inject(method = "updatePatterns", at = @At("TAIL"), remap = false)
    public void updatePatternsHook(CallbackInfo ci) {
        patternInputs.remove(AEItemKey.of(GTItems.INTEGRATED_CIRCUIT.get()));
    }

    @Unique
    private Map<PatternProviderTarget, Direction> gtlcore$targetDirections;

    @Unique
    private Map<PatternProviderTarget, Direction> gtlcore$targetDirections() {
        if (gtlcore$targetDirections == null) {
            gtlcore$targetDirections = new IdentityHashMap<>();
        }
        return gtlcore$targetDirections;
    }

    @Inject(method = "pushPattern", at = @At("HEAD"), remap = false)
    private void gtlcore$clearTargetDirections(IPatternDetails patternDetails, KeyCounter[] inputHolder,
                                               CallbackInfoReturnable<Boolean> cir) {
        gtlcore$targetDirections().clear();
    }

    /**
     * Records which direction each {@link PatternProviderTarget} belongs to, so that
     * {@code adapterAcceptsAll} can validate the actual neighbor instead of guessing from
     * the stale {@code sendDirection} field (which is only updated after a successful push).
     * Hooking the return of findAdapter (rather than its call site in pushPattern) keeps this
     * compatible with mods like MAE2 1.x that overwrite pushPattern entirely: the recorded
     * instance is exactly the one the caller will use, and no call-site matching is needed.
     */
    @Inject(method = "findAdapter", at = @At("RETURN"), remap = false)
    private void gtlcore$recordTargetDirection(Direction direction, CallbackInfoReturnable<PatternProviderTarget> cir) {
        var target = cir.getReturnValue();
        if (target != null) {
            gtlcore$targetDirections().put(target, direction);
        }
    }

    @Inject(method = "adapterAcceptsAll", at = @At("HEAD"), remap = false, cancellable = true)
    private void gtlcore$requireFullTargetCapacity(PatternProviderTarget target, KeyCounter[] inputHolder,
                                                   CallbackInfoReturnable<Boolean> cir) {
        if (!gtlcore$autoExpand) {
            return;
        }

        BlockEntity targetBE = null;
        Direction side = gtlcore$targetDirections().get(target);
        if (side != null) {
            targetBE = host.getBlockEntity().getLevel().getBlockEntity(host.getBlockEntity().getBlockPos().relative(side));
        }
        cir.setReturnValue(gtlcore$canTargetAccept(target, targetBE,
                side == null ? null : side.getOpposite(),
                gtlcore$toInputCounter(inputHolder), 1));
    }

    @Override
    public long gtlcore$getMaxPatternOperations(IPatternDetails pattern, long requestedOperations) {
        if (!gtlcore$autoExpand || requestedOperations <= 1 || !pattern.supportsPushInputsToExternalInventory()) {
            return Math.min(requestedOperations, 1);
        }

        var blockEntity = host.getBlockEntity();
        Level level = blockEntity.getLevel();
        if (level == null) {
            return requestedOperations;
        }

        var baseInputs = gtlcore$toInputCounter(pattern);
        long maxOperations = 0;
        long p2pMaxOperations = Long.MAX_VALUE;
        boolean hasAdapter = false;
        boolean hasP2PTunnel = false;
        boolean hasUnlimitedMachine = false;

        for (var direction : getActiveSides()) {
            var targetPosition = blockEntity.getBlockPos().relative(direction);
            var targetBlockEntity = level.getBlockEntity(targetPosition);
            var machine = ICraftingMachine.of(level, targetPosition, direction.getOpposite(), targetBlockEntity);
            if (machine != null && machine.acceptsPlans()) {
                if (MAE2Compat.isPatternP2PTunnelLogic(machine)) {
                    // MAE2 pattern P2P tunnels route one whole batch to a single output.
                    // The safe expansion is therefore limited by the smallest capacity among
                    // all of the tunnel's outputs. Because the provider may select this side
                    // before any other active side, we cap the global operation count by it.
                    hasP2PTunnel = true;
                    p2pMaxOperations = Math.min(p2pMaxOperations,
                            MAE2Compat.getPatternP2PMaxOperations(machine, pattern, requestedOperations,
                                    level, baseInputs, isBlocking(), patternInputs, this));
                } else {
                    hasUnlimitedMachine = true;
                }
                continue;
            }

            // MAE2 1.x pattern P2P tunnels are plain parts on the adjacent cable:
            // no ICraftingMachine and no external storage, so findAdapter below
            // would miss them entirely. Detect the part like MAE2 1.x itself does.
            if (targetBlockEntity instanceof IPartHost partHost) {
                var part = partHost.getPart(direction.getOpposite());
                if (part != null && MAE2Compat.isLegacyPatternP2PTunnel(part)) {
                    hasP2PTunnel = true;
                    p2pMaxOperations = Math.min(p2pMaxOperations,
                            MAE2Compat.getLegacyPatternP2PMaxOperations(part, requestedOperations,
                                    level, baseInputs, isBlocking(), patternInputs, this));
                    continue;
                }
            }

            var target = findAdapter(direction);
            if (target == null || (isBlocking() && target.containsPatternInput(patternInputs))) {
                continue;
            }
            hasAdapter = true;
            maxOperations = Math.max(maxOperations,
                    gtlcore$findMaxOperations(target, targetBlockEntity, direction.getOpposite(),
                            baseInputs, requestedOperations));
        }

        if (hasUnlimitedMachine && !hasP2PTunnel) {
            return requestedOperations;
        }

        if (!hasAdapter && !hasP2PTunnel) {
            // No usable target this tick (nothing adjacent, or every target was skipped
            // by blocking mode). Vanilla pushPattern will reject the push with the same
            // side/target view, so only extract a single operation's worth of inputs
            // instead of churning the whole remaining batch in and out every tick.
            return 1;
        }

        long result = hasP2PTunnel ? p2pMaxOperations : maxOperations;
        if (hasP2PTunnel && hasAdapter) {
            result = Math.min(result, maxOperations);
        }
        return Math.max(1, result);
    }

    @Override
    public long gtlcore$findMaxOperationsForTarget(PatternProviderTarget target, BlockEntity targetBE, Direction side,
                                                   KeyCounter baseInputs, long requestedOperations) {
        return gtlcore$findMaxOperations(target, targetBE, side, baseInputs, requestedOperations);
    }

    @Unique
    private long gtlcore$findMaxOperations(PatternProviderTarget target, BlockEntity targetBE, Direction side,
                                           KeyCounter baseInputs, long requestedOperations) {
        if (!gtlcore$canTargetAccept(target, targetBE, side, baseInputs, requestedOperations)) {
            long low = 0;
            long high = requestedOperations - 1;
            while (low < high) {
                long middle = low + ((high - low + 1) >>> 1);
                if (gtlcore$canTargetAccept(target, targetBE, side, baseInputs, middle)) {
                    low = middle;
                } else {
                    high = middle - 1;
                }
            }
            return low;
        }
        return requestedOperations;
    }

    @Unique
    private boolean gtlcore$canTargetAccept(PatternProviderTarget target, BlockEntity targetBE, Direction side,
                                            KeyCounter baseInputs, long operations) {
        if (operations <= 0) {
            return true;
        }

        // For ME interfaces and other AE2 blocks we only have the abstract MEStorage view,
        // so fall back to the aggregate capacity check. For plain block inventories we can
        // simulate exact slot/tank usage via capabilities.
        if (targetBE == null || side == null || targetBE instanceof InterfaceLogicHost || targetBE.getClass().getName().startsWith("appeng.")) {
            return gtlcore$targetAcceptsAll(target, baseInputs, operations);
        }

        var itemCap = targetBE.getCapability(ForgeCapabilities.ITEM_HANDLER, side);
        var fluidCap = targetBE.getCapability(ForgeCapabilities.FLUID_HANDLER, side);
        boolean hasHandler = itemCap.isPresent() || fluidCap.isPresent();

        // First pass: ensure each individual key can fit its own available space (cheap reject).
        if (!gtlcore$targetAcceptsAll(target, baseInputs, operations)) {
            return false;
        }

        if (itemCap.isPresent()) {
            IItemHandler handler = itemCap.orElseThrow(NullPointerException::new);
            for (var input : baseInputs) {
                AEKey key = input.getKey();
                if (key.getType() != AEKeyType.items() || !(key instanceof AEItemKey itemKey)) {
                    continue;
                }
                long amount = NumberUtils.saturatedMultiply(input.getLongValue(), operations);
                if (amount <= 0) {
                    continue;
                }
                if (!gtlcore$canItemHandlerAccept(handler, itemKey, amount)) {
                    return false;
                }
            }
        }

        if (fluidCap.isPresent()) {
            IFluidHandler handler = fluidCap.orElseThrow(NullPointerException::new);
            for (var input : baseInputs) {
                AEKey key = input.getKey();
                if (key.getType() != AEKeyType.fluids() || !(key instanceof AEFluidKey fluidKey)) {
                    continue;
                }
                long amount = NumberUtils.saturatedMultiply(input.getLongValue(), operations);
                if (amount <= 0) {
                    continue;
                }
                if (!gtlcore$canFluidHandlerAccept(handler, fluidKey, amount)) {
                    return false;
                }
            }
        }

        if (!hasHandler) {
            return gtlcore$targetAcceptsAll(target, baseInputs, operations);
        }
        return true;
    }

    @Unique
    private boolean gtlcore$canItemHandlerAccept(IItemHandler handler, AEItemKey itemKey, long amount) {
        long remaining = amount;
        int slots = handler.getSlots();
        ItemStack representative = itemKey.toStack();

        for (int i = 0; i < slots && remaining > 0; i++) {
            ItemStack current = handler.getStackInSlot(i);
            if (!current.isEmpty() && !ItemStack.isSameItem(current, representative)) {
                continue;
            }

            // Simulate inserting the whole remainder into this slot. This respects both
            // filters and unlimited-capacity slots (e.g. gtmthings huge buses), instead of
            // clamping per-slot capacity to the item's vanilla max stack size.
            int chunk = (int) Math.min(remaining, Integer.MAX_VALUE);
            ItemStack remainder = handler.insertItem(i, itemKey.toStack(chunk), true);
            int accepted = remainder.isEmpty() ? chunk : Math.max(0, chunk - remainder.getCount());
            if (accepted <= 0) {
                continue;
            }

            remaining -= accepted;
        }

        return remaining <= 0;
    }

    @Unique
    private boolean gtlcore$canFluidHandlerAccept(IFluidHandler handler, AEFluidKey fluidKey, long amount) {
        long remaining = amount;
        int tanks = handler.getTanks();
        FluidStack representative = fluidKey.toStack(1);

        for (int i = 0; i < tanks && remaining > 0; i++) {
            FluidStack current = handler.getFluidInTank(i);
            if (!current.isEmpty() && !current.isFluidEqual(representative)) {
                continue;
            }

            long free = handler.getTankCapacity(i) - current.getAmount();
            if (free <= 0) {
                continue;
            }

            // Verify the handler accepts this fluid at all.
            if (handler.fill(representative, IFluidHandler.FluidAction.SIMULATE) <= 0) {
                continue;
            }

            remaining -= free;
        }

        return remaining <= 0;
    }

    @Unique
    private boolean gtlcore$targetAcceptsAll(PatternProviderTarget target, KeyCounter baseInputs, long operations) {
        // Per-key capacity check: each key must fit its own required amount.
        // Aggregating per AEKeyType with a min() capacity would let a single
        // special-stack item (e.g. maxStackSize=1) cap the whole pattern.
        long requiredItemSlots = 0;
        long availableItemSlots = 0;

        for (var input : baseInputs) {
            long amount = NumberUtils.saturatedMultiply(input.getLongValue(), operations);
            if (amount <= 0) {
                continue;
            }

            AEKey key = input.getKey();
            long available = target.insert(key, Long.MAX_VALUE, Actionable.SIMULATE);
            if (amount > available) {
                return false;
            }

            // For items we additionally enforce a slot-aware bound, because single-slot
            // inventories cannot mix different item types in the same slot.
            if (key.getType() == AEKeyType.items() && key instanceof AEItemKey itemKey) {
                int maxStack = Math.max(1, itemKey.getItem().getMaxStackSize());
                requiredItemSlots = NumberUtils.saturatedAdd(requiredItemSlots, gtlcore$ceilDiv(amount, maxStack));
                availableItemSlots = Math.max(availableItemSlots, gtlcore$ceilDiv(available, maxStack));
            }
        }

        return requiredItemSlots <= availableItemSlots;
    }

    @Unique
    private long gtlcore$ceilDiv(long a, long b) {
        if (b <= 0) {
            return Long.MAX_VALUE;
        }
        return a / b + (a % b == 0 ? 0 : 1);
    }

    @Unique
    private KeyCounter gtlcore$toInputCounter(KeyCounter[] inputHolder) {
        var combinedInputs = new KeyCounter();
        for (var inputList : inputHolder) {
            for (var input : inputList) {
                // Programmed circuits only configure the circuit slot (always accepted,
                // overwritten in place), so they must not participate in capacity checks.
                if (AEUtils.isIntegratedCircuit(input.getKey())) {
                    continue;
                }
                combinedInputs.add(input.getKey(), input.getLongValue());
            }
        }
        return combinedInputs;
    }

    @Unique
    private KeyCounter gtlcore$toInputCounter(IPatternDetails pattern) {
        var baseInputs = new KeyCounter();
        for (var input : pattern.getInputs()) {
            var possibleInputs = input.getPossibleInputs();
            if (possibleInputs.length == 0) {
                continue;
            }
            // See above: circuits are exempt from expansion capacity checks.
            if (AEUtils.isIntegratedCircuit(possibleInputs[0].what())) {
                continue;
            }
            baseInputs.add(possibleInputs[0].what(), input.getMultiplier());
        }
        return baseInputs;
    }
}
