package org.gtlcore.gtlcore.common.item;

import org.gtlcore.gtlcore.GTLCore;
import org.gtlcore.gtlcore.api.pattern.DebugBlockPattern;
import org.gtlcore.gtlcore.utils.Registries;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.item.ComponentItem;
import com.gregtechceu.gtceu.api.item.component.IItemUIFactory;
import com.gregtechceu.gtceu.api.pattern.util.RelativeDirection;

import com.lowdragmc.lowdraglib.gui.factory.HeldItemUIFactory;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.utils.LocalizationUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import com.google.common.base.Joiner;

public class StructureWriteBehavior implements IItemUIFactory {

    public static final StructureWriteBehavior INSTANCE = new StructureWriteBehavior();

    protected StructureWriteBehavior() {
        /**/
    }

    @Override
    public ModularUI createUI(HeldItemUIFactory.HeldItemHolder playerInventoryHolder,
                              Player entityPlayer) {
        var container = new WidgetGroup(8, 8, 160, 54);
        container
                .addWidget(new ImageWidget(4, 4, 152, 46, GuiTextures.DISPLAY))
                .addWidget(new LabelWidget(7, 7, () -> {
                    int x = 0;
                    int y = 0;
                    int z = 0;
                    if (getPos(playerInventoryHolder.getHeld()) != null) {
                        BlockPos[] blockPos = getPos(playerInventoryHolder.getHeld());
                        if (blockPos != null) {
                            x = 1 + blockPos[1].getX() - blockPos[0].getX();
                            y = 1 + blockPos[1].getY() - blockPos[0].getY();
                            z = 1 + blockPos[1].getZ() - blockPos[0].getZ();
                        }
                    }
                    return LocalizationUtils.format("structure_writer.structural_scale",
                            x, y, z);
                }).setTextColor(0xFAF9F6)).addWidget(new LabelWidget(7, 20, () -> {
                    var direction = getDir(playerInventoryHolder.getHeld());
                    var dirs = DebugBlockPattern.getDir(direction);
                    return LocalizationUtils.format("structure_writer.export_order",
                            dirs[0].name(),
                            dirs[1].name(),
                            dirs[2].name());
                }).setTextColor(0xFAF9F6));
        container.setBackground(GuiTextures.BACKGROUND_INVERSE);
        return new ModularUI(176, 120, playerInventoryHolder, entityPlayer)
                .background(GuiTextures.BACKGROUND)
                .widget(container)
                .widget(new ButtonWidget(
                        9,
                        91,
                        158,
                        20,
                        new GuiTextureGroup(
                                GuiTextures.BUTTON,
                                new TextTexture("structure_writer.export_log")),
                        clickData -> exportLog(playerInventoryHolder)))
                .widget(new ButtonWidget(
                        9,
                        68,
                        77,
                        20,
                        new GuiTextureGroup(
                                GuiTextures.BUTTON,
                                new TextTexture("structure_writer.rotate_x")),
                        clickData -> changeDirX(playerInventoryHolder)))
                .widget(new ButtonWidget(
                        90,
                        68,
                        77,
                        20,
                        new GuiTextureGroup(
                                GuiTextures.BUTTON,
                                new TextTexture("structure_writer.rotate_y")),
                        clickData -> changeDirY(playerInventoryHolder)));
    }

    private void exportLog(HeldItemUIFactory.HeldItemHolder playerInventoryHolder) {
        if (getPos(playerInventoryHolder.getHeld()) != null &&
                playerInventoryHolder.getPlayer() instanceof ServerPlayer) {
            BlockPos[] blockPos = getPos(playerInventoryHolder.getHeld());
            Direction direction = getDir(playerInventoryHolder.getHeld());
            StringBuilder builder = new StringBuilder();
            DebugBlockPattern blockPattern = new DebugBlockPattern(
                    playerInventoryHolder.getPlayer().level(),
                    blockPos[0].getX(),
                    blockPos[0].getY(),
                    blockPos[0].getZ(),
                    blockPos[1].getX(),
                    blockPos[1].getY(),
                    blockPos[1].getZ());
            RelativeDirection[] dirs = DebugBlockPattern.getDir(direction);
            blockPattern.changeDir(dirs[0], dirs[1], dirs[2]);
            builder.append(".pattern(definition -> FactoryBlockPattern.start()\n");
            for (int i = 0; i < blockPattern.pattern.length; i++) {
                String[] strings = blockPattern.pattern[i];
                builder.append(".aisle(\"%s\")\n".formatted(Joiner.on("\", \"").join(strings)));
            }
            builder.append(".where(\"~\", Predicates.controller(Predicates.blocks(definition.get())))\n");
            blockPattern.legend.forEach((b, c) -> {
                if (c.equals(' ')) return;
                builder.append(".where(\"").append(c).append("\", Predicates.blocks(Registries.getBlock(\"")
                        .append(Registries.getBlockId(b)).append("\")))\n");
            });
            GTLCore.LOGGER.info(builder.toString());
        }
    }

