package org.gtlcore.gtlcore.api.pattern;

import org.gtlcore.gtlcore.common.item.UltimateTerminalBehavior;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;
import com.gregtechceu.gtceu.api.pattern.util.RelativeDirection;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.wrapper.PlayerInvWrapper;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import appeng.blockentity.grid.AENetworkBlockEntity;
import appeng.items.tools.powered.WirelessTerminalItem;
import it.unimi.dsi.fastutil.ints.IntObjectPair;
import it.unimi.dsi.fastutil.objects.*;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import oshi.util.tuples.Triplet;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.*;

/**
 * 代码参考自gtmthings
 * &#064;line <a href="https://github.com/liansishen/GTMThings">...</a>
 */

public class AdvancedBlockPattern extends BlockPattern {

    static Direction[] FACINGS = { Direction.SOUTH, Direction.NORTH, Direction.WEST, Direction.EAST, Direction.UP,
            Direction.DOWN };
    static Direction[] FACINGS_H = { Direction.SOUTH, Direction.NORTH, Direction.WEST, Direction.EAST };

    protected final int[][] aisleRepetitions;
    protected final RelativeDirection[] structureDir;
    protected final TraceabilityPredicate[][][] blockMatches; // [z][y][x]
    protected final int fingerLength; // z size
    protected final int thumbLength; // y size
    protected final int palmLength; // x size
    protected final int[] centerOffset; // x, y, z, minZ, maxZ

    public AdvancedBlockPattern(TraceabilityPredicate[][][] predicatesIn, RelativeDirection[] structureDir, int[][] aisleRepetitions, int[] centerOffset) {
        super(predicatesIn, structureDir, aisleRepetitions, centerOffset);
        this.blockMatches = predicatesIn;
        this.fingerLength = predicatesIn.length;
        this.structureDir = structureDir;
        this.aisleRepetitions = aisleRepetitions;

        if (this.fingerLength > 0) {
            this.thumbLength = predicatesIn[0].length;

            if (this.thumbLength > 0) {
                this.palmLength = predicatesIn[0][0].length;
            } else {
                this.palmLength = 0;
            }
        } else {
            this.thumbLength = 0;
            this.palmLength = 0;
        }

        this.centerOffset = centerOffset;
    }

    public static AdvancedBlockPattern getAdvancedBlockPattern(BlockPattern blockPattern) {
        try {
            Class<?> clazz = BlockPattern.class;
            // blockMatches
            Field blockMatchesField = clazz.getDeclaredField("blockMatches");
            blockMatchesField.setAccessible(true);
            TraceabilityPredicate[][][] blockMatches = (TraceabilityPredicate[][][]) blockMatchesField.get(blockPattern);
            // structureDir
            Field structureDirField = clazz.getDeclaredField("structureDir");
            structureDirField.setAccessible(true);
            RelativeDirection[] structureDir = (RelativeDirection[]) structureDirField.get(blockPattern);
            // aisleRepetitions
            Field aisleRepetitionsField = clazz.getDeclaredField("aisleRepetitions");
            aisleRepetitionsField.setAccessible(true);
            int[][] aisleRepetitions = (int[][]) aisleRepetitionsField.get(blockPattern);
            // centerOffset
            Field centerOffsetField = clazz.getDeclaredField("centerOffset");
            centerOffsetField.setAccessible(true);
            int[] centerOffset = (int[]) centerOffsetField.get(blockPattern);

            return new AdvancedBlockPattern(blockMatches, structureDir, aisleRepetitions, centerOffset);
        } catch (Exception e) {
            return null;
        }
    }

