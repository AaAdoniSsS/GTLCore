package org.gtlcore.gtlcore.mixin.ae2.gui;

import org.gtlcore.gtlcore.integration.ae2.common.CraftAmountReturnState;
import org.gtlcore.gtlcore.integration.ae2.common.IConfirmStartMenu;
import org.gtlcore.gtlcore.integration.ae2.common.ILongCraftAmountMenu;
import org.gtlcore.gtlcore.integration.ae2.common.ILongCraftConfirmMenu;

import net.minecraft.server.level.ServerPlayer;
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
import appeng.helpers.IMenuCraftingPacket;
import appeng.menu.AEBaseMenu;
import appeng.menu.ISubMenu;
import appeng.menu.MenuOpener;
import appeng.menu.me.common.IClientRepo;
import appeng.menu.me.common.IncrementalUpdateHelper;
import appeng.menu.me.crafting.CraftAmountMenu;
import appeng.menu.me.crafting.CraftConfirmMenu;
import appeng.menu.me.crafting.CraftingPlanSummary;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
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
    private CraftingPlanSummary plan;

    @Shadow(remap = false)
    @Final
    private ISubMenuHost host;

    @Shadow(remap = false)
    private List<IMenuCraftingPacket.AutoCraftEntry> autoCraftingQueue;

    @Shadow(remap = false)
    public abstract void clearError();

    @Unique
    private IClientRepo gtlcore$repo;

    @Unique
    private boolean gtlcore$sent = false;

    @Unique
    private long gtlcore$longAmount = CraftAmountReturnState.NO_LONG_AMOUNT;

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
        this.amount = CraftAmountReturnState.legacyAmount(amount);
        this.gtlcore$longAmount = CraftAmountReturnState.rememberedAmount(amount);

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
        if (gtlcore$sent || this.plan == null) return;
        KeyCounter relevantStored = gtlcore$getRelevantStoredAmounts();
        var builder = MEInventoryUpdatePacket.builder(containerId, true);
        builder.addFull(new IncrementalUpdateHelper(), relevantStored, Set.of(), new KeyCounter());
        builder.buildAndSend(this::sendPacketToClient);
        gtlcore$sent = true;
    }

    @Unique
    private KeyCounter gtlcore$getRelevantStoredAmounts() {
        KeyCounter relevantStored = new KeyCounter();
        var grid = getGrid();
        if (grid == null) {
            return relevantStored;
        }

        var cachedInventory = grid.getStorageService().getCachedInventory();
        for (var entry : this.plan.getEntries()) {
            long stored = cachedInventory.get(entry.getWhat());
            if (stored > 0) {
                relevantStored.add(entry.getWhat(), stored);
            }
        }
        return relevantStored;
    }

    @Inject(method = "goBack", at = @At("HEAD"), cancellable = true, remap = false)
    private void goBackWithLongAmount(CallbackInfo ci) {
        if (this.isClientSide()) return;

        ci.cancel();
        this.clearError();
        this.gtlcore$returnToPreviousMenu();
    }

    @Inject(method = "replan", at = @At("HEAD"), cancellable = true, remap = false)
    private void replanWithLongAmount(CallbackInfo ci) {
        if (this.isClientSide()) return;

        ci.cancel();
        this.clearError();
        if (this.whatToCraft == null || !this.gtlcore$planLongAmountJob(
                this.whatToCraft,
                CraftAmountReturnState.displayAmount(this.gtlcore$longAmount, this.amount),
                CalculationStrategy.CRAFT_LESS)) {
            this.gtlcore$returnToPreviousMenu();
        }
    }

    @Unique
    private void gtlcore$returnToPreviousMenu() {
        var player = this.getPlayer();
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        if (this.autoCraftingQueue != null && !this.autoCraftingQueue.isEmpty()) {
            CraftConfirmMenu.openWithCraftingList(
                    this.getActionHost(), serverPlayer, this.getLocator(), this.autoCraftingQueue);
        } else if (this.whatToCraft != null) {
            this.gtlcore$openLongAmountMenu(
                    serverPlayer, CraftAmountReturnState.displayAmount(this.gtlcore$longAmount, this.amount));
        } else {
            this.host.returnToMainMenu(player, (ISubMenu) (Object) this);
        }
    }

    @Unique
    private void gtlcore$openLongAmountMenu(ServerPlayer player, long amount) {
        MenuOpener.open(CraftAmountMenu.TYPE, player, this.getLocator());
        if (player.containerMenu instanceof CraftAmountMenu amountMenu) {
            ((ILongCraftAmountMenu) amountMenu).gtlcore$setLongWhatToCraft(this.whatToCraft, amount);
            amountMenu.broadcastChanges();
        }
    }
}
