from pathlib import Path

p = Path('app/src/main/java/com/squeeare/hidelockwidgets/MainHook.java')
s = p.read_text()

if 'DEPTH_BACKGROUND_FILE' not in s:
    s = s.replace(
        'private static final String SNAPSHOT_FILE = ROOT_DIR + "/widget_snapshot.png";\n',
        'private static final String SNAPSHOT_FILE = ROOT_DIR + "/widget_snapshot.png";\n'
        '    private static final String DEPTH_BACKGROUND_FILE = ROOT_DIR + "/depth_background.png";\n'
        '    private static final String DEPTH_FOREGROUND_FILE = ROOT_DIR + "/depth_foreground.png";\n'
        '    private static final String DEPTH_BACKGROUND_TAG = "hidelockwidgets_depth_background";\n'
        '    private static final String DEPTH_FOREGROUND_TAG = "hidelockwidgets_depth_foreground";\n'
    )

s = s.replace(
    'final ViewGroup overlayParent = getIconifyOverlayParent(triggerRoot, fallback);\n            disableClipping(overlayParent);',
    'final ViewGroup overlayParent = getCustomDepthOverlayParent(triggerRoot, fallback);\n            disableClipping(overlayParent);\n            ensureCustomDepthLayers(context, overlayParent, config);'
)

s = s.replace(
    'ViewGroup wantedParent = getIconifyOverlayParent(triggerRoot, fallback);',
    'ViewGroup wantedParent = getCustomDepthOverlayParent(triggerRoot, fallback);'
)

s = s.replace(
    'int foregroundIndex = findDirectChildIndexWithTag(parent, "iconify_depth_wallpaper_foreground");',
    'int foregroundIndex = findDirectChildIndexWithTag(parent, DEPTH_FOREGROUND_TAG);\n            if (foregroundIndex < 0) foregroundIndex = findDirectChildIndexWithTag(parent, "iconify_depth_wallpaper_foreground");'
)

s = s.replace(
    'View foreground = findTaggedContainsRecursive(triggerRoot.getRootView(), "iconify_depth_wallpaper_foreground");',
    'View foreground = findTaggedContainsRecursive(getCustomDepthOverlayParent(triggerRoot, fallback), DEPTH_FOREGROUND_TAG);'
)

s = s.replace('overlay.setZ(ICONIFY_LOCKSCREEN_ITEM_Z);', 'overlay.setZ(1.0f);')
s = s.replace('v.setZ(ICONIFY_LOCKSCREEN_ITEM_Z);', 'v.setZ(1.0f);')

s = s.replace(
    'config.snapshotPath = properties.getProperty("snapshot", SNAPSHOT_FILE);\n            XposedBridge.log(TAG + ": config provider="',
    'config.snapshotPath = properties.getProperty("snapshot", SNAPSHOT_FILE);\n            config.customDepth = parseBoolean(properties.getProperty("custom_depth"), true);\n            config.depthBackgroundPath = properties.getProperty("depth_background", DEPTH_BACKGROUND_FILE);\n            config.depthForegroundPath = properties.getProperty("depth_foreground", DEPTH_FOREGROUND_FILE);\n            XposedBridge.log(TAG + ": config provider="'
)

s = s.replace(
    'String snapshotPath;\n    }',
    'String snapshotPath;\n        boolean customDepth;\n        String depthBackgroundPath;\n        String depthForegroundPath;\n    }'
)

helper_marker = 'private static void ensureCustomDepthLayers(Context context, ViewGroup parent, Config config)'
if helper_marker not in s:
    helper = '''    private static ViewGroup getCustomDepthOverlayParent(View triggerRoot, ViewGroup fallback) {
        try {
            if (triggerRoot instanceof ViewGroup) {
                XposedBridge.log(TAG + ": using own depth wallpaper root=" + triggerRoot.getClass().getName());
                return (ViewGroup) triggerRoot;
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": getCustomDepthOverlayParent error " + t);
        }
        return fallback;
    }

    private static void ensureCustomDepthLayers(Context context, ViewGroup parent, Config config) {
        try {
            if (parent == null || config == null || !config.customDepth) return;
            removeTaggedViews(parent, DEPTH_BACKGROUND_TAG);
            removeTaggedViews(parent, DEPTH_FOREGROUND_TAG);

            android.util.DisplayMetrics dm = context.getResources().getDisplayMetrics();
            int[] loc = new int[2];
            parent.getLocationOnScreen(loc);

            View bg = createDepthImageView(context, config.depthBackgroundPath, ImageView.ScaleType.CENTER_CROP, DEPTH_BACKGROUND_TAG);
            if (bg != null) {
                bg.setZ(0.0f);
                bg.setTranslationX(-loc[0]);
                bg.setTranslationY(-loc[1]);
                parent.addView(bg, 0, new ViewGroup.LayoutParams(dm.widthPixels, dm.heightPixels));
                XposedBridge.log(TAG + ": own depth background added fullscreen size=" + dm.widthPixels + "x" + dm.heightPixels + " shift=" + (-loc[0]) + "," + (-loc[1]) + " z=" + bg.getZ());
            } else {
                XposedBridge.log(TAG + ": own depth background missing path=" + config.depthBackgroundPath);
            }

            View fg = createDepthImageView(context, config.depthForegroundPath, ImageView.ScaleType.CENTER_CROP, DEPTH_FOREGROUND_TAG);
            if (fg != null) {
                fg.setZ(2.0f);
                fg.setTranslationX(-loc[0]);
                fg.setTranslationY(-loc[1]);
                parent.addView(fg, new ViewGroup.LayoutParams(dm.widthPixels, dm.heightPixels));
                XposedBridge.log(TAG + ": own depth foreground added fullscreen size=" + dm.widthPixels + "x" + dm.heightPixels + " shift=" + (-loc[0]) + "," + (-loc[1]) + " z=" + fg.getZ());
            } else {
                XposedBridge.log(TAG + ": own depth foreground missing path=" + config.depthForegroundPath);
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": ensureCustomDepthLayers error " + t);
        }
    }

    private static View createDepthImageView(Context context, String path, ImageView.ScaleType scaleType, String tag) {
        try {
            if (path == null || path.length() == 0) return null;
            File file = new File(path);
            if (!file.exists()) return null;
            Bitmap bitmap = BitmapFactory.decodeFile(path);
            if (bitmap == null) return null;
            ImageView imageView = new ImageView(context);
            imageView.setTag(tag);
            imageView.setImageBitmap(bitmap);
            imageView.setScaleType(scaleType);
            imageView.setAdjustViewBounds(false);
            imageView.setClipToOutline(false);
            return imageView;
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": createDepthImageView error " + path + " / " + t);
            return null;
        }
    }

'''
    insert_before = '    private static ViewGroup getIconifyOverlayParent(View triggerRoot, ViewGroup fallback) {'
    if insert_before not in s:
        raise SystemExit('insert point not found for custom depth helper')
    s = s.replace(insert_before, helper + insert_before)

p.write_text(s)
