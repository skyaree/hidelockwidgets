package com.squeeare.hidelockwidgets;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private SharedPreferences sp;
    private LinearLayout root;
    private final ArrayList<String> providerValues = new ArrayList<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("cfg", 0);
        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(makeBg());
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(22), dp(18), dp(28));
        scroll.addView(root);
        setContentView(scroll);

        TextView title = text("Hide Lock Widgets", 34, true);
        title.setLetterSpacing(-0.04f);
        root.addView(title);
        TextView sub = text("HiOS/AOSP lockscreen clock hider + experimental KWGT overlay", 14, false);
        sub.setAlpha(.78f);
        root.addView(sub);

        addGap(18);
        addSwitchCard("Включить модуль", "Главный переключатель overlay/hide clock", "enabled", true);
        addSwitchCard("Скрывать системные часы", "Прячет lockscreen_clock_view и animatable_clock_view", "hide_clock", true);

        addGap(8);
        addModeCard();
        addGap(8);
        addWidgetCard();
        addGap(8);
        addTextCard();
        addGap(8);
        addHelpCard();
    }

    private void addModeCard() {
        LinearLayout card = card();
        card.addView(text("Режим замены", 20, true));
        RadioGroup group = new RadioGroup(this);
        group.setOrientation(RadioGroup.VERTICAL);
        RadioButton clock = radio("Свои часы / текстовый fallback", "clock");
        RadioButton widget = radio("KWGT / Android AppWidget", "kwgt");
        group.addView(clock);
        group.addView(widget);
        String mode = sp.getString("mode", "clock");
        if ("kwgt".equals(mode)) widget.setChecked(true); else clock.setChecked(true);
        group.setOnCheckedChangeListener((g, id) -> {
            View v = findViewById(id);
            if (v != null && v.getTag() != null) sp.edit().putString("mode", v.getTag().toString()).apply();
            toast("Сохранено. Перезапусти SystemUI.");
        });
        card.addView(group);
        root.addView(card);
    }

    private void addWidgetCard() {
        LinearLayout card = card();
        card.addView(text("KWGT / виджет", 20, true));
        TextView hint = text("Выбери провайдера виджета. Для KWGT лучше сначала создать пресет в KWGT. Если виджет не появится — включи root/LSPosed и перезапусти SystemUI.", 13, false);
        hint.setAlpha(.78f);
        card.addView(hint);

        Spinner spinner = new Spinner(this);
        ArrayList<String> labels = new ArrayList<>();
        providerValues.clear();
        labels.add("Не выбран");
        providerValues.add("");
        AppWidgetManager awm = AppWidgetManager.getInstance(this);
        List<AppWidgetProviderInfo> infos = awm.getInstalledProviders();
        for (AppWidgetProviderInfo info : infos) {
            CharSequence label = info.loadLabel(getPackageManager());
            ComponentName cn = info.provider;
            labels.add(label + "  •  " + cn.flattenToShortString());
            providerValues.add(cn.flattenToString());
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels);
        spinner.setAdapter(adapter);
        String current = sp.getString("widget_provider", "");
        int idx = providerValues.indexOf(current);
        if (idx >= 0) spinner.setSelection(idx);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                sp.edit().putString("widget_provider", providerValues.get(position)).apply();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        card.addView(spinner);

        Button btn = button("Открыть KWGT");
        btn.setOnClickListener(v -> {
            Intent launch = getPackageManager().getLaunchIntentForPackage("org.kustom.widget");
            if (launch != null) startActivity(launch); else toast("KWGT не найден");
        });
        card.addView(btn);
        root.addView(card);
    }

    private void addTextCard() {
        LinearLayout card = card();
        card.addView(text("Fallback текст", 20, true));
        EditText input = new EditText(this);
        input.setText(sp.getString("custom_text", "TECNO LG8n"));
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(0x88FFFFFF);
        input.setSingleLine(true);
        input.setHint("например: TECNO LG8n");
        card.addView(input);
        Button save = button("Сохранить fallback");
        save.setOnClickListener(v -> {
            sp.edit().putString("custom_text", input.getText().toString()).apply();
            toast("Сохранено");
        });
        card.addView(save);
        root.addView(card);
    }

    private void addHelpCard() {
        LinearLayout card = card();
        card.addView(text("После установки", 20, true));
        TextView t = text("1) LSPosed → включи модуль\n2) Scope: System UI / com.android.systemui\n3) Выполни: su -c 'killall com.android.systemui'\n\nЕсли SystemUI крашится: выключи модуль в LSPosed или удали APK через recovery/adb.", 14, false);
        t.setAlpha(.82f);
        card.addView(t);
        Button settings = button("Открыть LSPosed");
        settings.setOnClickListener(v -> {
            try { startActivity(new Intent("org.lsposed.manager.LAUNCH_MANAGER")); }
            catch (Exception e) { startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))); }
        });
        card.addView(settings);
        root.addView(card);
    }

    private void addSwitchCard(String title, String desc, String key, boolean def) {
        LinearLayout card = card();
        Switch sw = new Switch(this);
        sw.setText(title);
        sw.setTextSize(19);
        sw.setTextColor(Color.WHITE);
        sw.setTypeface(Typeface.DEFAULT_BOLD);
        sw.setChecked(sp.getBoolean(key, def));
        sw.setOnCheckedChangeListener((b, checked) -> sp.edit().putBoolean(key, checked).apply());
        card.addView(sw);
        TextView d = text(desc, 13, false);
        d.setAlpha(.72f);
        card.addView(d);
        root.addView(card);
    }

    private RadioButton radio(String label, String tag) {
        RadioButton rb = new RadioButton(this);
        rb.setText(label);
        rb.setTag(tag);
        rb.setTextColor(Color.WHITE);
        rb.setTextSize(15);
        return rb;
    }

    private LinearLayout card() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(18), dp(16), dp(18), dp(16));
        GradientDrawable gd = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0x553D2BFF, 0x3324E2FF, 0x22FFFFFF});
        gd.setCornerRadius(dp(28));
        gd.setStroke(dp(1), 0x33FFFFFF);
        l.setBackground(gd);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(8), 0, dp(8));
        l.setLayoutParams(lp);
        return l;
    }

    private GradientDrawable makeBg() {
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF070710, 0xFF101020, 0xFF050509});
    }

    private TextView text(String s, int spSize, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.WHITE);
        t.setTextSize(spSize);
        t.setGravity(Gravity.START);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        GradientDrawable gd = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xFF7C4DFF, 0xFF00D5FF});
        gd.setCornerRadius(dp(22));
        b.setBackground(gd);
        return b;
    }

    private void addGap(int h) {
        Space sp = new Space(this);
        sp.setLayoutParams(new LinearLayout.LayoutParams(1, dp(h)));
        root.addView(sp);
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + .5f); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
