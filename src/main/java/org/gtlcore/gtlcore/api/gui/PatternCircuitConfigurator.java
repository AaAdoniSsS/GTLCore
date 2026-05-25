package org.gtlcore.gtlcore.api.gui;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfigurator;
import com.gregtechceu.gtceu.api.gui.widget.IntInputWidget;
import com.gregtechceu.gtceu.common.item.IntCircuitBehaviour;

import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.util.ClickData;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.SwitchWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;

import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class PatternCircuitConfigurator implements IFancyConfigurator {

    private final Supplier<Integer> circuitSupplier;
    private final Consumer<Integer> circuitSetter;
    private final Supplier<Boolean> skipExistingSupplier;
    private final BiConsumer<ClickData, Boolean> skipExistingSetter;
    private final Consumer<ClickData> applyCircuit;
    private final Consumer<ClickData> clearCircuits;

    public PatternCircuitConfigurator(Supplier<Integer> circuitSupplier, Consumer<Integer> circuitSetter,
                                      Supplier<Boolean> skipExistingSupplier,
                                      BiConsumer<ClickData, Boolean> skipExistingSetter,
                                      Consumer<ClickData> applyCircuit, Consumer<ClickData> clearCircuits) {
        this.circuitSupplier = circuitSupplier;
        this.circuitSetter = circuitSetter;
        this.skipExistingSupplier = skipExistingSupplier;
        this.skipExistingSetter = skipExistingSetter;
        this.applyCircuit = applyCircuit;
        this.clearCircuits = clearCircuits;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("gui.gtlcore.pattern_buffer_circuit_config");
    }

    @Override
    public IGuiTexture getIcon() {
        return new GuiTextureGroup(GuiTextures.BUTTON, GuiTextures.INT_CIRCUIT_OVERLAY);
    }

    @Override
    public List<Component> getTooltips() {
        return List.of(Component.translatable("tooltip.gtlcore.pattern_buffer_circuit_config"));
    }

    @Override
    public Widget createConfigurator() {
        var group = new WidgetGroup(0, 0, 190, 104);
        var display = new WidgetGroup(4, 4, 182, 96);
        display.setBackground(GuiTextures.DISPLAY);

        display.addWidget(new LabelWidget(8, 6,
                "gui.gtlcore.pattern_buffer_circuit_config").setTextColor(0xFAF9F6));
        display.addWidget(new LabelWidget(10, 27,
                "gui.gtlcore.pattern_buffer_circuit_number"));
        display.addWidget(new IntInputWidget(96, 24, 78, 14, circuitSupplier, circuitSetter)
                .setMin(1)
                .setMax(IntCircuitBehaviour.CIRCUIT_MAX)
                .setHoverTooltips(Component.translatable("tooltip.gtlcore.pattern_buffer_circuit_number",
                        IntCircuitBehaviour.CIRCUIT_MAX)));

        display.addWidget(new LabelWidget(10, 49,
                "gui.gtlcore.pattern_buffer_skip_existing_circuit"));
        display.addWidget(new SwitchWidget(138, 46, 36, 14, skipExistingSetter)
                .setSupplier(skipExistingSupplier)
                .setPressed(skipExistingSupplier.get())
                .setTexture(
                        new GuiTextureGroup(GuiTextures.BUTTON, new TextTexture("OFF")),
                        new GuiTextureGroup(GuiTextures.BUTTON, new TextTexture("ON")))
                .setHoverTooltips(Component.translatable("tooltip.gtlcore.pattern_buffer_skip_existing_circuit")));

        display.addWidget(new ButtonWidget(10, 72, 78, 18,
                new GuiTextureGroup(GuiTextures.BUTTON,
                        new TextTexture(() -> Component.translatable("gui.gtlcore.pattern_buffer_apply_circuit").getString())),
                applyCircuit)
                .setHoverTooltips(Component.translatable("tooltip.gtlcore.pattern_buffer_apply_circuit")));
        display.addWidget(new ButtonWidget(96, 72, 78, 18,
                new GuiTextureGroup(GuiTextures.BUTTON,
                        new TextTexture(() -> Component.translatable("gui.gtlcore.pattern_buffer_clear_circuits").getString())),
                clearCircuits)
                .setHoverTooltips(Component.translatable("tooltip.gtlcore.pattern_buffer_clear_circuits")));

        group.addWidget(display);
        group.setBackground(GuiTextures.BACKGROUND_INVERSE);
        return group;
    }
}
