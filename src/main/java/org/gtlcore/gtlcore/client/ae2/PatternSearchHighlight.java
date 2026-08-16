package org.gtlcore.gtlcore.client.ae2;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.utils.GradientUtil;

import net.minecraft.client.gui.GuiGraphics;

public final class PatternSearchHighlight {

    private static final int SLOT_SIZE = 16;
    private static final int BORDER_THICKNESS = 2;
    private static final int BORDER_OFFSET = 1;
    private static final int SEGMENTS_PER_SIDE = 4;
    private static final int TOTAL_SEGMENTS = SEGMENTS_PER_SIDE * 4;
    private static final int ANIMATION_TIME_MASK = (1 << 20) - 1;
    private static final int OPAQUE_ALPHA = 0xFF000000;
    private static final float FULL_HUE_CYCLE = 360.0F;
    private static final float HUE_STEP = FULL_HUE_CYCLE / TOTAL_SEGMENTS;
    private static final float ANIMATION_SPEED = 4.0F;
    private static final float SATURATION_PERCENT = 100.0F;
    private static final float LIGHTNESS_PERCENT = 60.0F;

    private PatternSearchHighlight() {}

    public static void drawBorder(GuiGraphics graphics, int x, int y) {
        int left = x - BORDER_OFFSET;
        int top = y - BORDER_OFFSET;
        int right = x + SLOT_SIZE + BORDER_OFFSET;
        int bottom = y + SLOT_SIZE + BORDER_OFFSET;
        int outerSize = right - left;
        for (int segment = 0; segment < SEGMENTS_PER_SIDE; segment++) {
            int segmentStart = segment * outerSize / SEGMENTS_PER_SIDE;
            int segmentEnd = (segment + 1) * outerSize / SEGMENTS_PER_SIDE;
            graphics.fill(left + segmentStart, top, left + segmentEnd, top + BORDER_THICKNESS,
                    rainbowColor(segment));
            graphics.fill(right - BORDER_THICKNESS, top + segmentStart, right, top + segmentEnd,
                    rainbowColor(segment + SEGMENTS_PER_SIDE));
            graphics.fill(right - segmentEnd, bottom - BORDER_THICKNESS, right - segmentStart, bottom,
                    rainbowColor(segment + SEGMENTS_PER_SIDE * 2));
            graphics.fill(left, bottom - segmentEnd, left + BORDER_THICKNESS, bottom - segmentStart,
                    rainbowColor(segment + SEGMENTS_PER_SIDE * 3));
        }
    }

    private static int rainbowColor(int segment) {
        float hue = (GTValues.CLIENT_TIME & ANIMATION_TIME_MASK) * ANIMATION_SPEED + segment * HUE_STEP;
        return OPAQUE_ALPHA | GradientUtil.toRGB(hue, SATURATION_PERCENT, LIGHTNESS_PERCENT);
    }
}
