package org.gtlcore.gtlcore.client.gui;

import net.minecraft.world.inventory.Slot;

/**
 * 样板修改器（ExtendedAE Pattern Modifier）GUI 扩展接口，
 * 由 {@code GuiPatternModifierMixin} 实现，供客户端事件处理器调用。
 */
public interface IPatternModifierScreen {

    void gtlcore$openReplaceAmountScreen(Slot slot);
}
