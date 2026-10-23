package org.telegram.ui.Components;

import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Экран «Эквалайзер»: анализатор спектра в реальном времени + 5 полос с пресетами.
 * Открывается из меню «три точки» плеера.
 */
public class EqualizerSheet extends BottomSheet implements EqualizerBandsView.Delegate, SpectrumView.Delegate {

    private static final String[] PRESET_NAMES = {
            "Rock", "Pop", "Jazz", "Vocal", "Bass", "Treble", "Electronic", "Acoustic"
    };
    private static final float[][] PRESET_VALUES = {
            {4.5f, 2.5f, -1f, 2.5f, 4f},
            {-1f, 2f, 4f, 2f, -1f},
            {3f, 1.5f, -1f, 1.5f, 3f},
            {-2f, 0f, 4f, 3f, 0f},
            {7f, 4f, 1f, 0f, 0f},
            {0f, 0f, 0f, 3f, 6f},
            {5f, 2f, 0f, 2f, 5f},
            {2f, 1f, 2f, 3f, 2f}
    };

    private final Context context;
    private final SpectrumView spectrumView;
    private final EqualizerBandsView bandsView;
    private final LinearLayout presetsLayout;
    private final TextView hintTextView;
    private final TextView enableLabel;
    private final Switch enableSwitch;

    private final List<TextView> presetViews = new ArrayList<>();
    private boolean changingState;

    public EqualizerSheet(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, false, true, resourcesProvider);
        this.context = context;

        TextView titleTextView = new TextView(context);
        titleTextView.setText("Эквалайзер");
        titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        titleTextView.setTypeface(AndroidUtilities.bold());
        titleTextView.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        titleTextView.setGravity(Gravity.CENTER);
        titleTextView.setEllipsize(TextUtils.TruncateAt.END);

