package com.example.myapplication.controller;

import android.app.Activity;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.myapplication.R;

public class HeaderController {
    private final Activity activity;
    private final TextView tvStatus;
    private final TextView tvNoise;

    public HeaderController(Activity activity) {
        this.activity = activity;
        tvStatus = activity.findViewById(R.id.tvStatus);
        tvNoise = activity.findViewById(R.id.tvNoise);
    }

    public void setStatus(String text) {
        setStatus(text, "info");
    }

    public void setStatus(String text, String type) {
        if (tvStatus != null) {
            tvStatus.setText(text);
            switch (type) {
                case "error":
                    tvStatus.setTextColor(ContextCompat.getColor(activity, R.color.error));
                    break;
                case "success":
                    tvStatus.setTextColor(ContextCompat.getColor(activity, R.color.success));
                    break;
                default:
                    tvStatus.setTextColor(ContextCompat.getColor(activity, R.color.text_status));
                    break;
            }
        }
    }

    public void updateNoise(double db, boolean isMicActive) {
        if (tvNoise == null) return;
        
        if (isMicActive) {
            double noiseDb = Math.max(0, db + 94);
            String noiseText = String.format("%.1f", noiseDb);
            tvNoise.setText(String.format("环境噪音: %s dB", noiseText));

            if (noiseDb < 50) {
                tvNoise.setTextColor(ContextCompat.getColor(activity, R.color.success));
            } else if (noiseDb < 75) {
                tvNoise.setTextColor(ContextCompat.getColor(activity, R.color.warning));
            } else {
                tvNoise.setTextColor(ContextCompat.getColor(activity, R.color.error));
            }
        } else {
            tvNoise.setText("环境噪音: -- dB");
            tvNoise.setTextColor(ContextCompat.getColor(activity, R.color.text_secondary));
        }
    }
}