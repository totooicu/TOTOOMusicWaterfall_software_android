package com.example.myapplication.controller;

import android.app.Activity;
import android.os.Handler;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.myapplication.R;
import com.example.myapplication.tool.ParamsManager;

public class ParamsController {
    private final Activity activity;
    private final ParamsManager paramsManager;
    private final Handler handler;

    private LinearLayout llRedChannel, llGreenChannel, llBlueChannel, llGlobalGain;
    private TextView tvSaveHint;
    private Button btnSaveParams, btnEditParams;

    private SeekBar sbRedLow, sbRedMid, sbRedHigh, sbRedGain;
    private TextView tvRedRange, tvRedGain;
    private Spinner spRedModel;

    private SeekBar sbGreenLow, sbGreenMid, sbGreenHigh, sbGreenGain;
    private TextView tvGreenRange, tvGreenGain;
    private Spinner spGreenModel;

    private SeekBar sbBlueLow, sbBlueMid, sbBlueHigh, sbBlueGain;
    private TextView tvBlueRange, tvBlueGain;
    private Spinner spBlueModel;

    private SeekBar sbBrightness, sbMicGain;
    private TextView tvBrightness, tvMicGain;

    private TextView tvBarLabelB, tvBarLabelG, tvBarLabelR;

    private boolean isEditingParams = false;

    private final String[] modelNames = {"均值模型 I₁", "增值模型 I₂", "减值模型 I₃", "山峰模型 I₄", "山谷模型 I₅"};
    private final String[] modelValues = {"mean", "increase", "decrease", "mountain", "valley"};

    public ParamsController(Activity activity, ParamsManager paramsManager) {
        this.activity = activity;
        this.paramsManager = paramsManager;
        this.handler = new Handler();

        initViews();
        initSpinners();
        bindEvents();
        updateUI();
        setEditable(false);
    }

    private void initViews() {
        llRedChannel = activity.findViewById(R.id.llRedChannel);
        llGreenChannel = activity.findViewById(R.id.llGreenChannel);
        llBlueChannel = activity.findViewById(R.id.llBlueChannel);
        llGlobalGain = activity.findViewById(R.id.llGlobalGain);
        tvSaveHint = activity.findViewById(R.id.tvSaveHint);
        btnSaveParams = activity.findViewById(R.id.btnSaveParams);
        btnEditParams = activity.findViewById(R.id.btnEditParams);

        sbRedLow = activity.findViewById(R.id.sbRedLow);
        sbRedMid = activity.findViewById(R.id.sbRedMid);
        sbRedHigh = activity.findViewById(R.id.sbRedHigh);
        sbRedGain = activity.findViewById(R.id.sbRedGain);
        tvRedRange = activity.findViewById(R.id.tvRedRange);
        tvRedGain = activity.findViewById(R.id.tvRedGain);
        spRedModel = activity.findViewById(R.id.spRedModel);

        sbGreenLow = activity.findViewById(R.id.sbGreenLow);
        sbGreenMid = activity.findViewById(R.id.sbGreenMid);
        sbGreenHigh = activity.findViewById(R.id.sbGreenHigh);
        sbGreenGain = activity.findViewById(R.id.sbGreenGain);
        tvGreenRange = activity.findViewById(R.id.tvGreenRange);
        tvGreenGain = activity.findViewById(R.id.tvGreenGain);
        spGreenModel = activity.findViewById(R.id.spGreenModel);

        sbBlueLow = activity.findViewById(R.id.sbBlueLow);
        sbBlueMid = activity.findViewById(R.id.sbBlueMid);
        sbBlueHigh = activity.findViewById(R.id.sbBlueHigh);
        sbBlueGain = activity.findViewById(R.id.sbBlueGain);
        tvBlueRange = activity.findViewById(R.id.tvBlueRange);
        tvBlueGain = activity.findViewById(R.id.tvBlueGain);
        spBlueModel = activity.findViewById(R.id.spBlueModel);

        sbBrightness = activity.findViewById(R.id.sbBrightness);
        sbMicGain = activity.findViewById(R.id.sbMicGain);
        tvBrightness = activity.findViewById(R.id.tvBrightness);
        tvMicGain = activity.findViewById(R.id.tvMicGain);

        tvBarLabelB = activity.findViewById(R.id.tvBarLabelB);
        tvBarLabelG = activity.findViewById(R.id.tvBarLabelG);
        tvBarLabelR = activity.findViewById(R.id.tvBarLabelR);
    }

