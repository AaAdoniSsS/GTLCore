package org.gtlcore.gtlcore.test.wildcard;

import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialFlags;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.PropertyKey;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.common.data.GTMaterials;

import com.lowdragmc.lowdraglib.gui.widget.SelectorWidget;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.gui.widget.TextFieldWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import io.netty.buffer.Unpooled;
import org.leodreamer.wildcard_pattern.WildcardItems;
import org.leodreamer.wildcard_pattern.gui.GenericGTTag;
import org.leodreamer.wildcard_pattern.gui.PhantomGTMaterialSlot;
import org.leodreamer.wildcard_pattern.gui.PhantomGTTagSlot;
import org.leodreamer.wildcard_pattern.wildcard.WildcardPatternLogic;
import org.leodreamer.wildcard_pattern.wildcard.feature.IWildcardFilterComponent;
import org.leodreamer.wildcard_pattern.wildcard.feature.IWildcardIOComponent;
import org.leodreamer.wildcard_pattern.wildcard.gui.WildcardComponentListGroup;
import org.leodreamer.wildcard_pattern.wildcard.gui.WildcardFilterFancyConfigurator;
import org.leodreamer.wildcard_pattern.wildcard.gui.WildcardIOFancyConfigurator;
import org.leodreamer.wildcard_pattern.wildcard.impl.FlagFilterComponent;
import org.leodreamer.wildcard_pattern.wildcard.impl.PropertyFilterComponent;
import org.leodreamer.wildcard_pattern.wildcard.impl.SimpleFilterComponent;
import org.leodreamer.wildcard_pattern.wildcard.impl.SimpleIOComponent;
import org.leodreamer.wildcard_pattern.wildcard.impl.TagIOComponent;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

final class WildcardRegression {

    private WildcardRegression() {}

    static void run(MinecraftServer server, List<WildcardTestMod.Result> results) {
        test(results, "property_restore", () -> {
            // Test both choices so the old implementation fails regardless of Set iteration order.
            for (var property : List.of(PropertyKey.INGOT, PropertyKey.ROTOR)) {
                var component = new PropertyFilterComponent(property, GTMaterials.Steel, true);
                for (int i = 0; i < 3; i++) {
                    component.createUILine(new WidgetGroup());
                    equal(property.toString(), serialize(component).getString("flag"), "property after UI rebuild");
                    equal(property.toString(), field(component, "propertySelector", SelectorWidget.class).getValue(), "property selector");
                }
            }
        });
        test(results, "flag_restore", () -> {
            for (var flag : List.of(MaterialFlags.GENERATE_PLATE, MaterialFlags.GENERATE_ROTOR)) {
                var component = new FlagFilterComponent(flag, GTMaterials.Steel, true);
                component.createUILine(new WidgetGroup());
                equal(flag.toString(), serialize(component).getString("flag"), "flag after UI initialization");
                equal(flag.toString(), field(component, "flagSelector", SelectorWidget.class).getValue(), "flag selector");
            }
        });
        test(results, "material_changes", WildcardRegression::materialChanges);
        test(results, "filter_save_order", WildcardRegression::filterSaveOrder);
        for (var io : WildcardPatternLogic.IO.values()) {
            test(results, "item_save_" + io, () -> itemSave(io));
        }
        test(results, "io_rebuild", WildcardRegression::ioRebuild);
        test(results, "tag_and_fluid_save", WildcardRegression::tagAndFluidSave);
        test(results, "filter_nbt_roundtrip", WildcardRegression::filterRoundtrip);
        test(results, "pattern_expansion", () -> patternExpansion(server));
    }

    private static void materialChanges() throws Exception {
        var component = new PropertyFilterComponent(PropertyKey.INGOT, GTMaterials.Steel, true);
        component.createUILine(new WidgetGroup());
        var selector = field(component, "propertySelector", SelectorWidget.class);
        select(selector, "ingot");
        var slot = field(component, "exampleSlot", PhantomGTMaterialSlot.class);
        slot.setMaterial(GTMaterials.Steel);
        equal("ingot", selector.getValue(), "same material preserves selection");
        slot.setMaterial(GTMaterials.Lapis);
        equal("gtceu:lapis", serialize(component).getString("example"), "new example material");
        check(!"ingot".equals(selector.getValue()), "new material updates the candidates and selection");
        check(component.isWhitelist(), "material change preserves whitelist");

        var flag = new FlagFilterComponent(MaterialFlags.NO_SMASHING, GTMaterials.Rubber, false);
        flag.createUILine(new WidgetGroup());
        select(field(flag, "flagSelector", SelectorWidget.class), "no_smashing");
        field(flag, "exampleSlot", PhantomGTMaterialSlot.class).setMaterial(GTMaterials.Rubber);
        equal("no_smashing", serialize(flag).getString("flag"), "same material preserves blacklist flag");
        field(flag, "exampleSlot", PhantomGTMaterialSlot.class).setMaterial(GTMaterials.Steel);
        equal("gtceu:steel", serialize(flag).getString("example"), "flag example changes");
        check(!flag.isWhitelist(), "new material preserves blacklist");
    }

