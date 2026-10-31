package com.gathin.tvhome;

/** Geometry shared by the recycler cells and the surrounding shelf. */
final class TileShape {
    static int height(int width, String shape) {
        if ("square".equals(shape)) return Math.max(1, width);
        if ("compact".equals(shape)) return Math.max(1, Math.round(width * 0.40f));
        return Math.max(1, Math.round(width * 101f / 180f));
    }
}
