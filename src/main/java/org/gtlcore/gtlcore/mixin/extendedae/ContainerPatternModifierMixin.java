package org.gtlcore.gtlcore.mixin.extendedae;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AECraftingPattern;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.crafting.pattern.EncodedPatternItem;
import appeng.menu.SlotSemantics;
import com.glodblock.github.extendedae.common.inventory.PatternModifierInventory;
import com.glodblock.github.extendedae.container.ContainerPatternModifier;
import com.glodblock.github.extendedae.util.Ae2Reflect;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;
import java.util.LinkedHashMap;

@Mixin(ContainerPatternModifier.class)
public abstract class ContainerPatternModifierMixin {

    @Unique
    private static final int GTLCORE$SCOPE_ALL = 0;
    @Unique
    private static final int GTLCORE$SCOPE_INPUTS = 1;
    @Unique
    private static final int GTLCORE$SCOPE_OUTPUTS = 2;

    @ModifyConstant(method = "checkModify", remap = false, constant = @Constant(longValue = 999999L))
    private long modifyContainer(long constant) {
        return Integer.MAX_VALUE;
    }

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void gtlcore$registerActions(int id, Inventory playerInventory, PatternModifierInventory host,
                                         CallbackInfo ci) {
        var self = (ContainerPatternModifier) (Object) this;
        self.getActionMap().put("modify",
                o -> gtlcore$modify(o.get(0), o.get(1), o.getParaAmount() > 2 ? o.get(2) : GTLCORE$SCOPE_ALL));
        self.getActionMap().put("gtlReplace", o -> gtlcore$replace(o.get(0)));
        self.getActionMap().put("swapReplace", o -> gtlcore$swapReplace());
    }

    @Inject(method = "setPage", at = @At("TAIL"), remap = false)
    private void gtlcore$onSetPage(int page, CallbackInfo ci) {
        // 修复上游 bug：切换页面后槽位内容不重新同步，导致样板与标记不显示
        var self = (ContainerPatternModifier) (Object) this;
        if (!self.getPlayer().level().isClientSide) {
            self.sendAllDataToRemote();
        }
    }

    @Unique
    private void gtlcore$swapReplace() {
        var self = (ContainerPatternModifier) (Object) this;
        var target = self.replaceTarget.getItem();
        var with = self.replaceWith.getItem();
        self.replaceTarget.set(with);
        self.replaceWith.set(target);
    }

    /**
     * @author GTLCore
     * @reason 统一走带强制模式参数的新替换逻辑，旧动作默认关闭强制模式
     */
    @Overwrite(remap = false)
    public void replace() {
        gtlcore$replace(false);
    }

    @Unique
    private void gtlcore$modify(int scale, boolean div, int scope) {
        if (scale <= 0) {
            return;
        }
        var self = (ContainerPatternModifier) (Object) this;
        for (var slot : self.getSlots(SlotSemantics.ENCODED_PATTERN)) {
            var stack = slot.getItem();
            if (stack.getItem() instanceof EncodedPatternItem pattern) {
                var detail = pattern.decode(stack, self.getPlayer().level(), false);
                if (detail instanceof AEProcessingPattern process) {
                    var input = process.getSparseInputs();
                    var output = process.getOutputs();
                    boolean modifyInput = scope != GTLCORE$SCOPE_OUTPUTS;
                    boolean modifyOutput = scope != GTLCORE$SCOPE_INPUTS;
                    if ((!modifyInput || gtlcore$checkModify(input, scale, div)) &&
                            (!modifyOutput || gtlcore$checkModify(output, scale, div))) {
                        var mulInput = modifyInput ? gtlcore$modifyStacks(input, scale, div) : input;
                        var mulOutput = modifyOutput ? gtlcore$modifyStacks(output, scale, div) : output;
                        slot.set(gtlcore$encodeProcessingPreservingMeta(stack, gtlcore$compact(mulInput),
                                gtlcore$compact(mulOutput)));
                    }
                }
            }
        }
    }

