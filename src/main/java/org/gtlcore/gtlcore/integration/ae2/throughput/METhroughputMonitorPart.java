package org.gtlcore.gtlcore.integration.ae2.throughput;

import org.gtlcore.gtlcore.GTLCore;
import org.gtlcore.gtlcore.integration.ae2.MEFluidUnits;
import org.gtlcore.gtlcore.integration.ae2.wireless.GTLWirelessAeContent;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.orientation.BlockOrientation;
import appeng.api.parts.IPartItem;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AmountFormat;
import appeng.api.storage.MEStorage;
import appeng.api.util.AEColor;
import appeng.client.render.BlockEntityRenderHelper;
import appeng.hooks.ticking.TickHandler;
import appeng.parts.reporting.StorageMonitorPart;
import appeng.util.Platform;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

import java.util.Locale;

public class METhroughputMonitorPart extends StorageMonitorPart implements IGridTickable, ThroughputMonitorStorageTracker.Listener {

    private static final String TAG_THROUGHPUT = "throughput";
    private static final String TAG_LAST_VALUE = "lastValue";
    private static final String TAG_ROUTINE = "routine";

    private static final int MIN_TICK_RATE = 20;
    private static final int MAX_TICK_RATE = 100;
    private static final int TICK_SAMPLE_WINDOW_SECONDS = 10;
    private static final int SECOND_SAMPLE_WINDOW_SECONDS = 20;
    private static final int SECONDS_PER_MINUTE = 60;
    private static final int TEN_MINUTE_SAMPLE_WINDOW_SECONDS = 10 * SECONDS_PER_MINUTE;
    private static final double WHOLE_AMOUNT_THRESHOLD = 10.0D;
    private static final String SMALL_AMOUNT_FORMAT = "%.2f";

    private static final float MONITOR_FRONT_OFFSET = 0.5F;
    private static final float MONITOR_FACE_OFFSET = 0.05F;
    private static final float THROUGHPUT_TEXT_Y_OFFSET = -0.2F;
    private static final float TEXT_Z_OFFSET = 0.02F;
    private static final float TEXT_PIXEL_SCALE = 1.0F / 62.0F;
    private static final float TEXT_HALF_SCALE = 0.5F;
    private static final float TEXT_DEPTH = 0.5F;
    private static final float TEXT_CENTER = 0.5F;
    private static final float TEXT_ICON_Y_OFFSET = -0.6F;
    private static final int TREND_TEXTURE_SIZE = 5;
    private static final int TREND_TEXT_WIDTH = TREND_TEXTURE_SIZE;
    private static final float TREND_QUAD_SIZE = TREND_TEXTURE_SIZE * 2.0F;
    private static final int FULL_ALPHA_MASK = 0xFF000000;
    private static final int POSITIVE_COLOR = AEColor.GREEN.mediumVariant | FULL_ALPHA_MASK;
    private static final int NEGATIVE_COLOR = AEColor.RED.mediumVariant | FULL_ALPHA_MASK;
    private static final int TEXT_BACKGROUND_COLOR = 0;
    private static final int TEXTURE_COLOR = 255;

    private static final ResourceLocation POSITIVE_TEXTURE = GTLCore.id("textures/part/throughput_monitor_up.png");
    private static final ResourceLocation NEGATIVE_TEXTURE = GTLCore.id("textures/part/throughput_monitor_down.png");

    private static final WorkRoutine DEFAULT_ROUTINE = WorkRoutine.SECOND;

    private final ThroughputCache cache = new ThroughputCache();
    private double lastReportedValue = 0.0D;
    private WorkRoutine workRoutine = DEFAULT_ROUTINE;
    private WorkRoutine lastWorkRoutine = DEFAULT_ROUTINE;
    private MEStorage trackedStorage;
    private long trackedStorageTopologyVersion = Long.MIN_VALUE;

    public METhroughputMonitorPart(IPartItem<?> partItem) {
        super(partItem);
        getMainNode().addService(IGridTickable.class, this);
    }

    public MutableComponent getThroughputText() {
        AEKey displayed = getDisplayed();
        if (displayed == null) {
            return Component.empty();
        }

        String sign = lastReportedValue > 0.0D ? "+" : lastReportedValue == 0.0D ? "" : "-";
        String valueText = formatThroughputAmount(displayed, Math.abs(lastReportedValue));

        return Component.translatable(workRoutine.translationKey, sign, valueText);
    }

    public static String formatThroughputAmount(AEKey key, double absoluteValue) {
        if (key instanceof AEFluidKey) {
            return MEFluidUnits.formatDisplayAmount(absoluteValue);
        }
        return absoluteValue > WHOLE_AMOUNT_THRESHOLD || absoluteValue == 0.0D ?
                key.formatAmount(Math.round(absoluteValue), AmountFormat.SLOT) :
                String.format(Locale.ROOT, SMALL_AMOUNT_FORMAT, absoluteValue);
    }

