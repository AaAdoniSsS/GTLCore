package org.gtlcore.gtlcore.integration.ae2.handler;

import org.gtlcore.gtlcore.api.recipe.ingredient.CacheHashStrategies;

import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.common.item.IntCircuitBehaviour;

import com.lowdragmc.lowdraglib.syncdata.ITagSerializable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import com.google.gson.JsonParser;
import it.unimi.dsi.fastutil.objects.*;
import lombok.Getter;

/**
 * 负责管理单个槽位的所有缓存数据，包括：
 * - BestMatch缓存：ingredient到配方实际使用AEKey的映射
 * - 缓存失效和清理
 */
public class SlotCacheManager implements ITagSerializable<CompoundTag> {

    private final Object2ObjectMap<Ingredient, AEItemKey> itemBestMatchCache = new Object2ObjectOpenCustomHashMap<>(CacheHashStrategies.IngredientHashStrategy.INSTANCE);
    private final Object2ObjectMap<FluidIngredient, AEFluidKey> fluidBestMatchCache = new Object2ObjectOpenCustomHashMap<>(CacheHashStrategies.FluidIngredientHashStrategy.INSTANCE);

    public void setCircuitCache(int circuitCache) {
        this.circuitCache = circuitCache;
        this.circuitStack = IntCircuitBehaviour.stack(circuitCache);
    }

    @Getter
    private int circuitCache = -1;
    @Getter
    private ItemStack circuitStack = ItemStack.EMPTY;

    /**
     * 获取或计算item的最佳匹配
     *
     * @param ingredient 原料
     * @param inventory  物品库存映射
     * @param needAmount 需要的数量
     * @return 最佳匹配的AEItemKey，如果没有足够库存则返回null
     */
    public AEItemKey getBestItemMatch(Ingredient ingredient, Object2LongMap<AEItemKey> inventory, long needAmount) {
        AEItemKey cached = itemBestMatchCache.get(ingredient);

        if (cached != null && inventory.getLong(cached) >= needAmount) {
            return cached;
        }

        AEItemKey bestMatch = findBestItemMatch(ingredient, inventory, needAmount);
        if (bestMatch != null) {
            itemBestMatchCache.put(ingredient, bestMatch);
        }
        return bestMatch;
    }

    /**
     * 获取或计算fluid的最佳匹配
     *
     * @param ingredient 流体原料
     * @param inventory  流体库存映射
     * @param needAmount 需要的数量
     * @return 最佳匹配的AEFluidKey，如果没有足够库存则返回null
     */
    public AEFluidKey getBestFluidMatch(FluidIngredient ingredient, Object2LongMap<AEFluidKey> inventory, long needAmount) {
        AEFluidKey cached = fluidBestMatchCache.get(ingredient);

        if (cached != null && inventory.getLong(cached) >= needAmount) {
            return cached;
        }

        AEFluidKey bestMatch = findBestFluidMatch(ingredient, inventory, needAmount);
        if (bestMatch != null) {
            fluidBestMatchCache.put(ingredient, bestMatch);
        }
        return bestMatch;
    }

    private static AEItemKey findBestItemMatch(Ingredient ingredient, Object2LongMap<AEItemKey> inventory, long needAmount) {
        var items = ingredient.getItems();
        for (var item : items) {
            if (!item.isEmpty()) {
                AEItemKey aeKey = AEItemKey.of(item);
                if (inventory.getLong(aeKey) >= needAmount) {
                    return aeKey;
                }
            }
        }
        return null;
    }

    private static AEFluidKey findBestFluidMatch(FluidIngredient ingredient, Object2LongMap<AEFluidKey> inventory, long needAmount) {
        var stacks = ingredient.getStacks();
        for (var stack : stacks) {
            if (!stack.isEmpty()) {
                AEFluidKey aeKey = AEFluidKey.of(stack.getFluid());
                if (inventory.getLong(aeKey) >= needAmount) {
                    return aeKey;
                }
            }
        }
        return null;
    }

    // ========================================
    // Simulate with Catalyst
    // ========================================
    public AEItemKey getBestItemMatchSimulate(Ingredient ingredient, Object2LongMap<AEItemKey> inventory, Object2LongMap<AEItemKey> catalystInventory, long needAmount) {
        AEItemKey cached = itemBestMatchCache.get(ingredient);

        if (cached != null && (inventory.getLong(cached) >= needAmount || catalystInventory.getLong(cached) >= needAmount)) {
            return cached;
        }

        AEItemKey bestMatch = findBestItemMatchSimulate(ingredient, inventory, catalystInventory, needAmount);
        if (bestMatch != null) {
            itemBestMatchCache.put(ingredient, bestMatch);
        }
        return bestMatch;
    }

    public AEFluidKey getBestFluidMatchSimulate(FluidIngredient ingredient, Object2LongMap<AEFluidKey> inventory, Object2LongMap<AEFluidKey> catalystInventory, long needAmount) {
        AEFluidKey cached = fluidBestMatchCache.get(ingredient);

        if (cached != null && (inventory.getLong(cached) >= needAmount || catalystInventory.getLong(cached) >= needAmount)) {
            return cached;
        }

        AEFluidKey bestMatch = findBestFluidMatchSimulate(ingredient, inventory, catalystInventory, needAmount);
        if (bestMatch != null) {
            fluidBestMatchCache.put(ingredient, bestMatch);
        }
        return bestMatch;
    }

