# HideLockWidgets Depth UI

Material 3 / Compose settings UI for HideLockWidgets.

## Pages

- Settings: choose background and foreground images, enable depth wallpaper, set preview widget count.
- Preview: drag and pinch-to-zoom foreground/background layers in a lockscreen preview.

## Build locally

```bash
gradle assembleDebug
```

## Build on GitHub Actions

The workflow is here:

```text
.github/workflows/build.yml
```

Run it from GitHub Actions -> Build APK -> Run workflow.

## Output

The debug APK will be uploaded as an artifact named:

```text
HideLockWidgets-debug-apk
```

## Note

This archive contains the settings UI and a placeholder `MainHook.java` so the project keeps the LSPosed entry point path. Replace `MainHook.java` with your current working hook when you merge it into the real module.
