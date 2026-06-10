package com.squeeare.hidelockwidgets;

import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Binder;
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
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {
    private static final String TAG = "HideLockWidgets";

    private static final String SYSTEMUI_PACKAGE = "com.android.systemui";

    private static final String OVERLAY_TAG = "hidelockwidgets_widget";

    private static final String ROOT_DIR = "/data/local/tmp/hidelockwidgets";
    private static final String CONFIG_FILE = ROOT_DIR + "/config.txt";
    private static final String SNAPSHOT_FILE = ROOT_DIR + "/widget_snapshot.png";

    private static final String SYSTEMUI_WIDGET_ID_FILE = ROOT_DIR + "/systemui_widget_id.txt";
    private static final String SYSTEMUI_WIDGET_PROVIDER_FILE = ROOT_DIR + "/systemui_widget_provider.txt";

    private static final String DISABLE_LIVE_FILE = ROOT_DIR + "/disable_live";
    private static final String DISABLE_ALL_FILE = ROOT_DIR + "/disable_all";

    private static final int SYSTEMUI_HOST_ID = 4096;

    private static AppWidgetHost systemUiWidgetHost;
    private static int currentSystemUiWidgetId = -1;
    private static String currentBoundProvider = null;

    private static final AtomicInteger systemServerLogCounter = new AtomicInteger(0);
    private static final int MAX_SYSTEM_SERVER_LOGS = 40;

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

    private static final String[] HOOK_CLASSES = {
            "com.android.keyguard.KeyguardStatusView",
            "com.android.keyguard.KeyguardClockSwitch"
    };

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedBridge.log(
                    TAG + ": entry package=" + lpparam.packageName +
                            " process=" + lpparam.processName
            );

            if (isAllHookDisabled()) {
                XposedBridge.log(TAG + ": all hooks disabled by marker");
                return;
            }

            if ("android".equals(lpparam.packageName) || "system".equals(lpparam.packageName)) {
                XposedBridge.log(
                        TAG + ": framework candidate package=" + lpparam.packageName +
                                " process=" + lpparam.processName
                );

                handleAndroidSystem(lpparam);
                return;
            }

            if (SYSTEMUI_PACKAGE.equals(lpparam.packageName)) {
                handleSystemUi(lpparam);
            }

        } catch (Throwable t) {
            XposedBridge.log(TAG + ": handleLoadPackage error " + t);
        }
    }

    private static boolean isAllHookDisabled() {
        try {
            return new File(DISABLE_ALL_FILE).exists();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isLiveHookDisabled() {
        try {
            return new File(DISABLE_LIVE_FILE).exists();
        } catch (Throwable ignored) {
            return true;
        }
    }

    /*
     * =========================================================
     * android / system_server hook
     * =========================================================
     */

    private void handleAndroidSystem(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedBridge.log(
                    TAG + ": loaded in framework package=" + lpparam.packageName +
                            " process=" + lpparam.processName
            );

            if (isLiveHookDisabled()) {
                XposedBridge.log(TAG + ": system_server live hook disabled by marker");
                return;
            }

            /*
             * Главный фикс для HiOS / Android 12:
             * реальный метод находится прямо в AppWidgetServiceImpl,
             * а не в AppWidgetServiceImpl$SecurityPolicy.
             */
            hookServiceHasBindAppWidgetPermission(lpparam.classLoader);

            /*
             * Fallback для других прошивок, где проверка может быть внутри SecurityPolicy.
             */
            hookSecurityPolicyHasBindAppWidgetPermission(lpparam.classLoader);

            /*
             * Диагностический hook bindAppWidgetId, чтобы видеть итог true/false.
             */
            hookBindAppWidgetId(lpparam.classLoader);

        } catch (Throwable t) {
            XposedBridge.log(TAG + ": handleAndroidSystem error " + t);
        }
    }

    private void hookServiceHasBindAppWidgetPermission(ClassLoader classLoader) {
        try {
            Class<?> serviceClass = Class.forName(
                    "com.android.server.appwidget.AppWidgetServiceImpl",
                    false,
                    classLoader
            );

            Method method = serviceClass.getDeclaredMethod(
                    "hasBindAppWidgetPermission",
                    String.class,
                    int.class
            );

            method.setAccessible(true);

            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        if (param.args == null || param.args.length < 2) return;

                        String packageName = String.valueOf(param.args[0]);
                        int userId = safeInt(param.args[1], 0);

                        if (SYSTEMUI_PACKAGE.equals(packageName)) {
                            param.setResult(true);

                            int count = systemServerLogCounter.getAndIncrement();

                            if (count < MAX_SYSTEM_SERVER_LOGS) {
                                XposedBridge.log(
                                        TAG + ": allowed AppWidget bind permission via AppWidgetServiceImpl for " +
                                                packageName + " user=" + userId
                                );
                            }
                        }

                    } catch (Throwable t) {
                        XposedBridge.log(TAG + ": service hasBindAppWidgetPermission hook error " + t);
                    }
                }
            });

            XposedBridge.log(TAG + ": hooked AppWidgetServiceImpl#hasBindAppWidgetPermission(String,int)");

        } catch (Throwable t) {
            XposedBridge.log(TAG + ": hookServiceHasBindAppWidgetPermission error " + t);
        }
    }

    private void hookSecurityPolicyHasBindAppWidgetPermission(ClassLoader classLoader) {
        try {
            Class<?> policyClass = Class.forName(
                    "com.android.server.appwidget.AppWidgetServiceImpl$SecurityPolicy",
                    false,
                    classLoader
            );

            XposedBridge.log(TAG + ": found AppWidgetServiceImpl$SecurityPolicy");

            Method method = policyClass.getDeclaredMethod(
                    "hasBindAppWidgetPermission",
                    String.class,
                    int.class
            );

            method.setAccessible(true);

            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        if (param.args == null || param.args.length < 1) return;

                        String packageName = String.valueOf(param.args[0]);

                        if (SYSTEMUI_PACKAGE.equals(packageName)) {
                            param.setResult(true);

                            XposedBridge.log(
                                    TAG + ": allowed AppWidget bind permission via SecurityPolicy for " +
                                            SYSTEMUI_PACKAGE
                            );
                        }

                    } catch (Throwable t) {
                        XposedBridge.log(TAG + ": SecurityPolicy hasBindAppWidgetPermission hook inner error " + t);
                    }
                }
            });

            XposedBridge.log(TAG + ": hooked SecurityPolicy#hasBindAppWidgetPermission(String,int)");

        } catch (NoSuchMethodException e) {
            XposedBridge.log(TAG + ": SecurityPolicy hasBindAppWidgetPermission(String,int) not found");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": hookSecurityPolicyHasBindAppWidgetPermission error " + t);
        }
    }

    private void hookBindAppWidgetId(ClassLoader classLoader) {
        try {
            Class<?> serviceClass = Class.forName(
                    "com.android.server.appwidget.AppWidgetServiceImpl",
                    false,
                    classLoader
            );

            Method method = serviceClass.getDeclaredMethod(
                    "bindAppWidgetId",
                    String.class,
                    int.class,
                    int.class,
                    ComponentName.class,
                    Bundle.class
            );

            method.setAccessible(true);

            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        if (param.args == null || param.args.length < 4) return;

                        String callingPackage = String.valueOf(param.args[0]);
                        int appWidgetId = safeInt(param.args[1], -1);
                        int providerProfileId = safeInt(param.args[2], 0);
                        ComponentName provider = null;

                        if (param.args[3] instanceof ComponentName) {
                            provider = (ComponentName) param.args[3];
                        }

                        if (!SYSTEMUI_PACKAGE.equals(callingPackage)) {
                            return;
                        }

                        XposedBridge.log(
                                TAG + ": bindAppWidgetId called by SystemUI" +
                                        " id=" + appWidgetId +
                                        " providerProfileId=" + providerProfileId +
                                        " provider=" + provider
                        );

                        /*
                         * Дополнительная попытка выдать разрешение штатным методом.
                         * Если на HiOS он кидает InvocationTargetException — это не смертельно,
                         * главный обход теперь через hookServiceHasBindAppWidgetPermission().
                         */
                        grantBindPermissionToSystemUi(param.thisObject, providerProfileId);

                    } catch (Throwable t) {
                        XposedBridge.log(TAG + ": bindAppWidgetId before hook error " + t);
                    }
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        if (param.args == null || param.args.length < 4) return;

                        String callingPackage = String.valueOf(param.args[0]);

                        if (!SYSTEMUI_PACKAGE.equals(callingPackage)) {
                            return;
                        }

                        Object result = param.getResult();

                        XposedBridge.log(
                                TAG + ": bindAppWidgetId final result for SystemUI = " + result
                        );

                    } catch (Throwable t) {
                        XposedBridge.log(TAG + ": bindAppWidgetId after hook error " + t);
                    }
                }
            });

            XposedBridge.log(TAG + ": hooked AppWidgetServiceImpl#bindAppWidgetId(String,int,int,ComponentName,Bundle)");

        } catch (Throwable t) {
            XposedBridge.log(TAG + ": hookBindAppWidgetId error " + t);
            diagnoseAppWidgetMethods(classLoader);
        }
    }

    private static void grantBindPermissionToSystemUi(Object serviceObject, int userId) {
        try {
            Method grantMethod = serviceObject.getClass().getDeclaredMethod(
                    "setBindAppWidgetPermission",
                    String.class,
                    int.class,
                    boolean.class
            );

            grantMethod.setAccessible(true);

            long token = Binder.clearCallingIdentity();

            try {
                grantMethod.invoke(
                        serviceObject,
                        SYSTEMUI_PACKAGE,
                        userId,
                        true
                );

                XposedBridge.log(
                        TAG + ": granted bind appwidget permission to SystemUI user=" + userId
                );
            } finally {
                Binder.restoreCallingIdentity(token);
            }

        } catch (Throwable t) {
            XposedBridge.log(TAG + ": grant bind permission error " + t);
        }
    }

    private static int safeInt(Object value, int fallback) {
        try {
            if (value instanceof Integer) {
                return (Integer) value;
            }

            return Integer.parseInt(String.valueOf(value));
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private void diagnoseAppWidgetMethods(ClassLoader classLoader) {
        diagnoseClass(
                classLoader,
                "com.android.server.appwidget.AppWidgetServiceImpl"
        );

        diagnoseClass(
                classLoader,
                "com.android.server.appwidget.AppWidgetServiceImpl$SecurityPolicy"
        );
    }

    private void diagnoseClass(ClassLoader classLoader, String className) {
        try {
            Class<?> clazz = Class.forName(className, false, classLoader);

            XposedBridge.log(TAG + ": diagnostic found class " + className);

            Method[] methods = clazz.getDeclaredMethods();

            for (Method method : methods) {
                String name = method.getName().toLowerCase();

                if (
                        name.contains("bind") ||
                                name.contains("permission") ||
                                name.contains("widget") ||
                                name.contains("host") ||
                                name.contains("provider")
                ) {
                    XposedBridge.log(TAG + ": diagnostic method " + method.toString());
                }
            }

        } catch (Throwable t) {
            XposedBridge.log(TAG + ": diagnostic class error " + className + " / " + t);
        }
    }

    /*
     * =========================================================
     * SystemUI hook
     * =========================================================
     */

    private void handleSystemUi(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedBridge.log(TAG + ": loaded in " + lpparam.packageName);
            XposedBridge.log(TAG + ": SystemUI crash guard skipped for testing");

            for (String className : HOOK_CLASSES) {
                hookClass(lpparam.classLoader, className);
            }

        } catch (Throwable t) {
            XposedBridge.log(TAG + ": handleSystemUi error " + t);
        }
    }

    private void hookClass(ClassLoader classLoader, String className) {
        try {
            Class<?> clazz = Class.forName(className, false, classLoader);

            XposedBridge.log(TAG + ": found class " + className);

            hookMethod(clazz, "onFinishInflate");

        } catch (Throwable t) {
            XposedBridge.log(TAG + ": class not found " + className + " / " + t);
        }
    }

    private void hookMethod(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        try {
            Method method = clazz.getDeclaredMethod(methodName, parameterTypes);
            method.setAccessible(true);

            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        if (!(param.thisObject instanceof View)) return;

                        View root = (View) param.thisObject;

                        XposedBridge.log(TAG + ": hooked " + root.getClass().getName() + "#" + methodName);

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

            XposedBridge.log(TAG + ": hooked method " + clazz.getName() + "#" + methodName);

        } catch (NoSuchMethodException e) {
            XposedBridge.log(TAG + ": method not found " + clazz.getName() + "#" + methodName);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": method not hooked " + clazz.getName() + "#" + methodName + " / " + t);
        }
    }

    private static void applyAll(View root) {
        try {
            hideNow(root);
            hideKeyguardClockContainerLikeIconify(root);

            if (root.getClass().getName().contains("KeyguardStatusView")) {
                addWidgetToStatusContainer(root);
            }

        } catch (Throwable t) {
            XposedBridge.log(TAG + ": applyAll error " + t);
        }
    }

    /*
     * =========================================================
     * Hide lockscreen clocks
     * =========================================================
     */

    private static void hideNow(View root) {
        try {
            hideByIds(root);
            hideRecursive(root);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": hideNow error " + t);
        }
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

                    XposedBridge.log(TAG + ": hidden by id " + name + " / " + view.getClass().getName());
                }

            } catch (Throwable t) {
                XposedBridge.log(TAG + ": hideByIds error for " + name + " / " + t);
            }
        }
    }

    private static void hideRecursive(View view) {
        try {
            String className = view.getClass().getName();

            if (
                    className.contains("AnimatableClockView") ||
                            className.contains("TrKeyguardClockView") ||
                            className.contains("TrKeyguardSingleClockView") ||
                            className.contains("TrKeyguardDoubleClockView")
            ) {
                view.setVisibility(View.GONE);
                view.setAlpha(0f);
                view.setScaleX(0f);
                view.setScaleY(0f);

                XposedBridge.log(TAG + ": hidden by class " + className);
            }

            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;

                for (int i = 0; i < group.getChildCount(); i++) {
                    hideRecursive(group.getChildAt(i));
                }
            }

        } catch (Throwable t) {
            XposedBridge.log(TAG + ": hideRecursive error " + t);
        }
    }

    private static void hideKeyguardClockContainerLikeIconify(View root) {
        try {
            int id = root.getResources().getIdentifier(
                    "keyguard_clock_container",
                    "id",
                    "com.android.systemui"
            );

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

                XposedBridge.log(TAG + ": hidden keyguard_clock_container like Iconify");
            }

        } catch (Throwable t) {
            XposedBridge.log(TAG + ": hideKeyguardClockContainerLikeIconify error " + t);
        }
    }

    /*
     * =========================================================
     * Add widget overlay
     * =========================================================
     */

    private static void addWidgetToStatusContainer(View root) {
        try {
            Context systemUiContext = root.getContext();
            Config config = readConfig();

            if (config == null) {
                XposedBridge.log(TAG + ": config is null");
                return;
            }

            ViewGroup statusContainer = getStatusViewContainer(root);

            if (statusContainer == null) {
                XposedBridge.log(TAG + ": statusContainer is null");
                return;
            }

            disableClipping(statusContainer);

            View already = statusContainer.findViewWithTag(OVERLAY_TAG);

            if (already != null) {
                applyOverlayParams(systemUiContext, already, config);
                return;
            }

            removeOldOverlays(statusContainer);

            FrameLayout overlay = new FrameLayout(systemUiContext);
            overlay.setTag(OVERLAY_TAG);
            overlay.setId(View.generateViewId());
            overlay.setClipChildren(false);
            overlay.setClipToPadding(false);

            int widthPx = dp(systemUiContext, config.widthDp);
            int heightPx = dp(systemUiContext, config.heightDp);

            View widgetView = null;

            if (config.liveMode) {
                widgetView = createSystemUiLiveWidgetView(systemUiContext, config, widthPx, heightPx);
            } else {
                XposedBridge.log(TAG + ": live mode disabled by config");
            }

            if (widgetView == null) {
                widgetView = createSnapshotView(systemUiContext, config, widthPx, heightPx);
            }

            if (widgetView == null) {
                TextView error = new TextView(systemUiContext);
                error.setText("Widget unavailable\n" + config.provider);
                error.setTextSize(14f);
                error.setGravity(Gravity.CENTER);
                error.setAlpha(0.75f);
                widgetView = error;
            }

            overlay.addView(widgetView, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
            ));

            ViewGroup.LayoutParams lp = makeParentLayoutParams(statusContainer, widthPx, heightPx);
            statusContainer.addView(overlay, 0, lp);

            applyOverlayParams(systemUiContext, overlay, config);

            XposedBridge.log(TAG + ": widget overlay added, provider=" + config.provider);

        } catch (Throwable t) {
            XposedBridge.log(TAG + ": addWidgetToStatusContainer error " + t);
        }
    }

    /*
     * =========================================================
     * Live AppWidget through SystemUI host
     * =========================================================
     */

    private static View createSystemUiLiveWidgetView(Context context, Config config, int widthPx, int heightPx) {
        try {
            if (config.provider == null || config.provider.trim().isEmpty()) {
                XposedBridge.log(TAG + ": provider is empty");
                return null;
            }

            ComponentName provider = ComponentName.unflattenFromString(config.provider.trim());

            if (provider == null) {
                XposedBridge.log(TAG + ": bad provider " + config.provider);
                return null;
            }

            AppWidgetManager manager = AppWidgetManager.getInstance(context);

            if (systemUiWidgetHost == null) {
                systemUiWidgetHost = new AppWidgetHost(context, SYSTEMUI_HOST_ID);
                systemUiWidgetHost.startListening();
                XposedBridge.log(TAG + ": SystemUI AppWidgetHost started");
            }

            int savedId = readSystemUiWidgetId();
            String savedProvider = readSystemUiWidgetProvider();

            boolean needNewId = false;

            if (currentSystemUiWidgetId > 0 && currentBoundProvider != null) {
                savedId = currentSystemUiWidgetId;
                savedProvider = currentBoundProvider;
            }

            if (savedId <= 0) {
                XposedBridge.log(TAG + ": no saved SystemUI widget id");
                needNewId = true;
            } else if (savedProvider != null && savedProvider.length() > 0 && !savedProvider.equals(config.provider)) {
                XposedBridge.log(TAG + ": provider changed, old=" + savedProvider + ", new=" + config.provider);

                try {
                    systemUiWidgetHost.deleteAppWidgetId(savedId);
                    XposedBridge.log(TAG + ": deleted old SystemUI widget id=" + savedId);
                } catch (Throwable t) {
                    XposedBridge.log(TAG + ": delete old SystemUI widget id error " + t);
                }

                clearSystemUiWidgetId();
                clearSystemUiWidgetProvider();

                currentSystemUiWidgetId = -1;
                currentBoundProvider = null;

                needNewId = true;
            } else {
                AppWidgetProviderInfo savedInfo = null;

                try {
                    savedInfo = manager.getAppWidgetInfo(savedId);
                } catch (Throwable t) {
                    XposedBridge.log(TAG + ": getAppWidgetInfo(savedId) error " + t);
                }

                if (savedInfo == null) {
                    XposedBridge.log(TAG + ": saved SystemUI widget id invalid=" + savedId);

                    try {
                        systemUiWidgetHost.deleteAppWidgetId(savedId);
                    } catch (Throwable ignored) {}

                    clearSystemUiWidgetId();
                    clearSystemUiWidgetProvider();

                    currentSystemUiWidgetId = -1;
                    currentBoundProvider = null;

                    needNewId = true;
                }
            }

            if (needNewId) {
                savedId = systemUiWidgetHost.allocateAppWidgetId();
                XposedBridge.log(TAG + ": allocated new SystemUI widget id=" + savedId);

                boolean bound = false;

                try {
                    bound = manager.bindAppWidgetIdIfAllowed(savedId, provider);
                } catch (Throwable t) {
                    XposedBridge.log(TAG + ": bindAppWidgetIdIfAllowed error " + t);
                }

                XposedBridge.log(TAG + ": SystemUI bind result " + bound + " provider=" + config.provider);

                if (!bound) {
                    try {
                        systemUiWidgetHost.deleteAppWidgetId(savedId);
                    } catch (Throwable ignored) {}

                    return null;
                }

                currentSystemUiWidgetId = savedId;
                currentBoundProvider = config.provider;

                writeSystemUiWidgetId(savedId);
                writeSystemUiWidgetProvider(config.provider);
            }

            AppWidgetProviderInfo info = manager.getAppWidgetInfo(savedId);

            if (info == null) {
                XposedBridge.log(TAG + ": AppWidgetProviderInfo is null for SystemUI id=" + savedId);

                try {
                    systemUiWidgetHost.deleteAppWidgetId(savedId);
                } catch (Throwable ignored) {}

                clearSystemUiWidgetId();
                clearSystemUiWidgetProvider();

                currentSystemUiWidgetId = -1;
                currentBoundProvider = null;

                return null;
            }

            AppWidgetHostView hostView = systemUiWidgetHost.createView(context, savedId, info);
            hostView.setAppWidget(savedId, info);
            hostView.setPadding(0, 0, 0, 0);
            hostView.setClipChildren(false);
            hostView.setClipToPadding(false);
            hostView.setLayoutParams(new FrameLayout.LayoutParams(widthPx, heightPx));

            systemUiWidgetHost.startListening();

            XposedBridge.log(TAG + ": live SystemUI AppWidgetHostView created id=" + savedId);

            return hostView;

        } catch (Throwable t) {
            XposedBridge.log(TAG + ": createSystemUiLiveWidgetView error " + t);
            return null;
        }
    }

    /*
     * =========================================================
     * Snapshot fallback
     * =========================================================
     */

    private static View createSnapshotView(Context context, Config config, int widthPx, int heightPx) {
        try {
            String path = config.snapshotPath;

            if (path == null || path.length() == 0) {
                path = SNAPSHOT_FILE;
            }

            File file = new File(path);

            if (!file.exists()) {
                XposedBridge.log(TAG + ": snapshot does not exist " + path);
                return null;
            }

            Bitmap bitmap = BitmapFactory.decodeFile(path);

            if (bitmap == null) {
                XposedBridge.log(TAG + ": snapshot decode failed");
                return null;
            }

            ImageView imageView = new ImageView(context);
            imageView.setImageBitmap(bitmap);
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            imageView.setAlpha(1f);
            imageView.setLayoutParams(new FrameLayout.LayoutParams(widthPx, heightPx));

            XposedBridge.log(TAG + ": snapshot widget view created " + path);

            return imageView;

        } catch (Throwable t) {
            XposedBridge.log(TAG + ": createSnapshotView error " + t);
            return null;
        }
    }

    /*
     * =========================================================
     * Overlay position
     * =========================================================
     */

    private static void applyOverlayParams(Context context, View overlay, Config config) {
        try {
            int widthPx = dp(context, config.widthDp);
            int heightPx = dp(context, config.heightDp);

            ViewGroup.LayoutParams lp = overlay.getLayoutParams();

            if (lp != null) {
                lp.width = widthPx;
                lp.height = heightPx;
                overlay.setLayoutParams(lp);
            }

            overlay.setTranslationX(dp(context, config.xDp));
            overlay.setTranslationY(dp(context, config.yDp));

            float scale = config.scalePercent / 100f;
            overlay.setScaleX(scale);
            overlay.setScaleY(scale);
            overlay.setPivotX(widthPx / 2f);
            overlay.setPivotY(0f);

        } catch (Throwable t) {
            XposedBridge.log(TAG + ": applyOverlayParams error " + t);
        }
    }

    /*
     * =========================================================
     * Status container
     * =========================================================
     */

    private static ViewGroup getStatusViewContainer(View root) {
        try {
            Object field = getFieldRecursive(root, "mStatusViewContainer");

            if (field instanceof ViewGroup) {
                XposedBridge.log(TAG + ": found mStatusViewContainer field");
                return (ViewGroup) field;
            }
        } catch (Throwable ignored) {}

        try {
            int id = root.getResources().getIdentifier(
                    "status_view_container",
                    "id",
                    "com.android.systemui"
            );

            if (id != 0) {
                View view = root.findViewById(id);

                if (view instanceof ViewGroup) {
                    XposedBridge.log(TAG + ": found status_view_container by id");
                    return (ViewGroup) view;
                }
            }
        } catch (Throwable ignored) {}

        if (root instanceof ViewGroup) {
            XposedBridge.log(TAG + ": fallback root as statusContainer");
            return (ViewGroup) root;
        }

        return null;
    }

    private static Object getFieldRecursive(Object object, String fieldName) throws Throwable {
        Class<?> clazz = object.getClass();

        while (clazz != null) {
            try {
                Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(object);
            } catch (NoSuchFieldException ignored) {
                clazz = clazz.getSuperclass();
            }
        }

        return null;
    }

    private static void removeOldOverlays(ViewGroup parent) {
        try {
            View old = parent.findViewWithTag(OVERLAY_TAG);

            while (old != null) {
                ViewParent viewParent = old.getParent();

                if (viewParent instanceof ViewGroup) {
                    ((ViewGroup) viewParent).removeView(old);
                }

                old = parent.findViewWithTag(OVERLAY_TAG);
            }

        } catch (Throwable t) {
            XposedBridge.log(TAG + ": removeOldOverlays error " + t);
        }
    }

    private static void disableClipping(View view) {
        try {
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                group.setClipChildren(false);
                group.setClipToPadding(false);

                ViewParent parent = group.getParent();

                while (parent instanceof ViewGroup) {
                    ViewGroup parentGroup = (ViewGroup) parent;
                    parentGroup.setClipChildren(false);
                    parentGroup.setClipToPadding(false);
                    parent = parentGroup.getParent();
                }
            }
        } catch (Throwable ignored) {}
    }

    /*
     * =========================================================
     * Config
     * =========================================================
     */

    private static Config readConfig() {
        try {
            File file = new File(CONFIG_FILE);

            if (!file.exists()) {
                XposedBridge.log(TAG + ": config file does not exist " + CONFIG_FILE);
                return null;
            }

            Properties properties = new Properties();
            FileInputStream fis = new FileInputStream(file);
            properties.load(fis);
            fis.close();

            Config config = new Config();

            config.provider = properties.getProperty("provider", "");
            config.widgetId = parseInt(properties.getProperty("widget_id"), -1);

            config.xDp = parseInt(properties.getProperty("x_dp"), 0);
            config.yDp = parseInt(properties.getProperty("y_dp"), 120);
            config.widthDp = parseInt(properties.getProperty("width_dp"), 320);
            config.heightDp = parseInt(properties.getProperty("height_dp"), 160);
            config.scalePercent = parseInt(properties.getProperty("scale_percent"), 100);

            config.liveMode = parseBoolean(properties.getProperty("live_mode"), true);
            config.snapshotPath = properties.getProperty("snapshot", SNAPSHOT_FILE);

            XposedBridge.log(
                    TAG + ": config provider=" + config.provider +
                            " apk_id=" + config.widgetId +
                            " live=" + config.liveMode +
                            " x=" + config.xDp +
                            " y=" + config.yDp +
                            " w=" + config.widthDp +
                            " h=" + config.heightDp +
                            " scale=" + config.scalePercent
            );

            return config;

        } catch (Throwable t) {
            XposedBridge.log(TAG + ": readConfig error " + t);
            return null;
        }
    }

    private static int parseInt(String value, int fallback) {
        try {
            if (value == null) return fallback;
            return Integer.parseInt(value.trim());
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static boolean parseBoolean(String value, boolean fallback) {
        try {
            if (value == null) return fallback;

            String v = value.trim().toLowerCase();

            if ("true".equals(v) || "1".equals(v) || "yes".equals(v) || "on".equals(v)) {
                return true;
            }

            if ("false".equals(v) || "0".equals(v) || "no".equals(v) || "off".equals(v)) {
                return false;
            }

            return fallback;

        } catch (Throwable ignored) {
            return fallback;
        }
    }

    /*
     * =========================================================
     * Files: SystemUI widget id/provider
     * =========================================================
     */

    private static int readSystemUiWidgetId() {
        try {
            File file = new File(SYSTEMUI_WIDGET_ID_FILE);

            if (!file.exists()) return -1;

            FileInputStream fis = new FileInputStream(file);
            byte[] data = new byte[(int) file.length()];
            int read = fis.read(data);
            fis.close();

            if (read <= 0) return -1;

            String text = new String(data).trim();

            if (text.length() == 0) return -1;

            return Integer.parseInt(text);

        } catch (Throwable t) {
            XposedBridge.log(TAG + ": readSystemUiWidgetId error " + t);
            return -1;
        }
    }

    private static void writeSystemUiWidgetId(int id) {
        try {
            File dir = new File(ROOT_DIR);

            if (!dir.exists()) {
                dir.mkdirs();
            }

            File file = new File(SYSTEMUI_WIDGET_ID_FILE);
            FileOutputStream fos = new FileOutputStream(file, false);
            fos.write(String.valueOf(id).getBytes());
            fos.flush();
            fos.close();

            file.setReadable(true, false);
            file.setWritable(true, false);

            XposedBridge.log(TAG + ": wrote SystemUI widget id=" + id);

        } catch (Throwable t) {
            XposedBridge.log(TAG + ": writeSystemUiWidgetId error " + t);
        }
    }

    private static void clearSystemUiWidgetId() {
        try {
            File file = new File(SYSTEMUI_WIDGET_ID_FILE);

            if (file.exists()) {
                file.delete();
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": clearSystemUiWidgetId error " + t);
        }
    }

    private static String readSystemUiWidgetProvider() {
        try {
            File file = new File(SYSTEMUI_WIDGET_PROVIDER_FILE);

            if (!file.exists()) return "";

            FileInputStream fis = new FileInputStream(file);
            byte[] data = new byte[(int) file.length()];
            int read = fis.read(data);
            fis.close();

            if (read <= 0) return "";

            return new String(data).trim();

        } catch (Throwable t) {
            XposedBridge.log(TAG + ": readSystemUiWidgetProvider error " + t);
            return "";
        }
    }

    private static void writeSystemUiWidgetProvider(String provider) {
        try {
            File dir = new File(ROOT_DIR);

            if (!dir.exists()) {
                dir.mkdirs();
            }

            File file = new File(SYSTEMUI_WIDGET_PROVIDER_FILE);
            FileOutputStream fos = new FileOutputStream(file, false);
            fos.write(provider.getBytes());
            fos.flush();
            fos.close();

            file.setReadable(true, false);
            file.setWritable(true, false);

            XposedBridge.log(TAG + ": wrote SystemUI widget provider=" + provider);

        } catch (Throwable t) {
            XposedBridge.log(TAG + ": writeSystemUiWidgetProvider error " + t);
        }
    }

    private static void clearSystemUiWidgetProvider() {
        try {
            File file = new File(SYSTEMUI_WIDGET_PROVIDER_FILE);

            if (file.exists()) {
                file.delete();
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": clearSystemUiWidgetProvider error " + t);
        }
    }

    /*
     * =========================================================
     * Layout params / dp
     * =========================================================
     */

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

        if (parent instanceof GridLayout) {
            return new ViewGroup.LayoutParams(widthPx, heightPx);
        }

        return new ViewGroup.LayoutParams(widthPx, heightPx);
    }

    private static int dp(Context context, int value) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    private static class Config {
        String provider;
        int widgetId;

        int xDp;
        int yDp;
        int widthDp;
        int heightDp;
        int scalePercent;

        String snapshotPath;
        boolean liveMode;
    }
        }
