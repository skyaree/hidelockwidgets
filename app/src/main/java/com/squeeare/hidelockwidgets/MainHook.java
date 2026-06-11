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
import android.util.Pair;
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
import java.util.Collection;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {
    private static final String TAG = "HideLockWidgets";
    private static final String SYSTEMUI_PACKAGE = "com.android.systemui";

    private static final String ROOT_DIR = "/data/local/tmp/hidelockwidgets";
    private static final String CONFIG_FILE = ROOT_DIR + "/config.txt";
    private static final String SNAPSHOT_FILE = ROOT_DIR + "/widget_snapshot.png";
    private static final String SYSTEMUI_WIDGET_ID_FILE = ROOT_DIR + "/systemui_widget_id.txt";
    private static final String SYSTEMUI_WIDGET_PROVIDER_FILE = ROOT_DIR + "/systemui_widget_provider.txt";
    private static final String DISABLE_LIVE_FILE = ROOT_DIR + "/disable_live";
    private static final String DISABLE_ALL_FILE = ROOT_DIR + "/disable_all";

    private static final int SYSTEMUI_HOST_ID = 4096;
    private static final int MAX_SYSTEM_SERVER_LOGS = 80;

    private static final String OVERLAY_TAG = "hidelockwidgets_widget";
    private static final String ICONIFY_LOCKSCREEN_WIDGET_TAG = "iconify_lockscreen_widget";
    private static final String COMBINED_OVERLAY_TAG = OVERLAY_TAG + "|" + ICONIFY_LOCKSCREEN_WIDGET_TAG;

    // Iconify source uses: background=-2, lockscreen items=-1, depth foreground=-0.5.
    private static final float ICONIFY_LOCKSCREEN_ITEM_Z = -1.0f;
    private static final float ICONIFY_FOREGROUND_Z = -0.5f;

    private static final String[] CLOCK_IDS = new String[] {
            "lockscreen_clock_view",
            "lockscreen_clock_view_large",
            "animatable_clock_view",
            "animatable_clock_view_large",
            "keyguard_clock_container",
            "tr_single_clock",
            "tr_double_clock",
            "lock_screen_clock"
    };

    private static final String[] HOOK_CLASSES = new String[] {
            "com.android.keyguard.KeyguardStatusView",
            "com.android.keyguard.KeyguardClockSwitch"
    };

    private static AppWidgetHost systemUiWidgetHost;
    private static int currentSystemUiWidgetId = -1;
    private static String currentBoundProvider = null;
    private static final AtomicInteger systemServerLogCounter = new AtomicInteger(0);

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedBridge.log(TAG + ": entry package=" + lpparam.packageName + " process=" + lpparam.processName);
            if (isAllHookDisabled()) {
                XposedBridge.log(TAG + ": all hooks disabled by marker");
                return;
            }

            if ("android".equals(lpparam.packageName) || "system".equals(lpparam.packageName)) {
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

    private void handleAndroidSystem(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedBridge.log(TAG + ": loaded in framework package=" + lpparam.packageName + " process=" + lpparam.processName);
            if (isLiveHookDisabled()) {
                XposedBridge.log(TAG + ": system_server live hook disabled by marker");
                return;
            }
            hookServiceHasBindAppWidgetPermission(lpparam.classLoader);
            hookSecurityPolicyHasBindAppWidgetPermission(lpparam.classLoader);
            hookBindAppWidgetId(lpparam.classLoader);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": handleAndroidSystem error " + t);
        }
    }

    private void hookServiceHasBindAppWidgetPermission(ClassLoader classLoader) {
        try {
            Class<?> serviceClass = Class.forName("com.android.server.appwidget.AppWidgetServiceImpl", false, classLoader);
            Method method = serviceClass.getDeclaredMethod("hasBindAppWidgetPermission", String.class, int.class);
            method.setAccessible(true);
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        String packageName = String.valueOf(param.args[0]);
                        if (SYSTEMUI_PACKAGE.equals(packageName)) {
                            param.setResult(true);
                            logSystemServerLimited("allowed AppWidget bind permission via AppWidgetServiceImpl user=" + safeInt(param.args[1], 0));
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
            Class<?> policyClass = Class.forName("com.android.server.appwidget.AppWidgetServiceImpl$SecurityPolicy", false, classLoader);
            Method method = policyClass.getDeclaredMethod("hasBindAppWidgetPermission", String.class, int.class);
            method.setAccessible(true);
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        String packageName = String.valueOf(param.args[0]);
                        if (SYSTEMUI_PACKAGE.equals(packageName)) {
                            param.setResult(true);
                            logSystemServerLimited("allowed AppWidget bind permission via SecurityPolicy");
                        }
                    } catch (Throwable t) {
                        XposedBridge.log(TAG + ": SecurityPolicy hook error " + t);
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
            Class<?> serviceClass = Class.forName("com.android.server.appwidget.AppWidgetServiceImpl", false, classLoader);
            Method method = serviceClass.getDeclaredMethod("bindAppWidgetId", String.class, int.class, int.class, ComponentName.class, Bundle.class);
            method.setAccessible(true);
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        String callingPackage = String.valueOf(param.args[0]);
                        if (!SYSTEMUI_PACKAGE.equals(callingPackage)) return;

                        int appWidgetId = safeInt(param.args[1], -1);
                        int providerProfileId = safeInt(param.args[2], 0);
                        ComponentName provider = param.args[3] instanceof ComponentName ? (ComponentName) param.args[3] : null;
                        XposedBridge.log(TAG + ": bindAppWidgetId called by SystemUI id=" + appWidgetId + " user=" + providerProfileId + " provider=" + provider);
                        grantBindPermissionToSystemUi(param.thisObject, providerProfileId);
                        addSystemUiToPermissionCollection(param.thisObject, providerProfileId);
                    } catch (Throwable t) {
                        XposedBridge.log(TAG + ": bindAppWidgetId before hook error " + t);
                    }
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        String callingPackage = String.valueOf(param.args[0]);
                        if (SYSTEMUI_PACKAGE.equals(callingPackage)) {
                            XposedBridge.log(TAG + ": bindAppWidgetId final result for SystemUI = " + param.getResult());
                        }
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
        long token = Binder.clearCallingIdentity();
        try {
            Method grantMethod = serviceObject.getClass().getDeclaredMethod("setBindAppWidgetPermission", String.class, int.class, boolean.class);
            grantMethod.setAccessible(true);
            grantMethod.invoke(serviceObject, SYSTEMUI_PACKAGE, userId, true);
            logSystemServerLimited("granted bind appwidget permission to SystemUI user=" + userId);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": grant bind permission error " + t);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void addSystemUiToPermissionCollection(Object serviceObject, int userId) {
        try {
            Object lock = getFieldRecursive(serviceObject, "mLock");
            Object collectionObject = getFieldRecursive(serviceObject, "mPackagesWithBindWidgetPermission");
            if (!(collectionObject instanceof Collection)) {
                XposedBridge.log(TAG + ": mPackagesWithBindWidgetPermission is not Collection: " + collectionObject);
                return;
            }
            Collection collection = (Collection) collectionObject;
            Object pair = Pair.create(userId, SYSTEMUI_PACKAGE);
            if (lock != null) {
                synchronized (lock) {
                    if (!collection.contains(pair)) {
                        collection.add(pair);
                        logSystemServerLimited("added SystemUI to mPackagesWithBindWidgetPermission collection=" + collection.getClass().getName() + " user=" + userId);
                    }
                }
            } else if (!collection.contains(pair)) {
                collection.add(pair);
                logSystemServerLimited("added SystemUI to mPackagesWithBindWidgetPermission without lock user=" + userId);
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": addSystemUiToPermissionCollection error " + t);
        }
    }

    private void diagnoseAppWidgetMethods(ClassLoader classLoader) {
        diagnoseClass(classLoader, "com.android.server.appwidget.AppWidgetServiceImpl");
        diagnoseClass(classLoader, "com.android.server.appwidget.AppWidgetServiceImpl$SecurityPolicy");
    }

    private void diagnoseClass(ClassLoader classLoader, String className) {
        try {
            Class<?> clazz = Class.forName(className, false, classLoader);
            Method[] methods = clazz.getDeclaredMethods();
            for (Method method : methods) {
                String name = method.getName().toLowerCase();
                if (name.contains("bind") || name.contains("permission") || name.contains("widget") || name.contains("host") || name.contains("provider")) {
                    XposedBridge.log(TAG + ": diagnostic method " + method);
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": diagnostic class error " + className + " / " + t);
        }
    }

    private void handleSystemUi(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedBridge.log(TAG + ": loaded in " + lpparam.packageName);
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
            hookMethod(clazz, "onFinishInflate");
            XposedBridge.log(TAG + ": hooked class " + className);
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
                        final View root = (View) param.thisObject;
                        XposedBridge.log(TAG + ": hooked " + root.getClass().getName() + "#onFinishInflate");
                        applyAll(root);
                        root.postDelayed(new Runnable() { @Override public void run() { applyAll(root); } }, 250L);
                        root.postDelayed(new Runnable() { @Override public void run() { applyAll(root); } }, 1000L);
                        root.postDelayed(new Runnable() { @Override public void run() { applyAll(root); } }, 2500L);
                        root.postDelayed(new Runnable() { @Override public void run() { applyAll(root); } }, 5000L);
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

            // Only KeyguardStatusView is used as the trigger. The overlay is mounted into keyguard_root_view below.
            if (root.getClass().getName().contains("KeyguardStatusView")) {
                addWidgetToLockscreen(root);
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": applyAll error " + t);
        }
    }

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
                int id = root.getResources().getIdentifier(name, "id", SYSTEMUI_PACKAGE);
                if (id == 0) continue;
                View view = root.findViewById(id);
                if (view == null) continue;
                hideViewHard(view);
                XposedBridge.log(TAG + ": hidden by id " + name + " / " + view.getClass().getName());
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": hideByIds error for " + name + " / " + t);
            }
        }
    }

    private static void hideRecursive(View view) {
        try {
            String className = view.getClass().getName();
            if (className.contains("AnimatableClockView") || className.contains("TrKeyguardClockView") || className.contains("TrKeyguardSingleClockView") || className.contains("TrKeyguardDoubleClockView")) {
                hideViewHard(view);
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

    private static void hideViewHard(View view) {
        view.setVisibility(View.GONE);
        view.setAlpha(0.0f);
        view.setScaleX(0.0f);
        view.setScaleY(0.0f);
    }

    private static void hideKeyguardClockContainerLikeIconify(View root) {
        try {
            int id = root.getResources().getIdentifier("keyguard_clock_container", "id", SYSTEMUI_PACKAGE);
            if (id == 0) return;
            View view = root.findViewById(id);
            if (view == null) return;
            ViewGroup.LayoutParams lp = view.getLayoutParams();
            if (lp != null) {
                lp.width = 0;
                lp.height = 0;
                view.setLayoutParams(lp);
            }
            view.setVisibility(View.INVISIBLE);
            view.setAlpha(0.0f);
            XposedBridge.log(TAG + ": hidden keyguard_clock_container like Iconify");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": hideKeyguardClockContainerLikeIconify error " + t);
        }
    }

    private static void addWidgetToLockscreen(final View triggerRoot) {
        try {
            final Context context = triggerRoot.getContext();
            final Config config = readConfig();
            if (config == null) return;

            final ViewGroup fallback = getStatusViewContainer(triggerRoot);
            if (fallback == null) {
                XposedBridge.log(TAG + ": statusContainer fallback is null");
                return;
            }

            final ViewGroup overlayParent = getIconifyOverlayParent(triggerRoot, fallback);
            disableClipping(overlayParent);

            View existing = findExistingOverlay(overlayParent);
            if (existing != null) {
                applyIconifyStableDepthCompat(existing);
                applyOverlayParams(context, existing, config);
                scheduleDepthRepair(triggerRoot, existing);
                return;
            }

            removeOldOverlays(fallback);
            if (overlayParent != fallback) removeOldOverlays(overlayParent);

            final FrameLayout overlay = new FrameLayout(context);
            overlay.setTag(COMBINED_OVERLAY_TAG);
            overlay.setId(View.generateViewId());
            overlay.setClipChildren(false);
            overlay.setClipToPadding(false);
            applyIconifyStableDepthCompat(overlay);

            int widthPx = dp(context, config.widthDp);
            int heightPx = dp(context, config.heightDp);
            View widgetView = null;
            if (config.liveMode) {
                widgetView = createSystemUiLiveWidgetView(context, config, widthPx, heightPx);
            } else {
                XposedBridge.log(TAG + ": live mode disabled by config");
            }
            if (widgetView == null) widgetView = createSnapshotView(context, config, widthPx, heightPx);
            if (widgetView == null) {
                TextView error = new TextView(context);
                error.setText("Widget unavailable\n" + config.provider);
                error.setTextSize(14.0f);
                error.setGravity(17);
                error.setAlpha(0.75f);
                widgetView = error;
            }

            overlay.addView(widgetView, new FrameLayout.LayoutParams(-1, -1));
            addOverlayToDepthParent(overlayParent, overlay, makeParentLayoutParams(overlayParent, widthPx, heightPx));
            applyOverlayParams(context, overlay, config);
            scheduleDepthRepair(triggerRoot, overlay);
            XposedBridge.log(TAG + ": widget overlay added into Iconify depth parent, provider=" + config.provider);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": addWidgetToLockscreen error " + t);
        }
    }

    private static ViewGroup getIconifyOverlayParent(View triggerRoot, ViewGroup fallback) {
        try {
            View wholeRoot = triggerRoot.getRootView();
            int keyguardRootId = triggerRoot.getResources().getIdentifier("keyguard_root_view", "id", SYSTEMUI_PACKAGE);
            if (keyguardRootId != 0) {
                View keyguardRoot = wholeRoot.findViewById(keyguardRootId);
                if (keyguardRoot instanceof ViewGroup) {
                    XposedBridge.log(TAG + ": keyguard_root_view for Iconify overlay found id=" + keyguardRootId);
                    return (ViewGroup) keyguardRoot;
                }
            }

            View foreground = findTaggedContainsRecursive(wholeRoot, "iconify_depth_wallpaper_foreground");
            if (foreground != null && foreground.getParent() instanceof ViewGroup) {
                XposedBridge.log(TAG + ": Iconify foreground parent for overlay found");
                return (ViewGroup) foreground.getParent();
            }

            XposedBridge.log(TAG + ": keyguard_root_view for Iconify overlay NOT found, using fallback=" + fallback.getClass().getName());
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": getIconifyOverlayParent error " + t);
        }
        return fallback;
    }

    private static void addOverlayToDepthParent(ViewGroup parent, View overlay, ViewGroup.LayoutParams lp) {
        try {
            int foregroundIndex = findDirectChildIndexWithTag(parent, "iconify_depth_wallpaper_foreground");
            if (foregroundIndex >= 0) {
                parent.addView(overlay, foregroundIndex, lp);
                XposedBridge.log(TAG + ": overlay inserted before Iconify foreground index=" + foregroundIndex);
            } else {
                parent.addView(overlay, 0, lp);
                XposedBridge.log(TAG + ": overlay inserted at index=0 in depth parent, childCount=" + parent.getChildCount());
            }
            overlay.setZ(ICONIFY_LOCKSCREEN_ITEM_Z);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": addOverlayToDepthParent error " + t);
            try {
                parent.addView(overlay, lp);
                overlay.setZ(ICONIFY_LOCKSCREEN_ITEM_Z);
            } catch (Throwable ignored) { }
        }
    }

    private static void scheduleDepthRepair(final View triggerRoot, final View overlay) {
        try {
            Runnable repair = new Runnable() {
                @Override
                public void run() {
                    repairDepthLayer(triggerRoot, overlay);
                }
            };
            overlay.postDelayed(repair, 200L);
            overlay.postDelayed(repair, 700L);
            overlay.postDelayed(repair, 1500L);
            overlay.postDelayed(repair, 3000L);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": scheduleDepthRepair error " + t);
        }
    }

    private static void repairDepthLayer(View triggerRoot, View overlay) {
        try {
            if (overlay == null) return;
            ViewGroup fallback = getStatusViewContainer(triggerRoot);
            if (fallback == null) return;
            ViewGroup wantedParent = getIconifyOverlayParent(triggerRoot, fallback);
            ViewParent currentParent = overlay.getParent();
            if (currentParent != wantedParent && wantedParent != null) {
                if (currentParent instanceof ViewGroup) {
                    ((ViewGroup) currentParent).removeView(overlay);
                }
                addOverlayToDepthParent(wantedParent, overlay, makeParentLayoutParams(wantedParent, overlay.getLayoutParams() != null ? overlay.getLayoutParams().width : -2, overlay.getLayoutParams() != null ? overlay.getLayoutParams().height : -2));
                XposedBridge.log(TAG + ": overlay moved into Iconify depth parent by repair");
            }
            applyIconifyStableDepthCompat(overlay);

            View foreground = findTaggedContainsRecursive(triggerRoot.getRootView(), "iconify_depth_wallpaper_foreground");
            if (foreground != null) {
                foreground.setZ(ICONIFY_FOREGROUND_Z);
                XposedBridge.log(TAG + ": depth repair found foreground, overlayZ=" + overlay.getZ() + " fgZ=" + foreground.getZ());
            } else {
                XposedBridge.log(TAG + ": depth repair foreground not found, overlayParent=" + (overlay.getParent() == null ? "null" : overlay.getParent().getClass().getName()));
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": repairDepthLayer error " + t);
        }
    }

    private static View findExistingOverlay(ViewGroup parent) {
        try {
            View v = parent.findViewWithTag(COMBINED_OVERLAY_TAG);
            if (v != null) return v;
            v = parent.findViewWithTag(OVERLAY_TAG);
            if (v != null) return v;
            return findTaggedRecursive(parent, COMBINED_OVERLAY_TAG);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static View findTaggedRecursive(View view, String tag) {
        if (view == null) return null;
        Object currentTag = view.getTag();
        if (currentTag != null && tag.equals(String.valueOf(currentTag))) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findTaggedRecursive(group.getChildAt(i), tag);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static View findTaggedContainsRecursive(View view, String token) {
        if (view == null || token == null) return null;
        Object currentTag = view.getTag();
        if (currentTag != null && String.valueOf(currentTag).contains(token)) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findTaggedContainsRecursive(group.getChildAt(i), token);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static int findDirectChildIndexWithTag(ViewGroup parent, String token) {
        if (parent == null || token == null) return -1;
        for (int i = 0; i < parent.getChildCount(); i++) {
            Object tag = parent.getChildAt(i).getTag();
            if (tag == null) continue;
            String text = String.valueOf(tag);
            if (text.contains(token)) return i;
        }
        return -1;
    }

    private static void applyIconifyStableDepthCompat(final View overlay) {
        try {
            overlay.setTag(COMBINED_OVERLAY_TAG);
            overlay.setZ(ICONIFY_LOCKSCREEN_ITEM_Z);
            overlay.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View v) { v.setZ(ICONIFY_LOCKSCREEN_ITEM_Z); }
                @Override public void onViewDetachedFromWindow(View v) { }
            });
            overlay.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    v.setZ(ICONIFY_LOCKSCREEN_ITEM_Z);
                }
            });
            XposedBridge.log(TAG + ": Iconify stable depth compat applied tag=" + overlay.getTag() + " z=" + overlay.getZ());
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": applyIconifyStableDepthCompat error " + t);
        }
    }

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
                needNewId = true;
            } else if (savedProvider != null && savedProvider.length() > 0 && !savedProvider.equals(config.provider)) {
                deleteWidgetIdQuietly(savedId);
                clearSystemUiWidgetId();
                clearSystemUiWidgetProvider();
                currentSystemUiWidgetId = -1;
                currentBoundProvider = null;
                needNewId = true;
            } else {
                AppWidgetProviderInfo savedInfo = null;
                try { savedInfo = manager.getAppWidgetInfo(savedId); } catch (Throwable t) { XposedBridge.log(TAG + ": getAppWidgetInfo(savedId) error " + t); }
                if (savedInfo == null) {
                    deleteWidgetIdQuietly(savedId);
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
                try { bound = manager.bindAppWidgetIdIfAllowed(savedId, provider); } catch (Throwable t) { XposedBridge.log(TAG + ": bindAppWidgetIdIfAllowed error " + t); }
                XposedBridge.log(TAG + ": SystemUI bind result " + bound + " provider=" + config.provider);
                if (!bound) {
                    deleteWidgetIdQuietly(savedId);
                    return null;
                }
                currentSystemUiWidgetId = savedId;
                currentBoundProvider = config.provider;
                writeSystemUiWidgetId(savedId);
                writeSystemUiWidgetProvider(config.provider);
            }

            updateSystemUiWidgetSizeOptions(manager, savedId, config);
            AppWidgetProviderInfo info = manager.getAppWidgetInfo(savedId);
            if (info == null) {
                deleteWidgetIdQuietly(savedId);
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

    private static void updateSystemUiWidgetSizeOptions(AppWidgetManager manager, int appWidgetId, Config config) {
        try {
            if (manager == null || appWidgetId <= 0 || config == null) return;
            Bundle options = new Bundle();
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, config.widthDp);
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, config.heightDp);
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, config.widthDp);
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, config.heightDp);
            manager.updateAppWidgetOptions(appWidgetId, options);
            XposedBridge.log(TAG + ": updated SystemUI widget options id=" + appWidgetId + " w=" + config.widthDp + " h=" + config.heightDp);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": updateSystemUiWidgetSizeOptions error " + t);
        }
    }

    private static void deleteWidgetIdQuietly(int id) {
        try {
            if (systemUiWidgetHost != null && id > 0) systemUiWidgetHost.deleteAppWidgetId(id);
        } catch (Throwable ignored) { }
    }

    private static View createSnapshotView(Context context, Config config, int widthPx, int heightPx) {
        try {
            String path = config.snapshotPath;
            if (path == null || path.length() == 0) path = SNAPSHOT_FILE;
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
            imageView.setAlpha(1.0f);
            imageView.setLayoutParams(new FrameLayout.LayoutParams(widthPx, heightPx));
            XposedBridge.log(TAG + ": snapshot widget view created " + path);
            return imageView;
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": createSnapshotView error " + t);
            return null;
        }
    }

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
            float scale = config.scalePercent / 100.0f;
            overlay.setScaleX(scale);
            overlay.setScaleY(scale);
            overlay.setPivotX(widthPx / 2.0f);
            overlay.setPivotY(0.0f);
            overlay.setZ(ICONIFY_LOCKSCREEN_ITEM_Z);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": applyOverlayParams error " + t);
        }
    }

    private static ViewGroup getStatusViewContainer(View root) {
        try {
            Object field = getFieldRecursive(root, "mStatusViewContainer");
            if (field instanceof ViewGroup) {
                XposedBridge.log(TAG + ": found mStatusViewContainer field");
                return (ViewGroup) field;
            }
        } catch (Throwable ignored) { }

        try {
            int id = root.getResources().getIdentifier("status_view_container", "id", SYSTEMUI_PACKAGE);
            if (id != 0) {
                View view = root.findViewById(id);
                if (view instanceof ViewGroup) {
                    XposedBridge.log(TAG + ": found status_view_container by id");
                    return (ViewGroup) view;
                }
            }
        } catch (Throwable ignored) { }

        if (root instanceof ViewGroup) {
            XposedBridge.log(TAG + ": fallback root as statusContainer");
            return (ViewGroup) root;
        }
        return null;
    }

    private static Object getFieldRecursive(Object object, String fieldName) throws Throwable {
        if (object == null) return null;
        for (Class<?> clazz = object.getClass(); clazz != null; clazz = clazz.getSuperclass()) {
            try {
                Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(object);
            } catch (NoSuchFieldException ignored) { }
        }
        return null;
    }

    private static void removeOldOverlays(ViewGroup parent) {
        try {
            removeTaggedViews(parent, OVERLAY_TAG);
            removeTaggedViews(parent, COMBINED_OVERLAY_TAG);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": removeOldOverlays error " + t);
        }
    }

    private static void removeTaggedViews(ViewGroup parent, String tag) {
        View old = parent.findViewWithTag(tag);
        while (old != null) {
            ViewParent p = old.getParent();
            if (p instanceof ViewGroup) ((ViewGroup) p).removeView(old);
            old = parent.findViewWithTag(tag);
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
        } catch (Throwable ignored) { }
    }

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
            XposedBridge.log(TAG + ": config provider=" + config.provider + " apk_id=" + config.widgetId + " live=" + config.liveMode + " x=" + config.xDp + " y=" + config.yDp + " w=" + config.widthDp + " h=" + config.heightDp + " scale=" + config.scalePercent);
            return config;
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": readConfig error " + t);
            return null;
        }
    }

    private static int parseInt(String value, int fallback) {
        if (value == null) return fallback;
        try { return Integer.parseInt(value.trim()); } catch (Throwable ignored) { return fallback; }
    }

    private static boolean parseBoolean(String value, boolean fallback) {
        if (value == null) return fallback;
        try {
            String v = value.trim().toLowerCase();
            if ("true".equals(v) || "1".equals(v) || "yes".equals(v) || "on".equals(v)) return true;
            if ("false".equals(v) || "0".equals(v) || "no".equals(v) || "off".equals(v)) return false;
            return fallback;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

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
        writeTextFile(SYSTEMUI_WIDGET_ID_FILE, String.valueOf(id), "SystemUI widget id=" + id);
    }

    private static void clearSystemUiWidgetId() {
        deleteFile(SYSTEMUI_WIDGET_ID_FILE);
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
        writeTextFile(SYSTEMUI_WIDGET_PROVIDER_FILE, provider, "SystemUI widget provider=" + provider);
    }

    private static void clearSystemUiWidgetProvider() {
        deleteFile(SYSTEMUI_WIDGET_PROVIDER_FILE);
    }

    private static void writeTextFile(String path, String text, String logName) {
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
            XposedBridge.log(TAG + ": wrote " + logName);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": writeTextFile error " + path + " / " + t);
        }
    }

    private static void deleteFile(String path) {
        try {
            File file = new File(path);
            if (file.exists()) file.delete();
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": deleteFile error " + path + " / " + t);
        }
    }

    private static ViewGroup.LayoutParams makeParentLayoutParams(ViewGroup parent, int widthPx, int heightPx) {
        if (parent instanceof LinearLayout) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(widthPx, heightPx);
            lp.gravity = 49;
            return lp;
        }
        if (parent instanceof FrameLayout) {
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(widthPx, heightPx);
            lp.gravity = 49;
            return lp;
        }
        if (parent instanceof RelativeLayout) {
            RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(widthPx, heightPx);
            lp.addRule(14);
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

    private static int safeInt(Object value, int fallback) {
        try {
            if (value instanceof Integer) return ((Integer) value).intValue();
            return Integer.parseInt(String.valueOf(value));
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static void logSystemServerLimited(String message) {
        int count = systemServerLogCounter.getAndIncrement();
        if (count < MAX_SYSTEM_SERVER_LOGS) {
            XposedBridge.log(TAG + ": " + message);
        }
    }

    private static class Config {
        String provider;
        int widgetId;
        int xDp;
        int yDp;
        int widthDp;
        int heightDp;
        int scalePercent;
        boolean liveMode;
        String snapshotPath;
    }
}
