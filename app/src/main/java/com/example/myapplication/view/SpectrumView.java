package com.example.myapplication.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class SpectrumView extends View {

    private static final int MAX_FREQ = 2000;
    private static final int SAMPLE_RATE = 44100;
    private static final int FFT_SIZE = 2048;

    private Paint paint;
    private double[] frequencyData;
    private int[] channelParams;
    private String[] channelModels;
    private double freqPerBin = SAMPLE_RATE / (double) FFT_SIZE;

    public SpectrumView(Context context) {
        super(context);
        init();
    }

    public SpectrumView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SpectrumView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint = new Paint();
        paint.setAntiAlias(true);
        frequencyData = new double[0];
        channelParams = new int[9];
        channelModels = new String[]{"mountain", "mountain", "decrease"};
    }

    public void updateData(double[] data, int[] params) {
        this.frequencyData = data;
        this.channelParams = params;
        invalidate();
    }

    public void updateData(double[] data, int[] params, String[] models) {
        this.frequencyData = data;
        this.channelParams = params;
        this.channelModels = models;
        invalidate();
    }

    public void setFreqPerBin(double freq) {
        this.freqPerBin = freq;
        invalidate();
    }

    public void updateModels(String[] models) {
        this.channelModels = models;
        invalidate();
    }

    public void clear() {
        frequencyData = new double[0];
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();

        paint.setColor(Color.rgb(15, 23, 42));
        canvas.drawRect(0, 0, width, height, paint);

        if (frequencyData == null || frequencyData.length == 0) {
            return;
        }

        drawMarkers(canvas, width, height);
        drawGrid(canvas, width, height);
        drawBars(canvas, width, height);
    }

    private void drawMarkers(Canvas canvas, int width, int height) {
        int[] colors = {
                Color.argb(89, 239, 68, 68),
                Color.argb(89, 34, 197, 94),
                Color.argb(89, 59, 130, 246)
        };

        for (int ch = 0; ch < 3; ch++) {
            int low = channelParams[ch * 3];
            int high = channelParams[ch * 3 + 2];

            float xL = (low / (float) MAX_FREQ) * width;
            float xR = (high / (float) MAX_FREQ) * width;

            paint.setColor(colors[ch]);
            canvas.drawRect(xL, 0, xR, height, paint);

            paint.setColor(Color.argb(229, Color.red(colors[ch]), Color.green(colors[ch]), Color.blue(colors[ch])));
            paint.setStrokeWidth(1);
            for (int i = 0; i < 3; i++) {
                float x = (channelParams[ch * 3 + i] / (float) MAX_FREQ) * width;
                canvas.drawLine(x, 0, x, height, paint);
            }
        }
    }

    private void drawGrid(Canvas canvas, int width, int height) {
        paint.setColor(Color.argb(26, 255, 255, 255));
        paint.setStrokeWidth(1);

        int[] freqs = {0, 200, 400, 600, 800, 1000, 1200, 1400, 1600, 1800, 2000};
        for (int freq : freqs) {
            float x = (freq / (float) MAX_FREQ) * width;
            canvas.drawLine(x, 0, x, height, paint);

            paint.setColor(Color.rgb(100, 116, 139));
            paint.setTextSize(11);
            canvas.drawText(String.valueOf(freq), x - 20, height - 4, paint);
        }
    }

    private void drawBars(Canvas canvas, int width, int height) {
        int maxBin = (int) (MAX_FREQ / freqPerBin) + 1;

        double maxEnergy = 0;
        for (int i = 0; i < frequencyData.length && i < maxBin; i++) {
            if (frequencyData[i] > maxEnergy) {
                maxEnergy = frequencyData[i];
            }
        }

        if (maxEnergy == 0) maxEnergy = 1;

        float barWidth = width / (float) maxBin;

        for (int i = 0; i < frequencyData.length && i < maxBin; i++) {
            double energy = frequencyData[i];
            float barHeight = (float) (energy / maxEnergy) * height;
            float x = i * barWidth;
            float y = height - barHeight;

            float v = (float) (i * freqPerBin);
            float r = getModelWeight(v, channelParams[0], channelParams[1], channelParams[2], channelModels[0]);
            float g = getModelWeight(v, channelParams[3], channelParams[4], channelParams[5], channelModels[1]);
            float b = getModelWeight(v, channelParams[6], channelParams[7], channelParams[8], channelModels[2]);

            paint.setColor(Color.rgb((int) (r * 255), (int) (g * 255), (int) (b * 255)));
            canvas.drawRect(x, y, x + barWidth - 1, height, paint);
        }
    }

    private float getModelWeight(float v, int low, int mid, int high, String model) {
        if (v < low || v > high) return 0;

        switch (model) {
            case "mean":
                return 1;
            case "increase":
                return (v - low) / Math.max(1, high - low);
            case "decrease":
                return (high - v) / Math.max(1, high - low);
            case "mountain":
                if (v <= mid) return (v - low) / Math.max(1, mid - low);
                return (high - v) / Math.max(1, high - mid);
            case "valley":
                if (v <= mid) return (mid - v) / Math.max(1, mid - low);
                return (v - mid) / Math.max(1, high - mid);
            default:
                return 0;
        }
    }
}