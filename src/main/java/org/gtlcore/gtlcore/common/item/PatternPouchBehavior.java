package org.gtlcore.gtlcore.common.item;

import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.item.component.IItemUIFactory;

import com.lowdragmc.lowdraglib.gui.factory.HeldItemUIFactory;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.jei.IngredientIO;
import com.lowdragmc.lowdraglib.misc.ItemStackTransfer;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.api.parts.IPart;
import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import org.jetbrains.annotations.Nullable;

/**
 * 样板包装袋：仅可存储已编码样板，容量 36。
 * <p>
 * - 对空气右键（非潜行）：打开 UI。<br>
 * - 右键样板供应器（及实现 {@link PatternProviderLogicHost} 或 {@link MEPatternBufferPartMachine} 的方块）：取出其中的样板到包装袋。<br>
 * - 潜行右键对应方块：将包装袋内的样板放入供应器。
 */
public class PatternPouchBehavior implements IItemUIFactory {

    public static final PatternPouchBehavior INSTANCE = new PatternPouchBehavior();

    public static final int SLOT_COUNT = 36;
    private static final String INV_TAG = "PatternInv";

    private static final int COLS = 9;
    private static final int ROWS = SLOT_COUNT / COLS;
    private static final int SLOT_SIZE = 18;
    private static final int LEFT = 7;
    private static final int PATTERN_TOP = 18;
    // 样板区底部 + 间距后放置玩家背包
    private static final int INV_TOP = PATTERN_TOP + ROWS * SLOT_SIZE + 8;
    private static final int WIDTH = 176;
    private static final int HEIGHT = INV_TOP + 82;

    /**
     * 读取包装袋内部库存。库存仅接受已编码样板。
     */
    public static ItemStackTransfer getInventory(ItemStack pouch) {
        ItemStackTransfer transfer = new ItemStackTransfer(SLOT_COUNT);
        transfer.setFilter(stack -> stack.isEmpty() || PatternDetailsHelper.isEncodedPattern(stack));
        if (pouch.hasTag() && pouch.getTag().contains(INV_TAG)) {
            transfer.deserializeNBT(pouch.getTag().getCompound(INV_TAG));
        }
        return transfer;
    }

    /**
     * 将库存写回包装袋 NBT。
     */
    public static void saveInventory(ItemStack pouch, ItemStackTransfer transfer) {
        pouch.getOrCreateTag().put(INV_TAG, transfer.serializeNBT());
    }