    public double getThroughput() {
        return lastReportedValue;
    }

    public double getThroughputPerSecond() {
        return lastReportedValue / workRoutine.displayTicks * ThroughputCache.TICKS_PER_SECOND;
    }

    @Override
    public void writeToNBT(CompoundTag data) {
        super.writeToNBT(data);
        data.putDouble(TAG_THROUGHPUT, lastReportedValue);
        data.putInt(TAG_ROUTINE, workRoutine.ordinal());
    }

    @Override
    public void readFromNBT(CompoundTag data) {
        super.readFromNBT(data);
        lastReportedValue = data.getDouble(TAG_THROUGHPUT);
        workRoutine = readRoutine(data);
        lastWorkRoutine = workRoutine;
    }

    @Override
    public void writeToStream(FriendlyByteBuf data) {
        super.writeToStream(data);
        data.writeDouble(lastReportedValue);
        data.writeEnum(workRoutine);
    }

    @Override
    public boolean readFromStream(FriendlyByteBuf data) {
        boolean needRedraw = super.readFromStream(data);
        double previousValue = lastReportedValue;
        WorkRoutine previousRoutine = workRoutine;
        lastReportedValue = data.readDouble();
        workRoutine = data.readEnum(WorkRoutine.class);
        lastWorkRoutine = workRoutine;
        return needRedraw || Double.compare(previousValue, lastReportedValue) != 0 || previousRoutine != workRoutine;
    }

    @Override
    public void writeVisualStateToNBT(CompoundTag data) {
        super.writeVisualStateToNBT(data);
        data.putDouble(TAG_LAST_VALUE, lastReportedValue);
        data.putInt(TAG_ROUTINE, workRoutine.ordinal());
    }

    @Override
    public void readVisualStateFromNBT(CompoundTag data) {
        super.readVisualStateFromNBT(data);
        lastReportedValue = data.getDouble(TAG_LAST_VALUE);
        workRoutine = readRoutine(data);
        lastWorkRoutine = workRoutine;
    }

    @Override
    public boolean onPartActivate(Player player, InteractionHand hand, Vec3 pos) {
        ItemStack heldItem = player.getItemInHand(hand);
        if (heldItem.is(GTLWirelessAeContent.THROUGHPUT_MONITOR_CONFIGURATOR.get())) {
            if (isClientSide()) {
                return true;
            }

            if (!getMainNode().isActive() || !Platform.hasPermissions(getHost().getLocation(), player)) {
                return false;
            }

            cycleWorkRoutine();
            return true;
        }

        return super.onPartActivate(player, hand, pos);
    }

    @Override
    protected void configureWatchers() {
        super.configureWatchers();

        if (getDisplayed() != null) {
            startState();
            registerStorageTracker();
            getMainNode().ifPresent((grid, node) -> grid.getTickManager().wakeDevice(node));
        } else {
            resetState();
            unregisterStorageTracker();
            getMainNode().ifPresent((grid, node) -> grid.getTickManager().sleepDevice(node));
        }
    }

