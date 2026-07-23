package org.gtlcore.gtlcore.api.gui;

import org.gtlcore.gtlcore.common.block.BlockMap;

import com.gregtechceu.gtceu.api.gui.GuiTextures;

import com.lowdragmc.lowdraglib.gui.editor.ColorPattern;
import com.lowdragmc.lowdraglib.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib.gui.widget.*;

import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.*;
import java.util.function.*;

import static net.minecraft.network.chat.Component.translatable;

public class BlockMapSelectorWidget extends WidgetGroup {

    private static final int SELECTOR_HEIGHT = 136;
    private static final int PANEL_TOP = 2;
    private static final int PANEL_PADDING = 4;
    private static final int PANEL_GAP = 2;
    private static final int TYPE_PANEL_HEIGHT = 68;
    private static final int TYPE_ROW_HEIGHT = 15;
    private static final int TIER_ROW_HEIGHT = 20;
    private static final int SCROLLBAR_WIDTH = 2;

    public static final Component SC = translatable("gui.gtlcore.stellar_thermal_container");
    public static final Component SEPM = translatable("gui.gtlcore.space_elevator_module");
    public static final Component CAL = translatable("gui.gtlcore.component_assembly_casing");
    public static final Component COIL = translatable("gui.gtlcore.coil");

    private final BiConsumer<String, Integer> onChanged;
    private final BiPredicate<String, Integer> isSelected;
    private List<Block> blocks;
    private String currentType;
    private WidgetGroup typeGroup;
    private WidgetGroup tierGroup;

    public BlockMapSelectorWidget(int x, int y, int width, BiConsumer<String, Integer> onChanged,
                                  BiPredicate<String, Integer> isSelected) {
        super(x, y, width, SELECTOR_HEIGHT);
        this.onChanged = onChanged;
        this.isSelected = isSelected;
        this.setVisible(false);
    }

    public static Component getBlock(String string) {
        return switch (string) {
            case "sc" -> SC;
            case "sepm" -> SEPM;
            case "cal" -> CAL;
            case "coil" -> COIL;
            default -> throw new IllegalStateException("Unexpected value: " + string);
        };
    }

    public void setInit(ItemStack itemStack) {
        var tag = itemStack.getOrCreateTag();
        var block = tag.getString("blocks");
        if (!block.isEmpty()) {
            var tierBlocks = BlockMap.tierBlockMap.get(block);
            if (tierBlocks != null) {
                this.blocks = Arrays.stream(tierBlocks.get()).toList();
                this.currentType = block;
            }
        }
    }

    public void showType(boolean isShow) {
        if (!isShow) this.setVisible(false);
        else {
            refreshTypeGroup();
            this.setVisible(true);
            if (this.blocks != null && !this.blocks.isEmpty()) showTier(this.blocks);
        }
    }

    @Override
    public List<Rect2i> getGuiExtraAreas(Rect2i guiRect, List<Rect2i> list) {
        return isVisible() ? super.getGuiExtraAreas(guiRect, list) : list;
    }

    private void refreshTypeGroup() {
        if (this.typeGroup != null) {
            this.removeWidget(this.typeGroup);
        }

        int contentWidth = getSizeWidth() - PANEL_PADDING * 2;
        WidgetGroup group = new WidgetGroup(0, PANEL_TOP, getSizeWidth(), TYPE_PANEL_HEIGHT);
        group.setBackground(GuiTextures.BACKGROUND_INVERSE);
        var blockType = new DraggableScrollableWidgetGroup(
                PANEL_PADDING, PANEL_PADDING, contentWidth, TYPE_PANEL_HEIGHT - PANEL_PADDING * 2);
        blockType.setYScrollBarWidth(SCROLLBAR_WIDTH)
                .setYBarStyle(null, ColorPattern.T_WHITE.rectTexture().setRadius(1.0F))
                .setBackground(GuiTextures.DISPLAY);
        int rowWidth = contentWidth - SCROLLBAR_WIDTH * 2;
        int y = 0;
        for (var key : BlockMap.tierBlockMap.keySet()) {
            blockType.addWidget((new WidgetGroup(SCROLLBAR_WIDTH, y, rowWidth, TYPE_ROW_HEIGHT))
                    .addWidget(new ExtendLabelWidget(0, 2, getTypeLabel(key)))
                    .addWidget(new ButtonWidget(0, 0, rowWidth, TYPE_ROW_HEIGHT, (cd) -> {
                        currentType = key;
                        showTier(Arrays.stream(BlockMap.tierBlockMap.get(key).get()).toList());
                    })));
            y += TYPE_ROW_HEIGHT;
        }
        this.typeGroup = group.addWidget(blockType);
        this.addWidget(this.typeGroup);
    }

