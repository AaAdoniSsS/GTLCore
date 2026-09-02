package org.gtlcore.gtlcore.mixin.extendedae;

import org.gtlcore.gtlcore.client.gui.IPatternModifierScreen;
import org.gtlcore.gtlcore.client.gui.ModifyIcon;
import org.gtlcore.gtlcore.client.gui.ModifyIconButton;
import org.gtlcore.gtlcore.client.gui.SetReplaceAmountScreen;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import appeng.api.stacks.GenericStack;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.IconButton;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.InventoryActionPacket;
import appeng.helpers.InventoryAction;
import com.glodblock.github.extendedae.client.gui.GuiPatternModifier;
import com.glodblock.github.extendedae.container.ContainerPatternModifier;
import com.glodblock.github.extendedae.network.EPPNetworkHandler;
import com.glodblock.github.glodium.network.packet.CGenericPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(GuiPatternModifier.class)
public abstract class GuiPatternModifierMixin extends AEBaseScreen<ContainerPatternModifier>
                                              implements IPatternModifierScreen {

    @Shadow(remap = false)
    @Final
    private List<Button> multiBtns;

    @Shadow(remap = false)
    @Final
    private Button replace;

    @Unique
    private List<ModifyIconButton> gtlcore$scaleButtons;
    @Unique
    private Button gtlcore$scopeButton;
    @Unique
    private IconButton gtlcore$insertDeleteButton;
    @Unique
    private ModifyIconButton gtlcore$swapButton;
    @Unique
    private Button gtlcore$replaceButton;
    @Unique
    private int gtlcore$scope;
    @Unique
    private boolean gtlcore$insertDelete;

    private GuiPatternModifierMixin(ContainerPatternModifier menu, Inventory playerInventory, Component title,
                                    ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Override
    public void gtlcore$openReplaceAmountScreen(Slot slot) {
        var currentStack = GenericStack.fromItemStack(slot.getItem());
        if (currentStack == null) {
            return;
        }
        var screen = new SetReplaceAmountScreen((GuiPatternModifier) (Object) this, currentStack,
                newStack -> NetworkHandler.instance().sendToServer(new InventoryActionPacket(
                        InventoryAction.SET_FILTER, slot.index, GenericStack.wrapInItemStack(newStack))));
        this.switchToScreen(screen);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void gtlcore$initButtons(CallbackInfo ci) {
        // 移除原版 x2/x10/÷2/÷10 按钮与替换按钮，保留清除按钮
        for (int i = 0; i < 4; i++) {
            this.removeWidget(this.multiBtns.get(i));
        }
        this.removeWidget(this.replace);
        if (this.gtlcore$scaleButtons == null) {
            gtlcore$createButtons();
        }
        for (int i = 0; i < this.gtlcore$scaleButtons.size(); i++) {
            var button = this.gtlcore$scaleButtons.get(i);
            button.setPosition(this.leftPos + 7 + i * 18, this.topPos + 19);
            this.addRenderableWidget(button);
        }
        this.gtlcore$scopeButton.setPosition(this.leftPos + 108, this.topPos + 19);
        gtlcore$updateScopeButton();
        this.addRenderableWidget(this.gtlcore$scopeButton);
        // 清除按钮右移，为范围切换按钮腾出位置
        this.multiBtns.get(4).setPosition(this.leftPos + 138, this.topPos + 19);
        this.gtlcore$swapButton.setPosition(this.leftPos + 40, this.topPos + 21);
        this.addRenderableWidget(this.gtlcore$swapButton);
        this.gtlcore$insertDeleteButton.setPosition(this.leftPos + 106, this.topPos + 20);
        this.addRenderableWidget(this.gtlcore$insertDeleteButton);
        this.gtlcore$replaceButton.setPosition(this.leftPos + 126, this.topPos + 19);
        this.addRenderableWidget(this.gtlcore$replaceButton);
    }

    @Inject(method = "updateBeforeRender", at = @At("TAIL"), remap = false)
    private void gtlcore$syncButtonVisibility(CallbackInfo ci) {
        if (this.gtlcore$scaleButtons == null) {
            return;
        }
        boolean page0 = this.menu.page == 0;
        for (var button : this.gtlcore$scaleButtons) {
            button.setVisibility(page0);
        }
        this.gtlcore$scopeButton.visible = page0;
        boolean page1 = this.menu.page == 1;
        this.gtlcore$insertDeleteButton.visible = page1;
        this.gtlcore$swapButton.setVisibility(page1);
        this.gtlcore$replaceButton.visible = page1;
    }

    @Unique
    private void gtlcore$createButtons() {
        this.gtlcore$scaleButtons = new ArrayList<>();
        this.gtlcore$scaleButtons.add(gtlcore$createScaleButton(2, false, ModifyIcon.MULTIPLY_2,
                Component.translatable("gui.gtlcore.pattern_recipe_multiply_2"),
                Component.translatable("tooltip.gtlcore.pattern_materials_multiply_2")));
        this.gtlcore$scaleButtons.add(gtlcore$createScaleButton(3, false, ModifyIcon.MULTIPLY_3,
                Component.translatable("gui.gtlcore.pattern_recipe_multiply_3"),
                Component.translatable("tooltip.gtlcore.pattern_materials_multiply_3")));
        this.gtlcore$scaleButtons.add(gtlcore$createScaleButton(5, false, ModifyIcon.MULTIPLY_5,
                Component.translatable("gui.gtlcore.pattern_recipe_multiply_5"),
                Component.translatable("tooltip.gtlcore.pattern_materials_multiply_5")));
        this.gtlcore$scaleButtons.add(gtlcore$createScaleButton(2, true, ModifyIcon.DIVISION_2,
                Component.translatable("gui.gtlcore.pattern_recipe_divide_2"),
                Component.translatable("tooltip.gtlcore.pattern_materials_divide_2")));
        this.gtlcore$scaleButtons.add(gtlcore$createScaleButton(3, true, ModifyIcon.DIVISION_3,
                Component.translatable("gui.gtlcore.pattern_recipe_divide_3"),
                Component.translatable("tooltip.gtlcore.pattern_materials_divide_3")));
        this.gtlcore$scaleButtons.add(gtlcore$createScaleButton(5, true, ModifyIcon.DIVISION_5,
                Component.translatable("gui.gtlcore.pattern_recipe_divide_5"),
                Component.translatable("tooltip.gtlcore.pattern_materials_divide_5")));
        this.gtlcore$scopeButton = Button.builder(Component.empty(), b -> {
            this.gtlcore$scope = (this.gtlcore$scope + 1) % 3;
            gtlcore$updateScopeButton();
        }).size(28, 18)
                .tooltip(Tooltip.create(Component.translatable("tooltip.gtlcore.pattern_modify_scope")))
                .build();
        this.gtlcore$swapButton = new ModifyIconButton(
                b -> EPPNetworkHandler.INSTANCE.sendToServer(new CGenericPacket("swapReplace")),
                ModifyIcon.UNDO,
                Component.translatable("tooltip.gtlcore.pattern_modifier.swap_replace"));
        this.gtlcore$insertDeleteButton = new IconButton(b -> {
            this.gtlcore$insertDelete = !this.gtlcore$insertDelete;
        }) {

            @Override
            public List<Component> getTooltipMessage() {
                return List.of(
                        Component.translatable("gui.gtlcore.pattern_modifier.insert_delete"),
                        Component.translatable(gtlcore$insertDelete ?
                                "gui.gtlcore.pattern_modifier.insert_delete.on" :
                                "gui.gtlcore.pattern_modifier.insert_delete.off"));
            }

            @Override
            protected Icon getIcon() {
                return gtlcore$insertDelete ? Icon.VALID : Icon.INVALID;
            }
        };
        this.gtlcore$replaceButton = Button
                .builder(Component.translatable("gui.expatternprovider.pattern_modifier.replace_button"),
                        b -> EPPNetworkHandler.INSTANCE
                                .sendToServer(new CGenericPacket("gtlReplace", this.gtlcore$insertDelete)))
                .size(46, 18)
                .tooltip(Tooltip.create(Component.translatable("tooltip.gtlcore.pattern_modifier.replace_rounding")))
                .build();
    }

    @Unique
    private ModifyIconButton gtlcore$createScaleButton(int scale, boolean div, ModifyIcon icon,
                                                       Component displayName, Component displayValue) {
        return new ModifyIconButton(b -> EPPNetworkHandler.INSTANCE
                .sendToServer(new CGenericPacket("modify", scale, div, this.gtlcore$scope)),
                icon, displayName, displayValue);
    }

    @Unique
    private void gtlcore$updateScopeButton() {
        Component text = switch (this.gtlcore$scope) {
            case 1 -> Component.translatable("gui.gtlcore.pattern_modify_scope.inputs");
            case 2 -> Component.translatable("gui.gtlcore.pattern_modify_scope.outputs");
            default -> Component.translatable("gui.gtlcore.pattern_modify_scope.all");
        };
        this.gtlcore$scopeButton.setMessage(text);
    }
}
