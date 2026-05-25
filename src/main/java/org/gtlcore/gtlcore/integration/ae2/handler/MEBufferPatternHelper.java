package org.gtlcore.gtlcore.integration.ae2.handler;

import org.gtlcore.gtlcore.api.machine.trait.NotifiableCircuitItemStackHandler;

import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.item.IntCircuitBehaviour;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AEProcessingPattern;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 样板电路处理模块
 * 负责处理样板中的电路逻辑，包括：
 * - 电路提取和存储
 * - 样板重构（移除电路, 酌情删除副产物）
 */
public class MEBufferPatternHelper {

    private final NotifiableCircuitItemStackHandler mePatternCircuitInventory;

    public MEBufferPatternHelper(NotifiableCircuitItemStackHandler mePatternCircuitInventory) {
        this.mePatternCircuitInventory = mePatternCircuitInventory;
    }

    /**
     * @return ME样板总成是否配置有共享电路
     */
    public boolean haveSharedPatternCircuit() {
        return !mePatternCircuitInventory.storage.getStackInSlot(0).isEmpty();
    }

    /**
     * 处理包含电路的样板：提取电路并返回无电路并酌情删除副产物的样板
     * 
     * @param originalPatternStack 原始样板
     * @param storedCircuit        存储电路的引用
     * @param keepByProduct        是否保留副产物
     * @return 处理结果，包含无电路样板和提取的电路
     */
    public IPatternDetails processPatternWithCircuit(ItemStack originalPatternStack, Consumer<Integer> storedCircuit, Level level, boolean keepByProduct) {
        if (PatternDetailsHelper.decodePattern(originalPatternStack, level) instanceof AEProcessingPattern processingPattern) {
            int extractedCircuit = extractCircuitFromPattern(processingPattern);

            if (extractedCircuit < 0) {
                if (keepByProduct) return processingPattern;
            } else storedCircuit.accept(extractedCircuit);

            return createPatternWithoutCircuit(processingPattern, level, keepByProduct);
        } else {
            return null;
        }
    }

    /**
     * 获取用于配方的电路
     *
     * @param storedCircuit 存储的电路
     * @return 电路ItemStack，可能为空
     */
    public ItemStack getCircuitForRecipe(ItemStack storedCircuit) {
        if (storedCircuit == ItemStack.EMPTY || storedCircuit == null) {
            if (haveSharedPatternCircuit()) {
                return mePatternCircuitInventory.storage.getStackInSlot(0); // 返回配置的电路
            } else {
                return ItemStack.EMPTY;
            }
        }
        return storedCircuit;
    }

    /**
     * 创建一个写入指定电路的新处理样板。
     *
     * @param originalPatternStack 原始处理样板
     * @param circuitConfig        要写入的电路编号
     * @param replaceExisting      是否替换已有电路
     * @param level                当前世界
     * @return 修改后的样板；非处理样板返回空物品
     */
    public ItemStack createPatternWithCircuit(ItemStack originalPatternStack, int circuitConfig, boolean replaceExisting,
                                              Level level) {
        if (circuitConfig < 1 || circuitConfig > IntCircuitBehaviour.CIRCUIT_MAX) {
            return originalPatternStack;
        }
        return rebuildPatternCircuit(originalPatternStack, circuitConfig, replaceExisting, level);
    }

    public ItemStack removeCircuitFromPattern(ItemStack originalPatternStack, Level level) {
        return rebuildPatternCircuit(originalPatternStack, -1, true, level);
    }

    private ItemStack rebuildPatternCircuit(ItemStack originalPatternStack, int circuitConfig, boolean replaceExisting,
                                            Level level) {
        if (!(PatternDetailsHelper.decodePattern(originalPatternStack, level) instanceof AEProcessingPattern processingPattern)) {
            return ItemStack.EMPTY;
        }

        var filteredInputs = new ObjectArrayList<GenericStack>();
        boolean hasCircuit = false;

        for (var input : Arrays.stream(processingPattern.getSparseInputs()).filter(Objects::nonNull).toList()) {
            boolean isCircuit = input.what() instanceof AEItemKey itemKey &&
                    itemKey.getItem() == GTItems.INTEGRATED_CIRCUIT.asItem();

            if (isCircuit) {
                hasCircuit = true;
            } else {
                filteredInputs.add(input);
            }
        }

        if (circuitConfig < 0 && !hasCircuit) {
            return originalPatternStack;
        }
        if (circuitConfig > 0 && hasCircuit && !replaceExisting) {
            return originalPatternStack;
        }
        if (circuitConfig > 0) {
            filteredInputs.add(0, GenericStack.fromItemStack(IntCircuitBehaviour.stack(circuitConfig)));
        }

        return PatternDetailsHelper.encodeProcessingPattern(
                filteredInputs.toArray(new GenericStack[0]), processingPattern.getSparseOutputs());
    }

    /**
     * 从样板中提取电路
     *
     * @param processingPattern 处理样板
     * @return 提取的电路，如果没有则返回-1
     */
    private int extractCircuitFromPattern(AEProcessingPattern processingPattern) {
        for (var input : Arrays.stream(processingPattern.getSparseInputs()).filter(Objects::nonNull).toList()) {
            if (input.what() instanceof AEItemKey itemKey) {
                ItemStack itemStack = itemKey.toStack();
                if (IntCircuitBehaviour.isIntegratedCircuit(itemStack)) {
                    return IntCircuitBehaviour.getCircuitConfiguration(itemStack);
                }
            }
        }
        return -1;
    }

    /**
     * 创建一个移除了电路的新样板
     *
     * @param pattern 原始处理样板
     * @return 无电路并酌情删除副产物的样板
     */
    private IPatternDetails createPatternWithoutCircuit(AEProcessingPattern pattern, Level level, boolean keepByProduct) {
        var originalInputs = pattern.getSparseInputs();
        var originalOutputs = pattern.getSparseOutputs();
        var filteredInputs = new ObjectArrayList<GenericStack>();
        GenericStack[] filteredOutputs = originalOutputs;

        for (var input : Arrays.stream(originalInputs).filter(Objects::nonNull).toList()) {
            if (input.what() instanceof AEItemKey itemKey) {
                if (itemKey.getItem() == GTItems.INTEGRATED_CIRCUIT.asItem()) {
                    continue; // 跳过电路
                }
            }
            filteredInputs.add(input);
        }
        if (filteredInputs.isEmpty()) {
            filteredInputs.addAll(Arrays.stream(originalInputs).filter(Objects::nonNull).toList());
        }

        if (!keepByProduct) {
            var primary = Arrays.stream(originalOutputs).filter(Objects::nonNull).findFirst();
            if (primary.isPresent()) filteredOutputs = new GenericStack[] { primary.get() };
        }

        return PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeProcessingPattern(filteredInputs.toArray(new GenericStack[0]), filteredOutputs), level);
    }
}