    private Component getTypeLabel(String blockType) {
        Component typeName = getBlock(blockType);
        var lazyTierBlocks = BlockMap.tierBlockMap.get(blockType);
        if (this.isSelected == null || lazyTierBlocks == null) {
            return typeName;
        }
        Block[] tierBlocks = lazyTierBlocks.get();
        for (int tier = 0; tier < tierBlocks.length; tier++) {
            if (this.isSelected.test(blockType, tier)) {
                return typeName.copy()
                        .append(Component.literal("\uff08"))
                        .append(tierBlocks[tier].getName())
                        .append(Component.literal("\uff09"));
            }
        }
        return typeName;
    }

    public void showTier(List<Block> blocks) {
        if (this.tierGroup != null) {
            this.removeWidget(this.tierGroup);
        }
        if (!blocks.isEmpty()) {
            this.blocks = blocks;
            int tierPanelY = PANEL_TOP + TYPE_PANEL_HEIGHT + PANEL_GAP;
            int tierPanelHeight = getSizeHeight() - tierPanelY - PANEL_TOP;
            int contentWidth = getSizeWidth() - PANEL_PADDING * 2;
            WidgetGroup group = new WidgetGroup(0, tierPanelY, getSizeWidth(), tierPanelHeight);
            group.setBackground(GuiTextures.BACKGROUND_INVERSE);
            var blockTier = new DraggableScrollableWidgetGroup(
                    PANEL_PADDING, PANEL_PADDING, contentWidth, tierPanelHeight - PANEL_PADDING * 2);
            blockTier.setYScrollBarWidth(SCROLLBAR_WIDTH)
                    .setYBarStyle(null, ColorPattern.T_WHITE.rectTexture().setRadius(1.0F))
                    .setBackground(GuiTextures.DISPLAY);
            int rowWidth = contentWidth - SCROLLBAR_WIDTH * 2;
            for (int i = 0; i < blocks.size(); i++) {
                int finalI = i;
                var block = blocks.get(finalI);
                Component blockName = block.getName();
                if (this.isSelected != null && this.isSelected.test(this.currentType, finalI)) {
                    blockName = blockName.copy().append(Component.literal(" \u2713"));
                }
                blockTier.addWidget(new WidgetGroup(SCROLLBAR_WIDTH, 2 + i * TIER_ROW_HEIGHT,
                        rowWidth, TIER_ROW_HEIGHT + 2)
                        .addWidget(new ImageWidget(2, 0, 18, 18,
                                new ItemStackTexture(block.asItem().getDefaultInstance())))
                        .addWidget(new ExtendLabelWidget(20, 4, blockName))
                        .addWidget(new ButtonWidget(20, 0, rowWidth - 20, 18, (cd) -> {
                            if (onChanged != null) {
                                onChanged.accept(currentType, finalI);
                                refreshTypeGroup();
                                showTier(this.blocks);
                            }
                        })));
            }
            this.tierGroup = group.addWidget(blockTier);
            this.addWidget(this.tierGroup);
        }
    }
}
