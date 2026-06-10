package com.squeeare.hidelockwidgets;

import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {
    private static final String TAG = "HideLockWidgets";
    private static final String SYSTEMUI_PACKAGE = "com.android.systemui";

    private static final String ROOT_DIR = "/data/local/tmp/hidelockwidgets";
    private static final String CONFIG_FILE = ROOT_DIR + "/config.txt";
    private static final String DISABLE_ALL_FILE = ROOT_DIR + "/disable_all";
    private static final String DISABLE_LIVE_FILE = ROOT_DIR + "/disable_live";

    private static final int SYSTEMUI_HOST_ID = 4096;
    private static final int DESIGN_WIDTH = 1080;
    private static final int DESIGN_HEIGHT = 2400;
    private static final String TAG_WIDGET_PREFIX = "hidelockwidgets_widget_";
    private static final String TAG_DEPTH_BACKGROUND = "hidelockwidgets_depth_background";
    private static final String TAG_DEPTH_FOREGROUND = "hidelockwidgets_depth_foreground";

    private static AppWidgetHost systemUiWidgetHost;
    private static final Map<Integer, Integer> runtimeWidgetIds = new HashMap<>();
    private static final Map<Integer, String> runtimeProviders = new HashMap<>();

    private static final String[] HOOK_CLASSES = {
            "com.android.keyguard.KeyguardStatusView",
            "com.android.keyguard.KeyguardClockSwitch"
    };

    private static final String[] CLOCK_IDS = {
            "lockscreen_clock_view",
            "lockscreen_clock_view_large",
            "animatable_clock_view",
            "animatable_clock_view_large",
            "keyguard_clock_container",
            "tr_single_clock",
            "tr_double_clock",
            "lock_screen_clock"
    };

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            if (isAllHookDisabled()) return;
            XposedBridge.log(TAG + ": entry package=" + lpparam.packageName + " process=" + lpparam.processName);
            if (SYSTEMUI_PACKAGE.equals(lpparam.packageName)) {
                handleSystemUi(lpparam);
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": handleLoadPackage error " + t);
        }
    }

    private static boolean isAllHookDisabled() {
        try { return new File(DISABLE_ALL_FILE).exists(); } catch (Throwable ignored) { return false; }
    }

    private static boolean isLiveDisabled() {
        try { return new File(DISABLE_LIVE_FILE).exists(); } catch (Throwable ignored) { return false; }
    }

    private void handleSystemUi(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedBridge.log(TAG + ": loaded in com.android.systemui");
            for (String className : HOOK_CLASSES) hookClass(lpparam.classLoader, className);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": handleSystemUi error " + t);
        }
    }

    private void hookClass(ClassLoader classLoader, String className) {
        try {
            Class<?> clazz = Class.forName(className, false, classLoader);
            Method method = clazz.getDeclaredMethod("onFinishInflate");
            method.setAccessible(true);
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        if (!(param.thisObject instanceof View)) return;
                        View root = (View) param.thisObject;
                        applyAll(root);
                        root.postDelayed(() -> applyAll(root), 250);
                        root.postDelayed(() -> applyAll(root), 1000);
                        root.postDelayed(() -> applyAll(root), 2500);
                        root.postDelayed(() -> applyAll(root), 5000);
                    } catch (Throwable t) {
                        XposedBridge.log(TAG + ": hook after error " + t);
                    }
                }
            });
            XposedBridge.log(TAG + ": hooked method " + clazz.getName() + "#onFinishInflate");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": hookClass error " + className + " / " + t);
        }
    }

    private static void applyAll(View root) {
        try {
            FullConfig config = readConfig();
            if (config == null) return;
            if (config.hideClock) {
                hideByIds(root);
                hideRecursive(root);
                hideKeyguardClockContainerLikeIconify(root);
            }
            if (root.getClass().getName().contains("KeyguardStatusView")) {
                addEverything(root, config);
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": applyAll error " + t);
        }
    }

    private static void addEverything(View root, FullConfig config) {
        try {
            Context context = root.getContext();
            ViewGroup parent = getStatusViewContainer(root);
            if (parent == null) return;
            disableClipping(parent);

            if (config.depthEnabled && config.backgroundPath != null && config.backgroundPath.length() > 0) {
                addImageLayer(context, parent, TAG_DEPTH_BACKGROUND, config.backgroundPath, config.backgroundX, config.backgroundY, config.backgroundScale, true);
            } else {
                removeByTag(parent, TAG_DEPTH_BACKGROUND);
            }

            if (config.widgetsEnabled) {
                for (WidgetConfig w : config.widgets) addWidgetLayer(context, parent, w);
            }
            removeStaleWidgets(parent, config.widgets);

            if (config.depthEnabled && config.foregroundPath != null && config.foregroundPath.length() > 0) {
                addImageLayer(context, parent, TAG_DEPTH_FOREGROUND, config.foregroundPath, config.foregroundX, config.foregroundY, config.foregroundScale, false);
            } else {
                removeByTag(parent, TAG_DEPTH_FOREGROUND);
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": addEverything error " + t);
        }
    }

    private static void addImageLayer(Context context, ViewGroup parent, String tag, String path, int x, int y, int scale, boolean toBack) {
        try {
            int layerWidthDp = screenWidthDp(context);
            int layerHeightDp = screenHeightDp(context);
            int xDp = designToDpX(context, x);
            int yDp = designToDpY(context, y);

            View old = parent.findViewWithTag(tag);
            if (old != null) {
                applyLayerParams(context, old, layerWidthDp, layerHeightDp, xDp, yDp, scale);
                if (!toBack) old.bringToFront();
                return;
            }
            File file = new File(path);
            if (!file.exists()) return;
            Bitmap bitmap = BitmapFactory.decodeFile(path);
            if (bitmap == null) return;
            ImageView image = new ImageView(context);
            image.setTag(tag);
            image.setImageBitmap(bitmap);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setClipToOutline(false);
            ViewGroup.LayoutParams lp = makeParentLayoutParams(parent, dp(context, layerWidthDp), dp(context, layerHeightDp));
            if (toBack) parent.addView(image, 0, lp); else parent.addView(image, lp);
            applyLayerParams(context, image, layerWidthDp, layerHeightDp, xDp, yDp, scale);
            if (!toBack) image.bringToFront();
            XposedBridge.log(TAG + ": depth image added tag=" + tag + " path=" + path);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": addImageLayer error " + t);
        }
    }

    private static void addWidgetLayer(Context context, ViewGroup parent, WidgetConfig config) {
        try {
            int xDp = designToDpX(context, config.xDp);
            int yDp = designToDpY(context, config.yDp);
            int widthDp = Math.max(1, designToDpX(context, config.widthDp));
            int heightDp = Math.max(1, designToDpY(context, config.heightDp));

            String tag = TAG_WIDGET_PREFIX + config.index;
            View already = parent.findViewWithTag(tag);
            if (already != null) {
                applyLayerParams(context, already, widthDp, heightDp, xDp, yDp, config.scalePercent);
                return;
            }
            FrameLayout overlay = new FrameLayout(context);
            overlay.setTag(tag);
            overlay.setClipChildren(false);
            overlay.setClipToPadding(false);
            int widthPx = dp(context, widthDp);
            int heightPx = dp(context, heightDp);

            View widgetView = null;
            if (config.liveMode && !isLiveDisabled()) widgetView = createLiveWidgetView(context, config, widthPx, heightPx, widthDp, heightDp);
            if (widgetView == null) widgetView = createSnapshotView(context, config, widthPx, heightPx);
            if (widgetView == null) {
                TextView error = new TextView(context);
                error.setText("Widget unavailable\n" + config.title);
                error.setTextSize(13f);
                error.setGravity(Gravity.CENTER);
                widgetView = error;
            }
            overlay.addView(widgetView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            parent.addView(overlay, makeParentLayoutParams(parent, widthPx, heightPx));
            applyLayerParams(context, overlay, widthDp, heightDp, xDp, yDp, config.scalePercent);
            XposedBridge.log(TAG + ": widget overlay added index=" + config.index + " provider=" + config.provider);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": addWidgetLayer error " + t);
        }
    }

    private static View createLiveWidgetView(Context context, WidgetConfig config, int widthPx, int heightPx, int widthDp, int heightDp) {
        try {
            ComponentName provider = ComponentName.unflattenFromString(config.provider);
            if (provider == null) return null;
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            if (systemUiWidgetHost == null) {
                systemUiWidgetHost = new AppWidgetHost(context, SYSTEMUI_HOST_ID);
                systemUiWidgetHost.startListening();
                XposedBridge.log(TAG + ": SystemUI AppWidgetHost started");
            }

            int savedId = runtimeWidgetIds.containsKey(config.index) ? runtimeWidgetIds.get(config.index) : readWidgetId(config.index);
            String savedProvider = runtimeProviders.containsKey(config.index) ? runtimeProviders.get(config.index) : readWidgetProvider(config.index);
            boolean needNew = false;
            if (savedId <= 0) needNew = true;
            else if (savedProvider != null && savedProvider.length() > 0 && !savedProvider.equals(config.provider)) {
                try { systemUiWidgetHost.deleteAppWidgetId(savedId); } catch (Throwable ignored) {}
                clearWidgetFiles(config.index);
                needNew = true;
            } else {
                AppWidgetProviderInfo oldInfo = null;
                try { oldInfo = manager.getAppWidgetInfo(savedId); } catch (Throwable ignored) {}
                if (oldInfo == null) {
                    try { systemUiWidgetHost.deleteAppWidgetId(savedId); } catch (Throwable ignored) {}
                    clearWidgetFiles(config.index);
                    needNew = true;
                }
            }

            if (needNew) {
                savedId = systemUiWidgetHost.allocateAppWidgetId();
                XposedBridge.log(TAG + ": allocated SystemUI widget index=" + config.index + " id=" + savedId);
                boolean bound = false;
                try { bound = manager.bindAppWidgetIdIfAllowed(savedId, provider); } catch (Throwable t) { XposedBridge.log(TAG + ": bind error " + t); }
                XposedBridge.log(TAG + ": SystemUI bind result " + bound + " provider=" + config.provider);
                if (!bound) {
                    try { systemUiWidgetHost.deleteAppWidgetId(savedId); } catch (Throwable ignored) {}
                    return null;
                }
                writeWidgetId(config.index, savedId);
                writeWidgetProvider(config.index, config.provider);
            }

            runtimeWidgetIds.put(config.index, savedId);
            runtimeProviders.put(config.index, config.provider);

            Bundle options = new Bundle();
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp);
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp);
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp);
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp);
            try { manager.updateAppWidgetOptions(savedId, options); } catch (Throwable ignored) {}

            AppWidgetProviderInfo info = manager.getAppWidgetInfo(savedId);
            if (info == null) return null;
            AppWidgetHostView hostView = systemUiWidgetHost.createView(context, savedId, info);
            hostView.setAppWidget(savedId, info);
            hostView.setPadding(0, 0, 0, 0);
            hostView.setClipChildren(false);
            hostView.setClipToPadding(false);
            try { hostView.updateAppWidgetSize(options, widthDp, heightDp, widthDp, heightDp); } catch (Throwable ignored) {}
            systemUiWidgetHost.startListening();
            XposedBridge.log(TAG + ": live SystemUI AppWidgetHostView created index=" + config.index + " id=" + savedId);
            return hostView;
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": createLiveWidgetView error " + t);
            return null;
        }
    }

    private static View createSnapshotView(Context context, WidgetConfig config, int widthPx, int heightPx) {
        try {
            File file = new File(config.snapshotPath);
            if (!file.exists()) return null;
            Bitmap bitmap = BitmapFactory.decodeFile(config.snapshotPath);
            if (bitmap == null) return null;
            ImageView image = new ImageView(context);
            image.setImageBitmap(bitmap);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setLayoutParams(new FrameLayout.LayoutParams(widthPx, heightPx));
            return image;
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": createSnapshotView error " + t);
            return null;
        }
    }

    private static void applyLayerParams(Context context, View view, int widthDp, int heightDp, int x, int y, int scalePercent) {
        try {
            int widthPx = dp(context, widthDp);
            int heightPx = dp(context, heightDp);
            ViewGroup.LayoutParams lp = view.getLayoutParams();
            if (lp != null) {
                lp.width = widthPx;
                lp.height = heightPx;
                view.setLayoutParams(lp);
            }
            view.setTranslationX(dp(context, x));
            view.setTranslationY(dp(context, y));
            float scale = scalePercent / 100f;
            view.setScaleX(scale);
            view.setScaleY(scale);
            view.setPivotX(0f);
            view.setPivotY(0f);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": applyLayerParams error " + t);
        }
    }

    private static FullConfig readConfig() {
        try {
            File file = new File(CONFIG_FILE);
            if (!file.exists()) return null;
            Properties p = new Properties();
            FileInputStream fis = new FileInputStream(file);
            p.load(fis);
            fis.close();

            FullConfig config = new FullConfig();
            config.hideClock = parseBool(p.getProperty("hide_clock"), true);
            config.widgetsEnabled = parseBool(p.getProperty("widgets_enabled"), true);
            config.depthEnabled = parseBool(p.getProperty("depth_enabled"), false);
            config.backgroundPath = p.getProperty("depth_background", "");
            config.foregroundPath = p.getProperty("depth_foreground", "");
            config.backgroundX = parseInt(p.getProperty("depth_background_x"), 0);
            config.backgroundY = parseInt(p.getProperty("depth_background_y"), 0);
            config.backgroundScale = parseInt(p.getProperty("depth_background_scale"), 100);
            config.foregroundX = parseInt(p.getProperty("depth_foreground_x"), 0);
            config.foregroundY = parseInt(p.getProperty("depth_foreground_y"), 0);
            config.foregroundScale = parseInt(p.getProperty("depth_foreground_scale"), 100);

            int count = parseInt(p.getProperty("widgets"), 0);
            if (count > 0) {
                for (int i = 1; i <= count; i++) {
                    WidgetConfig w = new WidgetConfig();
                    w.index = i;
                    w.title = p.getProperty("widget." + i + ".title", "Widget #" + i);
                    w.provider = p.getProperty("widget." + i + ".provider", "");
                    w.xDp = parseInt(first(p, "widget." + i + ".x", "widget." + i + ".x_dp"), 0);
                    w.yDp = parseInt(first(p, "widget." + i + ".y", "widget." + i + ".y_dp"), 120);
                    w.widthDp = parseInt(first(p, "widget." + i + ".width", "widget." + i + ".width_dp"), 320);
                    w.heightDp = parseInt(first(p, "widget." + i + ".height", "widget." + i + ".height_dp"), 160);
                    w.scalePercent = parseInt(first(p, "widget." + i + ".scale", "widget." + i + ".scale_percent"), 100);
                    w.liveMode = parseBool(first(p, "widget." + i + ".live", "widget." + i + ".live_mode"), true);
                    w.snapshotPath = p.getProperty("widget." + i + ".snapshot", ROOT_DIR + "/widget_" + i + ".png");
                    if (w.provider != null && w.provider.length() > 0) config.widgets.add(w);
                }
            } else {
                WidgetConfig w = new WidgetConfig();
                w.index = 1;
                w.title = "Widget";
                w.provider = p.getProperty("provider", "");
                w.xDp = parseInt(p.getProperty("x_dp"), 0);
                w.yDp = parseInt(p.getProperty("y_dp"), 120);
                w.widthDp = parseInt(p.getProperty("width_dp"), 320);
                w.heightDp = parseInt(p.getProperty("height_dp"), 160);
                w.scalePercent = parseInt(p.getProperty("scale_percent"), 100);
                w.liveMode = parseBool(p.getProperty("live_mode"), true);
                w.snapshotPath = p.getProperty("snapshot", ROOT_DIR + "/widget_snapshot.png");
                if (w.provider != null && w.provider.length() > 0) config.widgets.add(w);
            }
            return config;
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": readConfig error " + t);
            return null;
        }
    }

    private static String first(Properties p, String a, String b) {
        String av = p.getProperty(a);
        if (av != null) return av;
        return p.getProperty(b);
    }

    private static int parseInt(String s, int fallback) {
        try { return Integer.parseInt(String.valueOf(s).trim()); } catch (Throwable ignored) { return fallback; }
    }

    private static boolean parseBool(String s, boolean fallback) {
        if (s == null) return fallback;
        String v = s.trim().toLowerCase();
        if ("true".equals(v) || "1".equals(v) || "yes".equals(v) || "on".equals(v)) return true;
        if ("false".equals(v) || "0".equals(v) || "no".equals(v) || "off".equals(v)) return false;
        return fallback;
    }

    private static String idFile(int index) { return ROOT_DIR + "/systemui_widget_" + index + "_id.txt"; }
    private static String providerFile(int index) { return ROOT_DIR + "/systemui_widget_" + index + "_provider.txt"; }

    private static int readWidgetId(int index) {
        try {
            File file = new File(idFile(index));
            if (!file.exists() && index == 1) file = new File(ROOT_DIR + "/systemui_widget_id.txt");
            if (!file.exists()) return -1;
            byte[] data = new byte[(int) file.length()];
            FileInputStream fis = new FileInputStream(file);
            int read = fis.read(data);
            fis.close();
            if (read <= 0) return -1;
            return Integer.parseInt(new String(data).trim());
        } catch (Throwable ignored) { return -1; }
    }

    private static String readWidgetProvider(int index) {
        try {
            File file = new File(providerFile(index));
            if (!file.exists() && index == 1) file = new File(ROOT_DIR + "/systemui_widget_provider.txt");
            if (!file.exists()) return "";
            byte[] data = new byte[(int) file.length()];
            FileInputStream fis = new FileInputStream(file);
            int read = fis.read(data);
            fis.close();
            if (read <= 0) return "";
            return new String(data).trim();
        } catch (Throwable ignored) { return ""; }
    }

    private static void writeWidgetId(int index, int id) {
        writeText(idFile(index), String.valueOf(id));
        if (index == 1) writeText(ROOT_DIR + "/systemui_widget_id.txt", String.valueOf(id));
    }

    private static void writeWidgetProvider(int index, String provider) {
        writeText(providerFile(index), provider);
        if (index == 1) writeText(ROOT_DIR + "/systemui_widget_provider.txt", provider);
    }

    private static void writeText(String path, String text) {
        try {
            File dir = new File(ROOT_DIR);
            if (!dir.exists()) dir.mkdirs();
            File file = new File(path);
            FileOutputStream fos = new FileOutputStream(file, false);
            fos.write(text.getBytes());
            fos.flush();
            fos.close();
            file.setReadable(true, false);
            file.setWritable(true, false);
        } catch (Throwable t) { XposedBridge.log(TAG + ": writeText error " + path + " / " + t); }
    }

    private static void clearWidgetFiles(int index) {
        try { new File(idFile(index)).delete(); } catch (Throwable ignored) {}
        try { new File(providerFile(index)).delete(); } catch (Throwable ignored) {}
    }

    private static void removeStaleWidgets(ViewGroup parent, List<WidgetConfig> widgets) {
        try {
            for (int i = 1; i <= 20; i++) {
                boolean keep = false;
                for (WidgetConfig w : widgets) if (w.index == i) { keep = true; break; }
                if (!keep) removeByTag(parent, TAG_WIDGET_PREFIX + i);
            }
        } catch (Throwable ignored) {}
    }

    private static void removeByTag(ViewGroup parent, String tag) {
        try {
            View v = parent.findViewWithTag(tag);
            while (v != null) {
                ViewParent p = v.getParent();
                if (p instanceof ViewGroup) ((ViewGroup) p).removeView(v);
                v = parent.findViewWithTag(tag);
            }
        } catch (Throwable ignored) {}
    }

    private static ViewGroup getStatusViewContainer(View root) {
        try {
            Object field = getFieldRecursive(root, "mStatusViewContainer");
            if (field instanceof ViewGroup) return (ViewGroup) field;
        } catch (Throwable ignored) {}
        try {
            int id = root.getResources().getIdentifier("status_view_container", "id", "com.android.systemui");
            if (id != 0) {
                View view = root.findViewById(id);
                if (view instanceof ViewGroup) return (ViewGroup) view;
            }
        } catch (Throwable ignored) {}
        if (root instanceof ViewGroup) return (ViewGroup) root;
        return null;
    }

    private static Object getFieldRecursive(Object object, String fieldName) throws Throwable {
        Class<?> clazz = object.getClass();
        while (clazz != null) {
            try {
                Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(object);
            } catch (NoSuchFieldException ignored) { clazz = clazz.getSuperclass(); }
        }
        return null;
    }

    private static void disableClipping(View view) {
        try {
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                group.setClipChildren(false);
                group.setClipToPadding(false);
                ViewParent parent = group.getParent();
                while (parent instanceof ViewGroup) {
                    ViewGroup p = (ViewGroup) parent;
                    p.setClipChildren(false);
                    p.setClipToPadding(false);
                    parent = p.getParent();
                }
            }
        } catch (Throwable ignored) {}
    }

    private static ViewGroup.LayoutParams makeParentLayoutParams(ViewGroup parent, int widthPx, int heightPx) {
        if (parent instanceof LinearLayout) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(widthPx, heightPx);
            lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            return lp;
        }
        if (parent instanceof FrameLayout) {
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(widthPx, heightPx);
            lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            return lp;
        }
        if (parent instanceof RelativeLayout) {
            RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(widthPx, heightPx);
            lp.addRule(RelativeLayout.CENTER_HORIZONTAL);
            return lp;
        }
        if (parent instanceof GridLayout) return new ViewGroup.LayoutParams(widthPx, heightPx);
        return new ViewGroup.LayoutParams(widthPx, heightPx);
    }

    private static int screenWidthDp(Context context) {
        try {
            return Math.max(1, Math.round(context.getResources().getDisplayMetrics().widthPixels / context.getResources().getDisplayMetrics().density));
        } catch (Throwable ignored) {
            return 360;
        }
    }

    private static int screenHeightDp(Context context) {
        try {
            return Math.max(1, Math.round(context.getResources().getDisplayMetrics().heightPixels / context.getResources().getDisplayMetrics().density));
        } catch (Throwable ignored) {
            return 800;
        }
    }

    private static int designToDpX(Context context, int value) {
        return Math.round(value * (screenWidthDp(context) / (float) DESIGN_WIDTH));
    }

    private static int designToDpY(Context context, int value) {
        return Math.round(value * (screenHeightDp(context) / (float) DESIGN_HEIGHT));
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static void hideByIds(View root) {
        for (String name : CLOCK_IDS) {
            try {
                int id = root.getResources().getIdentifier(name, "id", "com.android.systemui");
                if (id == 0) continue;
                View view = root.findViewById(id);
                if (view != null) {
                    view.setVisibility(View.GONE);
                    view.setAlpha(0f);
                    view.setScaleX(0f);
                    view.setScaleY(0f);
                }
            } catch (Throwable ignored) {}
        }
    }

    private static void hideRecursive(View view) {
        try {
            String className = view.getClass().getName();
            if (className.contains("AnimatableClockView") || className.contains("TrKeyguardClockView") || className.contains("TrKeyguardSingleClockView") || className.contains("TrKeyguardDoubleClockView")) {
                view.setVisibility(View.GONE);
                view.setAlpha(0f);
                view.setScaleX(0f);
                view.setScaleY(0f);
            }
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) hideRecursive(group.getChildAt(i));
            }
        } catch (Throwable ignored) {}
    }

    private static void hideKeyguardClockContainerLikeIconify(View root) {
        try {
            int id = root.getResources().getIdentifier("keyguard_clock_container", "id", "com.android.systemui");
            if (id == 0) return;
            View view = root.findViewById(id);
            if (view != null) {
                ViewGroup.LayoutParams lp = view.getLayoutParams();
                if (lp != null) {
                    lp.width = 0;
                    lp.height = 0;
                    view.setLayoutParams(lp);
                }
                view.setVisibility(View.INVISIBLE);
                view.setAlpha(0f);
            }
        } catch (Throwable ignored) {}
    }

    private static class FullConfig {
        boolean hideClock = true;
        boolean widgetsEnabled = true;
        boolean depthEnabled = false;
        String backgroundPath = "";
        String foregroundPath = "";
        int backgroundX = 0;
        int backgroundY = 0;
        int backgroundScale = 100;
        int foregroundX = 0;
        int foregroundY = 0;
        int foregroundScale = 100;
        ArrayList<WidgetConfig> widgets = new ArrayList<>();
    }

    private static class WidgetConfig {
        int index = 1;
        String title = "Widget";
        String provider = "";
        int xDp = 0;
        int yDp = 120;
        int widthDp = 320;
        int heightDp = 160;
        int scalePercent = 100;
        boolean liveMode = true;
        String snapshotPath = ROOT_DIR + "/widget_snapshot.png";
    }
}
