package org.gtlcore.gtlcore.client.preview;

import org.gtlcore.gtlcore.mixin.gtm.client.WorldPreviewAccessor;

import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.client.renderer.MultiblockInWorldPreviewRenderer;

import com.lowdragmc.lowdraglib.utils.TrackedDummyWorld;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

/** Main-thread placement transform and entity setup; no real world is written. */
public final class WorldPreview {

    private static BlockPos center;
    private static Direction front;
    private static Direction up;
    private static Object clientWorld;
    private static int layer = -1;
    private static int next;
    private static int duration;
    private static int remaining = -1;
    private static SparsePreviewShape shape;
    private static TrackedDummyWorld dummy;
    private static PreviewSnapshot preparing;
    private static PreviewMesh mesh;
    private static PreviewScenes.Key key;

    private WorldPreview() {}

    public static boolean show(BlockPos pos, MultiblockControllerMachine controller, int ticks) {
        if (!PreviewSettings.enabled() || !controller.getDefinition().isRenderWorldPreview()) return false;
        var shapes = controller.getDefinition().getMatchingShapes();
        if (shapes.isEmpty() || !PreviewShapeCache.isLarge(shapes.get(0))) return false;
        var geometry = PreviewShapeCache.sparse(shapes.get(0), controller.getDefinition());
        if (geometry.controller == null) return false;
        int newLayer = pos.equals(center) && clientWorld == Minecraft.getInstance().level ? layer + 1 : -1;
        if (newLayer >= geometry.height) newLayer = -1;
        MultiblockInWorldPreviewRenderer.cleanPreview();
        center = pos.immutable();
        front = controller.getFrontFacing();
        up = controller.getUpwardsFacing();
        clientWorld = Minecraft.getInstance().level;
        layer = newLayer;
        shape = geometry;
        duration = ticks;
        next = 0;
        key = new PreviewScenes.Key(PreviewScenes.generation(),
                new Anchor(clientWorld, center, front, up, controller.isFlipped()),
                controller.getDefinition().getId(), 0, layer, false);
        mesh = PreviewScenes.take(key);
        if (mesh == null) {
            dummy = new TrackedDummyWorld();
            preparing = new PreviewSnapshot();
        }
        return true;
    }

    public static boolean active() {
        return center != null;
    }

    public static void render(PoseStack pose, Camera camera, float partialTicks) {
        if (!active()) return;
        if (clientWorld != Minecraft.getInstance().level) {
            clear();
            return;
        }
        if (preparing != null) {
            try (var budget = PreviewFrameBudget.slice()) {
                while (next < shape.positions.length && budget.available()) {
                    int index = next++;
                    var local = BlockPos.of(shape.positions[index]);
                    if (layer >= 0 && local.getY() != layer) continue;
                    var offset = local.subtract(shape.controller).rotate(switch (front) {
                        case SOUTH -> Rotation.CLOCKWISE_180;
                        case EAST -> Rotation.COUNTERCLOCKWISE_90;
                        case WEST -> Rotation.CLOCKWISE_90;
                        default -> Rotation.NONE;
                    });
                    var rotation = switch (up) {
                        case EAST -> Rotation.CLOCKWISE_90;
                        case SOUTH -> Rotation.CLOCKWISE_180;
                        case WEST -> Rotation.COUNTERCLOCKWISE_90;
                        default -> Rotation.NONE;
                    };
                    var pos = center.offset(WorldPreviewAccessor.gtlcore$rotate(offset, front, rotation));
                    var info = SparsePreviewShape.copy(shape.blocks[index]);
                    BlockState state = info.getBlockState();
                    if (state.getBlock() instanceof MetaMachineBlock machine) {
                        var rotationState = machine.getRotationState();
                        if (rotationState != RotationState.NONE) {
                            Direction face = state.getValue(rotationState.property);
                            if (face.getAxis() != Direction.Axis.Y) {
                                face = switch (front) {
                                    case SOUTH -> face.getOpposite();
                                    case WEST -> face.getCounterClockWise();
                                    case EAST -> face.getClockWise();
                                    default -> front;
                                };
                            }
                            if (rotationState.test(face)) state = state.setValue(rotationState.property, face);
                        }
                    }
                    info.setBlockState(state);
                    dummy.addBlock(pos, info);
                    var entity = info.hasBlockEntity() ? info.getBlockEntity(dummy, pos) : null;
                    if (entity != null) dummy.setInnerBlockEntity(entity);
                    if (!local.equals(shape.controller)) preparing.add(pos, state, entity);
                }
            }
            if (next == shape.positions.length) {
                mesh = new PreviewMesh(preparing, true);
                preparing = null;
                dummy = null;
            }
        }
        if (mesh == null) return;
        mesh.update();
        if (mesh.failed()) {
            Minecraft.getInstance().gui.setOverlayMessage(net.minecraft.network.chat.Component.translatable(
                    "gui.gtlcore.preview.failed"), false);
            clear();
            return;
        }
        if (remaining < 0 && mesh.complete()) remaining = duration;
        pose.pushPose();
        var eye = camera.getPosition();
        pose.translate(-eye.x, -eye.y, -eye.z);
        float fog = RenderSystem.getShaderFogStart();
        try {
            RenderSystem.setShaderFogStart(Float.MAX_VALUE);
            mesh.draw(pose.last().pose(), partialTicks, pose);
        } finally {
            RenderSystem.setShaderFogStart(fog);
            pose.popPose();
        }
    }

    public static void tick() {
        if (remaining > 0 && --remaining == 0) clear();
    }

    public static void remove(BlockPos pos) {
        if (pos.equals(center)) clear();
    }

    public static void clear() {
        if (mesh != null) PreviewScenes.release(key, mesh);
        mesh = null;
        key = null;
        shape = null;
        dummy = null;
        preparing = null;
        center = null;
        clientWorld = null;
        remaining = -1;
        layer = -1;
    }

    private record Anchor(Object level, BlockPos pos, Direction front, Direction up, boolean flipped) {}
}
