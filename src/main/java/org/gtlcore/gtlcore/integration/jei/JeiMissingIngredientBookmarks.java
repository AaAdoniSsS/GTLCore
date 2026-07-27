package org.gtlcore.gtlcore.integration.jei;

import org.gtlcore.gtlcore.GTLCore;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.forge.ForgeTypes;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Optional;

public final class JeiMissingIngredientBookmarks {

    private static final String BOOKMARK_LIST_FIELD = "bookmarkList";
    private static final String BOOKMARK_CLASS = "mezz.jei.gui.bookmarks.IBookmark";
    private static final String INGREDIENT_BOOKMARK_CLASS = "mezz.jei.gui.bookmarks.IngredientBookmark";

    private static @Nullable IJeiRuntime runtime;
    private static @Nullable BookmarkAccess bookmarkAccess;

    private JeiMissingIngredientBookmarks() {}

    public static void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
        try {
            bookmarkAccess = BookmarkAccess.create(jeiRuntime);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            bookmarkAccess = null;
            GTLCore.LOGGER.error("Failed to access JEI bookmarks", exception);
        }
    }

    public static void onRuntimeUnavailable() {
        runtime = null;
        bookmarkAccess = null;
    }

    public static boolean isAvailable() {
        return runtime != null && bookmarkAccess != null;
    }

    public static AddResult add(Collection<AEKey> keys) {
        IJeiRuntime jeiRuntime = runtime;
        BookmarkAccess access = bookmarkAccess;
        if (jeiRuntime == null || access == null) {
            return new AddResult(AddStatus.UNAVAILABLE, 0);
        }

        IIngredientManager ingredientManager = jeiRuntime.getIngredientManager();
        int supported = 0;
        int added = 0;
        try {
            for (AEKey key : new LinkedHashSet<>(keys)) {
                Optional<ITypedIngredient<?>> ingredient = toTypedIngredient(key, ingredientManager);
                if (ingredient.isEmpty()) {
                    continue;
                }
                supported++;
                if (access.add(ingredient.get(), ingredientManager)) {
                    added++;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            GTLCore.LOGGER.error("Failed to add missing ingredients to JEI bookmarks", exception);
            return new AddResult(AddStatus.FAILED, added);
        }

        if (added > 0) {
            return new AddResult(AddStatus.ADDED, added);
        }
        return new AddResult(supported > 0 ? AddStatus.ALREADY_BOOKMARKED : AddStatus.NOTHING_TO_ADD, 0);
    }

    private static Optional<ITypedIngredient<?>> toTypedIngredient(AEKey key,
                                                                   IIngredientManager ingredientManager) {
        if (key instanceof AEItemKey itemKey) {
            return ingredientManager.createTypedIngredient(VanillaTypes.ITEM_STACK, itemKey.toStack())
                    .map(ingredient -> ingredient);
        }
        if (key instanceof AEFluidKey fluidKey) {
            return ingredientManager.createTypedIngredient(ForgeTypes.FLUID_STACK, fluidKey.toStack(1))
                    .map(ingredient -> ingredient);
        }
        return Optional.empty();
    }

    public enum AddStatus {
        ADDED,
        ALREADY_BOOKMARKED,
        NOTHING_TO_ADD,
        UNAVAILABLE,
        FAILED
    }

    public record AddResult(AddStatus status, int added) {}

    private record BookmarkAccess(Object bookmarkList, Method createBookmark, Method addBookmark) {

        private static BookmarkAccess create(IJeiRuntime runtime) throws ReflectiveOperationException {
            Object overlay = runtime.getBookmarkOverlay();
            Field bookmarkListField = overlay.getClass().getDeclaredField(BOOKMARK_LIST_FIELD);
            bookmarkListField.setAccessible(true);
            Object bookmarkList = bookmarkListField.get(overlay);

            ClassLoader classLoader = overlay.getClass().getClassLoader();
            Class<?> bookmarkClass = Class.forName(BOOKMARK_CLASS, false, classLoader);
            Class<?> ingredientBookmarkClass = Class.forName(INGREDIENT_BOOKMARK_CLASS, false, classLoader);
            Method createBookmark = ingredientBookmarkClass.getMethod(
                    "create", ITypedIngredient.class, IIngredientManager.class);
            Method addBookmark = bookmarkList.getClass().getMethod("add", bookmarkClass);
            return new BookmarkAccess(bookmarkList, createBookmark, addBookmark);
        }

        private boolean add(ITypedIngredient<?> ingredient, IIngredientManager ingredientManager)
                                                                                                  throws ReflectiveOperationException {
            Object bookmark = createBookmark.invoke(null, ingredient, ingredientManager);
            return Boolean.TRUE.equals(addBookmark.invoke(bookmarkList, bookmark));
        }
    }
}