    private void initSpinners() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_item, modelNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        if (spRedModel != null) spRedModel.setAdapter(adapter);
        if (spGreenModel != null) spGreenModel.setAdapter(adapter);
        if (spBlueModel != null) spBlueModel.setAdapter(adapter);
    }

    private void bindEvents() {
        if (btnSaveParams != null) {
            btnSaveParams.setOnClickListener(v -> {
                paramsManager.saveParams();
                if (tvSaveHint != null) {
                    tvSaveHint.setText("已保存");
                    tvSaveHint.setTextColor(ContextCompat.getColor(activity, R.color.success));
                }
                btnSaveParams.setBackgroundColor(ContextCompat.getColor(activity, R.color.success));
                handler.postDelayed(() -> {
                    btnSaveParams.setBackgroundColor(ContextCompat.getColor(activity, R.color.primary_blue));
                }, 800);
            });
        }

        if (btnEditParams != null) {
            btnEditParams.setOnClickListener(v -> {
                isEditingParams = !isEditingParams;
                setEditable(isEditingParams);
                btnEditParams.setText(isEditingParams ? "完成修改" : "修改参数");
                btnEditParams.setBackgroundColor(isEditingParams 
                        ? ContextCompat.getColor(activity, R.color.primary_orange) 
                        : ContextCompat.getColor(activity, R.color.primary_purple));
            });
        }

        bindChannelControls("red", sbRedLow, sbRedMid, sbRedHigh, sbRedGain, tvRedRange, tvRedGain, spRedModel);
        bindChannelControls("green", sbGreenLow, sbGreenMid, sbGreenHigh, sbGreenGain, tvGreenRange, tvGreenGain, spGreenModel);
        bindChannelControls("blue", sbBlueLow, sbBlueMid, sbBlueHigh, sbBlueGain, tvBlueRange, tvBlueGain, spBlueModel);

        if (sbBrightness != null) {
            sbBrightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    paramsManager.brightnessGain = 0.5f + progress * 0.1f;
                    if (tvBrightness != null) {
                        tvBrightness.setText(String.format("%.1fx", paramsManager.brightnessGain));
                    }
                    markUnsaved();
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }

        if (sbMicGain != null) {
            sbMicGain.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    paramsManager.micGainAmount = 1 + progress * 0.1f;
                    if (tvMicGain != null) {
                        tvMicGain.setText(String.format("%.1fx", paramsManager.micGainAmount));
                    }
                    markUnsaved();
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }
    }

    private void bindChannelControls(String channel, SeekBar sbLow, SeekBar sbMid, SeekBar sbHigh,
                                     SeekBar sbGain, TextView tvRange, TextView tvGain, Spinner spModel) {
        if (sbLow != null) {
            sbLow.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (!isEditingParams) {
                        seekBar.setProgress(getChannelParams(channel).low);
                        return;
                    }
                    getChannelParams(channel).low = progress;
                    updateRangeText(channel, tvRange);
                    updateBarLabels();
                    markUnsaved();
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }

        if (sbMid != null) {
            sbMid.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (!isEditingParams) {
                        seekBar.setProgress(getChannelParams(channel).mid);
                        return;
                    }
                    getChannelParams(channel).mid = progress;
                    updateRangeText(channel, tvRange);
                    updateBarLabels();
                    markUnsaved();
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }

        if (sbHigh != null) {
            sbHigh.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (!isEditingParams) {
                        seekBar.setProgress(getChannelParams(channel).high);
                        return;
                    }
                    getChannelParams(channel).high = progress;
                    updateRangeText(channel, tvRange);
                    updateBarLabels();
                    markUnsaved();
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }

        if (sbGain != null) {
            sbGain.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (!isEditingParams) {
                        seekBar.setProgress((int) (getChannelParams(channel).gain * 10));
                        return;
                    }
                    getChannelParams(channel).gain = progress / 10.0f;
                    if (tvGain != null) {
                        tvGain.setText(String.format("%.1f", getChannelParams(channel).gain));
                    }
                    markUnsaved();
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }

        if (spModel != null) {
            spModel.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (!isEditingParams) return;
                    getChannelParams(channel).model = modelValues[position];
                    updateBarLabels();
                    markUnsaved();
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });
        }
    }

    private ParamsManager.ChannelParams getChannelParams(String channel) {
        switch (channel) {
            case "red": return paramsManager.redParams;
            case "green": return paramsManager.greenParams;
            case "blue": return paramsManager.blueParams;
            default: return paramsManager.redParams;
        }
    }

    private void updateRangeText(String channel, TextView tvRange) {
        if (tvRange == null) return;
        ParamsManager.ChannelParams p = getChannelParams(channel);
        tvRange.setText(String.format("%d-%d-%dHz", p.low, p.mid, p.high));
    }

    private void updateBarLabels() {
        ParamsManager.ChannelParams pr = paramsManager.redParams;
        ParamsManager.ChannelParams pg = paramsManager.greenParams;
        ParamsManager.ChannelParams pb = paramsManager.blueParams;

        if (tvBarLabelR != null) {
            tvBarLabelR.setText(String.format("红通道 %d-%d-%dHz · %s", pr.low, pr.mid, pr.high, getModelDisplayName(pr.model)));
        }
        if (tvBarLabelG != null) {
            tvBarLabelG.setText(String.format("绿通道 %d-%d-%dHz · %s", pg.low, pg.mid, pg.high, getModelDisplayName(pg.model)));
        }
        if (tvBarLabelB != null) {
            tvBarLabelB.setText(String.format("蓝通道 %d-%d-%dHz · %s", pb.low, pb.mid, pb.high, getModelDisplayName(pb.model)));
        }
    }

    private String getModelDisplayName(String model) {
        for (int i = 0; i < modelValues.length; i++) {
            if (modelValues[i].equals(model)) {
                return modelNames[i];
            }
        }
        return model;
    }

    private void updateUI() {
        updateChannelUI("red", sbRedLow, sbRedMid, sbRedHigh, sbRedGain, tvRedRange, tvRedGain, spRedModel);
        updateChannelUI("green", sbGreenLow, sbGreenMid, sbGreenHigh, sbGreenGain, tvGreenRange, tvGreenGain, spGreenModel);
        updateChannelUI("blue", sbBlueLow, sbBlueMid, sbBlueHigh, sbBlueGain, tvBlueRange, tvBlueGain, spBlueModel);

        if (sbBrightness != null) {
            sbBrightness.setProgress((int) ((paramsManager.brightnessGain - 0.5) / 0.1));
        }
        if (tvBrightness != null) {
            tvBrightness.setText(String.format("%.1fx", paramsManager.brightnessGain));
        }

        if (sbMicGain != null) {
            sbMicGain.setProgress((int) ((paramsManager.micGainAmount - 1) / 0.1));
        }
        if (tvMicGain != null) {
            tvMicGain.setText(String.format("%.1fx", paramsManager.micGainAmount));
        }

        updateBarLabels();
    }

    private void updateChannelUI(String channel, SeekBar sbLow, SeekBar sbMid, SeekBar sbHigh,
                                  SeekBar sbGain, TextView tvRange, TextView tvGain, Spinner spModel) {
        ParamsManager.ChannelParams p = getChannelParams(channel);
        if (sbLow != null) sbLow.setProgress(p.low);
        if (sbMid != null) sbMid.setProgress(p.mid);
        if (sbHigh != null) sbHigh.setProgress(p.high);
        if (sbGain != null) sbGain.setProgress((int) (p.gain * 10));
        if (tvRange != null) tvRange.setText(String.format("%d-%d-%dHz", p.low, p.mid, p.high));
        if (tvGain != null) tvGain.setText(String.format("%.1f", p.gain));

        if (spModel != null) {
            for (int i = 0; i < modelValues.length; i++) {
                if (modelValues[i].equals(p.model)) {
                    spModel.setSelection(i);
                    break;
                }
            }
        }
    }

    private void setEditable(boolean editable) {
        setGroupEnabled(llRedChannel, editable);
        setGroupEnabled(llGreenChannel, editable);
        setGroupEnabled(llBlueChannel, editable);
        setGroupEnabled(llGlobalGain, editable);
    }

    private void setGroupEnabled(View view, boolean enabled) {
        if (view == null) return;
        view.setEnabled(enabled);
        view.setAlpha(enabled ? 1.0f : 0.55f);
    }

    private void markUnsaved() {
        if (tvSaveHint != null) {
            tvSaveHint.setText("有未保存修改");
            tvSaveHint.setTextColor(ContextCompat.getColor(activity, R.color.warning));
        }
    }
}