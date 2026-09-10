package com.example.myapplication.tool;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class ParamsManager {

    private static final String STORAGE_KEY = "rgb_atmosphere_light_params_v4";
    private static final String PREFS_NAME = "RGBParams";

    private Context context;
    private SharedPreferences prefs;
    private Gson gson;

    public ChannelParams redParams = new ChannelParams(800, 1200, 1600, 1.5f, "mountain");
    public ChannelParams greenParams = new ChannelParams(400, 600, 800, 1.5f, "mountain");
    public ChannelParams blueParams = new ChannelParams(0, 200, 400, 1.2f, "mean");

    public float brightnessGain = 2.0f;
    public float micGainAmount = 15.0f;
    public float playbackRate = 1.0f;

    public static class ChannelParams {
        public int low;
        public int mid;
        public int high;
        public float gain;
        public String model;

        public ChannelParams() {}

        public ChannelParams(int low, int mid, int high, float gain, String model) {
            this.low = low;
            this.mid = mid;
            this.high = high;
            this.gain = gain;
            this.model = model;
        }
    }

    public ParamsManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.gson = new Gson();
        loadParams();
    }

    public void saveParams() {
        Map<String, Object> params = new HashMap<>();
        
        params.put("red", redParams);
        params.put("green", greenParams);
        params.put("blue", blueParams);
        params.put("brightnessGain", brightnessGain);
        params.put("micGainAmount", micGainAmount);
        params.put("playbackRate", playbackRate);

        String json = gson.toJson(params);
        prefs.edit().putString(STORAGE_KEY, json).apply();
    }

    public void loadParams() {
        String saved = prefs.getString(STORAGE_KEY, null);
        if (saved == null) return;

        try {
            Type type = new TypeToken<Map<String, Object>>() {}.getType();
            Map<String, Object> params = gson.fromJson(saved, type);

            if (params.containsKey("red")) {
                String redJson = gson.toJson(params.get("red"));
                ChannelParams red = gson.fromJson(redJson, ChannelParams.class);
                if (red != null) redParams = red;
            }
            if (params.containsKey("green")) {
                String greenJson = gson.toJson(params.get("green"));
                ChannelParams green = gson.fromJson(greenJson, ChannelParams.class);
                if (green != null) greenParams = green;
            }
            if (params.containsKey("blue")) {
                String blueJson = gson.toJson(params.get("blue"));
                ChannelParams blue = gson.fromJson(blueJson, ChannelParams.class);
                if (blue != null) blueParams = blue;
            }

            if (params.containsKey("brightnessGain")) {
                brightnessGain = ((Number) params.get("brightnessGain")).floatValue();
            }
            if (params.containsKey("micGainAmount")) {
                micGainAmount = ((Number) params.get("micGainAmount")).floatValue();
            }
            if (params.containsKey("playbackRate")) {
                playbackRate = ((Number) params.get("playbackRate")).floatValue();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}