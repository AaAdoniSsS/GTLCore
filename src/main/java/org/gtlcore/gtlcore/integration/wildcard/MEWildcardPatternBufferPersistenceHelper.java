package org.gtlcore.gtlcore.integration.wildcard;

import org.gtlcore.gtlcore.api.item.tool.ae2.patternTool.RecipeStackHelper;
import org.gtlcore.gtlcore.integration.ae2.AEUtils;
import org.gtlcore.gtlcore.utils.Registries;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;

import com.lowdragmc.lowdraglib.side.fluid.FluidStack;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;

final class MEWildcardPatternBufferPersistenceHelper {

    private static final String DYNAMIC_SLOTS_KEY = "wildcardDynamicSlots";
    private static final String RECIPE_ID_KEY = "recipeId";
    private static final String PATTERN_OUTPUTS_KEY = "patternOutputs";
    private static final String SLOT_DATA_KEY = "slot";

    private final Object2ObjectMap<Object2LongMap<AEKey>, PendingSlotData> pendingSlotsByPatternOutput = new Object2ObjectOpenHashMap<>();
    private final Object2IntMap<Object2LongMap<AEKey>> outputSlots = new Object2IntOpenHashMap<>();
    private final Int2ObjectMap<PatternSignature> slotSignatures = new Int2ObjectOpenHashMap<>();

    MEWildcardPatternBufferPersistenceHelper() {
        outputSlots.defaultReturnValue(-1);
    }

    void clearPatternIndex() {
        outputSlots.clear();
        slotSignatures.clear();
    }

    void clearPendingRestoreData() {
        pendingSlotsByPatternOutput.clear();
    }

    void indexPattern(IPatternDetails pattern, int slot) {
        PatternSignature signature = PatternSignature.of(pattern);
        outputSlots.putIfAbsent(signature.outputs(), slot);
        slotSignatures.put(slot, signature);
    }

    void save(CompoundTag tag, int slotCount, IntPredicate isSlotActive, IntFunction<CompoundTag> serializeSlot,
              IntPredicate hasRecipeCache, Int2ReferenceMap<GTRecipe> recipeCacheMap) {
        saveDynamicSlots(tag, slotCount, isSlotActive, serializeSlot, hasRecipeCache, recipeCacheMap);
    }

    void load(CompoundTag tag) {
        loadPendingSlotData(tag);
    }

    void restore(Int2ReferenceMap<GTRecipe> recipeCacheMap,
                 BiConsumer<Integer, CompoundTag> deserializeSlot,
                 Consumer<List<CompoundTag>> refundSlots) {
        restorePersistedSlots(recipeCacheMap, deserializeSlot, refundSlots);
    }

    private void saveDynamicSlots(CompoundTag tag, int slotCount, IntPredicate isSlotActive,
                                  IntFunction<CompoundTag> serializeSlot,
                                  IntPredicate hasRecipeCache,
                                  Int2ReferenceMap<GTRecipe> recipeCacheMap) {
        ListTag slotEntries = new ListTag();
        for (int slot = 0; slot < slotCount; slot++) {
            boolean saveSlotData = isSlotActive.test(slot);
            boolean saveRecipeCache = hasRecipeCache.test(slot);
            if (!saveSlotData && !saveRecipeCache) continue;

            PatternSignature signature = slotSignatures.get(slot);
            if (signature == null) continue;

            CompoundTag slotData = saveSlotData ? serializeSlot.apply(slot) : null;
            boolean hasSlotData = slotData != null && !slotData.isEmpty();

            GTRecipe cachedRecipe = saveRecipeCache ? recipeCacheMap.get(slot) : null;
            boolean hasRecipeId = cachedRecipe != null && cachedRecipe.id != null;
            if (!hasSlotData && !hasRecipeId) continue;

            CompoundTag slotEntry = new CompoundTag();
            slotEntry.put(PATTERN_OUTPUTS_KEY, AEUtils.createListTag(AEKey::toTagGeneric, signature.outputs()));
            if (hasSlotData) slotEntry.put(SLOT_DATA_KEY, slotData);
            if (hasRecipeId) slotEntry.putString(RECIPE_ID_KEY, cachedRecipe.id.toString());
            slotEntries.add(slotEntry);
        }
        if (!slotEntries.isEmpty()) tag.put(DYNAMIC_SLOTS_KEY, slotEntries);
    }

    private void loadPendingSlotData(CompoundTag tag) {
        pendingSlotsByPatternOutput.clear();

        ListTag slotEntries = tag.getList(DYNAMIC_SLOTS_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < slotEntries.size(); i++) {
            CompoundTag slotEntry = slotEntries.getCompound(i);
            Object2LongMap<AEKey> patternOutputs = readStackMap(slotEntry);
            if (patternOutputs.isEmpty()) continue;

            CompoundTag slotData = slotEntry.contains(SLOT_DATA_KEY, Tag.TAG_COMPOUND) ?
                    slotEntry.getCompound(SLOT_DATA_KEY) : null;
            ResourceLocation recipeId = readRecipeId(slotEntry);
            if (slotData == null && recipeId == null) continue;

            pendingSlotsByPatternOutput.put(patternOutputs, new PendingSlotData(slotData, recipeId));
        }
    }

