package org.gtlcore.gtlcore.mixin.wildcard;

import net.minecraft.world.item.ItemStack;

import org.leodreamer.wildcard_pattern.wildcard.WildcardPatternLogic;
import org.leodreamer.wildcard_pattern.wildcard.feature.IWildcardIOComponent;
import org.leodreamer.wildcard_pattern.wildcard.gui.WildcardComponentListGroup;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Pseudo
@Mixin(targets = "org.leodreamer.wildcard_pattern.wildcard.gui.WildcardIOFancyConfigurator", remap = false)
public abstract class WildcardIOFancyConfiguratorMixin {

    @Shadow
    @Final
    private WildcardPatternLogic logic;

    @Shadow
    @Final
    private WildcardPatternLogic.IO io;

    @Shadow
    @Final
    private Consumer<ItemStack> onSave;

    @Shadow
    private WildcardComponentListGroup<IWildcardIOComponent> componentList;

    @Inject(method = "save", at = @At("HEAD"), cancellable = true)
    private void gtlcore$collectBeforeSerialize(CallbackInfo ci) {
        var components = componentList.getComponents();
        components.forEach(IWildcardIOComponent::onSave);
        onSave.accept(logic.setIOComponents(io, components));
        ci.cancel();
    }
}
