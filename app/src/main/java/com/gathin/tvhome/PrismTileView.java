package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.widget.FrameLayout;

/** One brief light sweep on focus, plus a subtle breathing rim on the single focused tile. */
public final class PrismTileView extends FrameLayout {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean active = true, motion, bloom;
    private long focusAt;
    private final Runnable frame = this::invalidate;
    public PrismTileView(Context context, AttributeSet attrs) {
        super(context, attrs);
        android.content.SharedPreferences p = PremiumPreferences.get(context);
        motion = p.getBoolean("visual_shimmer", true) && p.getBoolean("visual_motion", true)
                && context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).getBoolean("animations", true);
        bloom = p.getBoolean("visual_bloom", true);
    }
    void setEffectsActive(boolean value) { active = value; removeCallbacks(frame); invalidate(); }
    @Override protected void onFocusChanged(boolean focused, int direction, Rect rect) {
        super.onFocusChanged(focused, direction, rect); focusAt = SystemClock.elapsedRealtime(); removeCallbacks(frame); invalidate();
    }
    @Override protected void onDetachedFromWindow() { removeCallbacks(frame); super.onDetachedFromWindow(); }
    @Override protected void onWindowVisibilityChanged(int visibility) { super.onWindowVisibilityChanged(visibility); if (frame != null) { removeCallbacks(frame); if (visibility == VISIBLE) invalidate(); } }
    @Override protected void dispatchDraw(Canvas c) {
        super.dispatchDraw(c);
        if (!isFocused()) return;
        float d = getResources().getDisplayMetrics().density, w = getWidth(), h = getHeight();
        float age = (SystemClock.elapsedRealtime() - focusAt) / 1000f;
        if (motion && age < .85f) {
            float x = -w + w * 3 * age / .85f;
            paint.setShader(new LinearGradient(x, 0, x + w * .45f, h,
                    new int[]{0, 0x30FFFFFF, 0}, null, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, paint); paint.setShader(null);
        }
        if (bloom) {
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2 * d);
            paint.setColor(VisualPalette.alpha(0xFFCBF5FF, motion ? (int) (120 + 45 * Math.sin(age * 2)) : 155));
            c.drawRoundRect(d, d, w - d, h - d, 16 * d, 16 * d, paint); paint.setStyle(Paint.Style.FILL);
        }
        if (motion && active && getWindowVisibility() == VISIBLE && (bloom || age < .85f)) postDelayed(frame, 40);
    }
}
