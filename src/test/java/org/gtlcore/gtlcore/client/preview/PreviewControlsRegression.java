package org.gtlcore.gtlcore.client.preview;

import org.joml.Vector3f;

final class PreviewControlsRegression {

    private static int assertions;

    static int run() {
        var height = new PreviewHeight();
        check(height.aboveBottom(50, false) == -1, "empty preview has no fabricated height");
        check(!height.dimensions(false).known(), "empty preview has no fabricated dimensions");
        height.include(0, 50, 0, false);
        check(height.aboveBottom(50, false) == 0, "controller on bottom is zero blocks above it");
        height.include(0, 100, 0, false);
        check(height.aboveBottom(100, false) == 50, "height is a coordinate difference, not a layer number");
        height.include(-10, 35, 20, true);
        check(height.aboveBottom(100, false) == 50, "hidden module does not lower host bottom");
        check(height.aboveBottom(100, true) == 65, "enabled module contributes to combined bottom");
        check(height.dimensions(false).equals(new PreviewHeight.Dimensions(1, 1, 51)), "inclusive host dimensions");
        check(height.dimensions(true).equals(new PreviewHeight.Dimensions(11, 21, 66)), "modules expand occupied bounds");
        height.include(0, -70, 0, false);
        check(height.aboveBottom(100, false) == 170, "negative coordinates are supported");
        check(height.aboveBottom(-71, false) == -1, "invalid controller coordinate has no misleading negative height");
        var overflow = new PreviewHeight();
        overflow.include(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, false);
        overflow.include(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, false);
        check(overflow.aboveBottom(Integer.MAX_VALUE, true) == -1, "coordinate overflow is rejected");
        check(overflow.dimensions(true).equals(new PreviewHeight.Dimensions(4294967296L, 4294967296L, 4294967296L)),
                "dimensions do not overflow signed integers");
        for (int offset = -300; offset <= 300; offset++) {
            var translated = new PreviewHeight();
            translated.include(offset, offset, offset, false);
            translated.include(offset + 109, offset + 89, offset + 109, false);
            check(translated.aboveBottom(offset + 23, false) == 23, "translation invariant height");
            check(translated.dimensions(false).equals(new PreviewHeight.Dimensions(110, 110, 90)),
                    "translation invariant width/depth/height including end blocks");
        }
        pan();
        return assertions;
    }

    private static void pan() {
        var target = new Vector3f(31, 17, -12);
        var worldUp = new Vector3f(0, 1, 0);
        for (int degrees = 0; degrees < 360; degrees += 30) for (float pitch : new float[] { -89.9f, -40, 0, 40, 89.9f }) {
            double yaw = Math.toRadians(degrees), elevation = Math.toRadians(pitch);
            var eye = new Vector3f((float) (Math.cos(yaw) * Math.cos(elevation)),
                    (float) Math.sin(elevation), (float) (Math.sin(yaw) * Math.cos(elevation))).mul(30).add(target);
            var originalEye = new Vector3f(eye);
            var forward = new Vector3f(target).sub(eye).normalize();
            var right = new Vector3f(forward).cross(worldUp).normalize();
            var up = new Vector3f(right).cross(forward).normalize();
            var offset = PreviewPan.offset(eye, target, worldUp, 12, 7, 0.5);
            check(Math.abs(offset.dot(right) + 6) < 0.001, "drag follows horizontal screen reference");
            check(Math.abs(offset.dot(up) - 3.5) < 0.001, "drag follows vertical screen reference");
            check(Math.abs(offset.dot(forward)) < 0.001, "pan preserves camera depth");
            check(eye.equals(originalEye) && target.equals(new Vector3f(31, 17, -12)), "inputs not mutated");
            check(offset.add(PreviewPan.offset(eye, target, worldUp, -12, -7, 0.5)).length() < 0.001,
                    "opposite drags cancel");
        }
        var eye = new Vector3f(0, 0, 10);
        check(PreviewPan.offset(eye, new Vector3f(), worldUp, 0, 0, 1).length() == 0, "zero drag");
        check(PreviewPan.offset(eye, new Vector3f(), worldUp, Double.NaN, 1, 1).length() == 0, "invalid drag");
        check(PreviewPan.offset(eye, eye, worldUp, 12, 7, 1).length() == 0, "degenerate view");
        check(PreviewPan.offset(eye, new Vector3f(), new Vector3f(0, 0, 1), 12, 7, 1).length() == 0,
                "degenerate reference plane");
    }

    private static void check(boolean valid, String message) {
        if (!valid) throw new AssertionError(message);
        assertions++;
    }
}
