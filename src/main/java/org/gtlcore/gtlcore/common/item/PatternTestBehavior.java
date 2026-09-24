package org.gtlcore.gtlcore.common.item;

import org.gtlcore.gtlcore.GTLCore;
import org.gtlcore.gtlcore.api.item.tool.ae2.patternTool.Ae2GtmProcessingPattern;
import org.gtlcore.gtlcore.api.item.tool.ae2.patternTool.ConflictAnalysisManager;
import org.gtlcore.gtlcore.api.item.tool.ae2.patternTool.ConflictAnalysisResult;
import org.gtlcore.gtlcore.api.item.tool.ae2.patternTool.GTRecipeManager;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.item.component.IItemUIFactory;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.integration.ae2.gui.widget.AETextInputButtonWidget;

import com.lowdragmc.lowdraglib.gui.factory.HeldItemUIFactory;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraftforge.server.ServerLifecycleHooks;

import lombok.Setter;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Setter
public class PatternTestBehavior implements IItemUIFactory {

    public static final PatternTestBehavior INSTANCE = new PatternTestBehavior();

    private String ConflictAnalysisType = "";
    private int ConflictAnalysisCircuit = 0;

    private String Ae2PatternGeneratorType = "";
    private int Ae2PatternGeneratorCircuit = 0;
    private int Ae2PatternGeneratorScale = 1;

    private String Ae2PatternGeneratorInputsBlackKey = "";
    private String Ae2PatternGeneratorInputsWhiteKey = "";

    private String Ae2PatternGeneratorOutputsBlackKey = "";
    private String Ae2PatternGeneratorOutputsWhiteKey = "";

