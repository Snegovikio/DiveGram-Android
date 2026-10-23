package org.telegram.ui.Components;

import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.RectF;

import org.telegram.messenger.SharedConfig;

public final class DiveGramAvatarShapes {

    public static final String[] NAMES = {
            "Circle", "Square", "Slanted", "Arch", "Semicircle",
            "Oval", "Pill", "Triangle", "Arrow", "Fan",
            "Diamond", "Clamshell", "Pentagon", "Gem", "Very sunny",
            "Sunny", "4-sided cookie", "6-sided cookie", "7-sided cookie", "9-sided cookie",
            "12-sided cookie", "4-leaf clover", "8-leaf clover", "Burst", "Soft burst",
            "Boom", "Soft boom", "Flower", "Puffy", "Puffy diamond",
            "Ghost-ish", "Pixel circle", "Pixel triangle", "Bun", "Heart", "Plane"
    };

    public static final int COUNT = NAMES.length;

    private DiveGramAvatarShapes() {
    }

    public static int getSelected() {
        return SharedConfig.dgAvatarShape;
    }

    public static void setSelected(int value) {
        SharedConfig.setDgAvatarShape(value);
    }

    public static void buildPath(Path path, int shape, RectF r) {
        path.rewind();
        if (shape < 0 || shape >= COUNT) {
            shape = 0;
        }
        switch (shape) {
            case 0:
                path.addOval(r, Path.Direction.CW);
                break;
            case 1:
                path.addRoundRect(r, min(r) * 0.22f, min(r) * 0.22f, Path.Direction.CW);
                break;
            case 2:
                addRoundedPolygon(path, r, new float[]{
                        0.18f, 0.02f, 0.82f, 0.06f, 0.98f, 0.82f,
                        0.82f, 0.98f, 0.18f, 0.94f, 0.02f, 0.18f
                }, 0.10f);
                break;
            case 3:
                addArch(path, r);
                break;
            case 4:
                addSemicircle(path, r);
                break;
            case 5:
                addRotatedOval(path, r, -28f, 0.72f);
                break;
            case 6:
                addRotatedOval(path, r, 0f, 0.92f);
                break;
            case 7:
                addRoundedPolygon(path, r, new float[]{0.5f, 0.03f, 0.97f, 0.9f, 0.03f, 0.9f}, 0.10f);
                break;
            case 8:
                addRoundedPolygon(path, r, new float[]{
                        0.5f, 0.02f, 0.96f, 0.92f, 0.5f, 0.74f, 0.04f, 0.92f
                }, 0.08f);
                break;
            case 9:
                addRoundedPolygon(path, r, new float[]{
                        0.08f, 0.98f, 0.08f, 0.42f, 0.86f, 0.06f, 0.98f, 0.9f
                }, 0.12f);
                break;
            case 10:
                addRoundedPolygon(path, r, new float[]{
                        0.5f, 0.01f, 0.98f, 0.5f, 0.5f, 0.99f, 0.02f, 0.5f
                }, 0.10f);
                break;
            case 11:
                addRoundedPolygon(path, r, new float[]{
                        0.3f, 0.06f, 0.7f, 0.06f, 0.98f, 0.5f,
                        0.7f, 0.94f, 0.3f, 0.94f, 0.02f, 0.5f
                }, 0.10f);
                break;
            case 12:
                addRegularPolygon(path, r, 5, 0.48f, 0.10f);
                break;
            case 13:
                addRegularPolygon(path, r, 7, 0.48f, 0.10f);
                break;
            case 14:
                addScallop(path, r, 10, 0.10f, 0f);
                break;
            case 15:
                addScallop(path, r, 8, 0.11f, 0f);
                break;
            case 16:
                addScallop(path, r, 4, 0.17f, (float) Math.PI / 4f);
                break;
            case 17:
                addScallop(path, r, 6, 0.15f, 0f);
                break;
            case 18:
                addScallop(path, r, 7, 0.14f, 0f);
                break;
            case 19:
                addScallop(path, r, 9, 0.12f, 0f);
                break;
            case 20:
                addScallop(path, r, 12, 0.10f, 0f);
                break;
            case 21:
                addClover(path, r, 4, 0.32f);
                break;
            case 22:
                addClover(path, r, 8, 0.25f);
                break;
            case 23:
                addStar(path, r, 12, 0.62f, false);
                break;
            case 24:
                addStar(path, r, 12, 0.72f, true);
                break;
            case 25:
                addStar(path, r, 16, 0.34f, false);
                break;
            case 26:
                addStar(path, r, 16, 0.55f, true);
                break;
            case 27:
                addScallop(path, r, 6, 0.28f, 0f);
                break;
            case 28:
                addScallop(path, r, 8, 0.10f, 0f);
                break;
            case 29:
                addScallop(path, r, 8, 0.10f, (float) Math.PI / 8f);
                break;
            case 30:
                addGhost(path, r);
                break;
            case 31:
                addPixel(path, r, true);
                break;
            case 32:
                addPixel(path, r, false);
                break;
            case 33:
                addBun(path, r);
                break;
            case 34:
                addHeart(path, r);
                break;
            case 35:
                addPlane(path, r);
                break;
        }
    }

