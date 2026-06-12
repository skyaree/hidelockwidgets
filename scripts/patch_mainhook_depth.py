from pathlib import Path

p = Path('app/src/main/java/com/squeeare/hidelockwidgets/MainHook.java')
s = p.read_text()

if 'DEPTH_BACKGROUND_FILE' not in s:
    s = s.replace('private static final String SNAPSHOT_FILE = ROOT_DIR + "/widget_snapshot.png";\n',
        'private static final String SNAPSHOT_FILE = ROOT_DIR + "/widget_snapshot.png";\n'
        '    private static final String DEPTH_BACKGROUND_FILE = ROOT_DIR + "/depth_background.png";\n'
        '    private static final String DEPTH_FOREGROUND_FILE = ROOT_DIR + "/depth_foreground.png";\n'
        '    private static final String DEPTH_BACKGROUND_TAG = "hidelockwidgets_depth_background";\n'
        '    private static final String DEPTH_FOREGROUND_TAG = "hidelockwidgets_depth_foreground";\n')

s = s.replace('final ViewGroup overlayParent = getIconifyOverlayParent(triggerRoot, fallback);\n            disableClipping(overlayParent);',
              'final ViewGroup overlayParent = getOwnWidgetParent(triggerRoot, fallback);\n            disableClipping(overlayParent);\n            cleanupOwnDepthBackground(triggerRoot);\n            ensureOwnDepthForeground(context, overlayParent, config);')
s = s.replace('ViewGroup wantedParent = getIconifyOverlayParent(triggerRoot, fallback);', 'ViewGroup wantedParent = getOwnWidgetParent(triggerRoot, fallback);')
s = s.replace('int foregroundIndex = findDirectChildIndexWithTag(parent, "iconify_depth_wallpaper_foreground");',
              'int foregroundIndex = findDirectChildIndexWithTag(parent, DEPTH_FOREGROUND_TAG);\n            if (foregroundIndex < 0) foregroundIndex = findDirectChildIndexWithTag(parent, "iconify_depth_wallpaper_foreground");')
s = s.replace('View foreground = findTaggedContainsRecursive(triggerRoot.getRootView(), "iconify_depth_wallpaper_foreground");',
              'View foreground = findTaggedContainsRecursive(getOwnWidgetParent(triggerRoot, fallback), DEPTH_FOREGROUND_TAG);')
s = s.replace('overlay.setZ(ICONIFY_LOCKSCREEN_ITEM_Z);', 'overlay.setZ(1.0f);')
s = s.replace('v.setZ(ICONIFY_LOCKSCREEN_ITEM_Z);', 'v.setZ(1.0f);')
s = s.replace('foreground.setZ(ICONIFY_FOREGROUND_Z);', 'foreground.setZ(2.0f);')
s = s.replace('applyOverlayParams(context, overlay, config);\n            scheduleDepthRepair(triggerRoot, overlay);',
              'applyOverlayParams(context, overlay, config);\n            installLockscreenOnlyVisibility(context, triggerRoot, overlay);\n            scheduleDepthRepair(triggerRoot, overlay);')
s = s.replace('applyOverlayParams(context, existing, config);\n                scheduleDepthRepair(triggerRoot, existing);',
              'applyOverlayParams(context, existing, config);\n                installLockscreenOnlyVisibility(context, triggerRoot, existing);\n                scheduleDepthRepair(triggerRoot, existing);')
s = s.replace('config.snapshotPath = properties.getProperty("snapshot", SNAPSHOT_FILE);\n            XposedBridge.log(TAG + ": config provider="',
              'config.snapshotPath = properties.getProperty("snapshot", SNAPSHOT_FILE);\n            config.customDepth = parseBoolean(properties.getProperty("custom_depth"), true);\n            config.depthBackgroundPath = properties.getProperty("depth_background", DEPTH_BACKGROUND_FILE);\n            config.depthForegroundPath = properties.getProperty("depth_foreground", DEPTH_FOREGROUND_FILE);\n            XposedBridge.log(TAG + ": config provider="')
s = s.replace('String snapshotPath;\n    }', 'String snapshotPath;\n        boolean customDepth;\n        String depthBackgroundPath;\n        String depthForegroundPath;\n    }')

