/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 */

package org.telegram.ui;

import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DiveGramWeather;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;
import java.util.List;

public class WeatherSettingsActivity extends BaseFragment {

    private android.os.Handler handler;
    private Runnable pendingSearch;
    private ArrayAdapter<String> suggestionsAdapter;
    private boolean ignoreTextChange;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Погода");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        frameLayout.addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        TextCheckCell checkCell = new TextCheckCell(context);
        checkCell.setTextAndCheckAndSubText("Показывать погоду", "Температура и иконка облачности в правом углу списка чатов, рядом с надписью.", SharedConfig.weatherEnabled, true);
        checkCell.setOnClickListener(v -> {
            SharedConfig.setWeatherEnabled(!SharedConfig.weatherEnabled);
            checkCell.setChecked(SharedConfig.weatherEnabled);
            if (SharedConfig.weatherEnabled) {
                DiveGramWeather.fetch();
            } else {
                DiveGramWeather.notifyWeatherChanged();
            }
        });
        linearLayout.addView(checkCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        HeaderCell headerCell = new HeaderCell(context);
        headerCell.setText("Город");
        linearLayout.addView(headerCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        handler = new android.os.Handler(context.getMainLooper());

        suggestionsAdapter = new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, new ArrayList<>());
        AutoCompleteTextView editText = new AutoCompleteTextView(context);
        editText.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        editText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        editText.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        editText.setGravity(Gravity.TOP | Gravity.START);
        editText.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));
        editText.setHint("Начните вводить название города…");
        editText.setSingleLine(true);
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        editText.setThreshold(2);
        editText.setAdapter(suggestionsAdapter);
        editText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        editText.setText(SharedConfig.weatherCity);
        editText.setOnItemClickListener((parent, view, position, id) -> {
            String label = suggestionsAdapter.getItem(position);
            if (label != null) {
                String cityName = DiveGramWeather.cleanCityName(label);
                ignoreTextChange = true;
                editText.setText(cityName);
                editText.setSelection(cityName.length());
                ignoreTextChange = false;
                SharedConfig.setWeatherCity(cityName);
                DiveGramWeather.notifyWeatherChanged();
                if (SharedConfig.weatherEnabled) {
                    DiveGramWeather.fetch();
                }
            }
        });
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (ignoreTextChange) {
                    return;
                }
                String value = s.toString().trim();
                if (!value.equals(SharedConfig.weatherCity) && value.length() >= 2) {
                    SharedConfig.setWeatherCity(value);
                    if (pendingSearch != null) {
                        handler.removeCallbacks(pendingSearch);
                    }
                    String query = value;
                    pendingSearch = () -> DiveGramWeather.searchCities(query, cities -> {
                        if (getParentActivity() == null) {
                            return;
                        }
                        suggestionsAdapter.clear();
                        suggestionsAdapter.addAll(cities);
                        suggestionsAdapter.notifyDataSetChanged();
                        if (!cities.isEmpty()) {
                            editText.showDropDown();
                        } else {
                            editText.dismissDropDown();
                        }
                    });
                    handler.postDelayed(pendingSearch, 400);
                }
            }
        });
        linearLayout.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        TextInfoPrivacyCell infoCell = new TextInfoPrivacyCell(context);
        android.text.SpannableString infoText = new android.text.SpannableString("Данные берутся с openweathermap.org. Погода обновляется каждые 30 минут.");
        android.text.util.Linkify.addLinks(infoText, android.text.util.Linkify.WEB_URLS);
        infoCell.setText(infoText);
        infoCell.setBackground(Theme.getThemedDrawableByKey(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
        linearLayout.addView(infoCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        return fragmentView;
    }
}
