package org.gtlcore.gtlcore.integration.jei;

import com.lowdragmc.lowdraglib.gui.ingredient.IRecipeIngredientSlot;
import com.lowdragmc.lowdraglib.utils.Position;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;

import mezz.jei.api.gui.builder.IIngredientConsumer;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.inputs.RecipeSlotUnderMouse;
import mezz.jei.api.gui.widgets.ISlottedRecipeWidget;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IClickableIngredient;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import javax.annotation.ParametersAreNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class SlotRecipeWidget implements ISlottedRecipeWidget {

    public final IRecipeIngredientSlot slot;
    public final IRecipeSlotDrawable jeiSlot;
    public final Position position;

    public SlotRecipeWidget(IRecipeIngredientSlot slot, IRecipeSlotDrawable jeiSlot) {
        this.slot = slot;
        this.jeiSlot = jeiSlot;
        this.position = new Position(slot.self().getPositionX(), slot.self().getPositionY());
    }

    @Override
    public Optional<RecipeSlotUnderMouse> getSlotUnderMouse(double mouseX, double mouseY) {
        if (slot.self().isMouseOverElement(mouseX + position.x, mouseY + position.y)) {
            return Optional.of(new RecipeSlotUnderMouse(new RecipeSlotDrawable(), getPosition()));
        }
        return Optional.empty();
    }

    /**
     * Get the position of this widget, relative to its parent element.
     * 
     * @since 15.10.0
     */
    @Override
    public ScreenPosition getPosition() {
        return new ScreenPosition(position.x, position.y);
    }

    @SuppressWarnings({ "removal", "unchecked" })
    public class RecipeSlotDrawable implements IRecipeSlotDrawable {

        @Override
        public void drawTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
            jeiSlot.drawTooltip(guiGraphics, mouseX, mouseY);
        }

        @Override
        public void draw(GuiGraphics guiGraphics) {
            // do nothing
        }

        @Override
        public void drawHoverOverlays(GuiGraphics guiGraphics) {
            // do nothing
        }

        @Override
        public List<Component> getTooltip() {
            return jeiSlot.getTooltip();
        }

        @Override
        public void getTooltip(ITooltipBuilder builder) {
            jeiSlot.getTooltip(builder);
        }

        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            return slot.self().isMouseOverElement(mouseX + position.x, mouseY + position.y);
        }

        @Override
        public void setPosition(int x, int y) {
            // do nothing
        }

        @Override
        public IIngredientConsumer createDisplayOverrides() {
            return jeiSlot.createDisplayOverrides();
        }

        @Override
        public void clearDisplayOverrides() {
            jeiSlot.clearDisplayOverrides();
        }

        @Override
        public Rect2i getRect() {
            return new Rect2i(0, 0, slot.self().getSizeWidth(), slot.self().getSizeHeight());
        }

        @Override
        public Rect2i getAreaIncludingBackground() {
            return null;
        }

        @Override
        public Stream<ITypedIngredient<?>> getAllIngredients() {
            return slot.getXEIIngredients().stream()
                    .filter(IClickableIngredient.class::isInstance)
                    .map(IClickableIngredient.class::cast)
                    .map(IClickableIngredient::getTypedIngredient);
        }

        @Override
        public @Unmodifiable List<@Nullable ITypedIngredient<?>> getAllIngredientsList() {
            return List.of();
        }

        @Override
        public Optional<ITypedIngredient<?>> getDisplayedIngredient() {
            var ingredients = slot.getXEIIngredients();
            var index = ingredients.size() > 1 ? Math.abs((int) (System.currentTimeMillis() / 1000) % ingredients.size()) : 0;
            if (ingredients.isEmpty()) return Optional.empty();
            var ingredient = ingredients.get(index);
            if (ingredient instanceof IClickableIngredient<?> clickableIngredient) {
                return Optional.of(clickableIngredient.getTypedIngredient());
            }
            return Optional.empty();
        }

        @Override
        public RecipeIngredientRole getRole() {
            return jeiSlot.getRole();
        }

        @Override
        public void drawHighlight(GuiGraphics guiGraphics, int color) {
            jeiSlot.drawHighlight(guiGraphics, color);
        }

        @Override
        public Optional<String> getSlotName() {
            return jeiSlot.getSlotName();
        }
    }
}
