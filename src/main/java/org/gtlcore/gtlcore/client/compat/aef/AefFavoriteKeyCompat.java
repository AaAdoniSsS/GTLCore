package org.gtlcore.gtlcore.client.compat.aef;

import org.gtlcore.gtlcore.GTLCore;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.fml.ModList;

import appeng.api.stacks.AEKey;
import com.mojang.blaze3d.platform.InputConstants;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class AefFavoriteKeyCompat {

    private static final String AEF_MOD_ID = "aef";
    private static final String AEF_KEY_BINDINGS_CLASS = "me.cyanhana.aef.ModKeyBindings";
    private static final String AEF_FAVORITE_KEY_FIELD = "FAVORITE_ITEM";
    private static final String AEF_FAVORITES_CLASS = "me.cyanhana.aef.client.gui.FavoritesKeys";

    private static KeyMapping favoriteKey;
    private static Method isFavoriteMethod;
    private static Method addFavoriteMethod;
    private static Method removeFavoriteMethod;
    private static boolean lookupAttempted;
    private static boolean favoritesLookupAttempted;

    private AefFavoriteKeyCompat() {}

    public static void registerKeyMapping(RegisterKeyMappingsEvent event) {
        KeyMapping keyMapping = getFavoriteKey();
        if (keyMapping == null) {
            return;
        }
        keyMapping.setKeyConflictContext(KeyConflictContext.GUI);
        event.register(keyMapping);
    }

    public static boolean hasKeyMapping() {
        return getFavoriteKey() != null;
    }

    public static boolean matchesMouse(int button) {
        KeyMapping keyMapping = getFavoriteKey();
        return keyMapping != null && keyMapping.isActiveAndMatches(InputConstants.Type.MOUSE.getOrCreate(button));
    }

    public static boolean matchesLegacyMouse(int button) {
        return getFavoriteKey() != null && Minecraft.getInstance().options.keyPickItem.matchesMouse(button) && Screen.hasShiftDown();
    }

    public static boolean matchesKey(int keyCode, int scanCode) {
        KeyMapping keyMapping = getFavoriteKey();
        return keyMapping != null && keyMapping.isActiveAndMatches(InputConstants.getKey(keyCode, scanCode));
    }

    public static boolean toggleFavorite(AEKey key) {
        if (!findFavoriteMethods()) {
            return false;
        }
        try {
            boolean isFavorite = (boolean) isFavoriteMethod.invoke(null, key);
            (isFavorite ? removeFavoriteMethod : addFavoriteMethod).invoke(null, key);
            return true;
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException exception) {
            GTLCore.LOGGER.warn("Unable to update the AEF favorite-item list", exception);
            return false;
        }
    }

    private static KeyMapping getFavoriteKey() {
        if (lookupAttempted) {
            return favoriteKey;
        }
        lookupAttempted = true;
        if (!ModList.get().isLoaded(AEF_MOD_ID)) {
            return null;
        }
        try {
            Class<?> keyBindingsClass = Class.forName(AEF_KEY_BINDINGS_CLASS);
            Field favoriteKeyField = keyBindingsClass.getField(AEF_FAVORITE_KEY_FIELD);
            favoriteKey = (KeyMapping) favoriteKeyField.get(null);
        } catch (ReflectiveOperationException | ClassCastException exception) {
            GTLCore.LOGGER.warn("Unable to access the AEF favorite-item key mapping", exception);
        }
        return favoriteKey;
    }

    private static boolean findFavoriteMethods() {
        if (favoritesLookupAttempted) {
            return isFavoriteMethod != null;
        }
        favoritesLookupAttempted = true;
        if (!ModList.get().isLoaded(AEF_MOD_ID)) {
            return false;
        }
        try {
            Class<?> favoritesClass = Class.forName(AEF_FAVORITES_CLASS);
            isFavoriteMethod = favoritesClass.getMethod("isFavoritesKey", AEKey.class);
            addFavoriteMethod = favoritesClass.getMethod("addFavoritesKey", AEKey.class);
            removeFavoriteMethod = favoritesClass.getMethod("removeFavoritesKey", AEKey.class);
        } catch (ReflectiveOperationException exception) {
            GTLCore.LOGGER.warn("Unable to access the AEF favorite-item storage", exception);
        }
        return isFavoriteMethod != null;
    }
}
