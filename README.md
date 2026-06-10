# HideLockWidgets V3 Widget Studio

LSPosed module + settings app for custom lockscreen widgets on HiOS/SystemUI.

## V3

- Home screen in a FlClash / KernelSU Next inspired Material 3 style
- Real widget picker using `AppWidgetManager.getInstalledProviders()`
- Empty lockscreen preview canvas
- Drag / scale widgets, background and foreground layers
- Depth wallpaper: background -> widgets -> foreground
- Apply button writes `/data/local/tmp/hidelockwidgets/config.txt`
- Applies `appwidget grantbind --package com.android.systemui --user 0`
- Restarts SystemUI from the app
- LSPosed metadata and `xposed_init` included

## Notes

Grant can also be run manually:

```sh
su -c 'appwidget grantbind --package com.android.systemui --user 0'
```

If LSPosed does not show the module, reinstall the APK and check that `assets/xposed_init` exists.
