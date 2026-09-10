package com.example.myapplication.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class RingView extends View {

    private static final int RING_COUNT = 100;
    private static final float CORE_RADIUS_RATIO = 0.5f;
    private static final float MAX_RADIUS_RATIO = 1.5f;

    private Paint paint;
    private int[] ringHistoryR;
    private int[] ringHistoryG;
    private int[] ringHistoryB;
    private int ringHead;

    public RingView(Context context) {
        super(context);
        init();
    }

    public RingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public RingView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint = new Paint();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);

        ringHistoryR = new int[RING_COUNT];
        ringHistoryG = new int[RING_COUNT];
        ringHistoryB = new int[RING_COUNT];
        ringHead = 0;
    }

    public void updateColor(int r, int g, int b) {
        ringHead = (ringHead + 1) % RING_COUNT;
        ringHistoryR[ringHead] = r;
        ringHistoryG[ringHead] = g;
        ringHistoryB[ringHead] = b;
        invalidate();
    }

    public void reset() {
        for (int i = 0; i < RING_COUNT; i++) {
            ringHistoryR[i] = 0;
            ringHistoryG[i] = 0;
            ringHistoryB[i] = 0;
        }
        ringHead = 0;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();
        int centerX = width / 2;
        int centerY = height / 2;

        float containerSize = Math.min(width, height);
        float baseRadius = containerSize / (2 * MAX_RADIUS_RATIO);
        float ringStep = (MAX_RADIUS_RATIO - CORE_RADIUS_RATIO) / RING_COUNT;

        paint.setStrokeWidth(baseRadius * ringStep);

        for (int i = 0; i < RING_COUNT; i++) {
            int historyIndex = (ringHead - i + RING_COUNT) % RING_COUNT;
            int r = ringHistoryR[historyIndex];
            int g = ringHistoryG[historyIndex];
            int b = ringHistoryB[historyIndex];

            float radius = baseRadius * (CORE_RADIUS_RATIO + (i + 0.5f) * ringStep);

            paint.setColor(Color.rgb(r, g, b));
            canvas.drawCircle(centerX, centerY, radius, paint);
        }
    }
}