    private static AEItemKey findBestItemMatchSimulate(Ingredient ingredient, Object2LongMap<AEItemKey> inventory, Object2LongMap<AEItemKey> catalystInventory, long needAmount) {
        var items = ingredient.getItems();
        for (var item : items) {
            if (!item.isEmpty()) {
                AEItemKey aeKey = AEItemKey.of(item);
                if (inventory.getLong(aeKey) >= needAmount || catalystInventory.getLong(aeKey) >= needAmount) {
                    return aeKey;
                }
            }
        }
        return null;
    }

    private static AEFluidKey findBestFluidMatchSimulate(FluidIngredient ingredient, Object2LongMap<AEFluidKey> inventory, Object2LongMap<AEFluidKey> catalystInventory, long needAmount) {
        var stacks = ingredient.getStacks();
        for (var stack : stacks) {
            if (!stack.isEmpty()) {
                AEFluidKey aeKey = AEFluidKey.of(stack.getFluid());
                if (inventory.getLong(aeKey) >= needAmount || catalystInventory.getLong(aeKey) >= needAmount) {
                    return aeKey;
                }
            }
        }
        return null;
    }

    public void clearCircuitCache() {
        circuitCache = -1;
        circuitStack = ItemStack.EMPTY;
    }

    public void clearAllCaches() {
        itemBestMatchCache.clear();
        fluidBestMatchCache.clear();
        clearCircuitCache();
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();

        if (!itemBestMatchCache.isEmpty()) {
            ListTag itemCacheList = new ListTag();
            for (var entry : Object2ObjectMaps.fastIterable(itemBestMatchCache)) {
                CompoundTag entryTag = new CompoundTag();
                entryTag.put("ingredient", serializeIngredient(entry.getKey()));
                entryTag.put("aekey", entry.getValue().toTag());
                itemCacheList.add(entryTag);
            }
            tag.put("itemCache", itemCacheList);
        }

        if (!fluidBestMatchCache.isEmpty()) {
            ListTag fluidCacheList = new ListTag();
            for (var entry : Object2ObjectMaps.fastIterable(fluidBestMatchCache)) {
                CompoundTag entryTag = new CompoundTag();
                entryTag.put("ingredient", serializeFluidIngredient(entry.getKey()));
                entryTag.put("aekey", entry.getValue().toTag());
                fluidCacheList.add(entryTag);
            }
            tag.put("fluidCache", fluidCacheList);
        }

        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        clearAllCaches();

        if (tag.contains("itemCache", Tag.TAG_LIST)) {
            ListTag itemCacheList = tag.getList("itemCache", Tag.TAG_COMPOUND);
            for (Tag t : itemCacheList) {
                if (!(t instanceof CompoundTag entryTag)) continue;
                Ingredient ingredient = deserializeIngredient(entryTag.getCompound("ingredient"));
                AEItemKey aeKey = AEItemKey.fromTag(entryTag.getCompound("aekey"));
                if (ingredient != null && aeKey != null) {
                    itemBestMatchCache.put(ingredient, aeKey);
                }
            }
        }

        if (tag.contains("fluidCache", Tag.TAG_LIST)) {
            ListTag fluidCacheList = tag.getList("fluidCache", Tag.TAG_COMPOUND);
            for (Tag t : fluidCacheList) {
                if (!(t instanceof CompoundTag entryTag)) continue;
                FluidIngredient ingredient = deserializeFluidIngredient(entryTag.getCompound("ingredient"));
                AEFluidKey aeKey = AEFluidKey.fromTag(entryTag.getCompound("aekey"));
                if (ingredient != null && aeKey != null) {
                    fluidBestMatchCache.put(ingredient, aeKey);
                }
            }
        }
    }

    private static CompoundTag serializeIngredient(Ingredient ingredient) {
        CompoundTag tag = new CompoundTag();
        var json = ingredient.toJson();
        tag.putString("json", json.toString());
        return tag;
    }

    private static CompoundTag serializeFluidIngredient(FluidIngredient ingredient) {
        CompoundTag tag = new CompoundTag();
        var json = ingredient.toJson();
        tag.putString("json", json.toString());
        return tag;
    }

    private static Ingredient deserializeIngredient(CompoundTag tag) {
        try {
            String jsonStr = tag.getString("json");
            if (jsonStr.isEmpty()) return null;
            var json = JsonParser.parseString(jsonStr);
            return Ingredient.fromJson(json, false);
        } catch (Exception e) {
            return null;
        }
    }

    private static FluidIngredient deserializeFluidIngredient(CompoundTag tag) {
        try {
            String jsonStr = tag.getString("json");
            if (jsonStr.isEmpty()) return null;
            var json = JsonParser.parseString(jsonStr);
            return FluidIngredient.fromJson(json);
        } catch (Exception e) {
            return null;
        }
    }
}
