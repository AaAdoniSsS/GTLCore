package org.gtlcore.gtlcore.mixin.mc;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import appeng.menu.slot.AppEngSlot;
import com.glodblock.github.extendedae.container.ContainerPatternModifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 修复 ExtendedAE 样板修改器切换页面后槽位内容不显示的上游 bug：
 * 页面切换会禁用/启用槽位，服务端全量重同步时 {@link AppEngSlot#set(ItemStack)} 对已禁用的槽位是 no-op，
 * 若同步包先于客户端启用槽位到达，内容会被丢弃。这里在网络同步写入路径上兜底直接写入库存。
 */
@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {

    @Inject(method = "setItem", at = @At("HEAD"))
    private void gtlcore$syncDisabledSlot(int slotId, int stateId, ItemStack stack, CallbackInfo ci) {
        var self = (AbstractContainerMenu) (Object) this;
        if (self instanceof ContainerPatternModifier && slotId >= 0 && slotId < self.slots.size() &&
                self.getSlot(slotId) instanceof AppEngSlot slot && !slot.isSlotEnabled()) {
            slot.initialize(stack);
        }
    }

    @Inject(method = "initializeContents", at = @At("HEAD"))
    private void gtlcore$syncDisabledSlots(int containerId, List<ItemStack> items, ItemStack carried,
                                           CallbackInfo ci) {
        var self = (AbstractContainerMenu) (Object) this;
        if (!(self instanceof ContainerPatternModifier)) {
            return;
        }
        for (int i = 0; i < items.size() && i < self.slots.size(); i++) {
            if (self.slots.get(i) instanceof AppEngSlot slot && !slot.isSlotEnabled()) {
                slot.initialize(items.get(i));
            }
        }
    }
}
