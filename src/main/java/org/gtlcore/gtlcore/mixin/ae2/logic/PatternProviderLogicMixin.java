package org.gtlcore.gtlcore.mixin.ae2.logic;

import org.gtlcore.gtlcore.GTLCore;
import org.gtlcore.gtlcore.api.crafting.IAutoExpandSettings;
import org.gtlcore.gtlcore.config.ConfigHolder;
import org.gtlcore.gtlcore.integration.ae2.AEUtils;
import org.gtlcore.gtlcore.integration.ae2.compat.MAE2Compat;
import org.gtlcore.gtlcore.integration.ae2.crafting.IPatternProviderAutoExpand;
import org.gtlcore.gtlcore.mixin.ae2.storage.CompositeStorageAccessor;
import org.gtlcore.gtlcore.mixin.ae2.storage.DelegatingMEInventoryAccessor;
import org.gtlcore.gtlcore.mixin.ae2.storage.FluidHandlerFacadeAccessor;
import org.gtlcore.gtlcore.mixin.ae2.storage.ItemHandlerFacadeAccessor;
import org.gtlcore.gtlcore.mixin.ae2.storage.NetworkStorageAccessor;
import org.gtlcore.gtlcore.utils.NumberUtils;

import com.gregtechceu.gtceu.common.data.GTItems;

import com.lowdragmc.lowdraglib.side.fluid.IFluidTransfer;
import com.lowdragmc.lowdraglib.side.fluid.forge.FluidHelperImpl;

