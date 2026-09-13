package org.gtlcore.gtlcore.client.ae2.wireless;

import net.minecraft.client.gui.components.EditBox;

import javax.annotation.Nullable;

final class WirelessTerminalScreenSupport {

    private WirelessTerminalScreenSupport() {}

    static boolean isInside(double x, double y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }

    static String normalizedQuery(@Nullable EditBox field) {
        return field == null || field.getValue() == null ? "" : field.getValue().trim().toLowerCase(java.util.Locale.ROOT);
    }
}
