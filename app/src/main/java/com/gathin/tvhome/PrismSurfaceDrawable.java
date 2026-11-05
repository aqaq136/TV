package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** Tinted glass, a fine rim and a corner light; no blur buffers or offscreen screenshots. */
final class PrismSurfaceDrawable extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final int accent;
    private final float radius, density;
    private final String style;
    private Shader fill, sheen;
    private boolean focused;
    PrismSurfaceDrawable(int accent, float density, float radius, String style) {
        this.accent = accent; this.density = density; this.radius = radius; this.style = style;
    }
    @Override protected void onBoundsChange(Rect b) {
        int tint = "solid".equals(style) ? 0xFF192338 : VisualPalette.blend(0xFF0D1427, accent, .18f);
        fill = new LinearGradient(b.left, b.top, b.right, b.bottom, new int[]{VisualPalette.blend(tint, accent, .19f), tint, 0xED0C1120}, null, Shader.TileMode.CLAMP);
        sheen = new LinearGradient(b.left, b.top, b.right, b.bottom, new int[]{0x2AFFFFFF, 0x02FFFFFF, 0x00FFFFFF}, new float[]{0,.5f,1}, Shader.TileMode.CLAMP);
    }
    @Override public boolean isStateful() { return true; }
    @Override protected boolean onStateChange(int[] states) {
        boolean next = false;
        for (int s : states) if (s == android.R.attr.state_focused || s == android.R.attr.state_pressed) next = true;
        if (focused == next) return false; focused = next; invalidateSelf(); return true;
    }
    @Override public void draw(Canvas c) {
        Rect b = getBounds(); float inset = density;
        rect.set(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset);
        paint.setStyle(Paint.Style.FILL); paint.setShader(fill); c.drawRoundRect(rect, radius, radius, paint);
        if (!"solid".equals(style)) { paint.setShader(sheen); c.drawRoundRect(rect, radius, radius, paint); }
        paint.setShader(null); paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth((focused ? 1.6f : 1f) * density);
        paint.setColor(VisualPalette.alpha(accent, focused ? 235 : "neon".equals(style) ? 150 : 65));
        c.drawRoundRect(rect, radius, radius, paint);
        paint.setStyle(Paint.Style.FILL); paint.setColor(VisualPalette.alpha(accent, focused ? 210 : 105));
        c.drawRoundRect(rect.left + radius, rect.top, rect.left + radius + rect.width() * .19f, rect.top + 2 * density, density, density, paint);
    }
    @Override public void setAlpha(int alpha) {}
    @Override public void setColorFilter(ColorFilter filter) {}
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
