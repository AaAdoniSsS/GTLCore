package org.gtlcore.gtlcore.mixin.ae2.gui;

import org.gtlcore.gtlcore.api.crafting.IAutoExpandMenu;
import org.gtlcore.gtlcore.api.crafting.IAutoExpandSettings;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.AEBaseMenu;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.PatternProviderMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PatternProviderMenu.class)
public abstract class PatternProviderMenuMixin extends AEBaseMenu implements IAutoExpandMenu {

    @Shadow(remap = false)
    @Final
    protected PatternProviderLogic logic;

    @Unique
    @GuiSync(IAutoExpandMenu.GUI_SYNC_AUTO_EXPAND)
    public boolean gtlcore$autoExpand;

    protected PatternProviderMenuMixin(MenuType<?> menuType, int id, Inventory playerInventory, Object host) {
        super(menuType, id, playerInventory, host);
    }

    @Inject(method = "<init>(Lnet/minecraft/world/inventory/MenuType;ILnet/minecraft/world/entity/player/Inventory;Lappeng/helpers/patternprovider/PatternProviderLogicHost;)V",
            at = @At("TAIL"),
            remap = false)
    private void gtlcore$registerAutoExpandAction(MenuType<?> menuType, int id, Inventory playerInventory,
                                                  PatternProviderLogicHost host, CallbackInfo ci) {
        this.registerClientAction(IAutoExpandMenu.ACTION_TOGGLE_AUTO_EXPAND, this::gtlcore$toggleAutoExpand);
    }

    @Inject(method = "broadcastChanges",
            at = @At(value = "INVOKE",
                     target = "Lappeng/menu/AEBaseMenu;broadcastChanges()V"))
    private void gtlcore$syncAutoExpand(CallbackInfo ci) {
        if (isServerSide()) {
            this.gtlcore$autoExpand = ((IAutoExpandSettings) this.logic).isPatternAutoExpand();
        }
    }

    @Override
    public boolean gtlcore$isAutoExpand() {
        return this.gtlcore$autoExpand;
    }

    @Override
    public void gtlcore$toggleAutoExpand() {
        if (isClientSide()) {
            sendClientAction(IAutoExpandMenu.ACTION_TOGGLE_AUTO_EXPAND);
            return;
        }

        IAutoExpandSettings settings = (IAutoExpandSettings) this.logic;
        settings.setPatternAutoExpand(!settings.isPatternAutoExpand());
        this.logic.saveChanges();
        this.gtlcore$autoExpand = settings.isPatternAutoExpand();
    }
}