import net.minecraft.core.BlockPos;
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
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.helpers.InterfaceLogicHost;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.helpers.patternprovider.PatternProviderTarget;
import appeng.hooks.ticking.TickHandler;
import appeng.me.storage.CompositeStorage;
import appeng.me.storage.DelegatingMEInventory;
import appeng.me.storage.NetworkStorage;
import com.hepdd.gtmthings.common.block.machine.multiblock.part.HugeBusPartMachine;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
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

    /**
     * Push-time bookkeeping for auto-expanded batches. The capacity estimate already sized the
     * batch; this hook must NOT enforce all-or-nothing acceptance - vanilla accepts a batch when
     * every key can insert at all and drips the unfit remainder through the send list. Forcing
     * full acceptance turns a one-operation overestimate into a permanent stall (nothing ever
     * enters, nothing ever drains). Instead: detect when the real machine cannot take the whole
     * batch, refresh the caches so the next estimate rescans, and cool the provider down for the
     * rest of the tick - then let vanilla decide acceptance.
     */
    @Inject(method = "adapterAcceptsAll", at = @At("HEAD"), remap = false)
    private void gtlcore$trackTargetCapacity(PatternProviderTarget target, KeyCounter[] inputHolder,
                                             CallbackInfoReturnable<Boolean> cir) {
        if (!gtlcore$autoExpand) {
            return;
        }

        BlockEntity targetBE = null;
        Direction side = gtlcore$targetDirections().get(target);
        if (side != null) {
            targetBE = host.getBlockEntity().getLevel().getBlockEntity(host.getBlockEntity().getBlockPos().relative(side));
        }
        if (targetBE == null || side == null || targetBE instanceof InterfaceLogicHost || gtlcore$isInterfacePart(targetBE, side) ||
                gtlcore$isHugeComposite(targetBE, side)) {
            gtlcore$accountPushAgainstProbeCache(side, inputHolder);
            return;
        }
        boolean allFits = gtlcore$canTargetAccept(target, targetBE,
                side.getOpposite(), gtlcore$toInputCounter(inputHolder), 1);
        if (!allFits) {
            if (gtlcore$scanLoggingEnabled()) {
                StringBuilder sb = new StringBuilder();
                for (KeyCounter counter : inputHolder) {
                    for (var input : counter) {
                        sb.append(input.getKey()).append('x').append(input.getLongValue()).append(' ');
                    }
                }
                GTLCore.LOGGER.info("[GTLCore] push partial at {} (target {}): batch [{}] - remainder drips via send list",
                        targetBE.getBlockPos().toShortString(), targetBE.getClass().getSimpleName(), sb);
            }
            // The cached capacity estimate was stale - drop it so the next evaluation rescans.
            gtlcore$capCacheTick = Long.MIN_VALUE;
            gtlcore$snapshots.clear();
            gtlcore$snapTick = Long.MIN_VALUE;
            // But not within the same tick: the dispatch loop retries every round, and each
            // retry would otherwise rescan a machine that just told us it is full. Cool down
            // until next tick - the machine drains over ticks anyway, so this cannot deadlock.
            gtlcore$rejectTick = TickHandler.instance().getCurrentTick();
        }
    }

    @Unique
    private static final long CAP_CACHE_TTL_TICKS = 20;

    @Unique
    private long gtlcore$rejectTick = Long.MIN_VALUE;

    /** Per-tick probe results shared across patterns hitting the same side of this provider. */
    @Unique
    private long gtlcore$probeTick = Long.MIN_VALUE;
    @Unique
    private final Map<Direction, Map<AEKey, Long>> gtlcore$probeCache = new HashMap<>();

    /** Per-interface subnet slot snapshots, rebuilt at most once per TTL. */
    @Unique
    private long gtlcore$subnetTick = Long.MIN_VALUE;
    @Unique
    private final Map<BlockPos, MachineSnapshot> gtlcore$subnetSnapshots = new HashMap<>();

    @Unique
    private long gtlcore$capCacheTick = Long.MIN_VALUE;
    @Unique
    private IPatternDetails gtlcore$capCachePattern;
    @Unique
    private long gtlcore$capCacheCapacity;
    @Unique
    private long gtlcore$capCacheCeiling;

    @Unique
    private static boolean gtlcore$scanLoggingEnabled() {
        return ConfigHolder.INSTANCE != null &&
                ConfigHolder.INSTANCE.debugLogging.enableAe2PatternCapacityScanLogging;
    }

    @Override
    public long gtlcore$getMaxPatternOperations(IPatternDetails pattern, long requestedOperations) {
        // The capacity scan walks every active side and simulates inserts per slot, which is
        // expensive; the crafting dispatch loop calls this per task per provider per round,
        // and with pack-wide auto-expand the distinct (provider, pattern) pairs alone reach
        // tens of thousands per tick. Cache with a 1-second TTL: overestimates are caught by
        // adapterAcceptsAll at push time (which also invalidates this cache), so a stale
        // value can never cause a wrong push.
        long now = TickHandler.instance().getCurrentTick();
        boolean log = gtlcore$scanLoggingEnabled();
        if (log) {}
        if (now == gtlcore$rejectTick) {
            // A push was already rejected this tick - the target has not had time to drain.
            // Report "1" without rescanning; next tick reevaluates normally.
            if (log) {}
            return 1;
        }
        // The cache stores a capacity LOWER BOUND (pattern-keyed, request-independent):
        // dispatch calls arrive with a shrinking "remaining" count every round, so keying on
        // the requested amount would miss forever. min(bound, requested) is always safe -
        // overestimates get caught by adapterAcceptsAll at push time (and invalidate this).
        if (now - gtlcore$capCacheTick < CAP_CACHE_TTL_TICKS && gtlcore$capCachePattern == pattern) {
            boolean ceilingWasHit = gtlcore$capCacheCapacity == gtlcore$capCacheCeiling;
            boolean needRescan = ceilingWasHit && requestedOperations > gtlcore$capCacheCapacity;
            if (!needRescan) {
                if (log) {}
                return Math.min(gtlcore$capCacheCapacity, requestedOperations);
            }
        }
        long t0 = log ? System.nanoTime() : 0;
        long computed = gtlcore$computeMaxPatternOperations(pattern, requestedOperations);
        if (log) {
            long elapsed = System.nanoTime() - t0;
            if (elapsed > 50_000_000L) {
                GTLCore.LOGGER.warn(
                        "[GTLCore] slow pattern capacity scan: {}ms at {} for pattern {} (result={}, requested={})",
                        elapsed / 1_000_000, host.getBlockEntity().getBlockPos().toShortString(),
                        pattern.getPrimaryOutput().what().toString(), computed, requestedOperations);
            }
        }
        // Only cache real capacity results: the "blocked / no target → 1" verdicts are cheap
        // to recompute and state-critical - caching them across ticks locks blocking-mode
        // providers into a one-per-push loop after the machine drains.
        if (computed > 1) {
            gtlcore$capCacheTick = now;
            gtlcore$capCachePattern = pattern;
            gtlcore$capCacheCapacity = computed;
            gtlcore$capCacheCeiling = requestedOperations;
        } else {
            gtlcore$capCacheTick = Long.MIN_VALUE;
        }
        return computed;
    }

    @Unique
    private long gtlcore$computeMaxPatternOperations(IPatternDetails pattern, long requestedOperations) {
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
        // Giant GTMThings buses/hatches: every kind gets its own 2.1G slot, so capacity never
        // binds - only the KIND count does (a new kind needs a free slot).
        if (targetBE instanceof com.gregtechceu.gtceu.api.machine.IMachineBlockEntity machineBE &&
                machineBE.getMetaMachine() instanceof HugeBusPartMachine) {
            var hugeCap = targetBE.getCapability(ForgeCapabilities.ITEM_HANDLER, side);
            if (hugeCap.isPresent()) {
                IItemHandler handler = hugeCap.orElseThrow(NullPointerException::new);
                int freeSlots = 0;
                for (int i = 0; i < handler.getSlots(); i++) {
                    if (handler.getStackInSlot(i).isEmpty()) {
                        freeSlots++;
                    }
                }
                for (var input : baseInputs) {
                    if (!(input.getKey() instanceof AEItemKey itemKey)) {
                        continue;
                    }
                    boolean present = false;
                    for (int i = 0; i < handler.getSlots(); i++) {
                        if (ItemStack.isSameItem(handler.getStackInSlot(i), itemKey.toStack())) {
                            present = true;
                            break;
                        }
                    }
                    if (!present) {
                        if (freeSlots <= 0) {
                            return 0;
                        }
                        freeSlots--;
                    }
                }
            }
            return requestedOperations;
        }
        // Network-backed interface targets (incl. ExtendedAE interfaces feeding subnets):
        // resolve the interface's network storage down to the real machine handlers and run the
        // exact slot-aware computation against them - this sees the cross-key slot competition
        // that per-key network probes cannot (e.g. 16-stack gears next to 64-stack rods).
        if (targetBE == null || side == null || targetBE instanceof InterfaceLogicHost || gtlcore$isInterfacePart(targetBE, side)) {
            if (targetBE != null && side != null) {
                long subnetCap = gtlcore$subnetCapacity(targetBE, side, baseInputs, requestedOperations);
                if (subnetCap >= 0) {
                    return subnetCap;
                }
            }
            return gtlcore$probeCapacity(target, targetBE, side, baseInputs, requestedOperations);
        }
        // Fast path: full batch fits (verified against the real handler).
        if (gtlcore$canTargetAccept(target, targetBE, side, baseInputs, requestedOperations)) {
            return requestedOperations;
        }
        // Single-ingredient patterns have no slot competition by construction: one probe is
        // the exact answer, no batch check needed.
        if (gtlcore$isSingleIngredient(baseInputs)) {
            return gtlcore$probeCapacity(target, targetBE, side, baseInputs, requestedOperations);
        }
        // Huge composite inventories (merged multi-part views with hundreds of slots): slot
        // competition cannot strand anything at that scale, and ANY capability walk over the
        // merged view is quadratic. Probe per key once instead; the per-tick probe cache shares
        // results across patterns and providers.
        var itemCap = targetBE.getCapability(ForgeCapabilities.ITEM_HANDLER, side);
        if (itemCap.isPresent() && itemCap.orElseThrow(NullPointerException::new).getSlots() > 256) {
            return gtlcore$probeCapacity(target, targetBE, side, baseInputs, requestedOperations);
        }
        // Small machines: exact slot-aware computation, but against a per-TTL in-memory
        // snapshot of the container - the real handler is only walked when the snapshot is
        // rebuilt, and every pattern on this side shares it.
        MachineSnapshot snap = gtlcore$snapshotMachine(targetBE, side);
        long result = gtlcore$snapshotCapacity(snap, baseInputs, requestedOperations);
        return result;
    }

    @Unique
    private static boolean gtlcore$isHugeComposite(BlockEntity targetBE, Direction side) {
        if (targetBE == null || side == null) {
            return false;
        }
        var itemCap = targetBE.getCapability(ForgeCapabilities.ITEM_HANDLER, side);
        return itemCap.isPresent() && itemCap.orElseThrow(NullPointerException::new).getSlots() > 256;
    }

    @Unique
    private void gtlcore$accountPushAgainstProbeCache(Direction side, KeyCounter[] inputHolder) {
        if (side == null) {
            return;
        }
        Map<AEKey, Long> cache = gtlcore$probeCache.get(side);
        if (cache == null || cache.isEmpty()) {
            return;
        }
        for (KeyCounter counter : inputHolder) {
            for (var input : counter) {
                Long cached = cache.get(input.getKey());
                if (cached != null) {
                    cache.put(input.getKey(), Math.max(0, cached - input.getLongValue()));
                }
            }
        }
    }

    @Unique
    private static boolean gtlcore$slotAccepts(SlotSnap s, AEKey key, Map<AEKey, Map<Integer, Boolean>> validityMemo) {
        // Memo key combines handler identity with the slot index (slot indices repeat across
        // handlers).
        int memoKey = System.identityHashCode(s.handler()) * 100003 + s.slot();
        var memo = validityMemo.computeIfAbsent(key, k -> new HashMap<>());
        Boolean cached = memo.get(memoKey);
        if (cached != null) {
            return cached;
        }
        boolean ok;
        if (s.handler() instanceof IItemHandler ih) {
            ok = ih.insertItem(s.slot(), ((AEItemKey) key).toStack(1), true).isEmpty();
        } else if (s.handler() instanceof IFluidHandler fh) {
            // Per-tank probe: list-level fill() would happily land in a different tank (e.g. the
            // input tank) and mislabel an output tank as usable.
            IFluidTransfer ft = gtlcore$asFluidTransfer(fh);
            ok = ft != null ? ft.fill(s.slot(), FluidHelperImpl.toFluidStack(((AEFluidKey) key).toStack(1)), true, false) == 1 : fh.isFluidValid(s.slot(), ((AEFluidKey) key).toStack(1));
        } else {
            ok = true;
        }
        memo.put(memoKey, ok);
        return ok;
    }

    @Unique
    private static IFluidTransfer gtlcore$asFluidTransfer(IFluidHandler handler) {
        if (handler instanceof IFluidTransfer transfer) {
            return transfer;
        }
        // Forge-facing anonymous wrapper (FluidTransferHelperImpl$1): reflect the captured
        // delegate field - the name is compiler-generated but stable.
        try {
            for (var field : handler.getClass().getDeclaredFields()) {
                if (field.getName().equals("val$fluidTransfer")) {
                    field.setAccessible(true);
                    return (IFluidTransfer) field.get(handler);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {}
        return null;
    }

    @Unique
    private static boolean gtlcore$isSingleIngredient(KeyCounter baseInputs) {
        int count = 0;
        for (var input : baseInputs) {
            if (input.getLongValue() > 0 && ++count > 1) {
                return false;
            }
        }
        return count == 1;
    }

    /**
     * One slot/tank of a snapshot: key currently inside (null = empty), how much more fits, slot limit, and the owning
     * handler+index for filter checks.
     */
    @Unique
    private record SlotSnap(AEKey key, long free, long limit, Object handler, int slot) {}

    @Unique
    private record MachineSnapshot(List<SlotSnap> items, List<SlotSnap> fluids,
                                   Map<AEKey, Map<Integer, Boolean>> itemValidity, Map<AEKey, Map<Integer, Boolean>> fluidValidity) {}

    /** Per-side snapshots, rebuilt at most once per TTL; the real handler is only walked then. */
    @Unique
    private long gtlcore$snapTick = Long.MIN_VALUE;
    @Unique
    private final Map<Direction, MachineSnapshot> gtlcore$snapshots = new HashMap<>();

    /**
     * The snapshot IS the expensive part (one capability walk); everything per-pattern afterwards
     * is pure arithmetic against it. Circuits stay out of baseInputs, so a circuit occupying a
     * slot is just an occupied slot here - it never appears as capacity for anything.
     */
    @Unique
    private MachineSnapshot gtlcore$snapshotMachine(BlockEntity targetBE, Direction side) {
        long now = TickHandler.instance().getCurrentTick();
        if (now - gtlcore$snapTick >= CAP_CACHE_TTL_TICKS) {
            gtlcore$snapTick = now;
            gtlcore$snapshots.clear();
        }
        MachineSnapshot cached = gtlcore$snapshots.get(side);
        if (cached != null) {
            return cached;
        }
        List<SlotSnap> items = new ArrayList<>();
        List<SlotSnap> fluids = new ArrayList<>();
        IItemHandler itemHandler = null;
        IFluidHandler fluidHandler = null;
        var itemCap = targetBE.getCapability(ForgeCapabilities.ITEM_HANDLER, side);
        if (itemCap.isPresent()) {
            itemHandler = itemCap.orElseThrow(NullPointerException::new);
            for (int i = 0; i < itemHandler.getSlots(); i++) {
                ItemStack cur = itemHandler.getStackInSlot(i);
                if (cur.isEmpty()) {
                    long limit = itemHandler.getSlotLimit(i);
                    items.add(new SlotSnap(null, limit, limit, itemHandler, i));
                } else {
                    AEItemKey key = AEItemKey.of(cur);
                    ItemStack probe = key.toStack(Integer.MAX_VALUE);
                    ItemStack rem = itemHandler.insertItem(i, probe, true);
                    long accepted = rem.isEmpty() ? Integer.MAX_VALUE : Integer.MAX_VALUE - (long) rem.getCount();
                    items.add(new SlotSnap(key, accepted, itemHandler.getSlotLimit(i), itemHandler, i));
                }
            }
        }
        var fluidCap = targetBE.getCapability(ForgeCapabilities.FLUID_HANDLER, side);
        if (fluidCap.isPresent()) {
            fluidHandler = fluidCap.orElseThrow(NullPointerException::new);
            for (int i = 0; i < fluidHandler.getTanks(); i++) {
                FluidStack cur = fluidHandler.getFluidInTank(i);
                if (cur.isEmpty()) {
                    long cap = fluidHandler.getTankCapacity(i);
                    fluids.add(new SlotSnap(null, cap, cap, fluidHandler, i));
                } else {
                    AEFluidKey key = AEFluidKey.of(cur);
                    long accepted = fluidHandler instanceof IFluidTransfer transfer ? transfer.fill(i, FluidHelperImpl.toFluidStack(key.toStack(Integer.MAX_VALUE)), true, false) : fluidHandler.fill(key.toStack(Integer.MAX_VALUE), IFluidHandler.FluidAction.SIMULATE);
                    fluids.add(new SlotSnap(key, accepted, fluidHandler.getTankCapacity(i), fluidHandler, i));
                }
            }
        }
        MachineSnapshot snap = new MachineSnapshot(items, fluids, new HashMap<>(), new HashMap<>());
        gtlcore$snapshots.put(side, snap);
        return snap;
    }

    /** In-memory feasibility check mirroring the real batch acceptance check's greedy allocation. */
    @Unique
    private boolean gtlcore$snapshotFits(MachineSnapshot snap, KeyCounter baseInputs, long ops) {
        List<Entry<AEItemKey>> itemReqs = new ArrayList<>();
        List<Entry<AEFluidKey>> fluidReqs = new ArrayList<>();
        for (var input : baseInputs) {
            if (input.getKey() instanceof AEItemKey itemKey && input.getLongValue() > 0) {
                itemReqs.add(new Entry<>(itemKey, input.getLongValue()));
            } else if (input.getKey() instanceof AEFluidKey fluidKey && input.getLongValue() > 0) {
                fluidReqs.add(new Entry<>(fluidKey, input.getLongValue()));
            }
        }
        if (!gtlcore$snapshotFitsItems(snap.items(), itemReqs, ops, snap.itemValidity())) {
            return false;
        }
        return gtlcore$snapshotFitsItems(snap.fluids(), fluidReqs, ops, snap.fluidValidity());
    }

    @Unique
    private static <K extends AEKey> boolean gtlcore$snapshotFitsItems(List<SlotSnap> slots, List<Entry<K>> requirements, long ops,
                                                                       Map<AEKey, Map<Integer, Boolean>> validityMemo) {
        if (requirements.isEmpty()) {
            return true;
        }
        int n = slots.size();
        AEKey[] owner = new AEKey[n];
        long[] reserved = new long[n];
        for (var req : requirements) {
            K key = req.key();
            long perOp = req.amount();
            int itemMaxStack = key instanceof AEItemKey ik ? ik.toStack().getMaxStackSize() : Integer.MAX_VALUE;
            long remaining = NumberUtils.saturatedMultiply(perOp, ops);
            for (int i = 0; i < n && remaining > 0; i++) {
                SlotSnap s = slots.get(i);
                long capacity;
                if (s.key() == null) {
                    if (owner[i] != null && owner[i] != key) {
                        continue;
                    }
                    // Empty slots are NOT automatically usable: IO-restricted or filtered slots
                    // (circuit, battery, output...) reject inserts. The 1-unit probe covers every
                    // rejection reason; results are memoized per (handler, slot, key) for the whole
                    // snapshot lifetime because each probe may walk a composite handler.
                    if (s.handler() != null && !gtlcore$slotAccepts(s, key, validityMemo)) {
                        continue;
                    }
                    // Normal slots cap at the item's max stack size; unlimited-capacity
                    // slots (limit way past any stack size) take the full limit instead.
                    capacity = s.limit() > 1024 ? s.limit() : Math.min(s.limit(), itemMaxStack);
                } else {
                    if (!s.key().equals(key)) {
                        continue;
                    }
                    capacity = s.free();
                }
                long free = capacity - reserved[i];
                if (free <= 0) {
                    continue;
                }
                long taken = Math.min(free, remaining);
                reserved[i] += taken;
                if (s.key() == null) {
                    owner[i] = key;
                }
                remaining -= taken;
            }
            if (remaining > 0) {
                return false;
            }
        }
        return true;
    }

    /** Exact capacity: binary search, but every feasibility probe runs against the snapshot. */
    @Unique
    private long gtlcore$snapshotCapacity(MachineSnapshot snap, KeyCounter baseInputs, long requestedOperations) {
        if (gtlcore$snapshotFits(snap, baseInputs, requestedOperations)) {
            return requestedOperations;
        }
        if (!gtlcore$snapshotFits(snap, baseInputs, 1)) {
            return 0;
        }
        long low = 0;
        long high = requestedOperations - 1;
        while (low < high) {
            long middle = low + ((high - low + 1) >>> 1);
            if (gtlcore$snapshotFits(snap, baseInputs, middle)) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        return low;
    }

    /**
     * Exact subnet slot capacity for interface targets: walk the interface's network storage
     * down to the real machine handlers behind its storage buses (NetworkStorage ->
     * MEInventoryHandler -> CompositeStorage -> ExternalStorageFacade -> handler), snapshot all
     * of their slots once per TTL, and run the exact slot-aware allocation against the combined
     * view. This sees cross-key slot competition that per-key probes cannot. Returns -1 when the
     * network view cannot be resolved (caller falls back to probes).
     */
    @Unique
    private long gtlcore$subnetCapacity(BlockEntity targetBE, Direction side, KeyCounter baseInputs,
                                        long requestedOperations) {
        InterfaceLogicHost logicHost = targetBE instanceof InterfaceLogicHost ih ? ih : (targetBE instanceof IPartHost ph && ph.getPart(side) instanceof InterfaceLogicHost ih2 ? ih2 : null);
        if (logicHost == null) {
            return -1;
        }
        long now = TickHandler.instance().getCurrentTick();
        if (now - gtlcore$subnetTick >= CAP_CACHE_TTL_TICKS) {
            gtlcore$subnetTick = now;
            gtlcore$subnetSnapshots.clear();
        }
        MachineSnapshot snap = gtlcore$subnetSnapshots.get(targetBE.getBlockPos());
        if (snap == null) {
            snap = gtlcore$buildSubnetSnapshot(logicHost);
            if (snap == null) {
                return -1;
            }
            gtlcore$subnetSnapshots.put(targetBE.getBlockPos(), snap);
        }
        return gtlcore$snapshotCapacity(snap, baseInputs, requestedOperations);
    }

    @Unique
    private MachineSnapshot gtlcore$buildSubnetSnapshot(InterfaceLogicHost logicHost) {
        var gridNode = logicHost.getInterfaceLogic().getActionableNode();
        var grid = gridNode == null ? null : gridNode.getGrid();
        if (grid == null || !(grid.getStorageService().getInventory() instanceof NetworkStorage net)) {
            return null;
        }
        List<SlotSnap> items = new ArrayList<>();
        List<SlotSnap> fluids = new ArrayList<>();
        for (var tier : ((NetworkStorageAccessor) net).gtlcore$getPriorityInventory().values()) {
            for (MEStorage storage : tier) {
                MEStorage core = storage;
                while (core instanceof DelegatingMEInventory delegating) {
                    core = ((DelegatingMEInventoryAccessor) delegating).gtlcore$getDelegate();
                }
                if (core instanceof CompositeStorage composite) {
                    for (MEStorage inner : ((CompositeStorageAccessor) composite).gtlcore$getStorages().values()) {
                        gtlcore$collectFacadeSlots(inner, items, fluids);
                    }
                } else {
                    gtlcore$collectFacadeSlots(core, items, fluids);
                }
            }
        }
        return new MachineSnapshot(items, fluids, new HashMap<>(), new HashMap<>());
    }

    @Unique
    private static void gtlcore$collectFacadeSlots(MEStorage storage, List<SlotSnap> items, List<SlotSnap> fluids) {
        if (storage instanceof ItemHandlerFacadeAccessor itemFacade) {
            IItemHandler handler = itemFacade.gtlcore$getHandler();
            for (int i = 0; i < handler.getSlots(); i++) {
                ItemStack cur = handler.getStackInSlot(i);
                if (cur.isEmpty()) {
                    long limit = handler.getSlotLimit(i);
                    items.add(new SlotSnap(null, limit, limit, handler, i));
                } else {
                    AEItemKey key = AEItemKey.of(cur);
                    ItemStack probe = key.toStack(Integer.MAX_VALUE);
                    ItemStack rem = handler.insertItem(i, probe, true);
                    long accepted = rem.isEmpty() ? Integer.MAX_VALUE : Integer.MAX_VALUE - (long) rem.getCount();
                    items.add(new SlotSnap(key, accepted, handler.getSlotLimit(i), handler, i));
                }
            }
        } else if (storage instanceof FluidHandlerFacadeAccessor fluidFacade) {
            IFluidHandler handler = fluidFacade.gtlcore$getHandler();
            for (int i = 0; i < handler.getTanks(); i++) {
                FluidStack cur = handler.getFluidInTank(i);
                if (cur.isEmpty()) {
                    long cap = handler.getTankCapacity(i);
                    fluids.add(new SlotSnap(null, cap, cap, handler, i));
                } else {
                    AEFluidKey key = AEFluidKey.of(cur);
                    long accepted = handler instanceof IFluidTransfer transfer ? transfer.fill(i, FluidHelperImpl.toFluidStack(key.toStack(Integer.MAX_VALUE)), true, false) : handler.fill(key.toStack(Integer.MAX_VALUE), IFluidHandler.FluidAction.SIMULATE);
                    fluids.add(new SlotSnap(key, accepted, handler.getTankCapacity(i), handler, i));
                }
            }
        }
    }

    /**
     * Per-key probe capacity for targets where slot packing is irrelevant (network views, huge
     * composite inventories): one simulated insert per key answers how much fits; divided by the
     * per-operation amount that is the exact per-key bound, and the minimum across keys is the
     * answer. Probes are shared per provider-side per tick across patterns. Multi-key results are
     * verified with one full batch check; only disagreements fall back to the binary search.
     */
    @Unique
    private long gtlcore$probeCapacity(PatternProviderTarget target, BlockEntity targetBE, Direction side,
                                       KeyCounter baseInputs, long requestedOperations) {
        long now = TickHandler.instance().getCurrentTick();
        if (now != gtlcore$probeTick) {
            gtlcore$probeTick = now;
            gtlcore$probeCache.clear();
        }
        var targetCache = gtlcore$probeCache.computeIfAbsent(side, d -> new HashMap<>());

        long cap = requestedOperations;
        int keys = 0;
        for (var input : baseInputs) {
            long perOp = input.getLongValue();
            if (perOp <= 0) {
                continue;
            }
            keys++;
            AEKey key = input.getKey();
            Long cached = targetCache.get(key);
            long fits;
            if (cached != null) {
                fits = cached;
            } else {
                // Probe with an unbounded budget: the answer is the target's real free space for
                // this key, reusable for any request size.
                fits = target.insert(key, Long.MAX_VALUE, Actionable.SIMULATE);
                targetCache.put(key, fits);
            }
            cap = Math.min(cap, fits / perOp);
            if (cap <= 0) {
                return 0;
            }
        }
        if (keys > 1 && cap < requestedOperations && !gtlcore$canTargetAccept(target, targetBE, side, baseInputs, cap)) {
            // Probes disagreed with the batch reality - the slot-aware search is the arbiter.
            return gtlcore$binarySearchCapacity(target, targetBE, side, baseInputs, cap);
        }
        return cap;
    }

    @Unique
    private long gtlcore$binarySearchCapacity(PatternProviderTarget target, BlockEntity targetBE, Direction side,
                                              KeyCounter baseInputs, long requestedOperations) {
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

    /**
     * Real-handler batch acceptance check: can the target take `operations` operations' worth of
     * every input key? Probes the actual capability with per-slot accounting, so slot sharing
     * between keys is respected exactly.
     */
    @Unique
    private boolean gtlcore$canTargetAccept(PatternProviderTarget target, BlockEntity targetBE, Direction side,
                                            KeyCounter baseInputs, long operations) {
        if (operations <= 0) {
            return true;
        }

        // ME interfaces (block or cable part) expose their local config slots as item
        // capability, which does not match the network-backed storage the adapter pushes
        // into, so they must keep using the aggregate capacity check. Other AE2 machines
        // (e.g. the inscriber) expose the same inventory the adapter wraps, and NEED the
        // exact per-slot simulation: their recipe-driven slot filters lock each input to
        // dedicated non-overlapping slots, which the aggregate slot heuristic cannot
        // represent (it assumes shared slots and rejects multi-input patterns even at 1x).
        if (targetBE == null || side == null || targetBE instanceof InterfaceLogicHost || gtlcore$isInterfacePart(targetBE, side)) {
            return gtlcore$targetAcceptsAll(target, baseInputs, operations);
        }

        var itemCap = targetBE.getCapability(ForgeCapabilities.ITEM_HANDLER, side);
        var fluidCap = targetBE.getCapability(ForgeCapabilities.FLUID_HANDLER, side);
        boolean hasHandler = itemCap.isPresent() || fluidCap.isPresent();

        // First pass: each key must fit its own available space (cheap reject).
        // Slot reality is verified precisely below; the aggregate slot heuristic only
        // runs on the aggregate-only path.
        if (!gtlcore$keysFitIndividually(target, baseInputs, operations)) {
            return false;
        }

        if (itemCap.isPresent() &&
                !gtlcore$canItemHandlerAcceptAll(itemCap.orElseThrow(NullPointerException::new), baseInputs, operations)) {
            return false;
        }

        if (fluidCap.isPresent() &&
                !gtlcore$canFluidHandlerAcceptAll(fluidCap.orElseThrow(NullPointerException::new), baseInputs, operations)) {
            return false;
        }

        if (!hasHandler) {
            return gtlcore$targetAcceptsAll(target, baseInputs, operations);
        }
        return true;
    }

    @Unique
    private static boolean gtlcore$isInterfacePart(BlockEntity targetBE, Direction side) {
        return targetBE instanceof IPartHost partHost && partHost.getPart(side) instanceof InterfaceLogicHost;
    }

    /**
     * Cross-key slot-aware capacity check. Every key is simulated against the real handler,
     * but slots already claimed by a previous key in the same batch are accounted for, so
     * multiple item types competing for the same free slots cannot over-accept (which would
     * strand the overflow in the provider's sendList and stall the craft).
     */
    @Unique
    private boolean gtlcore$canItemHandlerAcceptAll(IItemHandler handler, KeyCounter baseInputs, long operations) {
        int slots = handler.getSlots();
        AEItemKey[] slotOwner = new AEItemKey[slots];
        long[] slotReserved = new long[slots];

        List<Entry<AEItemKey>> requirements = new ArrayList<>();
        for (var input : baseInputs) {
            if (input.getKey() instanceof AEItemKey itemKey) {
                long amount = NumberUtils.saturatedMultiply(input.getLongValue(), operations);
                if (amount > 0) {
                    requirements.add(new Entry<>(itemKey, amount));
                }
            }
        }

        // Most constrained keys first: fewer usable slots means higher risk of being
        // squeezed out by greedy allocation.
        requirements.sort((a, b) -> Integer.compare(
                gtlcore$countUsableSlots(handler, a.key().toStack(), slotOwner),
                gtlcore$countUsableSlots(handler, b.key().toStack(), slotOwner)));

        for (var requirement : requirements) {
            AEItemKey itemKey = requirement.key();
            ItemStack representative = itemKey.toStack();
            long remaining = requirement.amount();

            for (int i = 0; i < slots && remaining > 0; i++) {
                ItemStack current = handler.getStackInSlot(i);
                if (!current.isEmpty()) {
                    if (!ItemStack.isSameItem(current, representative)) {
                        continue;
                    }
                } else if (slotOwner[i] != null && slotOwner[i] != itemKey) {
                    continue;
                }

                // Simulate inserting the whole remainder into this slot. This respects both
                // filters and unlimited-capacity slots (e.g. gtmthings huge buses), instead of
                // clamping per-slot capacity to the item's vanilla max stack size.
                long probe = remaining + slotReserved[i];
                int chunk = (int) Math.min(probe, Integer.MAX_VALUE);
                ItemStack remainder = handler.insertItem(i, itemKey.toStack(chunk), true);
                long accepted = remainder.isEmpty() ? chunk : Math.max(0, chunk - remainder.getCount());
                long free = accepted - slotReserved[i];
                if (free <= 0) {
                    continue;
                }

                long taken = Math.min(free, remaining);
                slotReserved[i] += taken;
                if (current.isEmpty()) {
                    slotOwner[i] = itemKey;
                }
                remaining -= taken;
            }

            if (remaining > 0) {
                return false;
            }
        }

        return true;
    }

    @Unique
    private int gtlcore$countUsableSlots(IItemHandler handler, ItemStack representative, AEItemKey[] slotOwner) {
        int usable = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack current = handler.getStackInSlot(i);
            if (!current.isEmpty() && !ItemStack.isSameItem(current, representative)) {
                continue;
            }
            if (current.isEmpty() && slotOwner[i] != null) {
                continue;
            }
            if (handler.insertItem(i, representative.copyWithCount(1), true).isEmpty()) {
                usable++;
            }
        }
        return usable;
    }

    /**
     * Fluid equivalent of {@link #gtlcore$canItemHandlerAcceptAll}: tanks already claimed by
     * another fluid in the same batch are excluded, so multiple fluids cannot over-accept a
     * shared tank.
     */
    @Unique
    private boolean gtlcore$canFluidHandlerAcceptAll(IFluidHandler handler, KeyCounter baseInputs, long operations) {
        int tanks = handler.getTanks();
        AEFluidKey[] tankOwner = new AEFluidKey[tanks];
        long[] tankReserved = new long[tanks];

        List<Entry<AEFluidKey>> requirements = new ArrayList<>();
        for (var input : baseInputs) {
            if (input.getKey() instanceof AEFluidKey fluidKey) {
                long amount = NumberUtils.saturatedMultiply(input.getLongValue(), operations);
                if (amount > 0) {
                    requirements.add(new Entry<>(fluidKey, amount));
                }
            }
        }

        for (var requirement : requirements) {
            AEFluidKey fluidKey = requirement.key();
            FluidStack representative = fluidKey.toStack(1);
            long remaining = requirement.amount();

            for (int i = 0; i < tanks && remaining > 0; i++) {
                FluidStack current = handler.getFluidInTank(i);
                if (!current.isEmpty()) {
                    if (!current.isFluidEqual(representative)) {
                        continue;
                    }
                } else if (tankOwner[i] != null && tankOwner[i] != fluidKey) {
                    continue;
                }

                long free = handler.getTankCapacity(i) - current.getAmount() - tankReserved[i];
                if (free <= 0) {
                    continue;
                }

                // Verify the handler accepts this fluid at all (respects filters).
                FluidStack probe = representative.copy();
                probe.setAmount((int) Math.min(free, Integer.MAX_VALUE));
                int accepted = handler.fill(probe, IFluidHandler.FluidAction.SIMULATE);
                if (accepted <= 0) {
                    continue;
                }

                long taken = Math.min(Math.min(free, accepted), remaining);
                tankReserved[i] += taken;
                if (current.isEmpty()) {
                    tankOwner[i] = fluidKey;
                }
                remaining -= taken;
            }

            if (remaining > 0) {
                return false;
            }
        }

        return true;
    }

    @Unique
    private record Entry<K extends AEKey>(K key, long amount) {}

    /**
     * Aggregate-path capacity check for targets without a usable capability view (ME
     * interfaces, unknown inventories). Combines the per-key check with a conservative
     * slot-count bound: it assumes slots are shared, so it may underestimate for machines
     * with dedicated filtered slots — but those should be reached via the exact capability
     * path instead.
     */
    @Unique
    private boolean gtlcore$targetAcceptsAll(PatternProviderTarget target, KeyCounter baseInputs, long operations) {
        if (!gtlcore$keysFitIndividually(target, baseInputs, operations)) {
            return false;
        }

        // For items we additionally enforce a slot-aware bound, because single-slot
        // inventories cannot mix different item types in the same slot.
        long requiredItemSlots = 0;
        long availableItemSlots = 0;

        for (var input : baseInputs) {
            long amount = NumberUtils.saturatedMultiply(input.getLongValue(), operations);
            if (amount <= 0 || !(input.getKey() instanceof AEItemKey itemKey)) {
                continue;
            }

            long available = target.insert(itemKey, Long.MAX_VALUE, Actionable.SIMULATE);
            int maxStack = Math.max(1, itemKey.getItem().getMaxStackSize());
            requiredItemSlots = NumberUtils.saturatedAdd(requiredItemSlots, gtlcore$ceilDiv(amount, maxStack));
            availableItemSlots = Math.max(availableItemSlots, gtlcore$ceilDiv(available, maxStack));
        }

        return requiredItemSlots <= availableItemSlots;
    }

    @Unique
    private boolean gtlcore$keysFitIndividually(PatternProviderTarget target, KeyCounter baseInputs, long operations) {
        // Per-key capacity check: each key must fit its own required amount.
        // Aggregating per AEKeyType with a min() capacity would let a single
        // special-stack item (e.g. maxStackSize=1) cap the whole pattern.
        for (var input : baseInputs) {
            long amount = NumberUtils.saturatedMultiply(input.getLongValue(), operations);
            if (amount <= 0) {
                continue;
            }

            long available = target.insert(input.getKey(), Long.MAX_VALUE, Actionable.SIMULATE);
            if (amount > available) {
                return false;
            }
        }

        return true;
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
