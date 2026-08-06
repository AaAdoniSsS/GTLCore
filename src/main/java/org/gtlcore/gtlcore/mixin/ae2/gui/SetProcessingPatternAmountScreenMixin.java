package org.gtlcore.gtlcore.mixin.ae2.gui;

import org.gtlcore.gtlcore.client.gui.widget.IShiftAmountOperations;

import appeng.api.stacks.GenericStack;
import appeng.client.gui.me.items.PatternEncodingTermScreen;
import appeng.client.gui.me.items.SetProcessingPatternAmountScreen;
import appeng.client.gui.widgets.NumberEntryWidget;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(SetProcessingPatternAmountScreen.class)
public abstract class SetProcessingPatternAmountScreenMixin {

    @Shadow(remap = false)
    @Final
    private NumberEntryWidget amount;

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void gtlcore$allowLongAmountInput(PatternEncodingTermScreen<?> parent, GenericStack currentStack,
                                              Consumer<GenericStack> setter, CallbackInfo ci) {
        ((NumberEntryWidgetAccessor) this.amount).getTextField().setMaxLength(Long.toString(Long.MAX_VALUE).length());
        ((IShiftAmountOperations) this.amount).gtlcore$enableShiftAmountOperations();
    }

    /**
     * @author .
     * @reason 样板终端中键设置数量上限
     */
    @Overwrite(remap = false)
    private long getMaxAmount() {
        return Long.MAX_VALUE;
    }
}
