package org.gtlcore.gtlcore.api.recipe;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

public final class RecipeExtensionCopier {

    private static final ClassValue<List<Property>> PROPERTIES = new ClassValue<>() {

        @Override
        protected List<Property> computeValue(Class<?> type) {
            List<Property> properties = new ArrayList<>();
            for (Class<?> extensionInterface : type.getInterfaces()) {
                for (Method getter : extensionInterface.getMethods()) {
                    String propertyName = getPropertyName(getter);
                    if (propertyName == null) continue;

                    try {
                        Method setter = extensionInterface.getMethod("set" + propertyName,
                                getter.getReturnType());
                        if (isSetter(setter)) {
                            properties.add(new Property(getter, setter));
                        }
                    } catch (NoSuchMethodException ignored) {
                        // Read-only interface properties do not carry extension state into recipe copies.
                    }
                }
            }
            return List.copyOf(properties);
        }
    };

    private RecipeExtensionCopier() {}

    public static GTRecipe copy(GTRecipe source, GTRecipe target) {
        for (Property property : PROPERTIES.get(source.getClass())) {
            property.copy(source, target);
        }
        return target;
    }

    private static boolean isSetter(Method method) {
        return Modifier.isPublic(method.getModifiers()) && method.getName().startsWith("set") &&
                method.getName().length() > 3 && method.getParameterCount() == 1 &&
                method.getReturnType() == void.class;
    }

    private static String getPropertyName(Method method) {
        if (!Modifier.isPublic(method.getModifiers()) || method.getParameterCount() != 0 ||
                method.getReturnType() == void.class) {
            return null;
        }
        if (method.getName().startsWith("get") && method.getName().length() > 3) {
            return method.getName().substring(3);
        }
        if (method.getName().startsWith("is") && method.getName().length() > 2 &&
                (method.getReturnType() == boolean.class || method.getReturnType() == Boolean.class)) {
            return method.getName().substring(2);
        }
        return null;
    }

    private record Property(Method getter, Method setter) {

        private void copy(GTRecipe source, GTRecipe target) {
            try {
                setter.invoke(target, getter.invoke(source));
            } catch (IllegalAccessException | InvocationTargetException exception) {
                throw new IllegalStateException("Failed to copy extended recipe property " + getter.getName(),
                        exception);
            }
        }
    }
}