    @Override
    protected void onMainNodeStateChanged(IGridNodeListener.State reason) {
        registerStorageTracker();
        getMainNode().ifPresent((grid, node) -> grid.getTickManager().wakeDevice(node));
        super.onMainNodeStateChanged(reason);
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(MIN_TICK_RATE, MAX_TICK_RATE, !isActive() || getDisplayed() == null, true);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (!getMainNode().isActive() || getDisplayed() == null) {
            resetState();
            unregisterStorageTracker();
            return TickRateModulation.SLEEP;
        }

        refreshVisibleStorageLinks();
        long currentTick = TickHandler.instance().getCurrentTick();
        ThroughputCache.ThroughputSample sample = cache.sample(workRoutine.sampleWindowSeconds, currentTick);
        if (workRoutine != lastWorkRoutine) {
            lastReportedValue = 0.0D;
        } else if (cache.hasRecordedChanges()) {
            lastReportedValue = displayValue(sample, workRoutine.displayTicks);
        }

        lastWorkRoutine = workRoutine;
        getHost().markForUpdate();

        return TickRateModulation.SLOWER;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void renderDynamic(float partialTicks, PoseStack poseStack, MultiBufferSource buffers, int combinedLight, int combinedOverlay) {
        super.renderDynamic(partialTicks, poseStack, buffers, combinedLight, combinedOverlay);

        if (!isActive() || getDisplayed() == null) {
            return;
        }

        FormattedCharSequence text = getThroughputText().getVisualOrderText();
        Font font = Minecraft.getInstance().font;
        ResourceLocation trendTexture = getTrendTexture();
        int textWidth = font.width(text) + (trendTexture == null ? 0 : TREND_TEXT_WIDTH);
        int textColor = getTrendColor();

        poseStack.pushPose();
        poseStack.translate(TEXT_CENTER, TEXT_CENTER, TEXT_CENTER);
        BlockEntityRenderHelper.rotateToFace(poseStack, BlockOrientation.get(getSide(), getSpin()));
        poseStack.translate(0.0D, MONITOR_FACE_OFFSET, MONITOR_FRONT_OFFSET);
        poseStack.translate(0.0D, THROUGHPUT_TEXT_Y_OFFSET, 0.0D);
        renderText(poseStack, buffers, text, textWidth, textColor);
        if (trendTexture != null) {
            renderTrendTexture(poseStack, buffers, trendTexture, textWidth);
        }
        poseStack.popPose();
    }

    private static WorkRoutine readRoutine(CompoundTag data) {
        return data.contains(TAG_ROUTINE) ? WorkRoutine.fromOrdinal(data.getInt(TAG_ROUTINE)) : DEFAULT_ROUTINE;
    }

    private void cycleWorkRoutine() {
        workRoutine = workRoutine.next();
        getMainNode().ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
        getHost().markForSave();
        getHost().markForUpdate();
    }

    private void resetState() {
        cache.clear();
        lastReportedValue = 0.0D;
        lastWorkRoutine = workRoutine;
    }

    private void startState() {
        long currentTick = TickHandler.instance().getCurrentTick();
        cache.reset(currentTick);
        lastWorkRoutine = workRoutine;
    }

    private void registerStorageTracker() {
        if (isClientSide()) {
            return;
        }

        if (getDisplayed() == null) {
            return;
        }

        getMainNode().ifPresent((grid, node) -> {
            MEStorage storage = grid.getStorageService().getInventory();
            if (storage != trackedStorage) {
                unregisterStorageTracker();
                trackedStorage = storage;
                ThroughputMonitorStorageTracker.register(storage, this);
            }
            refreshVisibleStorageLinks();
        });
    }

    private void refreshVisibleStorageLinks() {
        if (trackedStorage == null) {
            return;
        }

        long topologyVersion = ThroughputMonitorStorageTracker.topologyVersion(trackedStorage);
        if (topologyVersion == trackedStorageTopologyVersion) {
            return;
        }

        ThroughputMonitorStorageTracker.refreshVisibleStorageLinks(trackedStorage);
        trackedStorageTopologyVersion = topologyVersion;
    }

    private void unregisterStorageTracker() {
        ThroughputMonitorStorageTracker.unregister(this);
        trackedStorage = null;
        trackedStorageTopologyVersion = Long.MIN_VALUE;
    }

    @Override
    public AEKey getTrackedKey() {
        return getDisplayed();
    }

    @Override
    public void recordThroughput(long amountDelta, long tick) {
        cache.recordChange(amountDelta, tick);
    }

    private static double displayValue(ThroughputCache.ThroughputSample sample, int displayTicks) {
        double inserted = sample.insertedPerTick() * displayTicks;
        double extracted = sample.extractedPerTick() * displayTicks;
        if (inserted == 0.0D && extracted == 0.0D) {
            return 0.0D;
        }
        return inserted >= extracted ? inserted : -extracted;
    }

    @OnlyIn(Dist.CLIENT)
    private ResourceLocation getTrendTexture() {
        if (lastReportedValue > 0.0D) {
            return POSITIVE_TEXTURE;
        }
        if (lastReportedValue < 0.0D) {
            return NEGATIVE_TEXTURE;
        }
        return null;
    }

    @OnlyIn(Dist.CLIENT)
    private int getTrendColor() {
        if (lastReportedValue > 0.0D) {
            return POSITIVE_COLOR;
        }
        if (lastReportedValue < 0.0D) {
            return NEGATIVE_COLOR;
        }
        return getColor().contrastTextColor | FULL_ALPHA_MASK;
    }

    @OnlyIn(Dist.CLIENT)
    private static void renderText(
                                   PoseStack poseStack,
                                   MultiBufferSource buffers,
                                   FormattedCharSequence text,
                                   int textWidth,
                                   int textColor) {
        Font font = Minecraft.getInstance().font;
        poseStack.pushPose();
        poseStack.translate(0.0F, 0.0F, TEXT_Z_OFFSET);
        poseStack.scale(TEXT_PIXEL_SCALE, -TEXT_PIXEL_SCALE, TEXT_PIXEL_SCALE);
        poseStack.scale(TEXT_HALF_SCALE, TEXT_HALF_SCALE, 0.0F);
        poseStack.translate(-TEXT_CENTER * textWidth, -TEXT_CENTER * font.lineHeight, TEXT_DEPTH);
        font.drawInBatch(
                text,
                0.0F,
                0.0F,
                textColor,
                false,
                poseStack.last().pose(),
                buffers,
                Font.DisplayMode.NORMAL,
                TEXT_BACKGROUND_COLOR,
                LightTexture.FULL_BRIGHT);
        poseStack.popPose();
    }

    @OnlyIn(Dist.CLIENT)
    private static void renderTrendTexture(
                                           PoseStack poseStack,
                                           MultiBufferSource buffers,
                                           ResourceLocation texture,
                                           int textWidth) {
        Font font = Minecraft.getInstance().font;
        poseStack.pushPose();
        poseStack.translate(0.0F, 0.0F, TEXT_Z_OFFSET);
        poseStack.scale(TEXT_PIXEL_SCALE, -TEXT_PIXEL_SCALE, TEXT_PIXEL_SCALE);
        poseStack.scale(TEXT_HALF_SCALE, TEXT_HALF_SCALE, 0.0F);
        poseStack.translate(TEXT_CENTER * textWidth, TEXT_ICON_Y_OFFSET * font.lineHeight, TEXT_DEPTH);

        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutout(texture));
        consumer.vertex(matrix, 0.0F, TREND_QUAD_SIZE, 0.0F)
                .color(TEXTURE_COLOR, TEXTURE_COLOR, TEXTURE_COLOR, TEXTURE_COLOR)
                .uv(0.0F, 1.0F)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(poseStack.last().normal(), 0.0F, 1.0F, 0.0F)
                .endVertex();
        consumer.vertex(matrix, TREND_QUAD_SIZE, TREND_QUAD_SIZE, 0.0F)
                .color(TEXTURE_COLOR, TEXTURE_COLOR, TEXTURE_COLOR, TEXTURE_COLOR)
                .uv(1.0F, 1.0F)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(poseStack.last().normal(), 0.0F, 1.0F, 0.0F)
                .endVertex();
        consumer.vertex(matrix, TREND_QUAD_SIZE, 0.0F, 0.0F)
                .color(TEXTURE_COLOR, TEXTURE_COLOR, TEXTURE_COLOR, TEXTURE_COLOR)
                .uv(1.0F, 0.0F)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(poseStack.last().normal(), 0.0F, 1.0F, 0.0F)
                .endVertex();
        consumer.vertex(matrix, 0.0F, 0.0F, 0.0F)
                .color(TEXTURE_COLOR, TEXTURE_COLOR, TEXTURE_COLOR, TEXTURE_COLOR)
                .uv(0.0F, 0.0F)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(poseStack.last().normal(), 0.0F, 1.0F, 0.0F)
                .endVertex();
        poseStack.popPose();
    }