    private static float min(RectF r) {
        return Math.min(r.width(), r.height());
    }

    private static void addRotatedOval(Path path, RectF r, float degrees, float heightScale) {
        final float cy = r.centerY();
        final float h = r.height() * heightScale;
        final RectF oval = new RectF(r.left, cy - h / 2f, r.right, cy + h / 2f);
        path.addOval(oval, Path.Direction.CW);
        if (degrees != 0f) {
            final Matrix m = new Matrix();
            m.postRotate(degrees, r.centerX(), r.centerY());
            path.transform(m);
        }
    }

    private static void addArch(Path path, RectF r) {
        final float left = r.left + r.width() * 0.04f;
        final float right = r.right - r.width() * 0.04f;
        final float bottom = r.bottom - r.height() * 0.02f;
        final float mid = r.top + r.height() * 0.5f;
        path.moveTo(left, bottom);
        path.lineTo(left, mid);
        path.cubicTo(left, r.top, right, r.top, right, mid);
        path.lineTo(right, bottom);
        path.quadTo(right, r.bottom, right - r.width() * 0.05f, r.bottom);
        path.lineTo(left + r.width() * 0.05f, r.bottom);
        path.quadTo(left, r.bottom, left, bottom);
        path.close();
    }

    private static void addSemicircle(Path path, RectF r) {
        final float left = r.left + r.width() * 0.02f;
        final float right = r.right - r.width() * 0.02f;
        final float bottom = r.bottom - r.height() * 0.02f;
        final float mid = r.centerY();
        path.moveTo(left, bottom);
        path.lineTo(left, mid);
        path.cubicTo(left, r.top, right, r.top, right, mid);
        path.lineTo(right, bottom);
        path.quadTo(right, r.bottom, right - r.width() * 0.05f, r.bottom);
        path.lineTo(left + r.width() * 0.05f, r.bottom);
        path.quadTo(left, r.bottom, left, bottom);
        path.close();
    }

    private static void addRoundedPolygon(Path path, RectF r, float[] pts, float rf) {
        final int n = pts.length / 2;
        final float radius = min(r) * rf;
        final float[] vx = new float[n];
        final float[] vy = new float[n];
        for (int i = 0; i < n; i++) {
            vx[i] = r.left + pts[i * 2] * r.width();
            vy[i] = r.top + pts[i * 2 + 1] * r.height();
        }
        final float[] ax = new float[n];
        final float[] ay = new float[n];
        final float[] bx = new float[n];
        final float[] by = new float[n];
        for (int i = 0; i < n; i++) {
            final int prev = (i + n - 1) % n;
            final int next = (i + 1) % n;
            final float d1 = dist(vx[i], vy[i], vx[prev], vy[prev]);
            final float d2 = dist(vx[i], vy[i], vx[next], vy[next]);
            final float rr = Math.min(radius, Math.min(d1, d2) * 0.45f);
            ax[i] = vx[i] + (vx[prev] - vx[i]) / d1 * rr;
            ay[i] = vy[i] + (vy[prev] - vy[i]) / d1 * rr;
            bx[i] = vx[i] + (vx[next] - vx[i]) / d2 * rr;
            by[i] = vy[i] + (vy[next] - vy[i]) / d2 * rr;
        }
        path.moveTo(ax[0], ay[0]);
        path.quadTo(vx[0], vy[0], bx[0], by[0]);
        for (int i = 1; i < n; i++) {
            path.lineTo(ax[i], ay[i]);
            path.quadTo(vx[i], vy[i], bx[i], by[i]);
        }
        path.close();
    }

