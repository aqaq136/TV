package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/** Crops a local wallpaper to fill the TV without stretching people or logos. */
final class PremiumWallpaperDrawable extends Drawable {
    private final Bitmap bitmap;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Rect source = new Rect();
    PremiumWallpaperDrawable(Bitmap bitmap) { this.bitmap = bitmap; }
    @Override public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds.width() <= 0 || bounds.height() <= 0) return;
        float scale = Math.max((float) bounds.width() / bitmap.getWidth(), (float) bounds.height() / bitmap.getHeight());
        int w = Math.min(bitmap.getWidth(), Math.round(bounds.width() / scale));
        int h = Math.min(bitmap.getHeight(), Math.round(bounds.height() / scale));
        int left = (bitmap.getWidth() - w) / 2, top = (bitmap.getHeight() - h) / 2;
        source.set(left, top, left + w, top + h);
        canvas.drawBitmap(bitmap, source, bounds, paint);
    }
    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.OPAQUE; }
}