    private net.minecraft.world.level.material.Fluid getFluid(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof LiquidBlock liquidBlock) {
            return liquidBlock.getFluid();
        } else if (stack.getItem() instanceof BucketItem bucketItem) {
            return bucketItem.getFluid();
        }
        return null;
    }

    public void autoBuild(Player player, MultiblockState worldState,
                          UltimateTerminalBehavior.AutoBuildSetting autoBuildSetting) {
        Level world = player.level();
        int minZ = -centerOffset[4];
        clearWorldState(worldState);
        IMultiController controller = worldState.getController();
        BlockPos centerPos = controller.self().getPos();
        Direction facing = controller.self().getFrontFacing();
        Direction upwardsFacing = controller.self().getUpwardsFacing();
        boolean isFlipped = autoBuildSetting.isFlipped();
        boolean aeMode = autoBuildSetting.isAeMode();

        GlobalPos boundCoord = autoBuildSetting.getBoundAE();
        IGrid grid = aeMode ? findBestGrid(world, player, boundCoord) : null;
        var aeInventory = grid != null ? grid.getStorageService().getInventory() : null;

        IActionSource source = IActionSource.ofPlayer(player);

        Object2IntOpenHashMap<SimplePredicate> cacheGlobal = new Object2IntOpenHashMap<>(worldState.getGlobalCount());
        Object2IntOpenHashMap<SimplePredicate> cacheLayer = new Object2IntOpenHashMap<>(worldState.getLayerCount());
        Object2ObjectOpenHashMap<BlockPos, Object> blocks = new Object2ObjectOpenHashMap<>();
        ObjectOpenHashSet<BlockPos> placeBlockPos = new ObjectOpenHashSet<>();
        blocks.put(centerPos, controller);
        if (controller.isFormed() && autoBuildSetting.isReplaceMode()) controller.onStructureInvalid();

        int[] repeat = new int[this.fingerLength];
        for (int h = 0; h < this.fingerLength; h++) {
            var minH = aisleRepetitions[h][0];
            var maxH = aisleRepetitions[h][1];
            if (minH != maxH) {
                repeat[h] = Math.max(minH, Math.min(maxH, autoBuildSetting.getRepeatCount()));
            } else repeat[h] = minH;
        }

        for (int c = 0, z = minZ, r; c < this.fingerLength; c++) {
            for (r = 0; r < repeat[c]; r++) {
                cacheLayer.clear();
                for (int b = 0, y = -centerOffset[1]; b < this.thumbLength; b++, y++) {
                    for (int a = 0, x = -centerOffset[0]; a < this.palmLength; a++, x++) {
                        TraceabilityPredicate predicate = this.blockMatches[c][b][a];
                        if (predicate.isAny()) continue;
                        BlockPos pos = setActualRelativeOffset(x, y, z, facing, upwardsFacing, isFlipped)
                                .offset(centerPos.getX(), centerPos.getY(), centerPos.getZ());
                        updateWorldState(worldState, pos, predicate);
                        ItemStack itemStack = null;
                        if (!world.isEmptyBlock(pos)) {
                            Block block = world.getBlockState(pos).getBlock();
                            if (autoBuildSetting.getBlocks().contains(block) && autoBuildSetting.isReplaceMode()) {
                                itemStack = block.asItem().getDefaultInstance();
                            } else {
                                blocks.put(pos, world.getBlockState(pos));
                                for (SimplePredicate limit : predicate.limited) limit.testLimited(worldState);
                                continue;
                            }
                        }

                        boolean find = false;
                        BlockInfo[] infos = new BlockInfo[0];
                        for (var limit : predicate.limited) {
                            if (limit.candidates != null && !autoBuildSetting.isPlaceHatch(limit.candidates.get())) continue;
                            if (limit.minLayerCount > 0) {
                                int curr = cacheLayer.getInt(limit);
                                if (curr < limit.minLayerCount &&
                                        (limit.maxLayerCount == -1 || curr < limit.maxLayerCount)) {
                                    cacheLayer.addTo(limit, 1);
                                    infos = limit.candidates == null ? null : limit.candidates.get();
                                    find = true;
                                    break;
                                }
                            }
                        }
                        if (!find) {
                            for (var limit : predicate.limited) {
                                if (limit.candidates != null && !autoBuildSetting.isPlaceHatch(limit.candidates.get())) continue;
                                if (limit.minCount > 0) {
                                    int curr = cacheGlobal.getInt(limit);
                                    if (curr < limit.minCount && (limit.maxCount == -1 || curr < limit.maxCount)) {
                                        cacheGlobal.addTo(limit, 1);
                                        infos = limit.candidates == null ? null : limit.candidates.get();
                                        find = true;
                                        break;
                                    }
                                }
                            }
                        }
                        if (!find) { // no limited
                            for (SimplePredicate limit : predicate.limited) {
                                if (limit.candidates != null && !autoBuildSetting.isPlaceHatch(limit.candidates.get()))
                                    continue;
                                if (limit.maxLayerCount != -1 &&
                                        cacheLayer.getOrDefault(limit, Integer.MAX_VALUE) == limit.maxLayerCount) {
                                    continue;
                                }
                                if (limit.maxCount != -1 &&
                                        cacheGlobal.getOrDefault(limit, Integer.MAX_VALUE) == limit.maxCount) {
                                    continue;
                                }
                                cacheLayer.addTo(limit, 1);
                                cacheGlobal.addTo(limit, 1);
                                infos = ArrayUtils.addAll(infos, limit.candidates == null ? null : limit.candidates.get());
                            }
                            for (SimplePredicate common : predicate.common) {
                                if (common.candidates != null && predicate.common.size() > 1 && !autoBuildSetting.isPlaceHatch(common.candidates.get())) {
                                    continue;
                                }
                                infos = ArrayUtils.addAll(infos, common.candidates == null ? null : common.candidates.get());
                            }
                        }

                        List<ItemStack> candidates = autoBuildSetting.apply(infos);

                        if (autoBuildSetting.isReplaceMode() && itemStack != null) {
                            ItemStack finalItemStack = itemStack;
                            if (candidates.stream().anyMatch(cand -> ItemStack.isSameItem(cand, finalItemStack)))
                                continue;
                        }

                        ItemStack found = null;
                        ItemStack aeItemToExtract = ItemStack.EMPTY;
                        net.minecraft.world.level.material.Fluid fluidToPlace = null;
                        boolean foundFromAE = false;
                        boolean fluidFromAE = false;
                        IItemHandler handler = null;
                        int foundSlot = -1;
                        for (ItemStack candidate : candidates) {
                            net.minecraft.world.level.material.Fluid fluid = getFluid(candidate);
                            if (fluid != null) {
                                if (aeMode && aeInventory != null &&
                                        aeInventory.extract(AEFluidKey.of(fluid), FluidType.BUCKET_VOLUME, Actionable.SIMULATE, source) >= FluidType.BUCKET_VOLUME) {
                                    fluidToPlace = fluid;
                                    fluidFromAE = true;
                                    break;
                                }
                                if (extractFluidFromPlayerRecursively(player, fluid)) {
                                    fluidToPlace = fluid;
                                    break;
                                }
                            } else {
                                if (aeMode && aeInventory != null &&
                                        aeInventory.extract(AEItemKey.of(candidate), 1, Actionable.SIMULATE, source) > 0) {
                                    found = candidate.copy();
                                    aeItemToExtract = candidate.copy();
                                    foundFromAE = true;
                                    break;
                                }
                                var result = foundItem(player, List.of(candidate), item -> true);
                                if (result.getA() != null) {
                                    found = result.getA();
                                    handler = result.getB();
                                    foundSlot = result.getC();
                                    break;
                                }
                            }
                        }

                        if (found == null && fluidToPlace == null) continue;

                        // check can get old coilBlock
                        IItemHandler holderHandler = null;
                        int holderSlot = -1;
                        if (autoBuildSetting.isReplaceMode() && itemStack != null) {
                            if (!aeMode || !canInsertItemIntoAE(aeInventory, itemStack, source)) {
                                var holderResult = foundHolderSlot(player, itemStack);
                                holderHandler = holderResult.first();
                                holderSlot = holderResult.rightInt();

                                if (holderHandler != null && holderSlot < 0) {
                                    continue;
                                }
                            }
                        }

                        if (autoBuildSetting.isReplaceMode() && itemStack != null) {
                            world.removeBlock(pos, true);
                            if (!insertItemIntoAE(aeInventory, itemStack, source) && holderHandler != null) {
                                holderHandler.insertItem(holderSlot, itemStack, false);
                            }
                        }

                        if (fluidToPlace != null) {
                            BlockState state = fluidToPlace.defaultFluidState().createLegacyBlock();
                            if (!state.isAir()) {
                                world.setBlock(pos, state, 3);
                                placeBlockPos.add(pos);
                                blocks.put(pos, state);
                                if (fluidFromAE) {
                                    aeInventory.extract(AEFluidKey.of(fluidToPlace), FluidType.BUCKET_VOLUME, Actionable.MODULATE, source);
                                }
                            }
                        } else if (found != null && found.getItem() instanceof BlockItem itemBlock) {
                            BlockPlaceContext context = new BlockPlaceContext(world, player, InteractionHand.MAIN_HAND,
                                    found, BlockHitResult.miss(player.getEyePosition(0), Direction.UP, pos));
                            InteractionResult interactionResult = itemBlock.place(context);
                            if (interactionResult != InteractionResult.FAIL) {
                                placeBlockPos.add(pos);
                                if (foundFromAE) {
                                    aeInventory.extract(AEItemKey.of(aeItemToExtract), 1, Actionable.MODULATE, source);
                                } else if (handler != null) handler.extractItem(foundSlot, 1, false);
                            }
                            if (world.getBlockEntity(pos) instanceof IMachineBlockEntity machineBlockEntity) {
                                blocks.put(pos, machineBlockEntity.getMetaMachine());
                            } else blocks.put(pos, world.getBlockState(pos));
                        }
                    }
                }
                z++;
            }
        }
        Direction frontFacing = controller.self().getFrontFacing();
        blocks.object2ObjectEntrySet().fastForEach((entry -> {
            // adjust facing
            var pos = entry.getKey();
            var block = entry.getValue();
            if (!(block instanceof IMultiController)) {
                if (block instanceof BlockState && placeBlockPos.contains(pos)) {
                    resetFacing(pos, (BlockState) block, frontFacing, (p, f) -> {
                        Object object = blocks.get(p.relative(f));
                        return object == null ||
                                (object instanceof BlockState && ((BlockState) object).getBlock() == Blocks.AIR);
                    }, state -> world.setBlock(pos, state, 3));
                } else if (block instanceof MetaMachine machine) {
                    resetFacing(pos, machine.getBlockState(), frontFacing, (p, f) -> {
                        Object object = blocks.get(p.relative(f));
                        if (object == null || (object instanceof BlockState blockState && blockState.isAir())) {
                            return machine.isFacingValid(f);
                        }
                        return false;
                    }, state -> world.setBlock(pos, state, 3));
                }
            }
        }));
    }

    public void dismantleMultiblock(IMultiController controller, Player player, int repeatCountSetting, boolean isFlipped, boolean aeMode, @Nullable GlobalPos boundAE) {
        var level = player.level();
        // 仅在服务端执行
        if (level.isClientSide()) return;

        BlockPos centerPos = controller.self().getPos();
        Direction facing = controller.self().getFrontFacing();
        Direction upwardsFacing = controller.self().getUpwardsFacing();

        IGrid grid = aeMode ? findBestGrid(level, player, boundAE) : null;
        var aeInventory = grid != null ? grid.getStorageService().getInventory() : null;
        IActionSource source = IActionSource.ofPlayer(player);

        // 获取多方块状态用于判定方块是否有效
        MultiblockState worldState = controller.getMultiblockState();

        // 计算重复次数
        int[] repeat = new int[this.fingerLength];
        for (int h = 0; h < this.fingerLength; h++) {
            var minH = this.aisleRepetitions[h][0];
            var maxH = this.aisleRepetitions[h][1];
            if (minH != maxH) {
                repeat[h] = Math.max(minH, Math.min(maxH, repeatCountSetting));
            } else repeat[h] = minH;
        }

        int minZ = -this.centerOffset[4];

        // 遍历模式中的每一个位置
        for (int c = 0, z = minZ; c < this.fingerLength; c++) {
            for (int r = 0; r < repeat[c]; r++) {
                for (int b = 0, y = -this.centerOffset[1]; b < this.thumbLength; b++, y++) {
                    for (int a = 0, x = -this.centerOffset[0]; a < this.palmLength; a++, x++) {
                        // 获取当前位置的判定谓词
                        TraceabilityPredicate predicate = this.blockMatches[c][b][a];
                        // 跳过 "Any" 类型的匹配
                        if (predicate.isAny()) continue;

                        BlockPos pos = setActualRelativeOffset(x, y, z, facing, upwardsFacing, isFlipped)
                                .offset(centerPos.getX(), centerPos.getY(), centerPos.getZ());

                        // 避开控制器本身
                        if (pos.equals(centerPos)) continue;

                        BlockState blockState = level.getBlockState(pos);
                        if (blockState.isAir()) continue;

                        if (!isBlockValid(blockState, pos, predicate, worldState)) continue;

                        if (!blockState.isAir()) {
                            if (level instanceof ServerLevel serverLevel) {
                                if (blockState.getBlock() instanceof LiquidBlock liquidBlock) {
                                    if (aeInventory != null) {
                                        aeInventory.insert(AEFluidKey.of(liquidBlock.getFluid()), FluidType.BUCKET_VOLUME, Actionable.MODULATE, source);
                                    }
                                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                                    continue;
                                }

                                // 1. 获取方块原本应掉落的物品列表
                                List<ItemStack> drops = Block.getDrops(blockState, serverLevel, pos, serverLevel.getBlockEntity(pos), player, player.getMainHandItem());
                                // 2. 直接移除方块
                                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                                // 3. 将物品存入玩家物品栏（优先嵌套容器，其次原生背包，最后掉落）
                                for (ItemStack drop : drops) {
                                    ItemStack remainder = drop;
                                    if (aeInventory != null) {
                                        long inserted = aeInventory.insert(AEItemKey.of(remainder), remainder.getCount(), Actionable.MODULATE, source);
                                        if (inserted > 0) {
                                            remainder.shrink((int) inserted);
                                        }
                                    }
                                    if (remainder.isEmpty()) continue;
                                    // 3.1 尝试放入嵌套容器（如背包袋）
                                    remainder = insertIntoNestedItemHandler(player, remainder);
                                    // 3.2 尝试放入玩家原生背包
                                    if (!remainder.isEmpty()) {
                                        if (player.getInventory().add(remainder)) {
                                            remainder = ItemStack.EMPTY;
                                        }
                                    }
                                    // 3.3 掉落剩余物品
                                    if (!remainder.isEmpty()) {
                                        player.drop(remainder, false);
                                    }
                                }
                            }
                        }
                    }
                }
                z++;
            }
        }

        // 如果是工作中的多方块，调用卸载
        if (controller instanceof WorkableMultiblockMachine machine) {
            machine.onPartUnload();
        }
    }

    private boolean isBlockValid(BlockState current, BlockPos pos, TraceabilityPredicate predicate, MultiblockState tempState) {
        // 合并 common 和 limited 谓词进行检查
        List<SimplePredicate> allPreds = new ArrayList<>();
        if (predicate.common != null) allPreds.addAll(predicate.common);
        if (predicate.limited != null) allPreds.addAll(predicate.limited);

        for (SimplePredicate p : allPreds) {
            BlockInfo[] infos = p.candidates == null ? null : p.candidates.get();
            if (infos != null && infos.length > 0) {
                for (BlockInfo info : infos) {
                    if (info.getBlockState().is(current.getBlock())) {
                        return true;
                    }
                }
            } else {
                clearWorldState(tempState);
                updateWorldState(tempState, pos, predicate);
                if (p.test(tempState)) return true;
            }
        }
        return false;
    }

    private boolean extractFluidFromPlayerRecursively(Player player, net.minecraft.world.level.material.Fluid fluid) {
        if (player.isCreative()) return true;
        LazyOptional<IItemHandler> cap = player.getCapability(ForgeCapabilities.ITEM_HANDLER);
        if (!cap.isPresent()) return false;

        return extractFluidRecursive(cap.resolve().orElse(null), fluid);
    }

    private boolean extractFluidRecursive(IItemHandler handler, net.minecraft.world.level.material.Fluid fluid) {
        if (handler == null) return false;

        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            // 1. Check for Fluid Capability
            LazyOptional<IFluidHandlerItem> fluidCap = stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM);
            if (fluidCap.isPresent()) {
                IFluidHandlerItem fluidHandler = fluidCap.resolve().orElse(null);
                if (fluidHandler != null) {
                    FluidStack simulated = fluidHandler.drain(new FluidStack(fluid, FluidType.BUCKET_VOLUME), IFluidHandler.FluidAction.SIMULATE);
                    if (simulated.getAmount() == FluidType.BUCKET_VOLUME) {
                        fluidHandler.drain(new FluidStack(fluid, FluidType.BUCKET_VOLUME), IFluidHandler.FluidAction.EXECUTE);
                        if (handler instanceof IItemHandlerModifiable modifiable) {
                            modifiable.setStackInSlot(i, fluidHandler.getContainer());
                        }
                        return true;
                    }
                }
            }

            // 2. Recursive Check
            LazyOptional<IItemHandler> subItemCap = stack.getCapability(ForgeCapabilities.ITEM_HANDLER);
            if (subItemCap.isPresent()) {
                if (extractFluidRecursive(subItemCap.resolve().orElse(null), fluid)) {
                    return true;
                }
            }
        }
        return false;
    }

    private IGrid findBestGrid(Level level, Player player, @Nullable GlobalPos boundCoord) {
        if (boundCoord != null) {
            if (boundCoord.dimension().equals(level.dimension())) {
                BlockEntity be = level.getBlockEntity(boundCoord.pos());
                IGridNode node = getGridNode(be);
                if (node != null && node.getGrid() != null) {
                    return node.getGrid();
                }
            }
        }
        return findWirelessTerminalGrid(player, level);
    }

    @Nullable
    private IGrid findWirelessTerminalGrid(Player player, Level level) {
        IItemHandler handler = player.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElse(null);
        if (handler == null) return null;
        return findWirelessTerminalGrid(handler, level, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    @Nullable
    private IGrid findWirelessTerminalGrid(IItemHandler handler, Level level, Set<IItemHandler> visited) {
        if (handler == null || !visited.add(handler)) return null;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            if (stack.getItem() instanceof WirelessTerminalItem terminal) {
                IGrid grid = terminal.getLinkedGrid(stack, level, null);
                if (grid != null) return grid;
            }

            IItemHandler nested = stack.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElse(null);
            IGrid nestedGrid = findWirelessTerminalGrid(nested, level, visited);
            if (nestedGrid != null) return nestedGrid;
        }
        return null;
    }

    private boolean canInsertItemIntoAE(@Nullable MEStorage aeInventory, ItemStack stack, IActionSource source) {
        return aeInventory != null && aeInventory.insert(AEItemKey.of(stack), stack.getCount(), Actionable.SIMULATE, source) >= stack.getCount();
    }

    private boolean insertItemIntoAE(@Nullable MEStorage aeInventory, ItemStack stack, IActionSource source) {
        if (!canInsertItemIntoAE(aeInventory, stack, source)) {
            return false;
        }
        return aeInventory.insert(AEItemKey.of(stack), stack.getCount(), Actionable.MODULATE, source) >= stack.getCount();
    }

    public static IGridNode getGridNode(BlockEntity be) {
        if (be == null) return null;
        if (be instanceof AENetworkBlockEntity networkBlock) {
            return networkBlock.getMainNode().getNode();
        }
        try {
            Method m = be.getClass().getMethod("getGridNode", Direction.class);
            for (Direction direction : Direction.values()) {
                IGridNode node = (IGridNode) m.invoke(be, direction);
                if (node != null) return node;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private ItemStack insertIntoNestedItemHandler(Player player, ItemStack stack) {
        LazyOptional<IItemHandler> cap = player.getCapability(ForgeCapabilities.ITEM_HANDLER);
        if (cap.isPresent()) {
            return insertIntoNestedItemHandlerRecursion(cap.resolve().orElse(null), stack);
        }
        return stack;
    }

    private ItemStack insertIntoNestedItemHandlerRecursion(IItemHandler handler, ItemStack stack) {
        if (handler == null) return stack;
        for (int i = 0; i < handler.getSlots(); i++) {
            if (stack.isEmpty()) return stack;
            ItemStack inSlot = handler.getStackInSlot(i);
            if (inSlot.isEmpty()) continue;

            // 检查槽位物品是否具有ItemHandler能力（即是否为容器）
            LazyOptional<IItemHandler> subCap = inSlot.getCapability(ForgeCapabilities.ITEM_HANDLER);
            if (subCap.isPresent()) {
                IItemHandler subHandler = subCap.resolve().orElse(null);
                if (subHandler != null) {
                    // 递归尝试放入更深层的容器
                    stack = insertIntoNestedItemHandlerRecursion(subHandler, stack);
                    if (stack.isEmpty()) return stack;

                    // 尝试放入当前容器
                    stack = ItemHandlerHelper.insertItemStacked(subHandler, stack, false);
                }
            }
        }
        return stack;
    }

    public static Triplet<ItemStack, IItemHandler, Integer> foundItem(Player player,
                                                                      List<ItemStack> candidates,
                                                                      Predicate<Item> test) {
        ItemStack found = null;
        IItemHandler handler = null;
        int foundSlot = -1;
        if (!player.isCreative()) {
            var foundHandler = getMatchStackWithHandler(candidates,
                    player.getCapability(ForgeCapabilities.ITEM_HANDLER), test);
            if (foundHandler != null) {
                foundSlot = foundHandler.firstInt();
                handler = foundHandler.second();
                found = handler.getStackInSlot(foundSlot).copy();
            }
        } else {
            for (ItemStack candidate : candidates) {
                found = candidate.copy();
                if (!found.isEmpty() && test.test(found.getItem())) break;
                found = null;
            }
        }
        return new Triplet<>(found, handler, foundSlot);
    }

    private ObjectIntPair<IItemHandler> foundHolderSlot(Player player, ItemStack coilItemStack) {
        if (!player.isCreative()) {
            var r = findFirstSlot(player.getCapability(ForgeCapabilities.ITEM_HANDLER), coilItemStack);
            if (r != null) return r;
        }
        return ObjectIntPair.of(null, -1);
    }

    private ObjectIntPair<IItemHandler> findFirstSlot(LazyOptional<IItemHandler> cap, ItemStack stack) {
        var handler = cap.resolve().orElse(null);
        if (handler == null) return null;
        int foundSlot = -1;
        for (int i = 0; i < handler.getSlots(); i++) {
            var inSlot = handler.getStackInSlot(i);
            var stackCap = inSlot.getCapability(ForgeCapabilities.ITEM_HANDLER);
            if (stackCap.isPresent()) {
                var rt = findFirstSlot(stackCap, stack);
                if (rt != null) return rt;
            } else {
                if (inSlot.isEmpty() && !((handler instanceof PlayerInvWrapper) && i >= 36)) {
                    if (foundSlot < 0) foundSlot = i;
                } else if (ItemStack.isSameItemSameTags(stack, inSlot) && (inSlot.getCount() + 1) <= inSlot.getMaxStackSize()) foundSlot = i;
                if (foundSlot > 0) break;
            }
        }
        return ObjectIntPair.of(handler, foundSlot);
    }

    private void clearWorldState(MultiblockState worldState) {
        try {
            Class<?> clazz = Class.forName("com.gregtechceu.gtceu.api.pattern.MultiblockState");
            Method method = clazz.getDeclaredMethod("clean");
            method.setAccessible(true);
            method.invoke(worldState);
        } catch (Exception ignored) {}
    }

    private void updateWorldState(MultiblockState worldState, BlockPos posIn, TraceabilityPredicate predicate) {
        try {
            Class<?> clazz = Class.forName("com.gregtechceu.gtceu.api.pattern.MultiblockState");
            Method method = clazz.getDeclaredMethod("update", BlockPos.class, TraceabilityPredicate.class);
            method.setAccessible(true);
            method.invoke(worldState, posIn, predicate);
        } catch (Exception ignored) {}
    }

    private BlockPos setActualRelativeOffset(int x, int y, int z, Direction facing, Direction upwardsFacing,
                                             boolean isFlipped) {
        int[] c0 = new int[] { x, y, z }, c1 = new int[3];
        if (facing == Direction.UP || facing == Direction.DOWN) {
            Direction of = facing == Direction.DOWN ? upwardsFacing : upwardsFacing.getOpposite();
            for (int i = 0; i < 3; i++) {
                switch (structureDir[i].getActualFacing(of)) {
                    case UP -> c1[1] = c0[i];
                    case DOWN -> c1[1] = -c0[i];
                    case WEST -> c1[0] = -c0[i];
                    case EAST -> c1[0] = c0[i];
                    case NORTH -> c1[2] = -c0[i];
                    case SOUTH -> c1[2] = c0[i];
                }
            }
            int xOffset = upwardsFacing.getStepX();
            int zOffset = upwardsFacing.getStepZ();
            int tmp;
            if (xOffset == 0) {
                tmp = c1[2];
                c1[2] = zOffset > 0 ? c1[1] : -c1[1];
                c1[1] = zOffset > 0 ? -tmp : tmp;
            } else {
                tmp = c1[0];
                c1[0] = xOffset > 0 ? c1[1] : -c1[1];
                c1[1] = xOffset > 0 ? -tmp : tmp;
            }
            if (isFlipped) {
                if (upwardsFacing == Direction.NORTH || upwardsFacing == Direction.SOUTH) {
                    c1[0] = -c1[0]; // flip X-axis
                } else {
                    c1[2] = -c1[2]; // flip Z-axis
                }
            }
        } else {
            for (int i = 0; i < 3; i++) {
                switch (structureDir[i].getActualFacing(facing)) {
                    case UP -> c1[1] = c0[i];
                    case DOWN -> c1[1] = -c0[i];
                    case WEST -> c1[0] = -c0[i];
                    case EAST -> c1[0] = c0[i];
                    case NORTH -> c1[2] = -c0[i];
                    case SOUTH -> c1[2] = c0[i];
                }
            }
            if (upwardsFacing == Direction.WEST || upwardsFacing == Direction.EAST) {
                int xOffset = upwardsFacing == Direction.EAST ? facing.getClockWise().getStepX() :
                        facing.getClockWise().getOpposite().getStepX();
                int zOffset = upwardsFacing == Direction.EAST ? facing.getClockWise().getStepZ() :
                        facing.getClockWise().getOpposite().getStepZ();
                int tmp;
                if (xOffset == 0) {
                    tmp = c1[2];
                    c1[2] = zOffset > 0 ? -c1[1] : c1[1];
                    c1[1] = zOffset > 0 ? tmp : -tmp;
                } else {
                    tmp = c1[0];
                    c1[0] = xOffset > 0 ? -c1[1] : c1[1];
                    c1[1] = xOffset > 0 ? tmp : -tmp;
                }
            } else if (upwardsFacing == Direction.SOUTH) {
                c1[1] = -c1[1];
                if (facing.getStepX() == 0) {
                    c1[0] = -c1[0];
                } else {
                    c1[2] = -c1[2];
                }
            }
            if (isFlipped) {
                if (upwardsFacing == Direction.NORTH || upwardsFacing == Direction.SOUTH) {
                    if (facing == Direction.NORTH || facing == Direction.SOUTH) {
                        c1[0] = -c1[0]; // flip X-axis
                    } else c1[2] = -c1[2]; // flip Z-axis
                } else c1[1] = -c1[1]; // flip Y-axis
            }
        }
        return new BlockPos(c1[0], c1[1], c1[2]);
    }

    @Nullable
    private static IntObjectPair<IItemHandler> getMatchStackWithHandler(List<ItemStack> candidates,
                                                                        LazyOptional<IItemHandler> cap,
                                                                        Predicate<Item> test) {
        IItemHandler handler = cap.resolve().orElse(null);
        if (handler == null) return null;
        for (int i = 0; i < handler.getSlots(); i++) {
            @NotNull
            ItemStack stack = handler.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            @NotNull
            LazyOptional<IItemHandler> stackCap = stack.getCapability(ForgeCapabilities.ITEM_HANDLER);
            if (stackCap.isPresent()) {
                var rt = getMatchStackWithHandler(candidates, stackCap, test);
                if (rt != null) return rt;
            } else if (candidates.stream().anyMatch(candidate -> ItemStack.isSameItemSameTags(candidate, stack)) &&
                    !stack.isEmpty() && test.test(stack.getItem())) {
                        return IntObjectPair.of(i, handler);
                    }
        }
        return null;
    }

    private void resetFacing(BlockPos pos, BlockState blockState, Direction facing,
                             BiPredicate<BlockPos, Direction> checker, Consumer<BlockState> consumer) {
        if (blockState.hasProperty(BlockStateProperties.FACING)) {
            tryFacings(blockState, pos, checker, consumer, BlockStateProperties.FACING,
                    facing == null ? FACINGS : ArrayUtils.addAll(new Direction[] { facing }, FACINGS));
        } else if (blockState.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            tryFacings(blockState, pos, checker, consumer, BlockStateProperties.HORIZONTAL_FACING,
                    facing == null || facing.getAxis() == Direction.Axis.Y ? FACINGS_H :
                            ArrayUtils.addAll(new Direction[] { facing }, FACINGS_H));
        }
    }

    private void tryFacings(BlockState blockState, BlockPos pos, BiPredicate<BlockPos, Direction> checker,
                            Consumer<BlockState> consumer, Property<Direction> property, Direction[] facings) {
        Direction found = null;
        for (Direction facing : facings) {
            if (checker.test(pos, facing)) {
                found = facing;
                break;
            }
        }
        if (found == null) found = Direction.NORTH;
        consumer.accept(blockState.setValue(property, found));
    }
}
