package com.example.myapplication.controller;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.example.myapplication.R;
import com.example.myapplication.tool.AudioAnalyzer;
import com.example.myapplication.tool.ParamsManager;
import com.example.myapplication.view.RingView;
import com.example.myapplication.view.SpectrumView;

public class RGBController {
    private final Activity activity;
    private final AudioAnalyzer audioAnalyzer;
    private final ParamsManager paramsManager;
    private BluetoothController bluetoothController;

    private RingView ringView;
    private View vInnerCore;
    private TextView tvRValue, tvGValue, tvBValue, tvDbValue, tvFreqValue;
    private SpectrumView spectrumView;

    private View vBarB, vBarG, vBarR;

    private double lastR = 0, lastG = 0, lastB = 0;
    private final double smoothFactor = 0.65;

    private static final double GAMMA = 2.2;
    private static final double GAMMA_INV = 1.0 / GAMMA;

    public RGBController(Activity activity, AudioAnalyzer audioAnalyzer, ParamsManager paramsManager, BluetoothController bluetoothController) {
        this.activity = activity;
        this.audioAnalyzer = audioAnalyzer;
        this.paramsManager = paramsManager;
        this.bluetoothController = bluetoothController;

        ringView = activity.findViewById(R.id.ringView);
        vInnerCore = activity.findViewById(R.id.vInnerCore);
        tvRValue = activity.findViewById(R.id.tvRValue);
        tvGValue = activity.findViewById(R.id.tvGValue);
        tvBValue = activity.findViewById(R.id.tvBValue);
        tvDbValue = activity.findViewById(R.id.tvDbValue);
        tvFreqValue = activity.findViewById(R.id.tvFreqValue);
        spectrumView = activity.findViewById(R.id.spectrumView);
        vBarB = activity.findViewById(R.id.vBarB);
        vBarG = activity.findViewById(R.id.vBarG);
        vBarR = activity.findViewById(R.id.vBarR);
    }

    public void updateRGB(double[] frequencyData) {
        ParamsManager.ChannelParams pr = paramsManager.redParams;
        ParamsManager.ChannelParams pg = paramsManager.greenParams;
        ParamsManager.ChannelParams pb = paramsManager.blueParams;

        double rRaw = audioAnalyzer.calcModel(frequencyData, pr.low, pr.mid, pr.high, pr.model);
        double gRaw = audioAnalyzer.calcModel(frequencyData, pg.low, pg.mid, pg.high, pg.model);
        double bRaw = audioAnalyzer.calcModel(frequencyData, pb.low, pb.mid, pb.high, pb.model);

        double brightness = paramsManager.brightnessGain;

        rRaw = applyGamma(rRaw) * pr.gain * brightness;
        gRaw = applyGamma(gRaw) * pg.gain * brightness;
        bRaw = applyGamma(bRaw) * pb.gain * brightness;

        rRaw = Math.min(1.0, Math.max(0.0, rRaw));
        gRaw = Math.min(1.0, Math.max(0.0, gRaw));
        bRaw = Math.min(1.0, Math.max(0.0, bRaw));

        double r = lastR * smoothFactor + rRaw * (1 - smoothFactor);
        double g = lastG * smoothFactor + gRaw * (1 - smoothFactor);
        double b = lastB * smoothFactor + bRaw * (1 - smoothFactor);

        lastR = r;
        lastG = g;
        lastB = b;

        int rInt = (int) Math.round(r * 255);
        int gInt = (int) Math.round(g * 255);
        int bInt = (int) Math.round(b * 255);

        if (ringView != null) ringView.updateColor(rInt, gInt, bInt);
        if (vInnerCore != null) {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.OVAL);
            drawable.setColor(Color.rgb(rInt, gInt, bInt));
            vInnerCore.setBackground(drawable);
        }

        if (tvRValue != null) tvRValue.setText(String.valueOf(rInt));
        if (tvGValue != null) tvGValue.setText(String.valueOf(gInt));
        if (tvBValue != null) tvBValue.setText(String.valueOf(bInt));

        if (bluetoothController != null && bluetoothController.isConnected()) {
            byte[] bands = buildAudioBands(frequencyData);
            bluetoothController.sendAudioFrame(rInt, gInt, bInt, bands);
        }