    private static float dist(float x1, float y1, float x2, float y2) {
        return Math.max(0.0001f, (float) Math.hypot(x1 - x2, y1 - y2));
    }

    private static void addRegularPolygon(Path path, RectF r, int sides, float radiusFraction, float rounding) {
        final float rd = min(r) * radiusFraction;
        final float cx = r.centerX();
        final float cy = r.centerY();
        final float[] pts = new float[sides * 2];
        for (int i = 0; i < sides; i++) {
            final double a = -Math.PI / 2 + 2 * Math.PI * i / sides;
            pts[i * 2] = (float) (0.5f + Math.cos(a) * rd / r.width());
            pts[i * 2 + 1] = (float) (0.5f + Math.sin(a) * rd / r.height());
        }
        addRoundedPolygon(path, r, pts, rounding);
    }

    private static void addScallop(Path path, RectF r, int lobes, float amp, float phase) {
        addPolar(path, r, Math.max(180, lobes * 36), a -> 1f - amp + amp * (float) Math.cos(lobes * (a - phase)));
    }

    private static void addClover(Path path, RectF r, int lobes, float amp) {
        addPolar(path, r, Math.max(200, lobes * 50), a -> 1f - amp + amp * (float) Math.cos(lobes * a));
    }

    private static void addStar(Path path, RectF r, int points, float inner, boolean smooth) {
        if (smooth) {
            addPolar(path, r, Math.max(200, points * 24), a -> {
                final float t = 0.5f + 0.5f * (float) Math.cos(points * a);
                return inner + (1f - inner) * (float) Math.pow(t, 0.7f);
            });
        } else {
            final int segments = points * 2;
            final float cx = r.centerX();
            final float cy = r.centerY();
            final float rx = r.width() / 2f;
            final float ry = r.height() / 2f;
            for (int i = 0; i <= segments; i++) {
                final double a = 2 * Math.PI * i / segments;
                final float rad = (i % 2 == 0) ? 1f : inner;
                final float x = cx + (float) Math.cos(a) * rx * rad;
                final float y = cy + (float) Math.sin(a) * ry * rad;
                if (i == 0) {
                    path.moveTo(x, y);
                } else {
                    path.lineTo(x, y);
                }
            }
            path.close();
        }
    }