if 'private static boolean hasShadeContent(View view)' not in s:
    helper = '''    private static ViewGroup getOwnWidgetParent(View triggerRoot, ViewGroup fallback) {
        try {
            if (triggerRoot instanceof ViewGroup) {
                XposedBridge.log(TAG + ": using own widget/foreground parent=" + triggerRoot.getClass().getName());
                return (ViewGroup) triggerRoot;
            }
        } catch (Throwable t) { XposedBridge.log(TAG + ": getOwnWidgetParent error " + t); }
        return fallback;
    }

    private static ViewGroup getOwnWindowRoot(View triggerRoot) {
        try { View r = triggerRoot == null ? null : triggerRoot.getRootView(); if (r instanceof ViewGroup) return (ViewGroup) r; }
        catch (Throwable t) { XposedBridge.log(TAG + ": getOwnWindowRoot error " + t); }
        return null;
    }

    private static void cleanupOwnDepthBackground(View triggerRoot) {
        try { ViewGroup r = getOwnWindowRoot(triggerRoot); if (r != null) removeTaggedViews(r, DEPTH_BACKGROUND_TAG); }
        catch (Throwable t) { XposedBridge.log(TAG + ": cleanupOwnDepthBackground error " + t); }
    }

    private static void ensureOwnDepthForeground(Context context, ViewGroup parent, Config config) {
        try {
            if (context == null || parent == null || config == null || !config.customDepth) return;
            disableClipping(parent); removeTaggedViews(parent, DEPTH_FOREGROUND_TAG);
            android.util.DisplayMetrics dm = context.getResources().getDisplayMetrics();
            int[] loc = new int[2]; parent.getLocationOnScreen(loc);
            View fg = createDepthImageView(context, config.depthForegroundPath, ImageView.ScaleType.FIT_XY, DEPTH_FOREGROUND_TAG);
            if (fg == null) { XposedBridge.log(TAG + ": own depth foreground missing path=" + config.depthForegroundPath); return; }
            fg.setVisibility(View.GONE);
            fg.setZ(2.0f); fg.setTranslationX(-loc[0]); fg.setTranslationY(-loc[1]);
            parent.addView(fg, new ViewGroup.LayoutParams(dm.widthPixels, dm.heightPixels));
            installLockscreenOnlyVisibility(context, parent, fg);
            XposedBridge.log(TAG + ": own depth foreground added above widget parent=" + parent.getClass().getName() + " size=" + dm.widthPixels + "x" + dm.heightPixels + " shift=" + (-loc[0]) + "," + (-loc[1]) + " z=" + fg.getZ());
        } catch (Throwable t) { XposedBridge.log(TAG + ": ensureOwnDepthForeground error " + t); }
    }

    private static void installLockscreenOnlyVisibility(final Context context, final View triggerRoot, final View overlay) {
        try {
            if (overlay == null) return;
            overlay.setVisibility(View.GONE);
            final Runnable r = new Runnable() { @Override public void run() { updateLockscreenOnlyVisibility(context, triggerRoot); } };
            overlay.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() { @Override public void onViewAttachedToWindow(View v) { r.run(); } @Override public void onViewDetachedFromWindow(View v) { } });
            overlay.addOnLayoutChangeListener(new View.OnLayoutChangeListener() { @Override public void onLayoutChange(View v, int a, int b, int c, int d, int e, int f, int g, int h) { r.run(); } });
            overlay.post(r); overlay.postDelayed(r,150L); overlay.postDelayed(r,500L); overlay.postDelayed(r,1200L); overlay.postDelayed(r,2500L);
        } catch (Throwable t) { XposedBridge.log(TAG + ": installLockscreenOnlyVisibility error " + t); }
    }

    private static boolean hasShadeContent(View view) {
        if (view == null) return false;
        try {
            String n = view.getClass().getName();
            if (n != null && (n.contains("NotificationStackScrollLayout") || n.contains("ExpandableNotificationRow")) && view.isShown() && view.getHeight() > 80) return true;
            if (view instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) view;
                for (int i = 0; i < g.getChildCount(); i++) if (hasShadeContent(g.getChildAt(i))) return true;
            }
        } catch (Throwable ignored) { }
        return false;
    }

    private static void updateLockscreenOnlyVisibility(Context context, View triggerRoot) {
        try {
            boolean locked = false;
            try { android.app.KeyguardManager km = (android.app.KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE); locked = km != null && km.isKeyguardLocked(); } catch (Throwable ignored) { }
            View root = triggerRoot == null ? null : triggerRoot.getRootView(); if (root == null) return;
            android.util.DisplayMetrics dm = context.getResources().getDisplayMetrics();
            int[] loc = new int[2]; if (triggerRoot != null) triggerRoot.getLocationOnScreen(loc);
            boolean stable = triggerRoot != null && triggerRoot.isShown() && loc[1] >= 0 && loc[1] < (dm.heightPixels / 2);
            boolean shade = hasShadeContent(root);
            boolean show = locked && stable && !shade;
            int vis = show ? View.VISIBLE : View.GONE;
            View overlay = findTaggedContainsRecursive(root, OVERLAY_TAG);
            View fg = findTaggedRecursive(root, DEPTH_FOREGROUND_TAG);
            if (overlay != null) overlay.setVisibility(vis);
            if (fg != null) fg.setVisibility(vis);
            if (!show) cleanupOwnDepthBackground(triggerRoot);
            XposedBridge.log(TAG + ": own depth visibility show=" + show + " locked=" + locked + " stable=" + stable + " shade=" + shade + " locY=" + loc[1] + " overlay=" + (overlay != null) + " foreground=" + (fg != null));
        } catch (Throwable t) { XposedBridge.log(TAG + ": updateLockscreenOnlyVisibility error " + t); }
    }

    private static View createDepthImageView(Context context, String path, ImageView.ScaleType scaleType, String tag) {
        try { if (path == null || path.length() == 0) return null; File f = new File(path); if (!f.exists()) return null; Bitmap b = BitmapFactory.decodeFile(path); if (b == null) return null; ImageView v = new ImageView(context); v.setTag(tag); v.setImageBitmap(b); v.setScaleType(scaleType); v.setAdjustViewBounds(false); v.setClipToOutline(false); return v; }
        catch (Throwable t) { XposedBridge.log(TAG + ": createDepthImageView error " + path + " / " + t); return null; }
    }

'''
    marker = '    private static ViewGroup getIconifyOverlayParent(View triggerRoot, ViewGroup fallback) {'
    if marker not in s: raise SystemExit('insert point not found')
    s = s.replace(marker, helper + marker)

p.write_text(s)
