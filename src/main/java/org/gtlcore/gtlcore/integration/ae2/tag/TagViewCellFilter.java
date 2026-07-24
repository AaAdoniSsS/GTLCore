package org.gtlcore.gtlcore.integration.ae2.tag;

import net.minecraft.world.item.ItemStack;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.AEKeyFilter;
import appeng.api.storage.cells.ICellWorkbenchItem;
import appeng.core.definitions.AEItems;
import appeng.items.storage.ViewCellItem;
import appeng.util.ConfigInventory;
import appeng.util.prioritylist.FuzzyPriorityList;
import appeng.util.prioritylist.IPartitionList;
import appeng.util.prioritylist.MergedPriorityList;
import appeng.util.prioritylist.PrecisePriorityList;
import com.glodblock.github.extendedae.common.me.taglist.TagPriorityList;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public final class TagViewCellFilter {

    private TagViewCellFilter() {}

    public static boolean containsTagViewCell(Collection<ItemStack> viewCells) {
        return viewCells.stream().anyMatch(TagViewCellItem::isTagViewCell);
    }

    public static @Nullable IPartitionList create(AEKeyFilter keyFilter, Collection<ItemStack> viewCells) {
        MergedPriorityList merged = new MergedPriorityList();
        for (ItemStack viewCell : viewCells) {
            if (TagViewCellItem.isTagViewCell(viewCell)) {
                addTagExpressions(merged, keyFilter, viewCell);
            } else if (viewCell != null && viewCell.getItem() instanceof ViewCellItem cellItem) {
                addStandardViewCell(merged, keyFilter, cellItem, viewCell);
            }
        }
        return merged.isEmpty() ? null : merged;
    }

    private static void addTagExpressions(MergedPriorityList merged, AEKeyFilter keyFilter, ItemStack viewCell) {
        addTagExpression(merged, keyFilter, TagViewCellItem.getWhitelist(viewCell), true);
        addTagExpression(merged, keyFilter, TagViewCellItem.getBlacklist(viewCell), false);
    }

    private static void addTagExpression(MergedPriorityList merged, AEKeyFilter keyFilter, String expression,
                                         boolean whitelist) {
        if (!expression.isBlank()) {
            merged.addNewList(
                    new KeyFilteredPartitionList(keyFilter, new TagPriorityList(expression, "")),
                    whitelist);
        }
    }

    private static void addStandardViewCell(MergedPriorityList merged, AEKeyFilter keyFilter,
                                            ICellWorkbenchItem cellItem, ItemStack viewCell) {
        KeyCounter configuredKeys = new KeyCounter();
        ConfigInventory config = cellItem.getConfigInventory(viewCell);
        for (int slot = 0; slot < config.size(); slot++) {
            AEKey key = config.getKey(slot);
            if (key != null && keyFilter.matches(key)) {
                configuredKeys.add(key, 1);
            }
        }
        if (configuredKeys.isEmpty()) {
            return;
        }

        var upgrades = cellItem.getUpgrades(viewCell);
        boolean whitelist = !upgrades.isInstalled(AEItems.INVERTER_CARD);
        IPartitionList partition = upgrades.isInstalled(AEItems.FUZZY_CARD) ?
                new FuzzyPriorityList(configuredKeys, cellItem.getFuzzyMode(viewCell)) :
                new PrecisePriorityList(configuredKeys);
        merged.addNewList(partition, whitelist);
    }

    private record KeyFilteredPartitionList(AEKeyFilter keyFilter, IPartitionList delegate)
            implements IPartitionList {

        @Override
        public boolean isListed(AEKey key) {
            return keyFilter.matches(key) && delegate.isListed(key);
        }

        @Override
        public boolean isEmpty() {
            return delegate.isEmpty();
        }

        @Override
        public Iterable<AEKey> getItems() {
            return delegate.getItems();
        }
    }
}