    @Override
    public ModularUI createUI(HeldItemUIFactory.HeldItemHolder heldItemHolder, Player player) {
        var containerPatternAnalysis = new WidgetGroup(8, 8, 160, 50)
                .addWidget(new ImageWidget(4, 4, 152, 42, GuiTextures.DISPLAY))
                .addWidget(new LabelWidget(6, 6, Component.translatable("gtlcore.gui.recipe_conflict_analysis").getString()))
                .addWidget(new AETextInputButtonWidget(82, 6, 72, 12)
                        .setText(ConflictAnalysisType)
                        .setOnConfirm(this::setConflictAnalysisType)
                        .setButtonTooltips(Component.translatable("tooltip.gtlcore.set_recipe_type")))
                .addWidget(new AETextInputButtonWidget(82, 20, 72, 12)
                        .setText(String.valueOf(ConflictAnalysisCircuit))
                        .setOnConfirm(s -> setConflictAnalysisCircuit(Integer.parseInt(s)))
                        .setButtonTooltips(Component.translatable("tooltip.gtlcore.set_programmed_circuit")))
                .addWidget(new ButtonWidget(6, 24, 64, 20,
                        new GuiTextureGroup(GuiTextures.BUTTON, new TextTexture(Component.translatable("gtlcore.gui.start_analysis").getString())),
                        clickData -> useAnalysisRecipesBaby(heldItemHolder))
                        .setHoverTooltips(Component.translatable("tooltip.gtlcore.current_recipe_type")
                                .append(Component.translatable("gtceu." + ConflictAnalysisType)).append(Component.translatable("gtlcore.tooltip.current_recipe_type_circuit", ConflictAnalysisCircuit))));

        var containerPatternGenerator = new WidgetGroup(8, 58, 160, 50)
                .addWidget(new ImageWidget(4, 4, 152, 42, GuiTextures.DISPLAY))
                .addWidget(new LabelWidget(6, 6, Component.translatable("gtlcore.gui.pattern_debug").getString()))
                .addWidget(new ButtonWidget(6, 24, 64, 20,
                        new GuiTextureGroup(GuiTextures.BUTTON, new TextTexture(Component.translatable("gtlcore.gui.get_pattern").getString())),
                        clickData -> useAe2PatternGenerator(heldItemHolder))
                        .setHoverTooltips(Component.translatable("tooltip.gtlcore.current_recipe_type")
                                .append(Component.translatable("gtceu." + Ae2PatternGeneratorType))
                                .append(Component.translatable("gtlcore.tooltip.current_recipe_type_circuit", Ae2PatternGeneratorCircuit))
                                .append(Component.translatable("gtlcore.tooltip.current_recipe_type_scale", Ae2PatternGeneratorScale))))
                .addWidget(new AETextInputButtonWidget(82, 6, 72, 12)
                        .setText(Ae2PatternGeneratorType)
                        .setOnConfirm(this::setAe2PatternGeneratorType)
                        .setButtonTooltips(Component.translatable("tooltip.gtlcore.set_recipe_type")))
                .addWidget(new AETextInputButtonWidget(82, 20, 72, 12)
                        .setText(String.valueOf(Ae2PatternGeneratorCircuit))
                        .setOnConfirm(s -> setAe2PatternGeneratorCircuit(Integer.parseInt(s)))
                        .setButtonTooltips(Component.translatable("tooltip.gtlcore.set_programmed_circuit")))
                .addWidget(new AETextInputButtonWidget(82, 34, 72, 12)
                        .setText(String.valueOf(Ae2PatternGeneratorScale))
                        .setOnConfirm(this::setAe2PatternGeneratorScale)
                        .setButtonTooltips(Component.translatable("tooltip.gtlcore.set_pattern_multiplier")))
                .addWidget(new AETextInputButtonWidget(82, 48, 72, 12)
                        .setText(String.valueOf(Ae2PatternGeneratorInputsWhiteKey))
                        .setOnConfirm(this::setAe2PatternGeneratorInputsWhiteKey)
                        .setButtonTooltips(Component.translatable("tooltip.gtlcore.input_whitelist_keywords")))
                .addWidget(new AETextInputButtonWidget(82, 62, 72, 12)
                        .setText(String.valueOf(Ae2PatternGeneratorInputsBlackKey))
                        .setOnConfirm(this::setAe2PatternGeneratorInputsBlackKey)
                        .setButtonTooltips(Component.translatable("tooltip.gtlcore.input_blacklist_keywords")))
                .addWidget(new AETextInputButtonWidget(82, 76, 72, 12)
                        .setText(String.valueOf(Ae2PatternGeneratorOutputsWhiteKey))
                        .setOnConfirm(this::setAe2PatternGeneratorOutputsWhiteKey)
                        .setButtonTooltips(Component.translatable("tooltip.gtlcore.output_whitelist_keywords")))
                .addWidget(new AETextInputButtonWidget(82, 90, 72, 12)
                        .setText(String.valueOf(Ae2PatternGeneratorOutputsBlackKey))
                        .setOnConfirm(this::setAe2PatternGeneratorOutputsBlackKey)
                        .setButtonTooltips(Component.translatable("tooltip.gtlcore.output_blacklist_keywords")));

        return new ModularUI(176, 124, heldItemHolder, player)
                .widget(containerPatternAnalysis)
                .widget(containerPatternGenerator)
                .background(GuiTextures.BACKGROUND);
    }

    private void setAe2PatternGeneratorScale(String s) {
        Ae2PatternGeneratorScale = Math.min(Integer.MAX_VALUE, Integer.parseInt(s));
    }

