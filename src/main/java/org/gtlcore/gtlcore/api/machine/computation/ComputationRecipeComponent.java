package org.gtlcore.gtlcore.api.machine.computation;

import com.google.gson.JsonElement;
import dev.latvian.mods.kubejs.recipe.RecipeJS;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponent;

/** KubeJS has its own content schema in addition to GTM's serializer. Both must preserve the long payload. */
public final class ComputationRecipeComponent implements RecipeComponent<Number> {

    public static final ComputationRecipeComponent INSTANCE = new ComputationRecipeComponent();

    private ComputationRecipeComponent() {}

    @Override
    public Class<?> componentClass() {
        return Number.class;
    }

    @Override
    public JsonElement write(RecipeJS recipe, Number value) {
        return ComputationContentSerializer.INSTANCE.toJson(value);
    }

    @Override
    public Number read(RecipeJS recipe, Object value) {
        return value instanceof JsonElement json ? ComputationContentSerializer.INSTANCE.fromJson(json) :
                ComputationContentSerializer.INSTANCE.of(value);
    }
}