        ImageView closeButton = new ImageView(context);
        closeButton.setImageResource(R.drawable.ic_ab_close);
        closeButton.setColorFilter(getThemedColor(Theme.key_dialogTextBlack));
        closeButton.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourcesProvider), 1, AndroidUtilities.dp(18)));
        closeButton.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(10), AndroidUtilities.dp(10), AndroidUtilities.dp(10));
        closeButton.setOnClickListener(v -> dismiss());

        spectrumView = new SpectrumView(context, resourcesProvider, this);

        hintTextView = new TextView(context);
        hintTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        hintTextView.setGravity(Gravity.CENTER);
        hintTextView.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(2), AndroidUtilities.dp(16), 0);
        hintTextView.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        hintTextView.setVisibility(View.GONE);

        presetsLayout = new LinearLayout(context);
        presetsLayout.setOrientation(LinearLayout.HORIZONTAL);
        HorizontalScrollView presetsScroll = new HorizontalScrollView(context);
        presetsScroll.setHorizontalScrollBarEnabled(false);
        presetsScroll.addView(presetsLayout, new HorizontalScrollView.LayoutParams(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        bandsView = new EqualizerBandsView(context, resourcesProvider);
        bandsView.setDelegate(this);

        enableLabel = new TextView(context);
        enableLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        enableLabel.setTextColor(getThemedColor(Theme.key_dialogTextBlack));

        enableSwitch = new Switch(context, resourcesProvider);
        enableSwitch.setColors(
                getThemedColor(Theme.key_switch2Track),
                getThemedColor(Theme.key_switch2TrackChecked),
                getThemedColor(Theme.key_windowBackgroundWhite),
                getThemedColor(Theme.key_windowBackgroundWhite));
        enableSwitch.setOnCheckedChangeListener((view, checked) -> {
            if (changingState) {
                return;
            }
            SharedConfig.eqEnabled = checked;
            SharedConfig.saveEqSettings();
            updateEnableLabel();
            bandsView.setEnabledState(checked);
        });

        LinearLayout enableRow = new LinearLayout(context);
        enableRow.setOrientation(LinearLayout.HORIZONTAL);
        enableRow.setGravity(Gravity.CENTER_VERTICAL);
        enableRow.addView(enableLabel, new LinearLayout.LayoutParams(0, LayoutHelper.WRAP_CONTENT, 1f));
        enableRow.addView(enableSwitch, new LinearLayout.LayoutParams(40, 24));

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, AndroidUtilities.dp(6), 0, AndroidUtilities.dp(10));

        FrameLayout header = new FrameLayout(context);
        header.addView(titleTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.CENTER));
        header.addView(closeButton, LayoutHelper.createFrame(40, 40, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 0, 0, AndroidUtilities.dp(6), 0));
        content.addView(header, new LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, AndroidUtilities.dp(48)));
        content.addView(spectrumView, new LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, AndroidUtilities.dp(180)));
        content.addView(hintTextView, new LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        content.addView(presetsScroll, new LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, AndroidUtilities.dp(44)));
        content.addView(bandsView, new LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, 0, 1f));
        content.addView(enableRow, new LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, AndroidUtilities.dp(40)));

        containerView = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(
                        Math.min(MeasureSpec.getSize(heightMeasureSpec), AndroidUtilities.dp(560)),
                        MeasureSpec.EXACTLY));
            }
        };
        containerView.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(16), getThemedColor(Theme.key_dialogBackground)));
        containerView.addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!SharedConfig.eqEnabled) {
            // не заставляем пользователя ничего включать — работает сразу
            SharedConfig.eqEnabled = true;
            SharedConfig.saveEqSettings();
        }
        if (SharedConfig.eqPreset >= PRESET_NAMES.length) {
            SharedConfig.eqPreset = -2;
        }
        buildPresets();
        bandsView.setValues(SharedConfig.eqBands);
        bandsView.setEnabledState(SharedConfig.eqEnabled);
        changingState = true;
        enableSwitch.setChecked(SharedConfig.eqEnabled, false);
        changingState = false;
        updateEnableLabel();
        spectrumView.updateColors();
        bandsView.updateColors();
        spectrumView.start();
    }

    @Override
    protected void onStop() {
        super.onStop();
        spectrumView.releaseResources();
    }

    private void updateEnableLabel() {
        enableLabel.setText(SharedConfig.eqEnabled ? "Эквалайзер включён" : "Эквалайзер выключен");
        enableLabel.setTextColor(getThemedColor(SharedConfig.eqEnabled ? Theme.key_dialogTextBlack : Theme.key_dialogTextGray));
    }

    /* * Пресеты в UI */

    private void buildPresets() {
        presetsLayout.removeAllViews();
        presetViews.clear();

        TextView flat = createPresetView("Плоский");
        flat.setOnClickListener(v -> applyFlat());
        presetsLayout.addView(flat);
        presetViews.add(flat);

        for (int i = 0; i < PRESET_NAMES.length; i++) {
            final short preset = (short) i;
            TextView view = createPresetView(PRESET_NAMES[i]);
            view.setTag(preset);
            view.setOnClickListener(v -> applyPreset(preset));
            presetsLayout.addView(view);
            presetViews.add(view);
        }
        highlightPreset(SharedConfig.eqPreset);
    }

    private TextView createPresetView(String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setPadding(AndroidUtilities.dp(14), 0, AndroidUtilities.dp(14), 0);
        view.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LayoutHelper.WRAP_CONTENT, AndroidUtilities.dp(34));
        lp.rightMargin = AndroidUtilities.dp(6);
        view.setLayoutParams(lp);
        return view;
    }

    private void highlightPreset(int selected) {
        for (int i = 0; i < presetViews.size(); i++) {
            final TextView view = presetViews.get(i);
            final Object tag = view.getTag();
            final boolean isSelected = i == 0
                    ? tag == null && selected == -1
                    : (tag instanceof Short && (Short) tag == (short) selected);
            view.setTextColor(isSelected ? 0xFFFFFFFF : getThemedColor(Theme.key_dialogTextBlack));
            view.setTypeface(isSelected ? AndroidUtilities.bold() : null);
            view.setBackground(isSelected
                    ? Theme.createRoundRectDrawable(AndroidUtilities.dp(16), getThemedColor(Theme.key_telegram_color))
                    : Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourcesProvider), 1, AndroidUtilities.dp(16)));
        }
    }

    private void applyPreset(short preset) {
        if (preset < 0 || preset >= PRESET_VALUES.length) {
            return;
        }
        SharedConfig.eqBands = Arrays.copyOf(PRESET_VALUES[preset], EqualizerBandsView.BAND_HZ.length);
        SharedConfig.eqPreset = preset;
        SharedConfig.saveEqSettings();
        bandsView.setValues(SharedConfig.eqBands);
        highlightPreset(preset);
    }

    private void applyFlat() {
        SharedConfig.eqBands = new float[EqualizerBandsView.BAND_HZ.length];
        SharedConfig.eqPreset = -1;
        SharedConfig.saveEqSettings();
        bandsView.setValues(SharedConfig.eqBands);
        highlightPreset(-1);
    }

    /* * Callbacks */

    @Override
    public void onBandValueChanged(int index, float db) {
        if (index < 0 || index >= SharedConfig.eqBands.length) {
            return;
        }
        SharedConfig.eqBands[index] = db;
        SharedConfig.eqPreset = -2; // ручная настройка
        SharedConfig.saveEqSettings();
        highlightPreset(-2);
    }

    @Override
    public void onBandsReleased() {
        final float[] current = bandsView.getValues();
        short best = -1;
        int bestDiff = Integer.MAX_VALUE;
        for (short i = 0; i < PRESET_VALUES.length; i++) {
            final float[] preset = PRESET_VALUES[i];
            int diff = 0;
            for (int b = 0; b < current.length; b++) {
                diff += Math.abs(Math.round(preset[b] - current[b]));
            }
            if (diff < bestDiff) {
                bestDiff = diff;
                best = i;
            }
        }
        SharedConfig.eqPreset = bestDiff <= 4 ? best : -2;
        SharedConfig.saveEqSettings();
        highlightPreset(SharedConfig.eqPreset);
    }

    @Override
    public void onSpectrumStateChanged(boolean available) {
        if (available) {
            hideHint();
        } else {
            showHint("Визуализатор заработает вместе с плеером");
        }
    }

    private void showHint(String text) {
        hintTextView.setText(text);
        hintTextView.setVisibility(View.VISIBLE);
    }

    private void hideHint() {
        hintTextView.setVisibility(View.GONE);
    }
}
