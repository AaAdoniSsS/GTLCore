package org.gtlcore.gtlcore.mixin.ae2.gui;

import org.gtlcore.gtlcore.integration.ae2.common.IConfirmStartMenu;
import org.gtlcore.gtlcore.integration.ae2.common.ILongCraftConfirmMenu;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

import appeng.api.config.SortDir;
import appeng.api.config.SortOrder;
import appeng.api.config.TypeFilter;
import appeng.api.config.ViewItems;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.ISubMenuHost;
import appeng.client.gui.me.common.Repo;
import appeng.client.gui.widgets.ISortSource;
import appeng.core.sync.packets.MEInventoryUpdatePacket;
import appeng.menu.AEBaseMenu;
import appeng.menu.me.common.IClientRepo;
import appeng.menu.me.common.IncrementalUpdateHelper;
import appeng.menu.me.crafting.CraftConfirmMenu;
import com.google.common.primitives.Ints;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;
import java.util.concurrent.Future;

@Mixin(CraftConfirmMenu.class)
public abstract class CraftConfirmMenuMixin extends AEBaseMenu implements IConfirmStartMenu, ILongCraftConfirmMenu {

    protected CraftConfirmMenuMixin(MenuType<?> menuType, int id, Inventory playerInventory, Object host) {
        super(menuType, id, playerInventory, host);
    }

    @Shadow(remap = false)
    protected abstract IGrid getGrid();

    @Shadow(remap = false)
    private Future<ICraftingPlan> job;

    @Shadow(remap = false)
    private ICraftingPlan result;

    @Shadow(remap = false)
    private AEKey whatToCraft;

    @Shadow(remap = false)
    private int amount;

    @Shadow(remap = false)
    public abstract void clearError();

    @Unique
    private IClientRepo gtlcore$repo;

    @Unique
    private boolean gtlcore$sent = false;

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void onConstructed(int id, Inventory ip, ISubMenuHost host, CallbackInfo ci) {
        this.gtlcore$repo = new Repo(() -> 0, new ISortSource() {

            @Override
            public SortOrder getSortBy() {
                return SortOrder.AMOUNT;
            }

            @Override
            public SortDir getSortDir() {
                return SortDir.ASCENDING;
            }

            @Override
            public ViewItems getSortDisplay() {
                return ViewItems.ALL;
            }

            @Override
            public TypeFilter getTypeFilter() {
                return TypeFilter.ALL;
            }
        });
    }

    @Override
    public IClientRepo gtlcore$getClientRepo() {
        return gtlcore$repo;
    }

    @Override
    public boolean gtlcore$planLongAmountJob(AEKey whatToCraft, long amount, CalculationStrategy strategy) {
        if (this.job != null) {
            this.job.cancel(true);
        }
        this.result = null;
        this.clearError();
        this.whatToCraft = whatToCraft;
        this.amount = Ints.saturatedCast(amount);

        var grid = this.getGrid();
        if (grid == null) {
            return false;
        }

        var player = this.getPlayer();
        this.job = grid.getCraftingService().beginCraftingCalculation(
                player.level(), this::getActionSource, whatToCraft, amount, strategy);
        return true;
    }

    @Inject(method = "broadcastChanges", at = @At("RETURN"))
    private void onBroadcastChanges(CallbackInfo ci) {
        if (gtlcore$sent) return;
        var builder = MEInventoryUpdatePacket.builder(containerId, true);
        builder.addFull(new IncrementalUpdateHelper(), getGrid().getStorageService().getInventory().getAvailableStacks(), Set.of(), new KeyCounter());
        builder.buildAndSend(this::sendPacketToClient);
        gtlcore$sent = true;
    }
}
