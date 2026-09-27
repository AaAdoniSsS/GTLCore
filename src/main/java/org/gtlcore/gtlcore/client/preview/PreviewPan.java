package org.gtlcore.gtlcore.client.preview;

import org.joml.Vector3f;
import org.joml.Vector3fc;

/** Converts a screen-space drag to a translation in the camera's reference plane. */
public final class PreviewPan {

    private PreviewPan() {}

    public static Vector3f offset(Vector3fc eye, Vector3fc target, Vector3fc worldUp,
                                  double dx, double dy, double unitsPerPixel) {
        if (!Double.isFinite(dx) || !Double.isFinite(dy) || !Double.isFinite(unitsPerPixel) || unitsPerPixel <= 0) return new Vector3f();
        var forward = new Vector3f(target).sub(eye);
        if (!forward.isFinite() || forward.lengthSquared() < 1e-12f) return new Vector3f();
        forward.normalize();
        var right = new Vector3f(forward).cross(worldUp);
        if (!right.isFinite() || right.lengthSquared() < 1e-12f) return new Vector3f();
        right.normalize();
        var up = new Vector3f(right).cross(forward).normalize();
        var delta = right.mul((float) (-dx * unitsPerPixel)).fma((float) (dy * unitsPerPixel), up);
        return delta.isFinite() ? delta : new Vector3f();
    }
}
