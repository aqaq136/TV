package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

/** Procedural GPU-friendly scenery. One capped frame loop, stopped whenever Home is not active. */
public final class AtmosphereView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path ribbon = new Path();
    private final float[] stars = new float[72 * 3];
    private final long epoch = SystemClock.elapsedRealtime();
    private VisualPalette palette;
    private Shader firstGlow, secondGlow, vignette;
    private boolean active, motion, particles, parallax, customWallpaper;
    private int quality = 2;
    private String scene;
    private float targetX, targetY, focusX, focusY;
    private final Runnable frame = () -> { invalidate(); schedule(); };

    public AtmosphereView(Context context, AttributeSet attrs) { super(context, attrs); initialize(); }
    public AtmosphereView(Context context) { super(context); initialize(); }
    private void initialize() {
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); setFocusable(false);
        for (int i = 0; i < stars.length / 3; i++) {
            stars[i * 3] = (float) ((i * 0.61803398875 + 0.08) % 1);
            stars[i * 3 + 1] = (float) ((i * 0.41421356237 + 0.13) % 1);
            stars[i * 3 + 2] = 0.3f + (i % 7) / 10f;
        }
        configure();
    }
    void configure() {
        SharedPreferences p = PremiumPreferences.get(getContext());
        palette = VisualPalette.of(p.getString("visual_palette", "cosmos"));
        scene = p.getString("visual_scene", "aurora");
        quality = Math.max(0, Math.min(2, p.getInt("visual_quality", 2)));
        motion = p.getBoolean("visual_motion", true) && getContext().getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).getBoolean("animations", true) && quality > 0;
        particles = p.getBoolean("visual_particles", true);
        parallax = p.getBoolean("visual_parallax", true);
        customWallpaper = p.getBoolean("custom_wallpaper", false);
        firstGlow = new RadialGradient(0, 0, 1, VisualPalette.alpha(palette.primary, 110), 0, Shader.TileMode.CLAMP);
        secondGlow = new RadialGradient(0, 0, 1, VisualPalette.alpha(palette.secondary, 140), 0, Shader.TileMode.CLAMP);
        resize(); invalidate(); schedule();
    }
    void setActive(boolean value) { active = value; removeCallbacks(frame); if (value) { invalidate(); schedule(); } }
    void follow(float x, float y) {
        targetX = Math.max(-1, Math.min(1, x)); targetY = Math.max(-1, Math.min(1, y));
        if (!motion) { focusX = targetX; focusY = targetY; invalidate(); }
    }
    boolean isRendering() { return active && motion && isShown() && getWindowVisibility() == VISIBLE; }
    private void schedule() {
        removeCallbacks(frame);
        if (isRendering()) postDelayed(frame, quality == 2 ? 40 : 67);
    }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); schedule(); }
    @Override protected void onDetachedFromWindow() { removeCallbacks(frame); super.onDetachedFromWindow(); }
    @Override protected void onWindowVisibilityChanged(int visibility) { super.onWindowVisibilityChanged(visibility); if (frame != null) schedule(); }
    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) { resize(); }
    private void resize() {
        if (getHeight() > 0) vignette = new LinearGradient(0, 0, 0, getHeight(),
                new int[]{0x22030712, 0x08030712, 0xE5050814}, new float[]{0, .4f, 1}, Shader.TileMode.CLAMP);
    }
    private void glow(Canvas c, float x, float y, float sx, float sy, Shader shader) {
        c.save(); c.translate(x, y); c.scale(sx, sy); paint.setShader(shader); c.drawCircle(0, 0, 1, paint); c.restore(); paint.setShader(null);
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight(); if (w <= 0 || h <= 0) return;
        float t = motion ? (SystemClock.elapsedRealtime() - epoch) / 1000f : 7f;
        focusX += (targetX - focusX) * .09f; focusY += (targetY - focusY) * .09f;
        float dx = parallax ? focusX * w * .025f : 0, dy = parallax ? focusY * h * .02f : 0;
        if (!customWallpaper) canvas.drawColor(palette.background);
        else canvas.drawColor(0x30030815);
        glow(canvas, w * (.19f + .06f * (float) Math.sin(t * .11f)) + dx, h * .20f + dy, w * .63f, h * .72f, firstGlow);
        glow(canvas, w * (.83f + .05f * (float) Math.cos(t * .09f)) - dx, h * .38f - dy, w * .57f, h * .90f, secondGlow);
        if ("aurora".equals(scene)) {
            for (int j = 0; j < 4; j++) {
                float y = h * (.21f + j * .05f), sway = h * .12f * (float) Math.sin(t * .12f + j * .7f);
                ribbon.reset(); ribbon.moveTo(-w * .1f, y + sway);
                ribbon.cubicTo(w * .25f, y - h * .37f, w * .63f, y + h * .43f + sway, w * 1.1f, y - h * .10f);
                paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(h * (.045f + j * .026f));
                paint.setColor(VisualPalette.alpha(j % 2 == 0 ? palette.primary : palette.secondary, 10 + j * 3));
                canvas.drawPath(ribbon, paint);
            }
            paint.setStyle(Paint.Style.FILL);
        } else if ("horizon".equals(scene)) {
            paint.setColor(VisualPalette.alpha(palette.primary, 18)); paint.setStrokeWidth(1);
            for (int i = 0; i < 13; i++) canvas.drawLine(w / 2, h * .37f, (i - 6) * w * .23f + w / 2 + dx, h, paint);
            for (int i = 0; i < 9; i++) {
                float f = ((i + (motion ? t * .07f % 1 : 0)) / 9f); float y = h * (.37f + .63f * f * f);
                canvas.drawLine(0, y, w, y, paint);
            }
        }
        if (particles) {
            int count = quality == 2 ? 72 : 32;
            for (int i = 0; i < count; i++) {
                float s = stars[i * 3 + 2];
                float x = (stars[i * 3] * w + dx * s + w) % w;
                float y = ((stars[i * 3 + 1] * h - (motion ? t * (1 + s) : 0) + dy * s) % h + h) % h;
                int alpha = (int) (35 + 65 * (.5 + .5 * Math.sin(t * .6 + i)));
                paint.setColor(VisualPalette.alpha(i % 3 == 0 ? palette.primary : 0xFFFFFFFF, alpha));
                canvas.drawCircle(x, y, (1 + s) * getResources().getDisplayMetrics().density * .45f, paint);
            }
        }
        if ("cosmos".equals(scene) && motion && quality == 2) {
            float streak = t % 14;
            if (streak < 1.3f) {
                float progress = streak / 1.3f;
                paint.setColor(VisualPalette.alpha(palette.primary, (int) (140 * Math.sin(progress * Math.PI)))); paint.setStrokeWidth(2);
                float x = w * (.55f + progress * .3f), y = h * (.03f + progress * .3f);
                canvas.drawLine(x - w * .10f, y - h * .08f, x, y, paint);
            }
        }
        paint.setShader(vignette); canvas.drawRect(0, 0, w, h, paint); paint.setShader(null);
    }
}
