package com.squeeare.hidelockwidgets;

import android.app.Activity;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;

public class MainActivity extends Activity {
    private static final String ROOT_DIR = "/data/local/tmp/hidelockwidgets";
    private static final String CONFIG_FILE = ROOT_DIR + "/config.txt";
    private static final String SNAPSHOT_FILE = ROOT_DIR + "/widget_snapshot.png";
    private static final String DISABLE_ALL_FILE = ROOT_DIR + "/disable_all";
    private static final String DISABLE_LIVE_FILE = ROOT_DIR + "/disable_live";
    private static final String SYSTEMUI_WIDGET_ID_FILE = ROOT_DIR + "/systemui_widget_id.txt";
    private static final String SYSTEMUI_WIDGET_PROVIDER_FILE = ROOT_DIR + "/systemui_widget_provider.txt";
    private static final String SYSTEMUI_CRASH_GUARD_FILE = ROOT_DIR + "/systemui_guard.txt";

    private static final int HOST_ID = 2048;
    private static final int REQ_PICK_WIDGET = 1001;
    private static final int REQ_CONFIGURE_WIDGET = 1002;

    private AppWidgetHost appWidgetHost;
    private AppWidgetManager appWidgetManager;
    private SharedPreferences prefs;

    private TextView statusText;
    private FrameLayout previewFrame;
    private AppWidgetHostView previewWidgetView;
    private TextView xLabel;
    private TextView yLabel;
    private TextView widthLabel;
    private TextView heightLabel;
    private TextView scaleLabel;
    private SeekBar xSeek;
    private SeekBar ySeek;
    private SeekBar widthSeek;
    private SeekBar heightSeek;
    private SeekBar scaleSeek;