    private static void addPolar(Path path, RectF r, int segments, RadiusFn fn) {
        final float cx = r.centerX();
        final float cy = r.centerY();
        final float rx = r.width() / 2f;
        final float ry = r.height() / 2f;
        for (int i = 0; i <= segments; i++) {
            final double a = 2 * Math.PI * i / segments;
            final float rad = fn.radius((float) a);
            final float x = cx + (float) Math.cos(a) * rx * rad;
            final float y = cy + (float) Math.sin(a) * ry * rad;
            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        path.close();
    }

    private interface RadiusFn {
        float radius(float angle);
    }

    private static void addGhost(Path path, RectF r) {
        final float l = r.left + r.width() * 0.04f;
        final float rr = r.right - r.width() * 0.04f;
        final float t = r.top + r.height() * 0.02f;
        final float b = r.bottom - r.height() * 0.02f;
        path.moveTo(l, b - r.height() * 0.16f);
        path.cubicTo(l, t, rr, t, rr, b - r.height() * 0.16f);
        path.cubicTo(rr, b - r.height() * 0.02f, r.centerX() + r.width() * 0.28f, b, r.centerX(), b - r.height() * 0.10f);
        path.cubicTo(r.centerX() - r.width() * 0.28f, b, l, b - r.height() * 0.02f, l, b - r.height() * 0.16f);
        path.close();
    }

    private static void addBun(Path path, RectF r) {
        final float cx = r.centerX();
        final float rad = min(r) * 0.315f;
        path.addCircle(cx, r.top + r.height() * 0.30f, rad, Path.Direction.CW);
        path.addCircle(cx, r.top + r.height() * 0.70f, rad, Path.Direction.CW);
        path.addRoundRect(new RectF(cx - min(r) * 0.10f, r.top + r.height() * 0.30f, cx + min(r) * 0.10f, r.top + r.height() * 0.70f), min(r) * 0.05f, min(r) * 0.05f, Path.Direction.CW);
    }

    private static void addHeart(Path path, RectF r) {
        final float w = r.width();
        final float h = r.height();
        path.moveTo(r.left + w * 0.5f, r.top + h * 0.94f);
        path.cubicTo(r.left + w * 0.05f, r.top + h * 0.62f, r.left + w * 0.02f, r.top + h * 0.26f, r.left + w * 0.22f, r.top + h * 0.13f);
        path.cubicTo(r.left + w * 0.37f, r.top + h * 0.03f, r.left + w * 0.48f, r.top + h * 0.12f, r.left + w * 0.5f, r.top + h * 0.25f);
        path.cubicTo(r.left + w * 0.52f, r.top + h * 0.12f, r.left + w * 0.63f, r.top + h * 0.03f, r.left + w * 0.78f, r.top + h * 0.13f);
        path.cubicTo(r.left + w * 0.98f, r.top + h * 0.26f, r.left + w * 0.95f, r.top + h * 0.62f, r.left + w * 0.5f, r.top + h * 0.94f);
        path.close();
    }

    private static void addPlane(Path path, RectF r) {
        addRoundedPolygon(path, r, new float[]{
                0.5f, 0.05f,
                0.9f, 0.86f,
                0.63f, 0.86f,
                0.5f, 0.63f,
                0.37f, 0.86f,
                0.1f, 0.86f
        }, 0.07f);
    }

    private static void addPixel(Path path, RectF r, boolean circle) {
        final int grid = 12;
        final float cw = r.width() / grid;
        final float ch = r.height() / grid;
        for (int y = 0; y < grid; y++) {
            int runStart = -1;
            for (int x = 0; x <= grid; x++) {
                final boolean inside = x < grid && pixelInside(x, y, grid, circle, r);
                if (inside && runStart < 0) {
                    runStart = x;
                } else if (!inside && runStart >= 0) {
                    path.addRect(r.left + runStart * cw, r.top + y * ch, r.left + x * cw, r.top + (y + 1) * ch, Path.Direction.CW);
                    runStart = -1;
                }
            }
        }
    }

    private static boolean pixelInside(int x, int y, int grid, boolean circle, RectF r) {
        final float nx = (x + 0.5f) / grid;
        final float ny = (y + 0.5f) / grid;
        if (circle) {
            final float dx = nx - 0.5f;
            final float dy = ny - 0.5f;
            return dx * dx + dy * dy <= 0.46f * 0.46f;
        }
        final float x1 = 0.5f, y1 = 0.04f;
        final float x2 = 0.98f, y2 = 0.96f;
        final float x3 = 0.02f, y3 = 0.96f;
        final float d1 = (nx - x2) * (y1 - y2) - (x1 - x2) * (ny - y2);
        final float d2 = (nx - x3) * (y2 - y3) - (x2 - x3) * (ny - y3);
        final float d3 = (nx - x1) * (y3 - y1) - (x3 - x1) * (ny - y1);
        final boolean neg = d1 < 0 || d2 < 0 || d3 < 0;
        final boolean pos = d1 > 0 || d2 > 0 || d3 > 0;
        return !(neg && pos);
    }
}