    private void restorePersistedSlots(Int2ReferenceMap<GTRecipe> recipeCacheMap,
                                       BiConsumer<Integer, CompoundTag> deserializeSlot,
                                       Consumer<List<CompoundTag>> refundSlots) {
        if (pendingSlotsByPatternOutput.isEmpty()) return;

        var recipeManager = Registries.getRecipeManager();
        List<CompoundTag> discardedSlotData = null;
        for (var pendingEntry : pendingSlotsByPatternOutput.object2ObjectEntrySet()) {
            int slot = outputSlots.getInt(pendingEntry.getKey());
            PendingSlotData pendingSlotData = pendingEntry.getValue();
            if (slot < 0) {
                if (pendingSlotData.slotData() != null) {
                    if (discardedSlotData == null) {
                        discardedSlotData = new ArrayList<>();
                    }
                    discardedSlotData.add(pendingSlotData.slotData());
                }
                continue;
            }

            if (pendingSlotData.slotData() != null) {
                deserializeSlot.accept(slot, pendingSlotData.slotData());
            }

            ResourceLocation recipeId = pendingSlotData.recipeId();
            if (recipeId == null) continue;

            PatternSignature signature = slotSignatures.get(slot);
            if (signature == null) continue;

            if (recipeManager.byKey(recipeId).orElse(null) instanceof GTRecipe recipe && signature.matches(recipe)) {
                recipeCacheMap.put(slot, recipe);
            }
        }
        if (discardedSlotData != null) {
            refundSlots.accept(discardedSlotData);
        }
        pendingSlotsByPatternOutput.clear();
    }

    private static Object2LongMap<AEKey> readStackMap(CompoundTag tag) {
        Object2LongOpenHashMap<AEKey> map = new Object2LongOpenHashMap<>();
        AEUtils.loadInventory(tag.getList(PATTERN_OUTPUTS_KEY, Tag.TAG_COMPOUND), AEKey::fromTagGeneric, map);
        return map;
    }

    private static Object2LongMap<AEKey> toPatternInputMap(IPatternDetails pattern) {
        Object2LongOpenHashMap<AEKey> inputs = new Object2LongOpenHashMap<>();
        for (IPatternDetails.IInput input : pattern.getInputs()) {
            GenericStack[] possibleInputs = input.getPossibleInputs();
            if (possibleInputs.length == 0) continue;

            GenericStack stack = possibleInputs[0];
            inputs.addTo(stack.what(), stack.amount() * input.getMultiplier());
        }
        return inputs;
    }

    private static Object2LongMap<AEKey> toPatternOutputMap(IPatternDetails pattern) {
        Object2LongOpenHashMap<AEKey> map = new Object2LongOpenHashMap<>();
        for (GenericStack stack : pattern.getOutputs()) {
            map.addTo(stack.what(), stack.amount());
        }
        return map;
    }

    private static Object2LongMap<AEKey> toRecipeInputMap(GTRecipe recipe) {
        Object2LongOpenHashMap<AEKey> inputs = new Object2LongOpenHashMap<>();
        for (var content : recipe.getInputContents(ItemRecipeCapability.CAP)) {
            if (content.chance <= 0) continue;
            ItemStack stack = ItemRecipeCapability.CAP.of(content.getContent()).kjs$getFirst();
            if (!stack.isEmpty()) inputs.addTo(AEItemKey.of(stack), stack.getCount());
        }
        for (var content : recipe.getInputContents(FluidRecipeCapability.CAP)) {
            if (content.chance <= 0) continue;
            FluidStack[] stacks = FluidRecipeCapability.CAP.of(content.getContent()).getStacks();
            FluidStack stack = stacks != null && stacks.length > 0 ? stacks[0] : FluidStack.empty();
            if (!stack.isEmpty()) inputs.addTo(AEFluidKey.of(stack.getFluid()), stack.getAmount());
        }
        return inputs;
    }

    private static Object2LongMap<AEKey> toRecipeOutputMap(GTRecipe recipe) {
        Object2LongOpenHashMap<AEKey> outputs = new Object2LongOpenHashMap<>();
        for (ItemStack stack : RecipeStackHelper.getOutputItemStacksFromRecipe(recipe)) {
            if (!stack.isEmpty()) outputs.addTo(AEItemKey.of(stack), stack.getCount());
        }
        for (FluidStack stack : RecipeStackHelper.getOutputFluidStacksFromRecipe(recipe)) {
            if (!stack.isEmpty()) outputs.addTo(AEFluidKey.of(stack.getFluid()), stack.getAmount());
        }
        return outputs;
    }

    @Nullable
    private static ResourceLocation readRecipeId(CompoundTag tag) {
        return tag.contains(RECIPE_ID_KEY, Tag.TAG_STRING) ? readRecipeId(tag.getString(RECIPE_ID_KEY)) : null;
    }

    @Nullable
    private static ResourceLocation readRecipeId(String value) {
        try {
            return new ResourceLocation(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private record PatternSignature(Object2LongMap<AEKey> inputs, Object2LongMap<AEKey> outputs) {

        private static PatternSignature of(IPatternDetails pattern) {
            return new PatternSignature(toPatternInputMap(pattern), toPatternOutputMap(pattern));
        }

        private boolean matches(GTRecipe recipe) {
            return inputs.equals(toRecipeInputMap(recipe)) && outputs.equals(toRecipeOutputMap(recipe));
        }
    }

    private record PendingSlotData(@Nullable CompoundTag slotData, @Nullable ResourceLocation recipeId) {}
}
