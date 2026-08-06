package org.gtlcore.gtlcore.api.gui;

import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfigurator;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.TextFieldWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;

import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** Shared native configurator used by tag-filter stocking chambers and remote terminals. */
public final class TagFilterConfigurator implements IFancyConfigurator {

    public static final IGuiTexture ICON = new ResourceTexture("gtceu:textures/gui/list.png").scale(1.25f);

    private final Supplier<String> whitelist;
    private final Consumer<String> setWhitelist;
    private final Supplier<String> blacklist;
    private final Consumer<String> setBlacklist;

    public TagFilterConfigurator(Supplier<String> whitelist, Consumer<String> setWhitelist,
                                 Supplier<String> blacklist, Consumer<String> setBlacklist) {
        this.whitelist = whitelist;
        this.setWhitelist = setWhitelist;
        this.blacklist = blacklist;
        this.setBlacklist = setBlacklist;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("gui.gtlcore.tag_filter_config");
    }

    @Override
    public IGuiTexture getIcon() {
        return ICON;
    }

    @Override
    public Widget createConfigurator() {
        return new WidgetGroup(0, 0, 132, 100)
                .addWidget(new LabelWidget(9, 4,
                        () -> Component.translatable("gui.gtlcore.tag_whitelist").getString()))
                .addWidget(new TextFieldWidget(9, 16, 114, 16, whitelist, setWhitelist))
                .addWidget(new LabelWidget(9, 36,
                        () -> Component.translatable("gui.gtlcore.tag_blacklist").getString()))
                .addWidget(new TextFieldWidget(9, 48, 114, 16, blacklist, setBlacklist))
                .addWidget(new LabelWidget(0, 68,
                        () -> Component.translatable("gui.gtlcore.wildcard_info").getString()))
                .addWidget(new LabelWidget(0, 84,
                        () -> Component.translatable("gui.gtlcore.logic_operators").getString()));
    }
}