        int[] channelParams = {
                pr.low, pr.mid, pr.high,
                pg.low, pg.mid, pg.high,
                pb.low, pb.mid, pb.high
        };
        String[] channelModels = {pr.model, pg.model, pb.model};
        if (spectrumView != null) {
            spectrumView.setFreqPerBin(audioAnalyzer.getFreqPerBin());
            spectrumView.updateData(frequencyData, channelParams, channelModels);
        }

         rRaw = audioAnalyzer.calcModel(frequencyData, pr.low, pr.mid, pr.high, pr.model);
         gRaw = audioAnalyzer.calcModel(frequencyData, pg.low, pg.mid, pg.high, pg.model);
         bRaw = audioAnalyzer.calcModel(frequencyData, pb.low, pb.mid, pb.high, pb.model);
        
        Log.d("RGBController", "Raw values - R:" + rRaw + " G:" + gRaw + " B:" + bRaw);
        Log.d("RGBController", "Params - R[" + pr.low + "," + pr.mid + "," + pr.high + "] G[" + pg.low + "," + pg.mid + "," + pg.high + "] B[" + pb.low + "," + pb.mid + "," + pb.high + "]");
        
        double rBar = applyGamma(rRaw) * pr.gain * brightness;
        double gBar = applyGamma(gRaw) * pg.gain * brightness;
        double bBar = applyGamma(bRaw) * pb.gain * brightness;

        updateBarWidth(vBarR, rBar);
        updateBarWidth(vBarG, gBar);
        updateBarWidth(vBarB, bBar);
    }

    private double applyGamma(double value) {
        return Math.pow(value, GAMMA_INV);
    }

    /**
     * 将归一化(0~1)的 FFT 数组聚合成硬件使用的 16×100Hz 频段电平(0~255)，
     * 与固件 DRV_MIC_FREQ_BAND=100Hz / DRV_MIC_FFT_BINS=16 对齐。
     */
    private byte[] buildAudioBands(double[] frequencyData) {
        int bandCount = BluetoothController.AUDIO_BAND_COUNT;
        byte[] bands = new byte[bandCount];
        if (frequencyData == null || frequencyData.length == 0) {
            return bands;
        }

        double freqPerBin = audioAnalyzer.getFreqPerBin();
        if (freqPerBin <= 0) {
            freqPerBin = 44100.0 / 2048.0;
        }
        int binsPerBand = Math.max(1, (int) Math.round(100.0 / freqPerBin));

        for (int band = 0; band < bandCount; band++) {
            double sum = 0;
            int count = 0;
            int start = band * binsPerBand;
            int end = Math.min(start + binsPerBand, frequencyData.length);
            for (int i = start; i < end; i++) {
                sum += frequencyData[i];
                count++;
            }
            double avg = count > 0 ? sum / count : 0;
            avg = Math.max(0, Math.min(1, avg));
            bands[band] = (byte) Math.round(avg * 255);
        }
        return bands;
    }

    private void updateBarWidth(View bar, double value) {
        if (bar == null) return;
        int percent = (int) Math.min(100, Math.round(value * 100));
        View parent = (View) bar.getParent();
        if (parent != null && parent.getWidth() > 0) {
            bar.getLayoutParams().width = percent > 0 ? (int) (parent.getWidth() * percent / 100.0) : 0;
            bar.requestLayout();
        }
    }

    public void updateDisplay(double db, int peakFreq) {
        if (tvDbValue != null) tvDbValue.setText(String.format("%.1f", db));
        if (tvFreqValue != null) tvFreqValue.setText(String.valueOf(peakFreq));
    }

    public void reset() {
        if (ringView != null) ringView.reset();
        if (vInnerCore != null) {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.OVAL);
            drawable.setColor(Color.BLACK);
            vInnerCore.setBackground(drawable);
        }

        lastR = 0;
        lastG = 0;
        lastB = 0;

        if (tvRValue != null) tvRValue.setText("0");
        if (tvGValue != null) tvGValue.setText("0");
        if (tvBValue != null) tvBValue.setText("0");
        if (tvDbValue != null) tvDbValue.setText("0.0");
        if (tvFreqValue != null) tvFreqValue.setText("0");

        updateBarWidth(vBarB, 0);
        updateBarWidth(vBarG, 0);
        updateBarWidth(vBarR, 0);

        if (spectrumView != null) spectrumView.clear();
    }
}