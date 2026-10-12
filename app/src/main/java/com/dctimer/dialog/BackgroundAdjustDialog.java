package com.dctimer.dialog;

import android.app.Dialog;
import android.content.Context;
import android.graphics.*;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.*;
import android.widget.*;

import com.dctimer.APP;
import com.dctimer.R;
import com.dctimer.util.BackgroundCrop;
import com.dctimer.util.Utils;

/** Edits a local copy of the framing; only Apply changes the saved background. */
public class BackgroundAdjustDialog extends Dialog {
    public interface OnApplyListener { boolean onApply(BackgroundCrop crop, Bitmap source); }

    private final String uri, path, scramble, puzzle;
    private int targetWidth, targetHeight;
    private final Typeface timerTypeface;
    private final OnApplyListener listener;
    private BackgroundCrop crop;
    private Bitmap source;
    private Preview preview;
    private Button apply;
    private TextView hint;

    public BackgroundAdjustDialog(Context context, String uri, String path, BackgroundCrop crop,
                                  int width, int height, String puzzle, String scramble,
                                  Typeface timerTypeface, OnApplyListener listener) {
        super(context);
        this.uri = uri;
        this.path = path;
        this.crop = crop;
        targetWidth = width;
        targetHeight = height;
        this.puzzle = puzzle;
        this.scramble = scramble;
        this.timerTypeface = timerTypeface;
        this.listener = listener;
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(APP.getBackgroundColor());
        int padding = Math.round(16 * getContext().getResources().getDisplayMetrics().density);
        root.setPadding(padding, padding, padding, padding);
        TextView title = new TextView(getContext());
        title.setText(R.string.background_adjust);
        title.setTextSize(20);
        title.setTextColor(APP.getTextColor());
        root.addView(title);
        hint = new TextView(getContext());
        hint.setText(R.string.background_loading);
        hint.setTextColor(APP.getTextColor());
        hint.setPadding(0, padding / 2, 0, padding / 2);
        root.addView(hint);
        preview = new Preview(getContext());
        preview.setContentDescription(getContext().getString(R.string.background_adjust_hint));
        root.addView(preview, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout buttons = new LinearLayout(getContext());
        addButton(buttons, R.string.btn_cancel, v -> dismiss());
        addButton(buttons, R.string.background_reset, v -> {
            crop = new BackgroundCrop(0.5f, 0.5f, 1);
            preview.invalidate();
        });
        apply = addButton(buttons, R.string.background_apply, v -> {
            if (listener.onApply(crop, source)) dismiss();
        });
        apply.setEnabled(false);
        root.addView(buttons);
        setContentView(root);
        setOnDismissListener(dialog -> {
            if (source != null) {
                source.recycle();
                source = null;
            }
        });
        new Thread(() -> {
            Bitmap loaded = null;
            try { loaded = Utils.loadBackgroundSource(getContext(), uri, path); }
            catch (Exception | OutOfMemoryError ignored) { }
            final Bitmap result = loaded;
            new Handler(Looper.getMainLooper()).post(() -> {
                if (!isShowing()) {
                    if (result != null) result.recycle();
                    return;
                }
                source = result;
                apply.setEnabled(result != null);
                hint.setText(result == null ? R.string.background_load_failed : R.string.background_adjust_hint);
                preview.invalidate();
            });
        }, "background-preview").start();
    }

    private Button addButton(LinearLayout row, int label, View.OnClickListener listener) {
        Button button = new Button(getContext());
        button.setText(label);
        button.setOnClickListener(listener);
        row.addView(button, new LinearLayout.LayoutParams(0, -2, 1));
        return button;
    }

    @Override public void show() {
        super.show();
        getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    public void updateViewport(int width, int height) {
        targetWidth = Math.max(1, width);
        targetHeight = Math.max(1, height);
        if (preview != null) {
            preview.onSizeChanged(preview.getWidth(), preview.getHeight(), 0, 0);
            preview.invalidate();
        }
    }

    private class Preview extends View {
        private final RectF viewport = new RectF();
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final ScaleGestureDetector scaleDetector;
        private float lastX, lastY;
        private boolean dragging;

        Preview(Context context) {
            super(context);
            scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override public boolean onScale(ScaleGestureDetector detector) {
                    if (source == null || viewport.isEmpty()) return false;
                    crop.scale(detector.getScaleFactor(),
                            (detector.getFocusX() - viewport.left) / viewport.width(),
                            (detector.getFocusY() - viewport.top) / viewport.height(),
                            source.getWidth(), source.getHeight(), viewport.width(), viewport.height());
                    invalidate();
                    return true;
                }
            });
        }

        @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            float scale = Math.min((float) w / targetWidth, (float) h / targetHeight);
            float width = targetWidth * scale, height = targetHeight * scale;
            viewport.set((w - width) / 2, (h - height) / 2, (w + width) / 2, (h + height) / 2);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (source == null || viewport.isEmpty()) return;
            canvas.save();
            canvas.clipRect(viewport);
            float[] b = crop.bounds(source.getWidth(), source.getHeight(), viewport.width(), viewport.height());
            Matrix matrix = new Matrix();
            matrix.setRectToRect(new RectF(b[0], b[1], b[2], b[3]), viewport, Matrix.ScaleToFit.FILL);
            canvas.drawBitmap(source, matrix, paint);
            canvas.drawColor(Color.argb(255 * (100 - APP.opacity) / 100, 255, 255, 255));
            canvas.translate(viewport.left, viewport.top);
            float scale = viewport.width() / targetWidth;
            canvas.scale(scale, scale);
            float density = getResources().getDisplayMetrics().density;
            TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            text.setColor(APP.getTextColor());
            text.setTextSize(14 * density);
            StaticLayout heading = new StaticLayout(puzzle, text, targetWidth, Layout.Alignment.ALIGN_CENTER, 1, 0, false);
            canvas.save();
            canvas.translate(0, 18 * density);
            heading.draw(canvas);
            canvas.restore();
            text.setTextSize(APP.scrambleSize * getResources().getDisplayMetrics().scaledDensity);
            StaticLayout scrambleLayout = new StaticLayout(scramble, text, Math.max(1, targetWidth - (int) (20 * density)),
                    Layout.Alignment.ALIGN_CENTER, 1.3f, 0, false);
            canvas.save();
            canvas.clipRect(0, 56 * density, targetWidth, targetHeight * 0.38f);
            canvas.translate(10 * density, 64 * density);
            scrambleLayout.draw(canvas);
            canvas.restore();
            text.setTextAlign(Paint.Align.CENTER);
            text.setTypeface(timerTypeface);
            text.setTextSize(APP.timerSize * getResources().getDisplayMetrics().scaledDensity);
            String time = "0" + (APP.decimalMark == 0 ? "." : ",") + (APP.timerAccuracy == 0 ? "00" : "000");
            canvas.drawText(time, targetWidth / 2f, targetHeight / 2f - (text.ascent() + text.descent()) / 2, text);
            if (APP.navigationPosition == 1) {
                paint.setColor(APP.getBackgroundColor());
                canvas.drawRect(0, targetHeight - 64 * density, targetWidth, targetHeight, paint);
                text.setTypeface(Typeface.DEFAULT);
                text.setTextSize(12 * getResources().getDisplayMetrics().scaledDensity);
                int[] labels = {R.string.tab_timer, R.string.tab_result, R.string.tab_settings};
                for (int i = 0; i < labels.length; i++) canvas.drawText(getContext().getString(labels[i]),
                        targetWidth * (i + 0.5f) / 3, targetHeight - 24 * density, text);
            }
            canvas.restore();
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            if (source == null || viewport.isEmpty()) return true;
            scaleDetector.onTouchEvent(event);
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    dragging = viewport.contains(event.getX(), event.getY());
                    lastX = event.getX(); lastY = event.getY();
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (dragging && event.getPointerCount() == 1 && !scaleDetector.isInProgress()) {
                        crop.drag(event.getX() - lastX, event.getY() - lastY,
                                source.getWidth(), source.getHeight(), viewport.width(), viewport.height());
                        invalidate();
                    }
                    lastX = event.getX(); lastY = event.getY();
                    break;
                case MotionEvent.ACTION_POINTER_UP:
                    int remaining = event.getActionIndex() == 0 ? 1 : 0;
                    lastX = event.getX(remaining); lastY = event.getY(remaining);
                    break;
                case MotionEvent.ACTION_UP:
                    performClick();
                case MotionEvent.ACTION_CANCEL:
                    dragging = false;
                    break;
            }
            return true;
        }

        @Override public boolean performClick() { super.performClick(); return true; }
    }
}