    @Unique
    private boolean gtlcore$checkModify(GenericStack[] stacks, int scale, boolean div) {
        if (div) {
            for (var stack : stacks) {
                if (stack != null && stack.amount() % scale != 0) {
                    return false;
                }
            }
        } else {
            for (var stack : stacks) {
                if (stack != null) {
                    long upper = (long) Integer.MAX_VALUE * stack.what().getAmountPerUnit();
                    if (stack.amount() * scale > upper) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * 压缩稀疏数组（去除 null 槽位）并合并同 key 条目（数量求和、保持首次出现顺序）：
     * 编码终端生成的样板 in/out 列表被填充至网格大小（81/27），
     * 直接追加或替换产生的重复条目会导致超出 AE2 的 MAX_INPUT_SLOTS 上限而被判为无效样板。
     */
    @Unique
    private GenericStack[] gtlcore$compact(GenericStack[] stacks) {
        var merged = new LinkedHashMap<AEKey, Long>();
        for (var stack : stacks) {
            if (stack != null) {
                merged.merge(stack.what(), stack.amount(), Long::sum);
            }
        }
        var compacted = new GenericStack[merged.size()];
        int i = 0;
        for (var entry : merged.entrySet()) {
            compacted[i++] = new GenericStack(entry.getKey(), entry.getValue());
        }
        return compacted;
    }

    @Unique
    private ItemStack gtlcore$encodeProcessingPreservingMeta(ItemStack original, GenericStack[] inputs,
                                                             GenericStack[] outputs) {
        var newPattern = PatternDetailsHelper.encodeProcessingPattern(inputs, outputs);
        gtlcore$copyNonStructuralTags(original, newPattern);
        return newPattern;
    }

    /** 重编码后保留原样板的非结构 NBT（如 gtlcore 的编码者信息、快速上传配方类型等）。 */
    @Unique
    private void gtlcore$copyNonStructuralTags(ItemStack original, ItemStack newPattern) {
        var originalTag = original.getTag();
        var newTag = newPattern.getTag();
        if (originalTag == null || newTag == null) {
            return;
        }
        for (String key : originalTag.getAllKeys()) {
            if (!newTag.contains(key)) {
                newTag.put(key, originalTag.get(key).copy());
            }
        }
    }

    @Unique
    private GenericStack[] gtlcore$modifyStacks(GenericStack[] stacks, int scale, boolean div) {
        var des = new GenericStack[stacks.length];
        for (int i = 0; i < stacks.length; i++) {
            if (stacks[i] != null) {
                long amt = div ? stacks[i].amount() / scale : stacks[i].amount() * scale;
                des[i] = new GenericStack(stacks[i].what(), amt);
            }
        }
        return des;
    }

    @Unique
    private boolean gtlcore$insertLimitExceeded;

    @Unique
    private void gtlcore$replace(boolean force) {
        var self = (ContainerPatternModifier) (Object) this;
        gtlcore$insertLimitExceeded = false;
        var targetStack = GenericStack.fromItemStack(self.replaceTarget.getItem());
        var withStack = GenericStack.fromItemStack(self.replaceWith.getItem());
        if (targetStack == null && withStack == null) {
            return;
        }
        AEKey targetKey = targetStack == null ? null : targetStack.what();
        AEKey withKey = withStack == null ? null : withStack.what();
        long targetAmt = targetStack == null ? 0 : targetStack.amount();
        long withAmt = withStack == null ? 0 : withStack.amount();
        boolean deleteMode = withKey == null || withAmt <= 0;
        boolean insertMode = targetKey == null || targetAmt <= 0;
        if (targetKey == null) {
            // 左槽留空：仅强制模式下允许纯插入
            if (!force || deleteMode) {
                return;
            }
        } else if ((!force && (deleteMode || insertMode)) || (deleteMode && insertMode)) {
            return;
        }
        for (var slot : self.getSlots(SlotSemantics.ENCODED_PATTERN)) {
            var stack = slot.getItem();
            if (stack.getItem() instanceof EncodedPatternItem pattern) {
                var detail = pattern.decode(stack, self.getPlayer().level(), false);
                if (detail instanceof AEProcessingPattern process) {
                    // 压缩并合并同 key 条目：去除占位槽，替换/插入产生的重复条目也会合并
                    var replaceInput = gtlcore$compact(gtlcore$replace(process.getSparseInputs(), targetKey, withKey,
                            targetAmt, withAmt, deleteMode));
                    var replaceOutput = gtlcore$compact(gtlcore$replace(process.getOutputs(), targetKey, withKey,
                            targetAmt, withAmt, deleteMode));
                    if (insertMode) {
                        // 已有同 key 条目则就地合并；新增条目受 AE2 输入槽上限约束，超出则跳过该样板
                        boolean merged = false;
                        for (int i = 0; i < replaceInput.length; i++) {
                            if (replaceInput[i].what().equals(withKey)) {
                                replaceInput[i] = new GenericStack(withKey, replaceInput[i].amount() + withAmt);
                                merged = true;
                                break;
                            }
                        }
                        if (!merged) {
                            if (replaceInput.length >= AEProcessingPattern.MAX_INPUT_SLOTS) {
                                gtlcore$insertLimitExceeded = true;
                                continue;
                            }
                            replaceInput = Arrays.copyOf(replaceInput, replaceInput.length + 1);
                            replaceInput[replaceInput.length - 1] = new GenericStack(withKey, withAmt);
                        }
                    }
                    try {
                        slot.set(gtlcore$encodeProcessingPreservingMeta(stack, replaceInput, replaceOutput));
                    } catch (Exception e) {
                        // It is an invalid change
                    }
                } else if (!insertMode && detail instanceof AECraftingPattern craft &&
                        targetKey instanceof AEItemKey && (withKey == null || withKey instanceof AEItemKey)) {
                            var replaceInput = gtlcore$replace(craft.getSparseInputs(), targetKey, withKey, targetAmt, withAmt,
                                    deleteMode);
                            try {
                                var newPattern = PatternDetailsHelper.encodeCraftingPattern(
                                        Ae2Reflect.getCraftRecipe(craft),
                                        gtlcore$itemize(replaceInput),
                                        gtlcore$itemize(craft.getPrimaryOutput()),
                                        craft.canSubstitute,
                                        craft.canSubstituteFluids);
                                // noinspection DataFlowIssue
                                var check = new AECraftingPattern(AEItemKey.of(newPattern), self.getPlayer().level());
                                // noinspection ConstantValue
                                if (check != null) {
                                    gtlcore$copyNonStructuralTags(stack, newPattern);
                                    slot.set(newPattern);
                                }
                            } catch (Exception e) {
                                // It is an invalid change
                            }
                        }
            }
        }
        if (gtlcore$insertLimitExceeded) {
            self.getPlayer().displayClientMessage(
                    Component.translatable("message.gtlcore.pattern_modifier.insert_limit_exceeded"), true);
        }
    }

    @Unique
    private GenericStack[] gtlcore$replace(GenericStack[] stacks, @Nullable AEKey target, @Nullable AEKey with,
                                           long targetAmt, long withAmt, boolean deleteMode) {
        var des = new GenericStack[stacks.length];
        for (int i = 0; i < stacks.length; i++) {
            if (stacks[i] != null) {
                if (targetAmt > 0 && stacks[i].what().equals(target)) {
                    if (deleteMode) {
                        des[i] = null;
                    } else {
                        long newAmt = Math.max(1, (long) ((double) stacks[i].amount() * withAmt / targetAmt));
                        des[i] = new GenericStack(with, newAmt);
                    }
                } else {
                    des[i] = new GenericStack(stacks[i].what(), stacks[i].amount());
                }
            }
        }
        return des;
    }

    @Unique
    private ItemStack[] gtlcore$itemize(GenericStack[] stacks) {
        var items = new ItemStack[stacks.length];
        for (int i = 0; i < stacks.length; i++) {
            items[i] = gtlcore$itemize(stacks[i]);
        }
        return items;
    }

    @Unique
    private ItemStack gtlcore$itemize(@Nullable GenericStack stack) {
        if (stack != null && stack.what() instanceof AEItemKey what) {
            return what.toStack((int) stack.amount());
        } else {
            return ItemStack.EMPTY;
        }
    }
}
