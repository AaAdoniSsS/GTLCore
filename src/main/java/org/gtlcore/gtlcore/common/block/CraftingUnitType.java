package org.gtlcore.gtlcore.common.block;

import org.gtlcore.gtlcore.common.data.GTLBlocks;
import org.gtlcore.gtlcore.common.data.GTLItems;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;

import appeng.block.crafting.CraftingUnitBlock;
import appeng.block.crafting.ICraftingUnitType;
import com.tterrag.registrate.util.entry.BlockEntry;
import lombok.Getter;

public enum CraftingUnitType implements ICraftingUnitType {

    STORAGE_1M(1, "1m_storage"),
    STORAGE_4M(4, "4m_storage"),
    STORAGE_16M(16, "16m_storage"),
    STORAGE_64M(64, "64m_storage"),
    STORAGE_256M(256, "256m_storage"),
    STORAGE_MAX(-1, "max_storage");

    private final int storageMb;

    @Getter
    private final String affix;

    CraftingUnitType(int storageMb, String affix) {
        this.storageMb = storageMb;
        this.affix = affix;
    }

    @Override
    public long getStorageBytes() {
        return storageMb == -1 ? Long.MAX_VALUE : 1024L * 1024 * storageMb;
    }

    @Override
    public int getAcceleratorThreads() {
        return 0;
    }

    public BlockEntry<CraftingUnitBlock> getDefinition() {
        return switch (this) {
            case STORAGE_1M -> GTLBlocks.CRAFTING_STORAGE_1M;
            case STORAGE_4M -> GTLBlocks.CRAFTING_STORAGE_4M;
            case STORAGE_16M -> GTLBlocks.CRAFTING_STORAGE_16M;
            case STORAGE_64M -> GTLBlocks.CRAFTING_STORAGE_64M;
            case STORAGE_256M -> GTLBlocks.CRAFTING_STORAGE_256M;
            case STORAGE_MAX -> GTLBlocks.CRAFTING_STORAGE_MAX;
        };
    }

    public ItemLike getDisassemblyExtra() {
        return switch (this) {
            case STORAGE_1M -> GTLItems.CELL_COMPONENT_1M.get();
            case STORAGE_4M -> GTLItems.CELL_COMPONENT_4M.get();
            case STORAGE_16M -> GTLItems.CELL_COMPONENT_16M.get();
            case STORAGE_64M -> GTLItems.CELL_COMPONENT_64M.get();
            case STORAGE_256M -> GTLItems.CELL_COMPONENT_256M.get();
            case STORAGE_MAX -> throw new IllegalStateException("MAX crafting storage has no disassembly component");
        };
    }

    @Override
    public Item getItemFromType() {
        return getDefinition().asItem();
    }
}