    public void useAe2PatternGenerator(HeldItemUIFactory.HeldItemHolder playerInventoryHolder) {
        if (playerInventoryHolder.getPlayer() instanceof ServerPlayer serverPlayer) {
            /*
             * 获取样板 入口
             */
            GTRecipeType recipeType = GTRecipeTypes.get("gtceu:" + Ae2PatternGeneratorType);
            GTRecipeManager gtRecipeManager = new GTRecipeManager();
            gtRecipeManager.filterRecipesByType(recipeType);
            gtRecipeManager.filterRecipesByCircuit(Ae2PatternGeneratorCircuit);
            if (!Ae2PatternGeneratorInputsWhiteKey.isEmpty()) gtRecipeManager.filterRecipesByInputsIdArray(Ae2PatternGeneratorInputsWhiteKey.split(" "), true);
            if (!Ae2PatternGeneratorInputsBlackKey.isEmpty()) gtRecipeManager.filterRecipesByInputsIdArray(Ae2PatternGeneratorInputsBlackKey.split(" "), false);
            if (!Ae2PatternGeneratorOutputsWhiteKey.isEmpty()) gtRecipeManager.filterRecipesByOutputsIdArray(Ae2PatternGeneratorOutputsWhiteKey.split(" "), true);
            if (!Ae2PatternGeneratorOutputsBlackKey.isEmpty()) gtRecipeManager.filterRecipesByOutputsIdArray(Ae2PatternGeneratorOutputsBlackKey.split(" "), false);
            List<GTRecipe> recipes = gtRecipeManager.getRecipes();
            for (GTRecipe recipe : recipes) {
                long inputEUt = RecipeHelper.getInputEUt(recipe);
                int recipeEUtTier = RecipeHelper.getRecipeEUtTier(recipe);
                String recipeTier = GTValues.VN[recipeEUtTier];

                Ae2GtmProcessingPattern ae2GtmProcessingPattern = Ae2GtmProcessingPattern.of(recipe, serverPlayer);
                ae2GtmProcessingPattern.setScale(Ae2PatternGeneratorScale, false);
                ae2GtmProcessingPattern.setDefaultFilter();
                ae2GtmProcessingPattern.setLore(Component.translatable("gui.gtlcore.machine_colon").append(Component.translatable("gtceu." + Ae2PatternGeneratorType).append(Component.translatable("gui.gtlcore.circuit_format", Ae2PatternGeneratorCircuit))));
                ae2GtmProcessingPattern.setLore(Component.translatable("gui.gtlcore.voltage_colon", recipeTier, inputEUt));
                ItemStack patternItemStack = ae2GtmProcessingPattern.getPatternItemStack();
                serverPlayer.kjs$give(patternItemStack);
            }
        }
    }

    public void useAnalysisRecipesBaby(HeldItemUIFactory.HeldItemHolder playerInventoryHolder) {
        if (playerInventoryHolder.getPlayer() instanceof ServerPlayer) {
            /*
             * 分析配方冲突 入口
             */
            analysisRecipesBaby(GTRecipeTypes.get("gtceu:" + ConflictAnalysisType), ConflictAnalysisCircuit);
        }
    }

    public void analysisRecipesBaby(GTRecipeType recipeType, int CIRCUIT) {
        // 筛选出指定机器的配方
        MinecraftServer currentServer = ServerLifecycleHooks.getCurrentServer();
        RecipeManager recipeManager = currentServer.getRecipeManager();
        Set<GTRecipe> recipes = new HashSet<>();
        for (Recipe<?> recipe : recipeManager.getRecipes()) {
            if (recipe instanceof GTRecipe && recipe.getType().equals(recipeType)) {
                recipes.add((GTRecipe) recipe);
            }
        }
        // 构造输入，调用类，对符合指定电路的配方分析
        ConflictAnalysisManager conflictAnalysisManager = new ConflictAnalysisManager(recipes.stream().toList());
        List<ConflictAnalysisResult> conflictAnalysisResults = conflictAnalysisManager.useFindConflictForAll(CIRCUIT);

        // 对获得的冲突样板集 遍历输出
        conflictAnalysisResults.forEach(ConflictAnalysisResult::exportToPrint);
        GTLCore.LOGGER.info("可能被错误合成配方数量{} / 所有此电路配方数{}", conflictAnalysisResults.toArray().length, recipes.toArray().length);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Item item, Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (player instanceof ServerPlayer serverPlayer) {
            HeldItemUIFactory.INSTANCE.openUI(serverPlayer, usedHand);
        }
        return new InteractionResultHolder<>(InteractionResult.SUCCESS, stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(Component.translatable("message.gtlcore.right_click_air_gui"), true);
        }
        return InteractionResult.SUCCESS;
    }
}
