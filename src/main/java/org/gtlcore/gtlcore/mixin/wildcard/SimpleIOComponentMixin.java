package org.gtlcore.gtlcore.mixin.wildcard;

import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.gui.widget.TextFieldWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;

import appeng.api.stacks.GenericStack;
import org.leodreamer.wildcard_pattern.util.MathUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "org.leodreamer.wildcard_pattern.wildcard.impl.SimpleIOComponent", remap = false)
public abstract class SimpleIOComponentMixin {

    @Shadow
    private GenericStack stack;

    @Shadow
    private SlotWidget itemSlot;

    @Shadow
    private TextFieldWidget amountEdit;

    @Shadow
    public abstract void onSave();

    @Inject(method = "createUILine", at = @At("RETURN"))
    private void gtlcore$keepItemEditsBeforeRebuild(WidgetGroup line, CallbackInfo ci) {
        // Initialize before binding: filling a new slot must not collect from an unfinished row.
        amountEdit.setCurrentString(MathUtils.saturatedCast(stack.amount()));
        // onSave only collects widget values; serialization remains the configurator's responsibility.
        itemSlot.setChangeListener(this::onSave);
    }
}
