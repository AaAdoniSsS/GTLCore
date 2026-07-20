package org.gtlcore.gtlcore.client.gui.widget;

import org.gtlcore.gtlcore.integration.ae2.MEFluidUnits;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.client.TooltipsHandler;

import com.lowdragmc.lowdraglib.gui.util.DrawerHelper;
import com.lowdragmc.lowdraglib.gui.widget.DraggableScrollableWidgetGroup;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import com.lowdragmc.lowdraglib.utils.Position;
import com.lowdragmc.lowdraglib.utils.Size;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

import static com.gregtechceu.gtceu.integration.ae2.gui.widget.slot.AEConfigSlotWidget.drawSelectionOverlay;
import static com.lowdragmc.lowdraglib.gui.util.DrawerHelper.drawItemStack;
import static com.lowdragmc.lowdraglib.gui.util.DrawerHelper.drawText;

public class MEOutListGridWidget extends DraggableScrollableWidgetGroup {

    protected final Object2LongMap<AEKey> list;
    private final int slotAmountY;
    private int slotRowsAmount;
    private static final int TYPE_ITEM = 0;
    private static final int TYPE_FLUID = 1;
    protected final static int ROW_CHANGE_ID = 2;
    protected final static int CONTENT_CHANGE_ID = 3;

    protected final Object2LongMap<AEKey> changeMap = new Object2LongOpenHashMap<>();
    protected final Object2LongMap<AEKey> cached = new Object2LongOpenHashMap<>();
    protected final List<GenericStack> displayList = new ObjectArrayList<>();

    public MEOutListGridWidget(int x, int y, int slotsY, @NotNull Object2LongMap<AEKey> internalList) {
        super(x, y, 18 + 140, slotsY * 18);
        this.list = internalList;
        this.slotAmountY = slotsY;
    }

    public GenericStack getAt(int index) {
        return index >= 0 && index < displayList.size() ? displayList.get(index) : null;
    }

    private void addSlotRows(int amount) {
        for (int i = 0; i < amount; i++) {
            int widgetAmount = this.widgets.size();
            Widget widget = createDisplayWidget(0, i * 18, widgetAmount);
            this.addWidget(widget);
        }
    }

    private void removeSlotRows(int amount) {
        for (int i = 0; i < amount; i++) {
            Widget slotWidget = this.widgets.remove(this.widgets.size() - 1);
            removeWidget(slotWidget);
        }
    }

    private void modifySlotRows(int delta) {
        if (delta > 0) {
            addSlotRows(delta);
        } else {
            removeSlotRows(delta);
        }
    }

    protected void writeListChange(FriendlyByteBuf buffer) {
        this.changeMap.clear();

        // Remove: 缓存里有但当前 list 里没有 => 负增量并移除缓存
        for (var it = Object2LongMaps.fastIterator(cached); it.hasNext();) {
            var entry = it.next();
            AEKey cachedKey = entry.getKey();
            if (!list.containsKey(cachedKey)) {
                this.changeMap.put(cachedKey, -entry.getLongValue());
                it.remove();
            }
        }

        // Change/Add: list 与 cached 的差异
        for (var it = Object2LongMaps.fastIterator(list); it.hasNext();) {
            var entry = it.next();
            AEKey key = entry.getKey();
            long value = entry.getLongValue();
            long cacheValue = cached.getOrDefault(key, 0L);
            long delta = value - cacheValue;
            if (cacheValue == 0L) {
                // 新增
                if (value != 0L) {
                    this.changeMap.put(key, value);
                    this.cached.put(key, value);
                }
            } else if (delta != 0L) {
                // 变更
                this.changeMap.put(key, delta);
                this.cached.put(key, value);
            }
        }

        buffer.writeVarInt(this.changeMap.size());
        for (var it = Object2LongMaps.fastIterator(this.changeMap); it.hasNext();) {
            var entry = it.next();
            writeAnyKey(buffer, entry.getKey());
            buffer.writeVarLong(entry.getLongValue());
        }
    }

    protected void readListChange(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        for (int i = 0; i < size; i++) {
            AEKey key = readAnyKey(buffer);
            long delta = buffer.readVarLong();

            boolean found = false;
            var li = displayList.listIterator();
            while (li.hasNext()) {
                var stack = li.next();
                if (stack.what().equals(key)) {
                    long newAmount = stack.amount() + delta;
                    if (newAmount > 0) {
                        li.set(new GenericStack(key, newAmount));
                    } else {
                        li.remove();
                    }
                    found = true;
                    break;
                }
            }
            if (!found) {
                if (delta > 0) {
                    displayList.add(new GenericStack(key, delta));
                }
            }
        }
    }