    private void changeDirX(HeldItemUIFactory.HeldItemHolder playerInventoryHolder) {
        if (getPos(playerInventoryHolder.getHeld()) != null &&
                playerInventoryHolder.getPlayer() instanceof ServerPlayer) {
            ItemStack itemStack = playerInventoryHolder.getHeld();
            Direction direction = getDir(itemStack);
            direction = direction.getClockWise(Direction.Axis.X);
            setDir(itemStack, direction);
        }
    }

    private void changeDirY(HeldItemUIFactory.HeldItemHolder playerInventoryHolder) {
        if (getPos(playerInventoryHolder.getHeld()) != null &&
                playerInventoryHolder.getPlayer() instanceof ServerPlayer) {
            ItemStack itemStack = playerInventoryHolder.getHeld();
            Direction direction = getDir(itemStack);
            direction = direction.getClockWise(Direction.Axis.Y);
            setDir(itemStack, direction);
        }
    }

    public static boolean isItemStructureWriter(ItemStack stack) {
        if (stack.isEmpty()) return false;

        if (stack.getItem() instanceof ComponentItem item) {
            return item.getComponents().contains(INSTANCE);
        }
        return false;
    }

    public static Direction getDir(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTagElement("structure_writer");
        if (!tag.contains("dir")) return Direction.WEST;
        return Direction.byName(tag.getString("dir"));
    }

    public static void setDir(ItemStack stack, Direction dir) {
        CompoundTag tag = stack.getOrCreateTagElement("structure_writer");
        tag.putString("dir", dir.getName());
    }

    public static BlockPos[] getPos(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTagElement("structure_writer");
        if (!tag.contains("minX")) return null;
        return new BlockPos[] {
                new BlockPos(tag.getInt("minX"), tag.getInt("minY"), tag.getInt("minZ")),
                new BlockPos(tag.getInt("maxX"), tag.getInt("maxY"), tag.getInt("maxZ"))
        };
    }

    public static void addPos(ItemStack stack, BlockPos pos) {
        CompoundTag tag = stack.getOrCreateTagElement("structure_writer");
        if (!tag.contains("minX") || tag.getInt("minX") > pos.getX()) {
            tag.putInt("minX", pos.getX());
        }
        if (!tag.contains("maxX") || tag.getInt("maxX") < pos.getX()) {
            tag.putInt("maxX", pos.getX());
        }

        if (!tag.contains("minY") || tag.getInt("minY") > pos.getY()) {
            tag.putInt("minY", pos.getY());
        }
        if (!tag.contains("maxY") || tag.getInt("maxY") < pos.getY()) {
            tag.putInt("maxY", pos.getY());
        }

        if (!tag.contains("minZ") || tag.getInt("minZ") > pos.getZ()) {
            tag.putInt("minZ", pos.getZ());
        }
        if (!tag.contains("maxZ") || tag.getInt("maxZ") < pos.getZ()) {
            tag.putInt("maxZ", pos.getZ());
        }
    }

    public static void removePos(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTagElement("structure_writer");
        tag.remove("minX");
        tag.remove("maxX");
        tag.remove("minY");
        tag.remove("maxY");
        tag.remove("minZ");
        tag.remove("maxZ");
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack itemStack, UseOnContext context) {
        var player = context.getPlayer();
        if (player == null) return InteractionResult.SUCCESS;
        ItemStack stack = player.getItemInHand(context.getHand());
        if (!player.isShiftKeyDown()) {
            addPos(stack, context.getClickedPos());
        } else {
            removePos(stack);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Item item, Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (player.isShiftKeyDown()) {
            removePos(stack);
        } else {
            if (player instanceof ServerPlayer serverPlayer) {
                HeldItemUIFactory.INSTANCE.openUI(serverPlayer, usedHand);
            }
        }
        return new InteractionResultHolder<>(InteractionResult.SUCCESS, stack);
    }
}
