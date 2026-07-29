package org.gtlcore.gtlcore.mixin.gtm.syncdata;

import org.gtlcore.gtlcore.api.recipe.IGTRecipe;
import org.gtlcore.gtlcore.utils.Registries;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeSerializer;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.syncdata.GTRecipePayload;

import com.lowdragmc.lowdraglib.syncdata.payload.ObjectTypedPayload;

import net.minecraft.nbt.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.SmeltingRecipe;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(GTRecipePayload.class)
public abstract class GTRecipePayloadMixin extends ObjectTypedPayload<GTRecipe> {

    @Nullable
    @Override
    public Tag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", payload.id.toString());
        tag.put("recipe", GTRecipeSerializer.CODEC.encodeStart(NbtOps.INSTANCE, payload).result().orElse(new CompoundTag()));
        tag.putLong("realParallels", IGTRecipe.of(payload).getRealParallels());
        tag.putInt("batchSize", IGTRecipe.of(payload).getBatchSize());
        tag.putInt("ocTier", payload.ocTier);
        return tag;
    }

    @Override
    public void deserializeNBT(Tag tag) {
        if (tag instanceof CompoundTag compoundTag) {
            payload = GTRecipeSerializer.CODEC.parse(NbtOps.INSTANCE, compoundTag.get("recipe")).result().orElse(null);
            if (payload != null) {
                payload.id = new ResourceLocation(compoundTag.getString("id"));
                IGTRecipe.of(payload).setRealParallels(compoundTag.contains("realParallels") ? compoundTag.getLong("realParallels") : 1);
                IGTRecipe.of(payload).setBatchSize(compoundTag.contains("batchSize") ? compoundTag.getInt("batchSize") : 1);
                payload.ocTier = compoundTag.getInt("ocTier");
            }
        } else if (tag instanceof StringTag stringTag) {
            var recipe = Registries.getRecipeManager().byKey(new ResourceLocation(stringTag.getAsString())).orElse(null);
            if (recipe instanceof GTRecipe gtRecipe) {
                payload = gtRecipe;
            } else if (recipe instanceof SmeltingRecipe smeltingRecipe) {
                payload = GTRecipeTypes.FURNACE_RECIPES.toGTrecipe(new ResourceLocation(stringTag.getAsString()),
                        smeltingRecipe);
            } else {
                payload = null;
            }
        } else if (tag instanceof ByteArrayTag byteArray) {
            ByteBuf copiedDataBuffer = Unpooled.copiedBuffer(byteArray.getAsByteArray());
            FriendlyByteBuf buf = new FriendlyByteBuf(copiedDataBuffer);
            payload = (GTRecipe) Registries.getRecipeManager().byKey(buf.readResourceLocation()).orElse(null);
            buf.release();
        }
    }

    @Override
    public void writePayload(FriendlyByteBuf buf) {
        buf.writeResourceLocation(this.payload.id);
        GTRecipeSerializer.SERIALIZER.toNetwork(buf, this.payload);
        buf.writeLong(IGTRecipe.of(this.payload).getRealParallels());
        buf.writeInt(IGTRecipe.of(this.payload).getBatchSize());
        buf.writeInt(this.payload.ocTier);
    }

    @Override
    public void readPayload(FriendlyByteBuf buf) {
        var id = buf.readResourceLocation();
        if (buf.isReadable()) {
            this.payload = GTRecipeSerializer.SERIALIZER.fromNetwork(id, buf);
            if (buf.isReadable()) {
                IGTRecipe.of(this.payload).setRealParallels(buf.readLong());
                if (buf.isReadable()) {
                    IGTRecipe.of(this.payload).setBatchSize(buf.readInt());
                    this.payload.ocTier = buf.readInt();
                }
            }
        } else {
            RecipeManager recipeManager = Registries.getRecipeManager();
            this.payload = (GTRecipe) recipeManager.byKey(id).orElse(null);
        }
    }
}
