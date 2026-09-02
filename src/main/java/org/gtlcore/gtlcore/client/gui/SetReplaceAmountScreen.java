package org.gtlcore.gtlcore.client.gui;

import org.gtlcore.gtlcore.client.gui.widget.IShiftAmountOperations;
import org.gtlcore.gtlcore.mixin.ae2.gui.NumberEntryWidgetAccessor;

import net.minecraft.world.item.ItemStack;

import appeng.api.stacks.GenericStack;
import appeng.client.gui.AESubScreen;
import appeng.client.gui.NumberEntryType;
import appeng.client.gui.me.common.ClientDisplaySlot;
import appeng.client.gui.widgets.NumberEntryWidget;
import appeng.client.gui.widgets.TabButton;
import appeng.core.localization.GuiText;
import appeng.menu.SlotSemantics;
import com.glodblock.github.extendedae.client.gui.GuiPatternModifier;
import com.glodblock.github.extendedae.common.EPPItemAndBlock;
import com.glodblock.github.extendedae.container.ContainerPatternModifier;

import java.util.function.Consumer;

/**
 * 样板修改器材料替换页中键设置数量的子界面，仿 AE2 {@code SetProcessingPatternAmountScreen}。
 * 与 AE2 原版不同，数量为 0 不会清除标记槽，而是作为强制替换模式的特殊语义保留。
 */
public class SetReplaceAmountScreen extends AESubScreen<ContainerPatternModifier, GuiPatternModifier> {

    private final NumberEntryWidget amount;

    private final GenericStack currentStack;

    private final Consumer<GenericStack> setter;

    public SetReplaceAmountScreen(GuiPatternModifier parentScreen, GenericStack currentStack,
                                  Consumer<GenericStack> setter) {
        super(parentScreen, "/screens/gtlcore_set_replace_amount.json");
        this.currentStack = currentStack;
        this.setter = setter;

        widgets.addButton("save", GuiText.Set.text(), this::confirm);

        var icon = new ItemStack(EPPItemAndBlock.PATTERN_MODIFIER);
        widgets.add("back", new TabButton(icon, icon.getHoverName(), btn -> returnToParent()));

        this.amount = widgets.addNumberEntryWidget("amountToStock", NumberEntryType.of(currentStack.what()));
        this.amount.setLongValue(currentStack.amount());
        this.amount.setMaxValue(Long.MAX_VALUE);
        this.amount.setTextFieldStyle(style.getWidget("amountToStockInput"));
        this.amount.setMinValue(0);
        this.amount.setHideValidationIcon(true);
        this.amount.setOnConfirm(this::confirm);
        ((NumberEntryWidgetAccessor) this.amount).getTextField().setMaxLength(Long.toString(Long.MAX_VALUE).length());
        ((IShiftAmountOperations) this.amount).gtlcore$enableShiftAmountOperations();

        addClientSideSlot(new ClientDisplaySlot(currentStack), SlotSemantics.MACHINE_OUTPUT);
    }

    @Override
    protected void init() {
        super.init();

        // 屏幕 JSON 包含工具箱，但这里用不到
        setSlotsHidden(SlotSemantics.TOOLBOX, true);
    }

    private void confirm() {
        this.amount.getLongValue().ifPresent(newAmount -> {
            this.setter.accept(new GenericStack(this.currentStack.what(), Math.max(0, newAmount)));
            returnToParent();
        });
    }
}