    @Override
    public ModularUI createUI(HeldItemUIFactory.HeldItemHolder holder, Player player) {
        ItemStack pouch = holder.getHeld();
        ItemStackTransfer inventory = getInventory(pouch);
        inventory.setOnContentsChanged(() -> {
            saveInventory(pouch, inventory);
            holder.markAsDirty();
        });

        WidgetGroup group = new WidgetGroup(0, 0, WIDTH, HEIGHT);
        group.addWidget(new LabelWidget(LEFT, 6, Component.translatable("item.gtlcore.pattern_pouch")));

        // 36 个样板槽：4 行 x 9 列
        for (int i = 0; i < SLOT_COUNT; i++) {
            int row = i / COLS;
            int col = i % COLS;
            group.addWidget(new SlotWidget(inventory, i,
                    LEFT + col * SLOT_SIZE, PATTERN_TOP + row * SLOT_SIZE, true, true)
                    .setBackgroundTexture(GuiTextures.SLOT)
                    .setIngredientIO(IngredientIO.INPUT));
        }

        // 手动构建玩家背包，与样板槽相同的对齐方式
        Inventory playerInv = player.getInventory();
        // 锁定当前持有包装袋的槽位，避免 UI 打开期间被移走导致引用失效
        int heldSlot = holder.getHand() == InteractionHand.MAIN_HAND ? playerInv.selected : -1;
        // 主背包 3 行 x 9 列（槽位 9..35）
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < COLS; col++) {
                int index = col + (row + 1) * COLS;
                group.addWidget(new SlotWidget(playerInv, index,
                        LEFT + col * SLOT_SIZE, INV_TOP + row * SLOT_SIZE, true, true)
                        .setBackgroundTexture(GuiTextures.SLOT));
            }
        }
        // 快捷栏 1 行 x 9 列（槽位 0..8）
        int hotbarY = INV_TOP + 3 * SLOT_SIZE + 4;
        for (int col = 0; col < COLS; col++) {
            boolean locked = col == heldSlot;
            group.addWidget(new SlotWidget(playerInv, col,
                    LEFT + col * SLOT_SIZE, hotbarY, !locked, !locked)
                    .setBackgroundTexture(GuiTextures.SLOT));
        }

        return new ModularUI(WIDTH, HEIGHT, holder, player)
                .widget(group)
                .background(GuiTextures.BACKGROUND);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Item item, Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        // 潜行右键空气不打开 UI（潜行用于向供应器写入样板）
        if (player.isShiftKeyDown()) {
            return new InteractionResultHolder<>(InteractionResult.PASS, stack);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            HeldItemUIFactory.INSTANCE.openUI(serverPlayer, usedHand);
        }
        return new InteractionResultHolder<>(InteractionResult.SUCCESS, stack);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack itemStack, UseOnContext context) {
        if (!(context.getPlayer() instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }

        InternalInventory providerInv = resolveProviderInventory(context);
        if (providerInv == null) {
            return InteractionResult.PASS;
        }

        ItemStackTransfer pouchInv = getInventory(itemStack);
        boolean changed;
        if (serverPlayer.isShiftKeyDown()) {
            // 潜行右键：包装袋 -> 供应器
            changed = movePouchToProvider(pouchInv, providerInv);
            if (changed) {
                saveInventory(itemStack, pouchInv);
                serverPlayer.displayClientMessage(Component.translatable("message.gtlcore.pattern_pouch_inserted"), true);
            } else {
                serverPlayer.displayClientMessage(Component.translatable("message.gtlcore.pattern_pouch_insert_failed"), true);
            }
        } else {
            // 右键：供应器 -> 包装袋
            changed = moveProviderToPouch(providerInv, pouchInv);
            if (changed) {
                saveInventory(itemStack, pouchInv);
                serverPlayer.displayClientMessage(Component.translatable("message.gtlcore.pattern_pouch_extracted"), true);
            } else {
                serverPlayer.displayClientMessage(Component.translatable("message.gtlcore.pattern_pouch_extract_failed"), true);
            }
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * 从点击的方块解析样板供应器库存，命中以下三类：
     * <ul>
     *     <li>AE2 线缆总线上的样板供应器部件</li>
     *     <li>实现 {@link PatternProviderLogicHost} 的方块（样板供应器方块）</li>
     *     <li>GTLCore 的 {@link MEPatternBufferPartMachine}（样板总成）</li>
     * </ul>
     */
    @Nullable
    private static InternalInventory resolveProviderInventory(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockEntity tile = level.getBlockEntity(pos);

        if (tile instanceof CableBusBlockEntity cable) {
            Vec3 hitVec = context.getClickLocation();
            Vec3 hitInBlock = new Vec3(hitVec.x - pos.getX(), hitVec.y - pos.getY(), hitVec.z - pos.getZ());
            IPart part = cable.getCableBus().selectPartLocal(hitInBlock).part;
            if (part instanceof PatternProviderLogicHost providerPart) {
                return providerPart.getLogic().getPatternInv();
            }
            return null;
        }
        if (tile instanceof PatternProviderLogicHost providerBlock) {
            return providerBlock.getLogic().getPatternInv();
        }
        if (tile instanceof MetaMachineBlockEntity mmbe &&
                mmbe.getMetaMachine() instanceof MEPatternBufferPartMachine buffer) {
            return buffer.getTerminalPatternInventory();
        }
        return null;
    }

    /**
     * 将供应器内的已编码样板转移到包装袋，直到包装袋装满或供应器取空。
     */
    private static boolean moveProviderToPouch(InternalInventory providerInv, ItemStackTransfer pouchInv) {
        boolean changed = false;
        for (int slot = 0; slot < providerInv.size(); slot++) {
            ItemStack stack = providerInv.getStackInSlot(slot);
            if (stack.isEmpty() || !PatternDetailsHelper.isEncodedPattern(stack)) {
                continue;
            }
            int pouchSlot = findEmptyPouchSlot(pouchInv);
            if (pouchSlot < 0) {
                break; // 包装袋已满
            }
            ItemStack extracted = providerInv.extractItem(slot, stack.getCount(), false);
            if (extracted.isEmpty()) {
                continue;
            }
            pouchInv.setStackInSlot(pouchSlot, extracted);
            changed = true;
        }
        return changed;
    }

    /**
     * 将包装袋内的样板转移到供应器空槽，直到供应器装满或包装袋取空。
     */
    private static boolean movePouchToProvider(ItemStackTransfer pouchInv, InternalInventory providerInv) {
        boolean changed = false;
        for (int slot = 0; slot < pouchInv.getSlots(); slot++) {
            ItemStack stack = pouchInv.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            int providerSlot = findEmptyProviderSlot(providerInv);
            if (providerSlot < 0) {
                break; // 供应器已满
            }
            providerInv.setItemDirect(providerSlot, stack.copy());
            pouchInv.setStackInSlot(slot, ItemStack.EMPTY);
            changed = true;
        }
        return changed;
    }

    private static int findEmptyPouchSlot(ItemStackTransfer pouchInv) {
        for (int slot = 0; slot < pouchInv.getSlots(); slot++) {
            if (pouchInv.getStackInSlot(slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    private static int findEmptyProviderSlot(InternalInventory providerInv) {
        for (int slot = 0; slot < providerInv.size(); slot++) {
            if (providerInv.getStackInSlot(slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }
}
