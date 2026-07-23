package org.gtlcore.gtlcore.client.ae2.wireless;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Locale;

public final class UniversalSearch {

    private static final String JECHARACTERS_MATCH_CLASS = "me.towdium.jecharacters.utils.Match";
    private static final MethodType JECHARACTERS_CONTAINS_TYPE = MethodType.methodType(
            boolean.class,
            CharSequence.class,
            CharSequence.class,
            boolean.class);
    private static final MethodHandle JECHARACTERS_CONTAINS = findJecharactersContains();

    private UniversalSearch() {}

    public static boolean contains(String value, String query) {
        if (query.isEmpty()) {
            return true;
        }
        if (JECHARACTERS_CONTAINS != null) {
            try {
                return (boolean) JECHARACTERS_CONTAINS.invokeExact(
                        (CharSequence) value,
                        (CharSequence) query,
                        true);
            } catch (Throwable ignored) {
                // Fall back to normal matching if the optional integration is incompatible.
            }
        }
        return value.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
    }

    private static MethodHandle findJecharactersContains() {
        try {
            Class<?> matchClass = Class.forName(JECHARACTERS_MATCH_CLASS);
            return MethodHandles.publicLookup().findStatic(
                    matchClass,
                    "contains",
                    JECHARACTERS_CONTAINS_TYPE);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }
}
