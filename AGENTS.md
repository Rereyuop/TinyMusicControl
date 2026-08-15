# Android build versioning

- The visible app version comes from `defaultConfig.versionName` and is rendered beside the “媒体音量” title through `BuildConfig.VERSION_NAME`.
- On every user-requested Android build, increment the patch version in `app/build.gradle.kts` before building (for example, `0.0.1` → `0.0.2`) and increment `versionCode` by one.
- When installing a debug APK to a connected Android device, launch `com.chayu.volumecontrol/.MainActivity` immediately after a successful install.
