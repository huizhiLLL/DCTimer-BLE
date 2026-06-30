package com.dctimer.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.animation.LinearInterpolator;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class SmartCube3DView extends GLSurfaceView {
    public static final int APPEARANCE_BLACK_STICKER = 0;
    public static final int APPEARANCE_SOLID = 1;

    private static final String SOLVED_FACELET = "UUUUUUUUURRRRRRRRRFFFFFFFFFDDDDDDDDDLLLLLLLLLBBBBBBBBB";
    private static final long DEFAULT_ANIMATION_DURATION_MS = 110L;
    private static final long GYRO_FRAME_IDLE_MS = 220L;
    private static final float TOUCH_DEGREES_PER_PX = 0.28f;
    private static final LinearInterpolator LINEAR_INTERPOLATOR = new LinearInterpolator();

    private final CubeRenderer cubeRenderer;
    private ValueAnimator animator;
    private float lastTouchX;
    private float lastTouchY;
    private float touchStartX;
    private float touchStartY;
    private boolean draggingView;
    private int touchSlop;
    private final Object gyroUpdateLock = new Object();
    private final float[] pendingGyroQuaternion = new float[4];
    private boolean hasPendingGyroQuaternion;
    private boolean gyroFrameCallbackPosted;
    private long lastGyroUpdateUptimeMs;
    private final Runnable scheduleGyroFrameRunnable = new Runnable() {
        @Override
        public void run() {
            Choreographer.getInstance().postFrameCallback(gyroFrameCallback);
        }
    };
    private final Choreographer.FrameCallback gyroFrameCallback = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            float x = 0f;
            float y = 0f;
            float z = 0f;
            float w = 0f;
            boolean hasPending;
            boolean keepAnimating;
            synchronized (gyroUpdateLock) {
                hasPending = hasPendingGyroQuaternion;
                if (hasPending) {
                    x = pendingGyroQuaternion[0];
                    y = pendingGyroQuaternion[1];
                    z = pendingGyroQuaternion[2];
                    w = pendingGyroQuaternion[3];
                }
                hasPendingGyroQuaternion = false;
                keepAnimating = isShown()
                        && (hasPending || SystemClock.uptimeMillis() - lastGyroUpdateUptimeMs < GYRO_FRAME_IDLE_MS);
                if (!keepAnimating) {
                    gyroFrameCallbackPosted = false;
                }
            }
            if (hasPending || keepAnimating) {
                final boolean frameHasTarget = hasPending;
                final float gx = x;
                final float gy = y;
                final float gz = z;
                final float gw = w;
                queueEvent(new Runnable() {
                    @Override
                    public void run() {
                        cubeRenderer.updateGyroFrame(frameHasTarget, gx, gy, gz, gw, frameTimeNanos);
                    }
                });
                requestRender();
            }
            if (keepAnimating) {
                Choreographer.getInstance().postFrameCallback(this);
            }
        }
    };

    public SmartCube3DView(Context context) {
        this(context, null);
    }

    public SmartCube3DView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setEGLContextClientVersion(2);
        setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        setZOrderOnTop(true);
        getHolder().setFormat(PixelFormat.TRANSLUCENT);
        setPreserveEGLContextOnPause(true);
        cubeRenderer = new CubeRenderer();
        setRenderer(cubeRenderer);
        setRenderMode(RENDERMODE_WHEN_DIRTY);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public void showCubeState(String facelets) {
        final String validState = sanitizeFacelets(facelets);
        if (validState == null) {
            return;
        }
        stopAnimator();
        queueEvent(new Runnable() {
            @Override
            public void run() {
                cubeRenderer.setState(validState);
            }
        });
        requestRender();
    }

    public void setAppearanceMode(final int appearanceMode) {
        queueEvent(new Runnable() {
            @Override
            public void run() {
                cubeRenderer.setAppearanceMode(appearanceMode);
            }
        });
        requestRender();
    }

    public void animateMove(String fromState, String toState, int move) {
        animateMove(fromState, toState, move, DEFAULT_ANIMATION_DURATION_MS);
    }

    public void animateMove(String fromState, String toState, final int move, long durationMs) {
        final String validFromState = sanitizeFacelets(fromState);
        final String validToState = sanitizeFacelets(toState);
        if (validFromState == null || validToState == null || !canAnimateMove(move) || !isShown()) {
            showCubeState(validToState);
            return;
        }
        stopAnimator();
        queueEvent(new Runnable() {
            @Override
            public void run() {
                cubeRenderer.startMove(validFromState, validToState, move);
            }
        });
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(durationMs > 0 ? durationMs : DEFAULT_ANIMATION_DURATION_MS);
        animator.setInterpolator(LINEAR_INTERPOLATOR);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                final float progress = (float) animation.getAnimatedValue();
                queueEvent(new Runnable() {
                    @Override
                    public void run() {
                        cubeRenderer.setMoveProgress(progress);
                    }
                });
                requestRender();
                if (progress >= 1f) {
                    queueEvent(new Runnable() {
                        @Override
                        public void run() {
                            cubeRenderer.setState(validToState);
                        }
                    });
                    requestRender();
                }
            }
        });
        animator.start();
    }

    public void setGyroQuaternion(float x, float y, float z, float w) {
        boolean shouldScheduleFrame = false;
        synchronized (gyroUpdateLock) {
            pendingGyroQuaternion[0] = x;
            pendingGyroQuaternion[1] = y;
            pendingGyroQuaternion[2] = z;
            pendingGyroQuaternion[3] = w;
            hasPendingGyroQuaternion = true;
            lastGyroUpdateUptimeMs = SystemClock.uptimeMillis();
            if (!gyroFrameCallbackPosted) {
                gyroFrameCallbackPosted = true;
                shouldScheduleFrame = true;
            }
        }
        if (shouldScheduleFrame) {
            post(scheduleGyroFrameRunnable);
        }
    }

    public void setGyroCalibration(final float x, final float y, final float z, final float w) {
        queueEvent(new Runnable() {
            @Override
            public void run() {
                cubeRenderer.setGyroCalibration(x, y, z, w);
            }
        });
        requestRender();
    }

    public void resetGyroPosture() {
        queueEvent(new Runnable() {
            @Override
            public void run() {
                float x = 0f;
                float y = 0f;
                float z = 0f;
                float w = 0f;
                boolean hasPending;
                synchronized (gyroUpdateLock) {
                    hasPending = hasPendingGyroQuaternion;
                    if (hasPending) {
                        x = pendingGyroQuaternion[0];
                        y = pendingGyroQuaternion[1];
                        z = pendingGyroQuaternion[2];
                        w = pendingGyroQuaternion[3];
                    }
                }
                if (hasPending) {
                    cubeRenderer.resetGyroPosture(x, y, z, w);
                } else {
                    cubeRenderer.resetGyroPosture();
                }
            }
        });
        requestRender();
    }

    public void resetGyroPosture(final float x, final float y, final float z, final float w) {
        queueEvent(new Runnable() {
            @Override
            public void run() {
                cubeRenderer.resetGyroPosture(x, y, z, w);
            }
        });
        requestRender();
    }

    public void disableGyroView() {
        synchronized (gyroUpdateLock) {
            hasPendingGyroQuaternion = false;
        }
        queueEvent(new Runnable() {
            @Override
            public void run() {
                cubeRenderer.disableGyroView();
            }
        });
        requestRender();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchX = event.getX();
                lastTouchY = event.getY();
                touchStartX = lastTouchX;
                touchStartY = lastTouchY;
                draggingView = false;
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                float x = event.getX();
                float y = event.getY();
                if (!draggingView
                        && (Math.abs(x - touchStartX) > touchSlop || Math.abs(y - touchStartY) > touchSlop)) {
                    draggingView = true;
                }
                final float deltaYaw = (x - lastTouchX) * TOUCH_DEGREES_PER_PX;
                final float deltaPitch = (y - lastTouchY) * TOUCH_DEGREES_PER_PX;
                lastTouchX = x;
                lastTouchY = y;
                queueEvent(new Runnable() {
                    @Override
                    public void run() {
                        cubeRenderer.rotateView(deltaYaw, deltaPitch);
                    }
                });
                requestRender();
                return true;
            case MotionEvent.ACTION_UP:
                if (!draggingView) {
                    performClick();
                }
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopAnimator();
        removeCallbacks(scheduleGyroFrameRunnable);
        Choreographer.getInstance().removeFrameCallback(gyroFrameCallback);
        synchronized (gyroUpdateLock) {
            hasPendingGyroQuaternion = false;
            gyroFrameCallbackPosted = false;
        }
        super.onDetachedFromWindow();
    }

    private void stopAnimator() {
        if (animator != null) {
            animator.cancel();
            animator.removeAllUpdateListeners();
            animator.removeAllListeners();
            animator = null;
        }
    }

    private boolean canAnimateMove(int move) {
        return move >= 0 && move < 18;
    }

    private String sanitizeFacelets(String facelets) {
        if (TextUtils.isEmpty(facelets) || facelets.length() < 54) {
            return null;
        }
        return facelets.substring(0, 54);
    }

    private static class CubeRenderer implements Renderer {
        private static final float FACE_DISTANCE = 1.5f;
        private static final float CELL_HALF = 0.505f;
        private static final float STICKER_HALF = 0.435f;
        private static final float SOLID_STICKER_HALF = 0.490f;
        private static final float CELL_CORNER_RADIUS = 0.045f;
        private static final float STICKER_CORNER_RADIUS_SMALL = 0.045f;
        private static final float STICKER_CORNER_RADIUS_LARGE = 0.255f;
        private static final float SOLID_CORNER_RADIUS_SCALE = SOLID_STICKER_HALF / STICKER_HALF;
        private static final float STICKER_Z_OFFSET = 0.032f;
        private static final float SOLID_STICKER_Z_OFFSET = 0.010f;
        private static final int CORNER_SEGMENTS = 5;
        private static final float MAX_VIEW_PITCH = 115f;
        private static final float PROJECTION_FIT_SCALE = 1.18f;
        private static final float GYRO_DEFAULT_VIEW_YAW = 0f;
        private static final float GYRO_DEFAULT_VIEW_PITCH = 0f;
        private static final float GYRO_INTERPOLATION_TIME_CONSTANT_MS = 42f;
        private static final String VERTEX_SHADER =
                "uniform mat4 uMvpMatrix;" +
                "attribute vec3 aPosition;" +
                "void main() {" +
                "  gl_Position = uMvpMatrix * vec4(aPosition, 1.0);" +
                "}";
        private static final String FRAGMENT_SHADER =
                "precision mediump float;" +
                "uniform vec4 uColor;" +
                "void main() {" +
                "  gl_FragColor = uColor;" +
                "}";
        private static final Facelet[] FACELETS = buildFacelets();
        private static final Vec3 LIGHT = new Vec3(-0.35f, 0.65f, 0.68f).normalize();

        private final float[] projectionMatrix = new float[16];
        private final float[] viewMatrix = new float[16];
        private final float[] gyroViewMatrix = new float[16];
        private final float[] modelMatrix = new float[16];
        private final float[] manualRotationMatrix = new float[16];
        private final float[] gyroRotationMatrix = new float[16];
        private final float[] vpMatrix = new float[16];
        private final float[] gyroVpMatrix = new float[16];
        private final float[] mvpMatrix = new float[16];
        private final float[] roundedQuad = new float[(1 + CORNER_SEGMENTS * 4 + 1) * 3];
        private final FloatBuffer roundedQuadBuffer = ByteBuffer.allocateDirect(roundedQuad.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        private String cubeState = SOLVED_FACELET;
        private String animationStartState;
        private String animationEndState;
        private MoveSpec animationMoveSpec;
        private float animationProgress;
        private float viewYaw;
        private float viewPitch;
        private Quaternion currentGyroQuaternion;
        private Quaternion displayedGyroQuaternion;
        private Quaternion targetGyroQuaternion;
        private Quaternion calibrationInverse;
        private boolean gyroViewEnabled;
        private int appearanceMode = APPEARANCE_BLACK_STICKER;
        private long lastGyroFrameTimeNanos;
        private int program;
        private int positionHandle;
        private int colorHandle;
        private int mvpHandle;

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
            positionHandle = GLES20.glGetAttribLocation(program, "aPosition");
            colorHandle = GLES20.glGetUniformLocation(program, "uColor");
            mvpHandle = GLES20.glGetUniformLocation(program, "uMvpMatrix");
            GLES20.glClearColor(0f, 0f, 0f, 0f);
            GLES20.glEnable(GLES20.GL_DEPTH_TEST);
            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            GLES20.glViewport(0, 0, width, height);
            float aspect = width > 0 && height > 0 ? (float) width / (float) height : 1f;
            Matrix.frustumM(projectionMatrix, 0,
                    -aspect * PROJECTION_FIT_SCALE, aspect * PROJECTION_FIT_SCALE,
                    -PROJECTION_FIT_SCALE, PROJECTION_FIT_SCALE,
                    3f, 18f);
            Matrix.setLookAtM(viewMatrix, 0, 4.8f, 4.1f, 7.2f, 0f, 0f, 0f, 0f, 1f, 0f);
            Matrix.setLookAtM(gyroViewMatrix, 0, 0f, 4.1f, 7.2f, 0f, 0f, 0f, 0f, 1f, 0f);
            Matrix.multiplyMM(vpMatrix, 0, projectionMatrix, 0, viewMatrix, 0);
            Matrix.multiplyMM(gyroVpMatrix, 0, projectionMatrix, 0, gyroViewMatrix, 0);
            updateMvpMatrix();
        }

        @Override
        public void onDrawFrame(GL10 gl) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
            GLES20.glUseProgram(program);
            GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix, 0);
            if (animationStartState != null && animationMoveSpec != null) {
                drawCube(animationStartState, animationMoveSpec, animationProgress);
            } else {
                drawCube(cubeState, null, 0f);
            }
        }

        void setState(String state) {
            cubeState = state;
            animationStartState = null;
            animationEndState = null;
            animationMoveSpec = null;
            animationProgress = 0f;
        }

        void startMove(String fromState, String toState, int move) {
            cubeState = toState;
            animationStartState = fromState;
            animationEndState = toState;
            animationMoveSpec = MoveSpec.fromMove(move);
            animationProgress = 0f;
        }

        void setMoveProgress(float progress) {
            animationProgress = Math.max(0f, Math.min(1f, progress));
            if (animationProgress >= 1f && animationEndState != null) {
                setState(animationEndState);
            }
        }

        void setAppearanceMode(int appearanceMode) {
            this.appearanceMode = appearanceMode == APPEARANCE_SOLID
                    ? APPEARANCE_SOLID : APPEARANCE_BLACK_STICKER;
        }

        void rotateView(float deltaYaw, float deltaPitch) {
            viewYaw = normalizeDegrees(viewYaw + deltaYaw);
            viewPitch = clamp(viewPitch + deltaPitch, -MAX_VIEW_PITCH, MAX_VIEW_PITCH);
            updateMvpMatrix();
        }

        void setGyroQuaternion(float x, float y, float z, float w) {
            Quaternion q = Quaternion.normalized(x, y, z, w);
            if (q == null) {
                return;
            }
            gyroViewEnabled = true;
            currentGyroQuaternion = q;
            targetGyroQuaternion = q;
            displayedGyroQuaternion = q;
            lastGyroFrameTimeNanos = 0L;
            if (calibrationInverse == null) {
                calibrationInverse = q.conjugate();
            }
            updateMvpMatrix();
        }

        void updateGyroFrame(boolean hasTarget, float x, float y, float z, float w, long frameTimeNanos) {
            if (hasTarget) {
                Quaternion q = Quaternion.normalized(x, y, z, w);
                if (q != null) {
                    gyroViewEnabled = true;
                    currentGyroQuaternion = q;
                    targetGyroQuaternion = q;
                    if (displayedGyroQuaternion == null) {
                        displayedGyroQuaternion = q;
                    }
                    if (calibrationInverse == null) {
                        calibrationInverse = q.conjugate();
                    }
                }
            }
            if (displayedGyroQuaternion != null && targetGyroQuaternion != null) {
                float alpha;
                if (lastGyroFrameTimeNanos <= 0L || frameTimeNanos <= lastGyroFrameTimeNanos) {
                    alpha = 1f;
                } else {
                    float deltaMs = (frameTimeNanos - lastGyroFrameTimeNanos) / 1000000f;
                    alpha = 1f - (float) Math.exp(-deltaMs / GYRO_INTERPOLATION_TIME_CONSTANT_MS);
                    alpha = clamp(alpha, 0.08f, 0.55f);
                }
                displayedGyroQuaternion = displayedGyroQuaternion.slerp(targetGyroQuaternion, alpha);
                lastGyroFrameTimeNanos = frameTimeNanos;
            }
            updateMvpMatrix();
        }

        void setGyroCalibration(float x, float y, float z, float w) {
            Quaternion q = Quaternion.normalized(x, y, z, w);
            if (q == null) {
                return;
            }
            calibrationInverse = q.conjugate();
            gyroViewEnabled = true;
            updateMvpMatrix();
        }

        void resetGyroPosture() {
            if (currentGyroQuaternion != null) {
                calibrationInverse = currentGyroQuaternion.conjugate();
                targetGyroQuaternion = currentGyroQuaternion;
                displayedGyroQuaternion = currentGyroQuaternion;
                gyroViewEnabled = true;
            } else {
                calibrationInverse = null;
                targetGyroQuaternion = null;
                displayedGyroQuaternion = null;
                gyroViewEnabled = false;
            }
            viewYaw = 0f;
            viewPitch = 0f;
            updateMvpMatrix();
        }

        void resetGyroPosture(float x, float y, float z, float w) {
            Quaternion q = Quaternion.normalized(x, y, z, w);
            if (q != null) {
                currentGyroQuaternion = q;
                targetGyroQuaternion = q;
                displayedGyroQuaternion = q;
                calibrationInverse = q.conjugate();
            }
            viewYaw = 0f;
            viewPitch = 0f;
            gyroViewEnabled = true;
            updateMvpMatrix();
        }

        void disableGyroView() {
            currentGyroQuaternion = null;
            targetGyroQuaternion = null;
            displayedGyroQuaternion = null;
            calibrationInverse = null;
            lastGyroFrameTimeNanos = 0L;
            viewYaw = 0f;
            viewPitch = 0f;
            gyroViewEnabled = false;
            updateMvpMatrix();
        }

        private void updateMvpMatrix() {
            Matrix.setIdentityM(manualRotationMatrix, 0);
            float defaultPitch = gyroViewEnabled ? GYRO_DEFAULT_VIEW_PITCH : 0f;
            float defaultYaw = gyroViewEnabled ? GYRO_DEFAULT_VIEW_YAW : 0f;
            Matrix.rotateM(manualRotationMatrix, 0, viewPitch + defaultPitch, 1f, 0f, 0f);
            Matrix.rotateM(manualRotationMatrix, 0, viewYaw + defaultYaw, 0f, 1f, 0f);
            Quaternion renderGyroQuaternion = displayedGyroQuaternion != null ? displayedGyroQuaternion : currentGyroQuaternion;
            if (renderGyroQuaternion != null && calibrationInverse != null) {
                Quaternion relative = calibrationInverse.multiply(renderGyroQuaternion).normalized();
                relative.toMatrix(gyroRotationMatrix);
                Matrix.multiplyMM(modelMatrix, 0, manualRotationMatrix, 0, gyroRotationMatrix, 0);
            } else {
                System.arraycopy(manualRotationMatrix, 0, modelMatrix, 0, manualRotationMatrix.length);
            }
            Matrix.multiplyMM(mvpMatrix, 0, gyroViewEnabled ? gyroVpMatrix : vpMatrix, 0, modelMatrix, 0);
        }

        private float normalizeDegrees(float degrees) {
            if (degrees > 180f) {
                return degrees - 360f;
            }
            if (degrees < -180f) {
                return degrees + 360f;
            }
            return degrees;
        }

        private float clamp(float value, float min, float max) {
            return Math.max(min, Math.min(max, value));
        }

        private void drawCube(String state, MoveSpec moveSpec, float moveProgress) {
            for (int i = 0; i < FACELETS.length; i++) {
                Facelet facelet = FACELETS[i];
                Transform transform = buildTransform(facelet, moveSpec, moveProgress);
                boolean solidAppearance = appearanceMode == APPEARANCE_SOLID;
                int baseColor = solidAppearance
                        ? shadeColor(0xffdddddd, transform.normal, 0.70f)
                        : shadeColor(0xff111111, transform.normal, 0.42f);
                int stickerColor = shadeColor(faceColor(state.charAt(i)), transform.normal, solidAppearance ? 0.94f : 0.86f);
                float stickerHalf = solidAppearance ? SOLID_STICKER_HALF : STICKER_HALF;
                float[] cornerRadii = solidAppearance ? facelet.solidCornerRadii : facelet.stickerCornerRadii;
                float stickerZOffset = solidAppearance ? SOLID_STICKER_Z_OFFSET : STICKER_Z_OFFSET;
                drawRoundedQuad(transform.center, transform.u, transform.v, transform.normal,
                        CELL_HALF, CELL_CORNER_RADIUS, baseColor);
                GLES20.glEnable(GLES20.GL_POLYGON_OFFSET_FILL);
                GLES20.glPolygonOffset(-1f, -1f);
                drawRoundedQuad(transform.center.add(transform.normal.scale(stickerZOffset)), transform.u, transform.v,
                        transform.normal, stickerHalf, cornerRadii, stickerColor);
                GLES20.glDisable(GLES20.GL_POLYGON_OFFSET_FILL);
            }
        }

        private Transform buildTransform(Facelet facelet, MoveSpec moveSpec, float moveProgress) {
            Vec3 center = facelet.center;
            Vec3 normal = facelet.normal;
            Vec3 u = facelet.u;
            Vec3 v = facelet.v;
            if (moveSpec != null && moveSpec.belongsToLayer(center)) {
                float angle = moveSpec.angleDegrees * moveProgress;
                center = rotateAroundAxis(center, moveSpec.axis, angle);
                normal = rotateAroundAxis(normal, moveSpec.axis, angle);
                u = rotateAroundAxis(u, moveSpec.axis, angle);
                v = rotateAroundAxis(v, moveSpec.axis, angle);
            }
            return new Transform(center, normal.normalize(), u.normalize(), v.normalize());
        }

        private void drawRoundedQuad(Vec3 center, Vec3 u, Vec3 v, Vec3 normal,
                                     float halfSize, float cornerRadius, int color) {
            int vertexCount = putRoundedQuad(center, u, v, halfSize,
                    cornerRadius, cornerRadius, cornerRadius, cornerRadius);
            roundedQuadBuffer.clear();
            roundedQuadBuffer.put(roundedQuad, 0, vertexCount * 3);
            roundedQuadBuffer.position(0);
            GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, roundedQuadBuffer);
            GLES20.glEnableVertexAttribArray(positionHandle);
            setColorUniform(color, normal);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, vertexCount);
            GLES20.glDisableVertexAttribArray(positionHandle);
        }

        private void drawRoundedQuad(Vec3 center, Vec3 u, Vec3 v, Vec3 normal,
                                     float halfSize, float[] cornerRadii, int color) {
            int vertexCount = putRoundedQuad(center, u, v, halfSize,
                    cornerRadii[0], cornerRadii[1], cornerRadii[2], cornerRadii[3]);
            roundedQuadBuffer.clear();
            roundedQuadBuffer.put(roundedQuad, 0, vertexCount * 3);
            roundedQuadBuffer.position(0);
            GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, roundedQuadBuffer);
            GLES20.glEnableVertexAttribArray(positionHandle);
            setColorUniform(color, normal);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, vertexCount);
            GLES20.glDisableVertexAttribArray(positionHandle);
        }

        private int putRoundedQuad(Vec3 center, Vec3 u, Vec3 v, float halfSize,
                                   float radiusRightBottom, float radiusRightTop,
                                   float radiusLeftTop, float radiusLeftBottom) {
            putPoint(center, 0);
            int vertexIndex = 1;
            vertexIndex = putCorner(center, u, v, halfSize, radiusRightBottom, 1f, -1f, -90f, 0f, vertexIndex);
            vertexIndex = putCorner(center, u, v, halfSize, radiusRightTop, 1f, 1f, 0f, 90f, vertexIndex);
            vertexIndex = putCorner(center, u, v, halfSize, radiusLeftTop, -1f, 1f, 90f, 180f, vertexIndex);
            vertexIndex = putCorner(center, u, v, halfSize, radiusLeftBottom, -1f, -1f, 180f, 270f, vertexIndex);
            roundedQuad[vertexIndex * 3] = roundedQuad[3];
            roundedQuad[vertexIndex * 3 + 1] = roundedQuad[4];
            roundedQuad[vertexIndex * 3 + 2] = roundedQuad[5];
            return vertexIndex + 1;
        }

        private int putCorner(Vec3 center, Vec3 u, Vec3 v, float halfSize, float cornerRadius,
                              float xSign, float ySign, float startDegrees, float endDegrees, int vertexIndex) {
            float radius = Math.max(0f, Math.min(cornerRadius, halfSize));
            float cornerX = xSign * (halfSize - radius);
            float cornerY = ySign * (halfSize - radius);
            for (int i = 0; i < CORNER_SEGMENTS; i++) {
                float t = CORNER_SEGMENTS == 1 ? 0f : (float) i / (float) (CORNER_SEGMENTS - 1);
                float angle = (float) Math.toRadians(startDegrees + (endDegrees - startDegrees) * t);
                float localX = cornerX + (float) Math.cos(angle) * radius;
                float localY = cornerY + (float) Math.sin(angle) * radius;
                putPoint(center.add(u.scale(localX)).add(v.scale(localY)), vertexIndex * 3);
                vertexIndex++;
            }
            return vertexIndex;
        }

        private void putPoint(Vec3 point, int offset) {
            roundedQuad[offset] = point.x;
            roundedQuad[offset + 1] = point.y;
            roundedQuad[offset + 2] = point.z;
        }

        private void setColorUniform(int color, Vec3 normal) {
            float alpha = Color.alpha(color) / 255f;
            float red = Color.red(color) / 255f;
            float green = Color.green(color) / 255f;
            float blue = Color.blue(color) / 255f;
            GLES20.glUniform4f(colorHandle, red, green, blue, alpha);
        }

        private int shadeColor(int color, Vec3 normal, float ambient) {
            float diffuse = Math.max(0f, normal.normalize().dot(LIGHT));
            float factor = Math.min(1.12f, ambient + diffuse * 0.28f);
            return Color.argb(Color.alpha(color),
                    clampColor(Color.red(color) * factor),
                    clampColor(Color.green(color) * factor),
                    clampColor(Color.blue(color) * factor));
        }

        private int clampColor(float value) {
            return Math.max(0, Math.min(255, Math.round(value)));
        }

        private int faceColor(char face) {
            switch (face) {
                case 'U':
                    return 0xfffbfbfb;
                case 'R':
                    return 0xffef4444;
                case 'F':
                    return 0xff3f9b46;
                case 'D':
                    return 0xfff5d142;
                case 'L':
                    return 0xfff28b24;
                case 'B':
                    return 0xff2d67cf;
                default:
                    return 0xff8c8c8c;
            }
        }

        private Vec3 rotateAroundAxis(Vec3 point, int axis, float angleDegrees) {
            float radians = (float) Math.toRadians(angleDegrees);
            float sin = (float) Math.sin(radians);
            float cos = (float) Math.cos(radians);
            switch (axis) {
                case 0:
                    return new Vec3(point.x, point.y * cos - point.z * sin, point.y * sin + point.z * cos);
                case 1:
                    return new Vec3(point.x * cos + point.z * sin, point.y, -point.x * sin + point.z * cos);
                default:
                    return new Vec3(point.x * cos - point.y * sin, point.x * sin + point.y * cos, point.z);
            }
        }

        private static Facelet[] buildFacelets() {
            List<Facelet> facelets = new ArrayList<>(54);
            addFace(facelets, new Vec3(0f, FACE_DISTANCE, 0f), new Vec3(0f, 1f, 0f), new Vec3(1f, 0f, 0f), new Vec3(0f, 0f, 1f));
            addFace(facelets, new Vec3(FACE_DISTANCE, 0f, 0f), new Vec3(1f, 0f, 0f), new Vec3(0f, 0f, -1f), new Vec3(0f, -1f, 0f));
            addFace(facelets, new Vec3(0f, 0f, FACE_DISTANCE), new Vec3(0f, 0f, 1f), new Vec3(1f, 0f, 0f), new Vec3(0f, -1f, 0f));
            addFace(facelets, new Vec3(0f, -FACE_DISTANCE, 0f), new Vec3(0f, -1f, 0f), new Vec3(1f, 0f, 0f), new Vec3(0f, 0f, -1f));
            addFace(facelets, new Vec3(-FACE_DISTANCE, 0f, 0f), new Vec3(-1f, 0f, 0f), new Vec3(0f, 0f, 1f), new Vec3(0f, -1f, 0f));
            addFace(facelets, new Vec3(0f, 0f, -FACE_DISTANCE), new Vec3(0f, 0f, -1f), new Vec3(-1f, 0f, 0f), new Vec3(0f, -1f, 0f));
            return facelets.toArray(new Facelet[0]);
        }

        private static void addFace(List<Facelet> facelets, Vec3 faceCenter, Vec3 normal, Vec3 u, Vec3 v) {
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    float uOffset = col - 1f;
                    float vOffset = row - 1f;
                    Vec3 center = faceCenter.add(u.scale(uOffset)).add(v.scale(vOffset));
                    float[] stickerCornerRadii = createStickerCornerRadii(row, col);
                    facelets.add(new Facelet(center, normal, u, v, stickerCornerRadii, scaleCornerRadii(stickerCornerRadii)));
                }
            }
        }

        private static float[] createStickerCornerRadii(int row, int col) {
            float[] radii = new float[4];
            if (row == 1 && col == 1) {
                radii[0] = STICKER_CORNER_RADIUS_LARGE;
                radii[1] = STICKER_CORNER_RADIUS_LARGE;
                radii[2] = STICKER_CORNER_RADIUS_LARGE;
                radii[3] = STICKER_CORNER_RADIUS_LARGE;
            } else if (row == 1) {
                float innerXSign = col == 0 ? 1f : -1f;
                setRadiiOnInnerSide(radii, innerXSign, 0f, STICKER_CORNER_RADIUS_LARGE);
            } else if (col == 1) {
                float innerYSign = row == 0 ? 1f : -1f;
                setRadiiOnInnerSide(radii, 0f, innerYSign, STICKER_CORNER_RADIUS_LARGE);
            } else {
                float innerXSign = col == 0 ? 1f : -1f;
                float innerYSign = row == 0 ? 1f : -1f;
                setRadiiOnInnerSide(radii, innerXSign, innerYSign, STICKER_CORNER_RADIUS_SMALL);
            }
            return radii;
        }

        private static void setRadiiOnInnerSide(float[] radii, float innerXSign, float innerYSign, float radius) {
            for (int i = 0; i < radii.length; i++) {
                float cornerXSign = i < 2 ? 1f : -1f;
                float cornerYSign = i == 1 || i == 2 ? 1f : -1f;
                if ((innerXSign == 0f || cornerXSign == innerXSign)
                        && (innerYSign == 0f || cornerYSign == innerYSign)) {
                    radii[i] = radius;
                }
            }
        }

        private static float[] scaleCornerRadii(float[] radii) {
            float[] scaled = new float[radii.length];
            for (int i = 0; i < radii.length; i++) {
                scaled[i] = radii[i] * SOLID_CORNER_RADIUS_SCALE;
            }
            return scaled;
        }

        private int createProgram(String vertexShaderSource, String fragmentShaderSource) {
            int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderSource);
            int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderSource);
            int shaderProgram = GLES20.glCreateProgram();
            GLES20.glAttachShader(shaderProgram, vertexShader);
            GLES20.glAttachShader(shaderProgram, fragmentShader);
            GLES20.glLinkProgram(shaderProgram);
            return shaderProgram;
        }

        private int loadShader(int type, String shaderSource) {
            int shader = GLES20.glCreateShader(type);
            GLES20.glShaderSource(shader, shaderSource);
            GLES20.glCompileShader(shader);
            return shader;
        }
    }

    private static class Facelet {
        final Vec3 center;
        final Vec3 normal;
        final Vec3 u;
        final Vec3 v;
        final float[] stickerCornerRadii;
        final float[] solidCornerRadii;

        Facelet(Vec3 center, Vec3 normal, Vec3 u, Vec3 v, float[] stickerCornerRadii, float[] solidCornerRadii) {
            this.center = center;
            this.normal = normal;
            this.u = u;
            this.v = v;
            this.stickerCornerRadii = stickerCornerRadii;
            this.solidCornerRadii = solidCornerRadii;
        }
    }

    private static class Transform {
        final Vec3 center;
        final Vec3 normal;
        final Vec3 u;
        final Vec3 v;

        Transform(Vec3 center, Vec3 normal, Vec3 u, Vec3 v) {
            this.center = center;
            this.normal = normal;
            this.u = u;
            this.v = v;
        }
    }

    private static class MoveSpec {
        private static final int[] FACE_AXIS = {1, 0, 2, 1, 0, 2};
        private static final int[] FACE_LAYER = {1, 1, 1, -1, -1, -1};
        private static final int[] FACE_SIGN = {-1, -1, -1, 1, 1, 1};

        final int axis;
        final int layer;
        final float angleDegrees;

        MoveSpec(int axis, int layer, float angleDegrees) {
            this.axis = axis;
            this.layer = layer;
            this.angleDegrees = angleDegrees;
        }

        static MoveSpec fromMove(int move) {
            int face = move / 3;
            int turns = move % 3 == 1 ? 2 : 1;
            int sign = FACE_SIGN[face];
            if (move % 3 == 2) {
                sign = -sign;
            }
            return new MoveSpec(FACE_AXIS[face], FACE_LAYER[face], sign * turns * 90f);
        }

        boolean belongsToLayer(Vec3 center) {
            float coordinate;
            switch (axis) {
                case 0:
                    coordinate = center.x;
                    break;
                case 1:
                    coordinate = center.y;
                    break;
                default:
                    coordinate = center.z;
                    break;
            }
            return layer > 0 ? coordinate > 0.5f : coordinate < -0.5f;
        }
    }

    private static class Vec3 {
        final float x;
        final float y;
        final float z;

        Vec3(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        Vec3 add(Vec3 other) {
            return new Vec3(x + other.x, y + other.y, z + other.z);
        }

        Vec3 scale(float scale) {
            return new Vec3(x * scale, y * scale, z * scale);
        }

        Vec3 normalize() {
            float length = (float) Math.sqrt(x * x + y * y + z * z);
            if (length <= 0f) {
                return this;
            }
            return new Vec3(x / length, y / length, z / length);
        }

        float dot(Vec3 other) {
            return x * other.x + y * other.y + z * other.z;
        }
    }

    private static class Quaternion {
        final float x;
        final float y;
        final float z;
        final float w;

        Quaternion(float x, float y, float z, float w) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.w = w;
        }

        static Quaternion normalized(float x, float y, float z, float w) {
            float length = (float) Math.sqrt(x * x + y * y + z * z + w * w);
            if (length <= 0f || Float.isNaN(length) || Float.isInfinite(length)) {
                return null;
            }
            return new Quaternion(x / length, y / length, z / length, w / length);
        }

        Quaternion normalized() {
            Quaternion normalized = normalized(x, y, z, w);
            return normalized == null ? this : normalized;
        }

        Quaternion conjugate() {
            return new Quaternion(-x, -y, -z, w);
        }

        Quaternion multiply(Quaternion other) {
            return new Quaternion(
                    w * other.x + x * other.w + y * other.z - z * other.y,
                    w * other.y - x * other.z + y * other.w + z * other.x,
                    w * other.z + x * other.y - y * other.x + z * other.w,
                    w * other.w - x * other.x - y * other.y - z * other.z);
        }

        Quaternion slerp(Quaternion other, float t) {
            float dot = x * other.x + y * other.y + z * other.z + w * other.w;
            Quaternion end = other;
            if (dot < 0f) {
                dot = -dot;
                end = new Quaternion(-other.x, -other.y, -other.z, -other.w);
            }
            t = Math.max(0f, Math.min(1f, t));
            if (dot > 0.9995f) {
                return normalized(
                        x + t * (end.x - x),
                        y + t * (end.y - y),
                        z + t * (end.z - z),
                        w + t * (end.w - w));
            }
            float theta0 = (float) Math.acos(dot);
            float theta = theta0 * t;
            float sinTheta = (float) Math.sin(theta);
            float sinTheta0 = (float) Math.sin(theta0);
            float scale0 = (float) Math.cos(theta) - dot * sinTheta / sinTheta0;
            float scale1 = sinTheta / sinTheta0;
            return new Quaternion(
                    scale0 * x + scale1 * end.x,
                    scale0 * y + scale1 * end.y,
                    scale0 * z + scale1 * end.z,
                    scale0 * w + scale1 * end.w).normalized();
        }

        void toMatrix(float[] matrix) {
            Matrix.setIdentityM(matrix, 0);
            float xx = x * x;
            float yy = y * y;
            float zz = z * z;
            float xy = x * y;
            float xz = x * z;
            float yz = y * z;
            float wx = w * x;
            float wy = w * y;
            float wz = w * z;
            matrix[0] = 1f - 2f * (yy + zz);
            matrix[1] = 2f * (xy + wz);
            matrix[2] = 2f * (xz - wy);
            matrix[4] = 2f * (xy - wz);
            matrix[5] = 1f - 2f * (xx + zz);
            matrix[6] = 2f * (yz + wx);
            matrix[8] = 2f * (xz + wy);
            matrix[9] = 2f * (yz - wx);
            matrix[10] = 1f - 2f * (xx + yy);
        }
    }
}