    private enum WorkRoutine {

        TICK(1, TICK_SAMPLE_WINDOW_SECONDS, "gui.gtlcore.throughput_monitor_value_tick"),
        SECOND(ThroughputCache.TICKS_PER_SECOND, SECOND_SAMPLE_WINDOW_SECONDS, "gui.gtlcore.throughput_monitor_value"),
        MINUTE(ThroughputCache.TICKS_PER_SECOND * SECONDS_PER_MINUTE, SECONDS_PER_MINUTE, "gui.gtlcore.throughput_monitor_value_minute"),
        TEN_MINUTE(12000, TEN_MINUTE_SAMPLE_WINDOW_SECONDS, "gui.gtlcore.throughput_monitor_value_ten_minutes");

        private static final WorkRoutine[] ROUTINES = values();

        private final int displayTicks;
        private final int sampleWindowSeconds;
        private final String translationKey;

        WorkRoutine(int displayTicks, int sampleWindowSeconds, String translationKey) {
            this.displayTicks = displayTicks;
            this.sampleWindowSeconds = sampleWindowSeconds;
            this.translationKey = translationKey;
        }

        private WorkRoutine next() {
            return ROUTINES[(ordinal() + 1) % ROUTINES.length];
        }

        private WorkRoutineView view() {
            return new WorkRoutineView(name(), displayTicks, sampleWindowSeconds);
        }

        private static WorkRoutine fromOrdinal(int ordinal) {
            return ordinal >= 0 && ordinal < ROUTINES.length ? ROUTINES[ordinal] : DEFAULT_ROUTINE;
        }
    }

    record WorkRoutineView(String name, int displayTicks, int sampleWindowSeconds) {}
}
