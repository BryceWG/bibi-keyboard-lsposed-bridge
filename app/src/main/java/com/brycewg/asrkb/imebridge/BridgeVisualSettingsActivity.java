/*
 * Simple native settings screen for bridge capture strip visuals.
 *
 * Module: lsposed-ime-bridge
 */
package com.brycewg.asrkb.imebridge;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

public final class BridgeVisualSettingsActivity extends Activity {
    private BridgeVisualPrefs.VisualConfig visualConfig;
    private TextView widthLabel;
    private TextView heightLabel;
    private BridgeWaveformPreviewView idlePreview;
    private BridgeWaveformPreviewView recordingPreview;
    private SeekBar widthSeekBar;
    private SeekBar heightSeekBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        visualConfig = BridgeVisualPrefs.readForSettings(this);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(24), dp(20), dp(32));
        scrollView.addView(content, new ScrollView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView title = new TextView(this);
        title.setText(R.string.bridge_visual_title);
        title.setTextSize(24);
        title.setTextColor(resolveTextColor(android.R.attr.textColorPrimary, Color.rgb(28, 28, 28)));
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        content.addView(title, matchWrap());

        TextView summary = new TextView(this);
        summary.setText(R.string.bridge_visual_summary);
        summary.setTextSize(14);
        summary.setLineSpacing(0f, 1.12f);
        summary.setTextColor(resolveTextColor(android.R.attr.textColorSecondary, Color.rgb(96, 96, 96)));
        LinearLayout.LayoutParams summaryParams = matchWrap();
        summaryParams.topMargin = dp(8);
        content.addView(summary, summaryParams);

        content.addView(createPreviewPanel(), topMargin(dp(22)));
        content.addView(createSliderPanel(), topMargin(dp(16)));

        Button resetButton = new Button(this);
        resetButton.setText(R.string.bridge_visual_restore_defaults);
        resetButton.setMinHeight(dp(48));
        resetButton.setAllCaps(false);
        resetButton.setOnClickListener(v -> {
            applyConfig(BridgeVisualPrefs.defaults(), true);
        });
        content.addView(resetButton, topMargin(dp(18)));

        setContentView(scrollView);
    }

    private LinearLayout createPreviewPanel() {
        LinearLayout panel = createPanel();
        TextView label = sectionLabel(R.string.bridge_visual_preview_title);
        panel.addView(label, matchWrap());

        TextView idleLabel = smallLabel(R.string.bridge_visual_preview_idle);
        panel.addView(idleLabel, topMargin(dp(12)));
        idlePreview = new BridgeWaveformPreviewView(this, false);
        idlePreview.setVisualConfig(visualConfig);
        panel.addView(idlePreview, matchWrap());

        TextView recordingLabel = smallLabel(R.string.bridge_visual_preview_recording);
        panel.addView(recordingLabel, topMargin(dp(8)));
        recordingPreview = new BridgeWaveformPreviewView(this, true);
        recordingPreview.setVisualConfig(visualConfig);
        panel.addView(recordingPreview, matchWrap());
        return panel;
    }

    private LinearLayout createSliderPanel() {
        LinearLayout panel = createPanel();

        widthLabel = sectionLabel(0);
        panel.addView(widthLabel, matchWrap());
        widthSeekBar = new SeekBar(this);
        widthSeekBar.setMax(BridgeVisualPrefs.MAX_WIDTH_DP - BridgeVisualPrefs.MIN_WIDTH_DP);
        widthSeekBar.setProgress(visualConfig.widthDp - BridgeVisualPrefs.MIN_WIDTH_DP);
        widthSeekBar.setMinHeight(dp(48));
        widthSeekBar.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                applyConfig(
                    new BridgeVisualPrefs.VisualConfig(
                        BridgeVisualPrefs.MIN_WIDTH_DP + progress,
                        visualConfig.heightDp
                    ),
                    true
                );
            }
        });
        panel.addView(widthSeekBar, topMargin(dp(8)));

        heightLabel = sectionLabel(0);
        LinearLayout.LayoutParams heightLabelParams = topMargin(dp(18));
        panel.addView(heightLabel, heightLabelParams);
        heightSeekBar = new SeekBar(this);
        heightSeekBar.setMax(BridgeVisualPrefs.MAX_HEIGHT_DP - BridgeVisualPrefs.MIN_HEIGHT_DP);
        heightSeekBar.setProgress(visualConfig.heightDp - BridgeVisualPrefs.MIN_HEIGHT_DP);
        heightSeekBar.setMinHeight(dp(48));
        heightSeekBar.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                applyConfig(
                    new BridgeVisualPrefs.VisualConfig(
                        visualConfig.widthDp,
                        BridgeVisualPrefs.MIN_HEIGHT_DP + progress
                    ),
                    true
                );
            }
        });
        panel.addView(heightSeekBar, topMargin(dp(8)));

        updateLabels();
        return panel;
    }

    private void applyConfig(BridgeVisualPrefs.VisualConfig config, boolean save) {
        visualConfig = config;
        updateLabels();
        if (idlePreview != null) idlePreview.setVisualConfig(config);
        if (recordingPreview != null) recordingPreview.setVisualConfig(config);
        if (widthSeekBar != null) {
            widthSeekBar.setProgress(config.widthDp - BridgeVisualPrefs.MIN_WIDTH_DP);
        }
        if (heightSeekBar != null) {
            heightSeekBar.setProgress(config.heightDp - BridgeVisualPrefs.MIN_HEIGHT_DP);
        }
        if (save) BridgeVisualPrefs.saveForSettings(this, config);
    }

    private void updateLabels() {
        if (widthLabel != null) {
            widthLabel.setText(getString(R.string.bridge_visual_width_value, visualConfig.widthDp));
        }
        if (heightLabel != null) {
            heightLabel.setText(getString(R.string.bridge_visual_height_value, visualConfig.heightDp));
        }
    }

    private LinearLayout createPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(16), dp(16), dp(16));
        GradientDrawable background = new GradientDrawable();
        background.setColor(resolvePanelColor());
        background.setCornerRadius(dp(12));
        panel.setBackground(background);
        return panel;
    }

    private TextView sectionLabel(int stringRes) {
        TextView textView = new TextView(this);
        if (stringRes != 0) textView.setText(stringRes);
        textView.setTextSize(16);
        textView.setTypeface(textView.getTypeface(), android.graphics.Typeface.BOLD);
        textView.setTextColor(resolveTextColor(android.R.attr.textColorPrimary, Color.rgb(32, 32, 32)));
        return textView;
    }

    private TextView smallLabel(int stringRes) {
        TextView textView = new TextView(this);
        textView.setText(stringRes);
        textView.setTextSize(13);
        textView.setTextColor(resolveTextColor(android.R.attr.textColorSecondary, Color.rgb(96, 96, 96)));
        return textView;
    }

    private int resolvePanelColor() {
        return Color.argb(20, 128, 128, 128);
    }

    private int resolveTextColor(int attr, int fallback) {
        try {
            android.util.TypedValue value = new android.util.TypedValue();
            if (getTheme().resolveAttribute(attr, value, true)) {
                if (value.resourceId != 0) return getColor(value.resourceId);
                if (value.data != 0) return value.data;
            }
        } catch (Throwable ignored) {
        }
        return fallback;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams topMargin(int margin) {
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = margin;
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private abstract static class SimpleSeekListener implements SeekBar.OnSeekBarChangeListener {
        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }
    }
}
