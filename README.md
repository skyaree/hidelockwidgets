<<<<<<< HEAD
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
=======
# HideLockWidgets LSPosed

LSPosed module for hiding stock lockscreen clocks on HiOS/SystemUI and placing a Yandex Music AppWidget/snapshot on the lockscreen.

## Important

Scopes:

- `com.android.systemui`
- `android`
- `system`
- `com.squeeare.hidelockwidgets`

Grant SystemUI AppWidget bind permission once:
>>>>>>> 43ab26e (Fix)

```sh
su -c 'appwidget grantbind --package com.android.systemui --user 0'
```

<<<<<<< HEAD
If LSPosed does not show the module, reinstall the APK and check that `assets/xposed_init` exists.


## V7
- Hotfix: Apply/Restart/Soft Restart/Reset now use a more reliable root path.
- Grants SystemUI and app package for AppWidget bind.
- Copies depth images into /data/local/tmp/hidelockwidgets before applying.
=======
## Config

Path:

```txt
/data/local/tmp/hidelockwidgets/config.txt
```

Example:

```ini
provider=ru.yandex.music/ru.yandex.music.ui.widget.WidgetRecentlyRectangleReceiver
widget_id=23
x_dp=0
y_dp=120
width_dp=320
height_dp=160
scale_percent=100
live_mode=true
depth_compat=true
snapshot=/data/local/tmp/hidelockwidgets/widget_snapshot.png
```

## Iconify stable depth wallpaper compatibility

This version uses Iconify stable tags exactly:

- `iconify_lockscreen_widget`
- `iconify_depth_wallpaper_foreground`
- `iconify_depth_wallpaper_background`

The widget tag is combined as:

```txt
hidelockwidgets_widget|iconify_lockscreen_widget
```

Iconify splits tags by `|`, so it can detect this widget as a lockscreen widget. The module keeps the widget at `z=-1`, Iconify background at `z=-2`, and Iconify foreground/subject at `z=-0.5`.

## Reset live widget

```sh
su -c 'rm -f /data/local/tmp/hidelockwidgets/systemui_widget_id.txt'
su -c 'rm -f /data/local/tmp/hidelockwidgets/systemui_widget_provider.txt'
su -c 'rm -f /data/local/tmp/hidelockwidgets/disable_live'
su -c 'killall com.android.systemui'
```

## Logs

```sh
su -c 'grep -i "HideLockWidgets" /data/adb/lspd/log/verbose_*.log /data/adb/lspd/log/modules_*.log 2>/dev/null | grep -i "Iconify stable depth compat\|SystemUI bind result\|live SystemUI\|snapshot" | tail -150'
```
>>>>>>> 43ab26e (Fix)