    private int pendingWidgetId = -1;
    private int xDp = 0;
    private int yDp = 120;
    private int widthDp = 320;
    private int heightDp = 160;
    private int scalePercent = 100;
    private boolean liveMode = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        appWidgetHost = new AppWidgetHost(this, HOST_ID);
        appWidgetManager = AppWidgetManager.getInstance(this);
        prefs = getSharedPreferences("settings", MODE_PRIVATE);
        loadPrefs();
        buildUi();
        updateStatus();
    }

    @Override
    protected void onStart() {
        super.onStart();
        try { appWidgetHost.startListening(); } catch (Throwable ignored) { }
    }

    @Override
    protected void onStop() {
        super.onStop();
        try { appWidgetHost.stopListening(); } catch (Throwable ignored) { }
    }

    private void loadPrefs() {
        xDp = prefs.getInt("x_dp", 0);
        yDp = prefs.getInt("y_dp", 120);
        widthDp = prefs.getInt("width_dp", 320);
        heightDp = prefs.getInt("height_dp", 160);
        scalePercent = prefs.getInt("scale_percent", 100);
        liveMode = prefs.getBoolean("live_mode", true);
    }

    private void savePrefs() {
        prefs.edit()
                .putInt("x_dp", xDp)
                .putInt("y_dp", yDp)
                .putInt("width_dp", widthDp)
                .putInt("height_dp", heightDp)
                .putInt("scale_percent", scalePercent)
                .putBoolean("live_mode", liveMode)
                .apply();
    }

    private void buildUi() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(false);
        scrollView.setBackgroundColor(Color.rgb(9, 9, 15));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(48), dp(24), dp(24));

        TextView title = new TextView(this);
        title.setText("Hide Lock Widgets");
        title.setTextColor(Color.WHITE);
        title.setTextSize(30.0f);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dp(16));

        statusText = new TextView(this);
        statusText.setTextColor(Color.rgb(210, 210, 220));
        statusText.setTextSize(13.0f);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(0, 0, 0, dp(18));

        previewFrame = new FrameLayout(this);
        previewFrame.setClipChildren(false);
        previewFrame.setClipToPadding(false);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(20, 20, 28));
        bg.setCornerRadius(dp(24));
        previewFrame.setBackground(bg);

        TextView previewHint = new TextView(this);
        previewHint.setText("Preview");
        previewHint.setTextColor(0x88FFFFFF);
        previewHint.setTextSize(14.0f);
        previewHint.setGravity(Gravity.CENTER);
        previewFrame.addView(previewHint, new FrameLayout.LayoutParams(-1, -1));

        xLabel = label();
        yLabel = label();
        widthLabel = label();
        heightLabel = label();
        scaleLabel = label();

        xSeek = makeSeekBar(-250, 250, xDp);
        ySeek = makeSeekBar(-300, 600, yDp);
        widthSeek = makeSeekBar(100, 430, widthDp);
        heightSeek = makeSeekBar(60, 360, heightDp);
        scaleSeek = makeSeekBar(50, 160, scalePercent);

        xSeek.setOnSeekBarChangeListener(makeListener(new Runnable() { @Override public void run() { xDp = getSeekRealValue(xSeek); onEditorChanged(); } }));
        ySeek.setOnSeekBarChangeListener(makeListener(new Runnable() { @Override public void run() { yDp = getSeekRealValue(ySeek); onEditorChanged(); } }));
        widthSeek.setOnSeekBarChangeListener(makeListener(new Runnable() { @Override public void run() { widthDp = getSeekRealValue(widthSeek); onEditorChanged(); } }));
        heightSeek.setOnSeekBarChangeListener(makeListener(new Runnable() { @Override public void run() { heightDp = getSeekRealValue(heightSeek); onEditorChanged(); } }));
        scaleSeek.setOnSeekBarChangeListener(makeListener(new Runnable() { @Override public void run() { scalePercent = getSeekRealValue(scaleSeek); onEditorChanged(); } }));

        Button rootButton = button("Проверить root");
        rootButton.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { checkRoot(); } });

        Button pickButton = button("Выбрать виджет");
        pickButton.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { pickWidget(); } });

        Button saveButton = button("Сохранить позицию/config");
        saveButton.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { saveEverything(); toast("Сохранено"); } });

        Button restartButton = button("Restart SystemUI");
        restartButton.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            saveEverything();
            RootResult result = runRootCommand("killall com.android.systemui");
            toast(result.success ? "SystemUI restarted" : result.output);
        }});

        final Button liveToggleButton = button(liveMode ? "Live mode: ON" : "Live mode: OFF");
        liveToggleButton.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            liveMode = !liveMode;
            liveToggleButton.setText(liveMode ? "Live mode: ON" : "Live mode: OFF");
            saveEverything();
        }});

        Button enableLiveHookButton = button("Включить experimental live hook");
        enableLiveHookButton.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            RootResult result = runRootCommand("mkdir -p " + ROOT_DIR + " && rm -f " + DISABLE_LIVE_FILE + " && chmod 777 " + ROOT_DIR);
            updateStatus();
            toast(result.success ? "Live hook включён. Сделай reboot." : result.output);
        }});

        Button disableLiveHookButton = button("Выключить experimental live hook");
        disableLiveHookButton.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            RootResult result = runRootCommand("mkdir -p " + ROOT_DIR + " && touch " + DISABLE_LIVE_FILE + " && chmod 777 " + ROOT_DIR + " && chmod 666 " + DISABLE_LIVE_FILE);
            updateStatus();
            toast(result.success ? "Live hook выключен" : result.output);
        }});

        Button enableAllButton = button("Включить модульный hook");
        enableAllButton.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            RootResult result = runRootCommand("mkdir -p " + ROOT_DIR + " && rm -f " + DISABLE_ALL_FILE + " && rm -f " + SYSTEMUI_CRASH_GUARD_FILE + " && chmod 777 " + ROOT_DIR);
            updateStatus();
            toast(result.success ? "Модульный hook включён. Restart SystemUI/reboot." : result.output);
        }});

        Button disableAllButton = button("Аварийно выключить весь hook");
        disableAllButton.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            RootResult result = runRootCommand("mkdir -p " + ROOT_DIR + " && touch " + DISABLE_ALL_FILE + " && chmod 777 " + ROOT_DIR + " && chmod 666 " + DISABLE_ALL_FILE);
            updateStatus();
            toast(result.success ? "Весь hook выключен" : result.output);
        }});

        Button resetSystemUiWidgetIdButton = button("Сбросить live widget id");
        resetSystemUiWidgetIdButton.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            RootResult result = runRootCommand("rm -f " + SYSTEMUI_WIDGET_ID_FILE + " " + SYSTEMUI_WIDGET_PROVIDER_FILE);
            updateStatus();
            toast(result.success ? "live widget id/provider сброшен" : result.output);
        }});

        Button grantButton = button("Grant SystemUI widget bind");
        grantButton.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            RootResult result = runRootCommand("appwidget grantbind --package com.android.systemui --user 0");
            updateStatus();
            toast(result.success ? "grantbind выполнен" : result.output);
        }});

        Button clearButton = button("Очистить выбранный виджет");
        clearButton.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { clearWidget(); } });

        root.addView(title, matchWrap());
        root.addView(statusText, matchWrap());
        root.addView(previewFrame, new LinearLayout.LayoutParams(-1, dp(520)));
        addSpace(root, 12);
        root.addView(xLabel); root.addView(xSeek);
        root.addView(yLabel); root.addView(ySeek);
        root.addView(widthLabel); root.addView(widthSeek);
        root.addView(heightLabel); root.addView(heightSeek);
        root.addView(scaleLabel); root.addView(scaleSeek);
        addSpace(root, 14);
        root.addView(rootButton, buttonLp());
        root.addView(pickButton, buttonLp());
        root.addView(saveButton, buttonLp());
        root.addView(restartButton, buttonLp());
        root.addView(liveToggleButton, buttonLp());
        root.addView(enableLiveHookButton, buttonLp());
        root.addView(disableLiveHookButton, buttonLp());
        root.addView(enableAllButton, buttonLp());
        root.addView(disableAllButton, buttonLp());
        root.addView(resetSystemUiWidgetIdButton, buttonLp());
        root.addView(grantButton, buttonLp());
        root.addView(clearButton, buttonLp());

        scrollView.addView(root);
        setContentView(scrollView);
        updateLabels();

        int widgetId = prefs.getInt("widget_id", -1);
        if (widgetId != -1) {
            AppWidgetProviderInfo info = appWidgetManager.getAppWidgetInfo(widgetId);
            if (info != null) createPreview(widgetId, info);
        }
    }

    private TextView label() {
        TextView tv = new TextView(this);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(14.0f);
        tv.setPadding(0, dp(8), 0, 0);
        return tv;
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        return b;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(-1, -2);
    }

    private LinearLayout.LayoutParams buttonLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(4), 0, dp(4));
        return lp;
    }

    private void addSpace(LinearLayout root, int heightDpValue) {
        View space = new View(this);
        root.addView(space, new LinearLayout.LayoutParams(-1, dp(heightDpValue)));
    }

    private SeekBar makeSeekBar(int min, int max, int value) {
        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(max - min);
        seekBar.setProgress(value - min);
        seekBar.setTag(new int[]{min, max});
        return seekBar;
    }

    private int getSeekRealValue(SeekBar seekBar) {
        int[] range = (int[]) seekBar.getTag();
        return range[0] + seekBar.getProgress();
    }

    private SeekBar.OnSeekBarChangeListener makeListener(final Runnable onChange) {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { onChange.run(); }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { saveEverything(); }
        };
    }

    private void onEditorChanged() {
        updateLabels();
        applyPreviewTransform();
    }

    private void updateLabels() {
        xLabel.setText("X: " + xDp + "dp");
        yLabel.setText("Y: " + yDp + "dp");
        widthLabel.setText("Width: " + widthDp + "dp");
        heightLabel.setText("Height: " + heightDp + "dp");
        scaleLabel.setText("Scale: " + scalePercent + "%");
    }

    private void checkRoot() {
        RootResult result = runRootCommand("id");
        toast((result.success ? "Root OK:\n" : "Root error:\n") + result.output);
        ensureRootFiles();
        updateStatus();
    }

    private void ensureRootFiles() {
        runRootCommand("mkdir -p " + ROOT_DIR + " && chmod 777 " + ROOT_DIR + " && touch " + SYSTEMUI_WIDGET_ID_FILE + " && chmod 666 " + SYSTEMUI_WIDGET_ID_FILE);
    }

    private void pickWidget() {
        clearOldWidgetIdOnly();
        pendingWidgetId = appWidgetHost.allocateAppWidgetId();
        Intent pickIntent = new Intent(AppWidgetManager.ACTION_APPWIDGET_PICK);
        pickIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId);
        try {
            startActivityForResult(pickIntent, REQ_PICK_WIDGET);
        } catch (Throwable t) {
            try { appWidgetHost.deleteAppWidgetId(pendingWidgetId); } catch (Throwable ignored) { }
            pendingWidgetId = -1;
            toast("Не удалось открыть системный выбор виджетов: " + t);
        }
    }

    private void configureOrSave(int appWidgetId) {
        AppWidgetProviderInfo info = appWidgetManager.getAppWidgetInfo(appWidgetId);
        if (info == null || info.provider == null) {
            toast("Widget info is null");
            return;
        }
        if (info.configure != null) {
            try {
                Intent configIntent = new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE);
                configIntent.setComponent(info.configure);
                configIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
                startActivityForResult(configIntent, REQ_CONFIGURE_WIDGET);
                return;
            } catch (Throwable t) {
                Toast.makeText(this, "Configure skipped: " + t, Toast.LENGTH_SHORT).show();
            }
        }
        saveWidget(appWidgetId);
    }

    private void saveWidget(int appWidgetId) {
        AppWidgetProviderInfo info = appWidgetManager.getAppWidgetInfo(appWidgetId);
        if (info == null || info.provider == null) {
            toast("Widget info is null");
            return;
        }
        String provider = info.provider.flattenToString();
        String pkg = info.provider.getPackageName();
        prefs.edit()
                .putInt("widget_id", appWidgetId)
                .putString("widget_package", pkg)
                .putString("widget_provider", provider)
                .apply();
        createPreview(appWidgetId, info);
        saveEverything();
        toast("Виджет выбран:\n" + provider);
    }

    private void createPreview(int appWidgetId, AppWidgetProviderInfo info) {
        try {
            previewFrame.removeAllViews();
            previewWidgetView = appWidgetHost.createView(this, appWidgetId, info);
            previewWidgetView.setAppWidget(appWidgetId, info);
            previewWidgetView.setClipChildren(false);
            previewWidgetView.setClipToPadding(false);
            previewFrame.addView(previewWidgetView);
            applyPreviewTransform();
        } catch (Throwable t) {
            TextView error = new TextView(this);
            error.setText("Preview error:\n" + t);
            error.setTextColor(Color.WHITE);
            error.setGravity(Gravity.CENTER);
            previewFrame.removeAllViews();
            previewFrame.addView(error, new FrameLayout.LayoutParams(-1, -1));
        }
    }

    private void applyPreviewTransform() {
        if (previewWidgetView == null) return;
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(widthDp), dp(heightDp), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        previewWidgetView.setLayoutParams(lp);
        previewWidgetView.setTranslationX(dp(xDp));
        previewWidgetView.setTranslationY(dp(yDp));
        previewWidgetView.setScaleX(scalePercent / 100.0f);
        previewWidgetView.setScaleY(scalePercent / 100.0f);
        previewWidgetView.setPivotX(dp(widthDp) / 2.0f);
        previewWidgetView.setPivotY(0.0f);
    }

    private void saveEverything() {
        savePrefs();
        saveConfigWithRoot();
        saveSnapshotDelayed();
        updateStatus();
    }

    private void saveConfigWithRoot() {
        int widgetId = prefs.getInt("widget_id", -1);
        String provider = prefs.getString("widget_provider", "");
        String config = "provider=" + provider + "\n"
                + "widget_id=" + widgetId + "\n"
                + "x_dp=" + xDp + "\n"
                + "y_dp=" + yDp + "\n"
                + "width_dp=" + widthDp + "\n"
                + "height_dp=" + heightDp + "\n"
                + "scale_percent=" + scalePercent + "\n"
                + "live_mode=" + liveMode + "\n"
                + "depth_compat=true\n"
                + "snapshot=" + SNAPSHOT_FILE + "\n";
        String cmd = "mkdir -p " + ROOT_DIR + "\n"
                + "cat > " + CONFIG_FILE + " <<'EOF'\n"
                + config
                + "EOF\n"
                + "chmod 777 " + ROOT_DIR + "\n"
                + "chmod 666 " + CONFIG_FILE + "\n"
                + "touch " + SYSTEMUI_WIDGET_ID_FILE + "\n"
                + "chmod 666 " + SYSTEMUI_WIDGET_ID_FILE + "\n";
        RootResult result = runRootCommand(cmd);
        if (!result.success) {
            toast("Config save root error:\n" + result.output);
        }
    }

    private void saveSnapshotDelayed() {
        if (previewWidgetView == null) return;
        previewWidgetView.postDelayed(new Runnable() {
            @Override public void run() { saveSnapshotNow(); }
        }, 1200L);
    }

    private void saveSnapshotNow() {
        try {
            int w = dp(widthDp);
            int h = dp(heightDp);
            previewWidgetView.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
            previewWidgetView.layout(0, 0, w, h);
            Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            previewWidgetView.draw(canvas);
            File localFile = new File(getCacheDir(), "widget_snapshot.png");
            FileOutputStream fos = new FileOutputStream(localFile);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.flush();
            fos.close();
            String cmd = "mkdir -p " + ROOT_DIR + " && cp " + shellQuote(localFile.getAbsolutePath()) + " " + SNAPSHOT_FILE + " && chmod 777 " + ROOT_DIR + " && chmod 666 " + SNAPSHOT_FILE;
            RootResult result = runRootCommand(cmd);
            if (!result.success) toast("Snapshot root error:\n" + result.output);
        } catch (Throwable t) {
            toast("Snapshot error:\n" + t);
        }
    }

    private void clearWidget() {
        int widgetId = prefs.getInt("widget_id", -1);
        if (widgetId != -1) {
            try { appWidgetHost.deleteAppWidgetId(widgetId); } catch (Throwable ignored) { }
        }
        prefs.edit().remove("widget_id").remove("widget_package").remove("widget_provider").apply();
        previewWidgetView = null;
        if (previewFrame != null) previewFrame.removeAllViews();
        RootResult result = runRootCommand("rm -f " + CONFIG_FILE + " " + SNAPSHOT_FILE + " " + SYSTEMUI_WIDGET_ID_FILE + " " + SYSTEMUI_WIDGET_PROVIDER_FILE);
        updateStatus();
        toast(result.success ? "Виджет очищен" : "Ошибка очистки:\n" + result.output);
    }

    private void clearOldWidgetIdOnly() {
        int oldWidgetId = prefs.getInt("widget_id", -1);
        if (oldWidgetId != -1) {
            try { appWidgetHost.deleteAppWidgetId(oldWidgetId); } catch (Throwable ignored) { }
        }
        prefs.edit().remove("widget_id").apply();
        runRootCommand("rm -f " + SYSTEMUI_WIDGET_ID_FILE + " " + SYSTEMUI_WIDGET_PROVIDER_FILE);
    }

    private void updateStatus() {
        int widgetId = prefs.getInt("widget_id", -1);
        String pkg = prefs.getString("widget_package", "none");
        String provider = prefs.getString("widget_provider", "none");
        RootResult readResult = runRootCommand("echo '--- config ---'; [ -f " + CONFIG_FILE + " ] && cat " + CONFIG_FILE + " || echo null; echo '--- flags ---'; [ -f " + DISABLE_LIVE_FILE + " ] && echo disable_live=1 || echo disable_live=0; [ -f " + DISABLE_ALL_FILE + " ] && echo disable_all=1 || echo disable_all=0; echo '--- systemui id ---'; [ -f " + SYSTEMUI_WIDGET_ID_FILE + " ] && cat " + SYSTEMUI_WIDGET_ID_FILE + " || echo null; echo; echo '--- grants ---'; dumpsys appwidget 2>/dev/null | sed -n '/Grants:/,/^$/p'");
        String configText = readResult.output == null ? "" : readResult.output.trim();
        if (configText.length() == 0) configText = "null";
        if (statusText != null) {
            statusText.setText("APK Widget ID: " + widgetId
                    + "\nPackage: " + pkg
                    + "\nProvider: " + provider
                    + "\nLive mode: " + liveMode
                    + "\n\n" + configText);
        }
    }

    private RootResult runRootCommand(String command) {
        StringBuilder output = new StringBuilder();
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", command});
            BufferedReader stdout = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader stderr = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            String line;
            while ((line = stdout.readLine()) != null) output.append(line).append('\n');
            while ((line = stderr.readLine()) != null) output.append(line).append('\n');
            int exitCode = process.waitFor();
            return new RootResult(exitCode == 0, output.toString());
        } catch (Throwable t) {
            return new RootResult(false, String.valueOf(t));
        }
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }

    private static String shellQuote(String value) {
        return value == null ? "''" : "'" + value.replace("'", "'\\''") + "'";
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_PICK_WIDGET) {
            if (resultCode == RESULT_OK) {
                int appWidgetId = pendingWidgetId;
                if (data != null) appWidgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId);
                pendingWidgetId = appWidgetId;
                Toast.makeText(this, "Widget picked ID: " + appWidgetId, Toast.LENGTH_SHORT).show();
                configureOrSave(appWidgetId);
            } else {
                if (pendingWidgetId != -1) {
                    try { appWidgetHost.deleteAppWidgetId(pendingWidgetId); } catch (Throwable ignored) { }
                }
                pendingWidgetId = -1;
                Toast.makeText(this, "Выбор виджета отменён", Toast.LENGTH_SHORT).show();
            }
            updateStatus();
            return;
        }

        if (requestCode == REQ_CONFIGURE_WIDGET) {
            if (pendingWidgetId != -1) {
                saveWidget(pendingWidgetId);
            } else {
                toast("pendingWidgetId == -1");
            }
            updateStatus();
            return;
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    private static class RootResult {
        final boolean success;
        final String output;
        RootResult(boolean success, String output) {
            this.success = success;
            this.output = output == null ? "" : output;
        }
    }
}