    private static void filterSaveOrder() throws Exception {
        var stack = newPattern();
        var saved = new AtomicReference<ItemStack>();
        var page = new WildcardFilterFancyConfigurator(WildcardPatternLogic.on(stack), saved::set);
        page.createMainPage(null);
        var component = new CountingProperty();
        filterList(page).addComponent(component);
        String old = serialize(component).getString("flag");
        String wanted = old.equals("ingot") ? "rotor" : "ingot";
        field(component, "propertySelector", SelectorWidget.class).setValue(wanted);
        component.saves = 0;
        save(page);
        equal(1, component.saves, "onSave called once per save");
        check(saved.get() == stack, "save callback receives pattern");
        equal(wanted, stack.getOrCreateTag().getList("filter", 10).getCompound(0).getCompound("data").getString("flag"),
                "serialize collected filter value");
    }

    private static void itemSave(WildcardPatternLogic.IO io) throws Exception {
        var stack = newPattern();
        var page = new WildcardIOFancyConfigurator(WildcardPatternLogic.on(stack), io, saved -> check(saved == stack, "save callback"));
        page.createMainPage(null);
        var component = new CountingIO();
        ioList(page).addComponent(component);
        itemSlot(component).getHandler().set(new ItemStack(Items.GOLD_INGOT));
        text(component).setCurrentString("7");
        component.saves = 0;
        save(page);
        equal(1, component.saves, "onSave called once per save");
        GenericStack restored = WildcardPatternLogic.on(stack.copy()).getIOComponents(io).get(0).apply(GTMaterials.Steel);
        equal(new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 7), restored, "single save uses edited item and amount");
    }

    private static void ioRebuild() throws Exception {
        var component = new SimpleIOComponent(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 2));
        var list = new WildcardComponentListGroup<IWildcardIOComponent>(List.of(component), 0, 0, 158);
        list.setLineStyle((i, group) -> {});
        // Follow the real text edit callback before changing the slot.
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            text(component).handleClientAction(1, buffer.writeUtf("7"));
        } finally {
            buffer.release();
        }
        itemSlot(component).getHandler().set(new ItemStack(Items.GOLD_INGOT));
        list.addComponent(SimpleIOComponent.empty());
        equal(Items.GOLD_INGOT, itemSlot(component).getHandler().getItem().getItem(), "adding a row preserves edited item");
        equal("7", text(component).getCurrentString(), "adding a row preserves edited amount");
        list.removeComponent(1);
        equal(Items.GOLD_INGOT, itemSlot(component).getHandler().getItem().getItem(), "removing a row preserves edited item");
    }

    private static void tagAndFluidSave() throws Exception {
        var stack = newPattern();
        var logic = WildcardPatternLogic.on(stack);
        logic.setIOComponents(WildcardPatternLogic.IO.IN, List.of(new TagIOComponent(GenericGTTag.item(TagPrefix.ingot), 1)));
        var page = new WildcardIOFancyConfigurator(logic, WildcardPatternLogic.IO.IN, saved -> {});
        page.createMainPage(null);
        var component = ioList(page).getComponents().get(0);
        field(component, "tagSlot", PhantomGTTagSlot.class).setTag(GenericGTTag.item(TagPrefix.plate));
        text(component).setCurrentString("3");
        save(page);
        equal(new GenericStack(AEItemKey.of(ChemicalHelper.get(TagPrefix.plate, GTMaterials.Steel)), 3),
                logic.getIOComponents(WildcardPatternLogic.IO.IN).get(0).apply(GTMaterials.Steel), "tag and amount saved");

        logic.setIOComponents(WildcardPatternLogic.IO.OUT, List.of(new SimpleIOComponent(new GenericStack(AEItemKey.of(Items.BUCKET), 1))));
        var output = new WildcardIOFancyConfigurator(logic, WildcardPatternLogic.IO.OUT, saved -> {});
        output.createMainPage(null);
        var fluid = ioList(output).getComponents().get(0);
        text(fluid).setCurrentString("1000");
        itemSlot(fluid).getHandler().set(new ItemStack(Items.WATER_BUCKET));
        save(output);
        equal(new GenericStack(AEFluidKey.of(Fluids.WATER), 1000),
                logic.getIOComponents(WildcardPatternLogic.IO.OUT).get(0).apply(GTMaterials.Steel), "fluid and amount saved");
    }

    private static void filterRoundtrip() throws Exception {
        ItemStack stack = newPattern();
        WildcardPatternLogic.on(stack).setFilterComponents(correctFilters());
        CompoundTag expected = stack.getOrCreateTag().copy();
        for (int i = 0; i < 3; i++) {
            var page = new WildcardFilterFancyConfigurator(WildcardPatternLogic.on(stack), saved -> {});
            page.createMainPage(null);
            filterList(page).addComponent(SimpleFilterComponent.empty());
            filterList(page).removeComponent(4);
            save(page);
            equal(expected, stack.getOrCreateTag(), "filter NBT after reopen/add/remove/save");
            var file = Path.of("wildcard-roundtrip.nbt").toFile();
            NbtIo.writeCompressed(stack.save(new CompoundTag()), file);
            stack = ItemStack.of(NbtIo.readCompressed(file));
            equal(expected, stack.getOrCreateTag(), "filter NBT after disk roundtrip");
        }
    }

    private static void patternExpansion(MinecraftServer server) {
        var stack = newPattern();
        var logic = WildcardPatternLogic.on(stack);
        logic.setFilterComponents(correctFilters());
        logic.setIOComponents(WildcardPatternLogic.IO.IN, List.of(new TagIOComponent(GenericGTTag.item(TagPrefix.ingot), 1)));
        logic.setIOComponents(WildcardPatternLogic.IO.OUT, List.of(new TagIOComponent(GenericGTTag.item(TagPrefix.plate), 1)));
        for (Material material : List.of(GTMaterials.Steel, GTMaterials.Gold, GTMaterials.Copper, GTMaterials.Iron)) {
            check(logic.test(material), "included material " + material);
            var wanted = new GenericStack(AEItemKey.of(ChemicalHelper.get(TagPrefix.plate, material)), 1);
            check(logic.generateAllPatterns(server.overworld()).anyMatch(pattern -> List.of(pattern.getOutputs()).contains(wanted)),
                    "expanded output " + material);
        }
        for (Material material : List.of(GTMaterials.Rubber, GTMaterials.Polyethylene)) {
            check(!logic.test(material), "excluded material " + material);
        }
    }

    private static List<IWildcardFilterComponent> correctFilters() {
        return List.of(new PropertyFilterComponent(PropertyKey.INGOT, GTMaterials.Steel, true),
                new FlagFilterComponent(MaterialFlags.GENERATE_PLATE, GTMaterials.Steel, true),
                new FlagFilterComponent(MaterialFlags.NO_SMASHING, GTMaterials.Rubber, false),
                new FlagFilterComponent(MaterialFlags.NO_WORKING, GTMaterials.Lapis, false));
    }

    private static ItemStack newPattern() {
        return new ItemStack(WildcardItems.WILDCARD_PATTERN.get());
    }

    private static CompoundTag serialize(IWildcardFilterComponent component) {
        return component.getSerializer().serialize(component);
    }

    @SuppressWarnings("unchecked")
    private static WildcardComponentListGroup<IWildcardFilterComponent> filterList(Object page) throws Exception {
        return field(page, "componentList", WildcardComponentListGroup.class);
    }

    @SuppressWarnings("unchecked")
    private static WildcardComponentListGroup<IWildcardIOComponent> ioList(Object page) throws Exception {
        return field(page, "componentList", WildcardComponentListGroup.class);
    }

    private static TextFieldWidget text(Object component) throws Exception {
        return field(component, "amountEdit", TextFieldWidget.class);
    }

    private static SlotWidget itemSlot(Object component) throws Exception {
        return field(component, "itemSlot", SlotWidget.class);
    }

    private static <T> T field(Object target, String name, Class<T> type) throws Exception {
        Class<?> owner = target instanceof CountingProperty ? PropertyFilterComponent.class :
                target instanceof CountingIO ? SimpleIOComponent.class : target.getClass();
        var field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return type.cast(field.get(target));
    }

    private static void save(Object page) throws Exception {
        var method = page.getClass().getDeclaredMethod("save");
        method.setAccessible(true);
        method.invoke(page);
    }

    private static void select(SelectorWidget selector, String choice) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            selector.handleClientAction(2, buffer.writeUtf(choice));
        } finally {
            buffer.release();
        }
    }

    private static void equal(Object expected, Object actual, String context) {
        if (!expected.equals(actual)) throw new AssertionError(context + ": expected " + expected + ", got " + actual);
    }

    private static void check(boolean condition, String context) {
        if (!condition) throw new AssertionError(context);
    }

    private static void test(List<WildcardTestMod.Result> results, String name, CheckedRunnable body) {
        try {
            body.run();
            results.add(new WildcardTestMod.Result(name, true, ""));
            System.out.println("WILDCARD PASS " + name);
        } catch (Throwable failure) {
            failure.printStackTrace();
            results.add(new WildcardTestMod.Result(name, false, failure.toString()));
            System.out.println("WILDCARD FAIL " + name + ": " + failure);
        }
    }

    @FunctionalInterface
    private interface CheckedRunnable {

        void run() throws Exception;
    }

    private static final class CountingProperty extends PropertyFilterComponent {

        int saves;

        CountingProperty() {
            super(PropertyKey.INGOT, GTMaterials.Steel, true);
        }

        @Override
        public void onSave() {
            saves++;
            super.onSave();
        }
    }

    private static final class CountingIO extends SimpleIOComponent {

        int saves;

        CountingIO() {
            super(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 2));
        }

        @Override
        public void onSave() {
            saves++;
            super.onSave();
        }
    }
}
