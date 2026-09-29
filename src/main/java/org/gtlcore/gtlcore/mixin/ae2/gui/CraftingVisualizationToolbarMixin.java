package org.gtlcore.gtlcore.mixin.ae2.gui;

import org.gtlcore.gtlcore.integration.ae2.graph.GraphPlanSummaryView;

import net.minecraft.client.gui.components.Button;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.me.crafting.CraftConfirmScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Graph plans use the ring browser; legacy plans retain the optional AE2CT tree. */
@Mixin(AEBaseScreen.class)
public abstract class CraftingVisualizationToolbarMixin {

    @Unique
    private Button gtlcore$treeButton;

    @Inject(method = "addToLeftToolbar", at = @At("HEAD"), remap = false)
    private void gtlcore$rememberTreeButton(Button button, CallbackInfoReturnable<Button> cir) {
        if ((Object) this instanceof CraftConfirmScreen && button.getClass().getName().equals("com.neuvillette.ae2ct.gui.ChangeButton")) {
            gtlcore$treeButton = button;
            // The server's plan has not necessarily arrived during screen construction.
            button.visible = button.active = false;
        }
    }

    @Inject(method = "updateBeforeRender", at = @At("TAIL"), remap = false)
    private void gtlcore$updateTreeButton(CallbackInfo ci) {
        if (gtlcore$treeButton != null && (Object) this instanceof CraftConfirmScreen screen) {
            var plan = screen.getMenu().getPlan();
            // Use the actual server plan, not a potentially different client config.
            boolean legacy = plan != null &&
                    (!(plan instanceof GraphPlanSummaryView view) || view.gtlcore$graphPlanId() == null);
            gtlcore$treeButton.visible = gtlcore$treeButton.active = legacy;
        }
    }
}
