package com.dctimer.util;

import org.junit.Test;
import java.util.Random;
import static org.junit.Assert.*;

public class BackgroundCropTest {
    @Test public void defaultMatchesCenteredCover() {
        assertArrayEquals(new float[] {1000, 0, 3000, 3000},
                new BackgroundCrop(.5f, .5f, 1).bounds(4000, 3000, 400, 600), .01f);
    }

    @Test public void dragMovesImageInFingerDirectionAndClampsEdges() {
        BackgroundCrop crop = new BackgroundCrop(.5f, .5f, 2);
        crop.drag(100, 0, 1000, 1000, 500, 500);
        assertEquals(.4f, crop.centerX, .001f);
        crop.drag(10000, -10000, 1000, 1000, 500, 500);
        assertArrayEquals(new float[] {0, 500, 500, 1000}, crop.bounds(1000, 1000, 500, 500), .01f);
    }

    @Test public void pinchPreservesPointUnderFingers() {
        BackgroundCrop crop = new BackgroundCrop(.5f, .5f, 1);
        crop.scale(2, .25f, .75f, 1000, 1000, 500, 500);
        float[] b = crop.bounds(1000, 1000, 500, 500);
        assertEquals(250, b[0] + .25f * (b[2] - b[0]), .01f);
        assertEquals(750, b[1] + .75f * (b[3] - b[1]), .01f);
    }

    @Test public void previewAndActualSizeUseSameSourceRegion() {
        BackgroundCrop crop = new BackgroundCrop(.7f, .3f, 2.4f);
        assertArrayEquals(crop.bounds(3000, 4000, 360, 720),
                crop.bounds(3000, 4000, 1080, 2160), .01f);
    }

    @Test public void rotationKeepsSavedSubjectPosition() {
        BackgroundCrop crop = new BackgroundCrop(.65f, .35f, 2);
        crop.bounds(4000, 3000, 600, 300);
        assertEquals(.65f, crop.centerX, 0);
        assertEquals(.35f, crop.centerY, 0);
    }

    @Test public void zoomHasFiniteLimits() {
        BackgroundCrop crop = new BackgroundCrop(-1, 2, 100);
        assertEquals(5, crop.zoom, 0);
        crop.scale(.001f, .5f, .5f, 1000, 1000, 500, 500);
        assertEquals(1, crop.zoom, 0);
        assertArrayEquals(new float[] {0, 0, 1000, 1000}, crop.bounds(1000, 1000, 500, 500), .01f);
    }

    @Test public void extremeAspectRatiosNeverExposeEmptySpace() {
        Random random = new Random(42);
        for (int i = 0; i < 1000; i++) {
            int iw = 1 + random.nextInt(10000), ih = 1 + random.nextInt(10000);
            int w = 1 + random.nextInt(2000), h = 1 + random.nextInt(2000);
            BackgroundCrop crop = new BackgroundCrop(random.nextFloat(), random.nextFloat(), 1 + random.nextFloat() * 4);
            crop.drag(random.nextFloat() * 10000, -random.nextFloat() * 10000, iw, ih, w, h);
            float[] b = crop.bounds(iw, ih, w, h);
            assertTrue(b[0] >= 0 && b[1] >= 0);
            assertTrue(b[2] <= iw + .01f && b[3] <= ih + .01f);
            assertEquals((float) w / h, (b[2] - b[0]) / (b[3] - b[1]), .03f);
        }
    }
}
