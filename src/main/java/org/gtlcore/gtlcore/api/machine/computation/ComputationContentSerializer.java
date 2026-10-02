package org.gtlcore.gtlcore.api.machine.computation;

import com.gregtechceu.gtceu.api.recipe.content.IContentSerializer;

import net.minecraft.network.FriendlyByteBuf;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;

/** Accepts old integer JSON/NBT and writes exact, nonnegative 64-bit CWU. */
public final class ComputationContentSerializer implements IContentSerializer<Number> {

    public static final ComputationContentSerializer INSTANCE = new ComputationContentSerializer();
    private static final Codec<Number> CODEC = Codec.PASSTHROUGH.flatXmap(value -> {
        try {
            return DataResult.success(INSTANCE.fromJson(value.convert(JsonOps.INSTANCE).getValue()));
        } catch (IllegalArgumentException | ArithmeticException e) {
            return DataResult.error(() -> "Invalid CWU: " + e.getMessage());
        }
    }, value -> DataResult.success(new Dynamic<>(JsonOps.INSTANCE, INSTANCE.toJson(value))));

    private ComputationContentSerializer() {}

    @Override
    public void toNetwork(FriendlyByteBuf buf, Number content) {
        buf.writeVarLong(ComputationAmounts.read(content));
    }

    @Override
    public Number fromNetwork(FriendlyByteBuf buf) {
        return ComputationAmounts.box(ComputationAmounts.read(buf.readVarLong()));
    }

    @Override
    public Number fromJson(JsonElement json) {
        return of(json.getAsString());
    }

    @Override
    public JsonElement toJson(Number content) {
        return new JsonPrimitive(ComputationAmounts.read(content));
    }

    @Override
    public Number of(Object value) {
        return ComputationAmounts.box(ComputationAmounts.read(value));
    }

    @Override
    public Number defaultValue() {
        return Integer.valueOf(0);
    }

    @Override
    public Codec<Number> codec() {
        return CODEC;
    }
}
