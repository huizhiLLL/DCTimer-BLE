package com.dctimer.util;

/** Normalized, non-destructive background framing shared by preview and rendering. */
public class BackgroundCrop {
    public float centerX, centerY, zoom;

    public BackgroundCrop(float x, float y, float zoom) {
        centerX = clamp(x, 0, 1);
        centerY = clamp(y, 0, 1);
        this.zoom = clamp(zoom, 1, 5);
    }

    private static float clamp(float value, float min, float max) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return (min + max) / 2;
        return Math.max(min, Math.min(max, value));
    }

    public float[] bounds(int imageWidth, int imageHeight, float width, float height) {
        float scale = Math.max(width / imageWidth, height / imageHeight) * zoom;
        float cropWidth = Math.min(imageWidth, width / scale);
        float cropHeight = Math.min(imageHeight, height / scale);
        float left = clamp(centerX * imageWidth - cropWidth / 2, 0, imageWidth - cropWidth);
        float top = clamp(centerY * imageHeight - cropHeight / 2, 0, imageHeight - cropHeight);
        return new float[] {left, top, left + cropWidth, top + cropHeight};
    }

    public void drag(float dx, float dy, int iw, int ih, float w, float h) {
        float[] b = bounds(iw, ih, w, h);
        centerX = ((b[0] + b[2]) / 2 - dx * (b[2] - b[0]) / w) / iw;
        centerY = ((b[1] + b[3]) / 2 - dy * (b[3] - b[1]) / h) / ih;
        constrain(iw, ih, w, h);
    }

    public void scale(float factor, float fx, float fy, int iw, int ih, float w, float h) {
        float[] before = bounds(iw, ih, w, h);
        zoom = clamp(zoom * factor, 1, 5);
        float[] after = bounds(iw, ih, w, h);
        centerX = (before[0] + fx * (before[2] - before[0])
                + (0.5f - fx) * (after[2] - after[0])) / iw;
        centerY = (before[1] + fy * (before[3] - before[1])
                + (0.5f - fy) * (after[3] - after[1])) / ih;
        constrain(iw, ih, w, h);
    }

    private void constrain(int iw, int ih, float w, float h) {
        float[] b = bounds(iw, ih, w, h);
        centerX = (b[0] + b[2]) / (2 * iw);
        centerY = (b[1] + b[3]) / (2 * ih);
    }
}