    protected Widget createDisplayWidget(int x, int y, int index) {
        return new DisplaySlot(x, y, this, index);
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        int slotRowsRequired = Math.max(this.slotAmountY, list.size());
        if (this.slotRowsAmount != slotRowsRequired) {
            int slotsToAdd = slotRowsRequired - this.slotRowsAmount;
            this.slotRowsAmount = slotRowsRequired;
            this.writeUpdateInfo(ROW_CHANGE_ID, buf -> buf.writeVarInt(slotsToAdd));
            this.modifySlotRows(slotsToAdd);
        }
        this.writeUpdateInfo(CONTENT_CHANGE_ID, this::writeListChange);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void readUpdateInfo(int id, FriendlyByteBuf buffer) {
        super.readUpdateInfo(id, buffer);
        if (id == ROW_CHANGE_ID) {
            int slotsToAdd = buffer.readVarInt();
            this.modifySlotRows(slotsToAdd);
        }
        if (id == CONTENT_CHANGE_ID) {
            this.readListChange(buffer);
        }
    }

    @Override
    public void writeInitialData(FriendlyByteBuf buffer) {
        super.writeInitialData(buffer);
        int slotRowsRequired = Math.max(this.slotAmountY, list.size());
        int slotsToAdd = slotRowsRequired - this.slotRowsAmount;
        this.slotRowsAmount = slotRowsRequired;
        this.modifySlotRows(slotsToAdd);
        buffer.writeVarInt(slotsToAdd);
        this.writeListChange(buffer);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void readInitialData(FriendlyByteBuf buffer) {
        super.readInitialData(buffer);
        this.modifySlotRows(buffer.readVarInt());
        this.readListChange(buffer);
    }

    private static void writeAnyKey(FriendlyByteBuf buf, AEKey key) {
        if (key instanceof AEItemKey) {
            buf.writeVarInt(TYPE_ITEM);
            key.writeToPacket(buf);
        } else if (key instanceof AEFluidKey) {
            buf.writeVarInt(TYPE_FLUID);
            key.writeToPacket(buf);
        }
    }

    private static AEKey readAnyKey(FriendlyByteBuf buf) {
        int t = buf.readVarInt();
        return switch (t) {
            case TYPE_ITEM -> AEItemKey.fromPacket(buf);
            case TYPE_FLUID -> AEFluidKey.fromPacket(buf);
            default -> throw new IllegalStateException("Unknown AEKey type id: " + t);
        };
    }

    public static class DisplaySlot extends Widget {

        private final MEOutListGridWidget gridWidget;
        private final int index;

        public DisplaySlot(int x, int y, MEOutListGridWidget gridWidget, int index) {
            super(new Position(x, y), new Size(18, 18));
            this.gridWidget = gridWidget;
            this.index = index;
        }

        @Override
        public void drawInBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
            Position pos = getPosition();
            GenericStack gs = this.gridWidget.getAt(this.index);

            if (gs != null && gs.what() instanceof AEFluidKey) {
                GuiTextures.FLUID_SLOT.draw(graphics, mouseX, mouseY, pos.x, pos.y, 18, 18);
            } else {
                GuiTextures.SLOT.draw(graphics, mouseX, mouseY, pos.x, pos.y, 18, 18);
            }
            GuiTextures.NUMBER_BACKGROUND.draw(graphics, mouseX, mouseY, pos.x + 18, pos.y, 140, 18);

            int stackX = pos.x + 1;
            int stackY = pos.y + 1;

            if (gs != null) {
                AEKey key = gs.what();
                long amt = gs.amount();

                if (key instanceof AEItemKey itemKey) {
                    ItemStack stackForRender = new ItemStack(itemKey.getItem());
                    drawItemStack(graphics, stackForRender, stackX, stackY, -1, null);
                    String amountStr = String.format("x%,d", amt);
                    drawText(graphics, amountStr, stackX + 20, stackY + 5, 1, 0xFFFFFFFF);
                } else if (key instanceof AEFluidKey fluidKey) {
                    FluidStack fs = FluidStack.create(fluidKey.getFluid(), amt, fluidKey.getTag());
                    DrawerHelper.drawFluidForGui(graphics, fs, amt, stackX, stackY, 16, 16);
                    String amountStr = MEFluidUnits.formatDisplayAmount(amt);
                    drawText(graphics, amountStr, stackX + 20, stackY + 5, 1, 0xFFFFFFFF);
                }
            }

            if (isMouseOverElement(mouseX, mouseY)) {
                drawSelectionOverlay(graphics, stackX, stackY, 16, 16);
            }
        }

        @Override
        public void drawInForeground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            if (!isMouseOverElement(mouseX, mouseY)) return;

            GenericStack gs = this.gridWidget.getAt(this.index);
            if (gs == null) return;

            AEKey key = gs.what();
            long amt = gs.amount();

            if (key instanceof AEItemKey) {
                graphics.renderTooltip(Minecraft.getInstance().font, GenericStack.wrapInItemStack(gs), mouseX, mouseY);
            } else if (key instanceof AEFluidKey fluidKey) {
                FluidStack fs = FluidStack.create(fluidKey.getFluid(), amt, fluidKey.getTag());
                List<Component> tips = new ObjectArrayList<>();
                tips.add(fs.getDisplayName());
                tips.add(Component.literal(String.format("%,d mB", amt)));
                TooltipsHandler.appendFluidTooltips(fs.getFluid(), fs.getAmount(), tips::add, TooltipFlag.NORMAL);
                graphics.renderTooltip(Minecraft.getInstance().font, tips, Optional.empty(), mouseX, mouseY);
            }
        }
    }
}
