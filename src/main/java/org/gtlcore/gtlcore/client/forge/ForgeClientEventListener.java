package org.gtlcore.gtlcore.client.forge;

import org.gtlcore.gtlcore.GTLCore;
import org.gtlcore.gtlcore.client.ae2.JeiTerminalSearchTarget;
import org.gtlcore.gtlcore.common.item.StructureWriteBehavior;
import org.gtlcore.gtlcore.integration.ae2.wireless.JeiWirelessTerminalOrderPackets;
import org.gtlcore.gtlcore.integration.jei.JeiMeInventoryTooltip;

import com.lowdragmc.lowdraglib.LDLib;
import com.lowdragmc.lowdraglib.client.utils.RenderBufferUtils;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = GTLCore.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
@OnlyIn(Dist.CLIENT)
public class ForgeClientEventListener {

    private static final int JEI_TERMINAL_SEARCH_KEY = InputConstants.KEY_F;
    private static final int JEI_WIRELESS_ORDER_MOUSE_BUTTON = GLFW.GLFW_MOUSE_BUTTON_MIDDLE;

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onJeiTerminalSearchKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (event.getKeyCode() != JEI_TERMINAL_SEARCH_KEY ||
                !(event.getScreen() instanceof JeiTerminalSearchTarget terminalSearchTarget) ||
                !LDLib.isJeiLoaded()) {
            return;
        }
        JeiMeInventoryTooltip.getHoveredIngredientName().ifPresent(searchText -> {
            terminalSearchTarget.gtlcore$setJeiSearchText(searchText);
            event.setCanceled(true);
        });
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onJeiWirelessOrderMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.isCanceled() || event.getButton() != JEI_WIRELESS_ORDER_MOUSE_BUTTON || !LDLib.isJeiLoaded()) {
            return;
        }
        JeiMeInventoryTooltip.getHoveredIngredientKey().ifPresent(key -> {
            JeiWirelessTerminalOrderPackets.sendRequest(key);
            event.setCanceled(true);
        });
    }

    @SubscribeEvent
    public static void onRenderWorldLast(RenderLevelStageEvent event) {
        RenderLevelStageEvent.Stage stage = event.getStage();
        if (stage == RenderLevelStageEvent.Stage.AFTER_TRIPWIRE_BLOCKS) {
            Minecraft mc = Minecraft.getInstance();
            ClientLevel level = mc.level;
            LocalPlayer player = mc.player;
            if (level == null || player == null) return;
            PoseStack poseStack = event.getPoseStack();
            Camera camera = event.getCamera();
            ItemStack held = player.getMainHandItem();
            if (StructureWriteBehavior.isItemStructureWriter(held)) {
                BlockPos[] poses = StructureWriteBehavior.getPos(held);
                if (poses == null) return;
                Vec3 pos = camera.getPosition();

                poseStack.pushPose();
                poseStack.translate(-pos.x, -pos.y, -pos.z);

                RenderSystem.disableDepthTest();
                RenderSystem.enableBlend();
                RenderSystem.disableCull();
                RenderSystem.blendFunc(
                        GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
                Tesselator tesselator = Tesselator.getInstance();
                BufferBuilder buffer = tesselator.getBuilder();

                buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
                RenderSystem.setShader(GameRenderer::getPositionColorShader);

                RenderBufferUtils.renderCubeFace(
                        poseStack,
                        buffer,
                        poses[0].getX(),
                        poses[0].getY(),
                        poses[0].getZ(),
                        poses[1].getX() + 1,
                        poses[1].getY() + 1,
                        poses[1].getZ() + 1,
                        0.2f,
                        0.2f,
                        1f,
                        0.25f,
                        true);

                tesselator.end();

                buffer.begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
                RenderSystem.setShader(GameRenderer::getRendertypeLinesShader);
                RenderSystem.lineWidth(3);

                RenderBufferUtils.drawCubeFrame(
                        poseStack,
                        buffer,
                        poses[0].getX(),
                        poses[0].getY(),
                        poses[0].getZ(),
                        poses[1].getX() + 1,
                        poses[1].getY() + 1,
                        poses[1].getZ() + 1,
                        0.0f,
                        0.0f,
                        1f,
                        0.5f);

                tesselator.end();

                RenderSystem.enableCull();

                RenderSystem.disableBlend();
                RenderSystem.enableDepthTest();
                poseStack.popPose();
            }
        }
    }
}
