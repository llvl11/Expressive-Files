# Fix Android Resource Linking Failed

The build is failing because the XML theme `Theme.MyApplication` in `themes.xml` uses `Theme.Material3.DayNight.NoActionBar` as a parent. This theme is part of the **Google Material Components for Android** library, which is currently missing from the project's dependencies.

## Proposed Changes

### Build Configuration

#### [MODIFY] [libs.versions.toml](file:///C:/Users/pc/StudioProjects/File-manager2/gradle/libs.versions.toml)
- Add versions for `material` and `appcompat`.
- Add library definitions for `com.google.android.material:material` and `androidx.appcompat:appcompat`.

#### [MODIFY] [build.gradle.kts](file:///C:/Users/pc/StudioProjects/File-manager2/app/build.gradle.kts)
- Add `libs.material` and `libs.androidx.appcompat` to the `dependencies` block.

## Verification Plan

### Automated Tests
- Run `./gradlew :app:processDebugResources` to verify that resource linking now succeeds.
- Run a full build: `./gradlew assembleDebug`.

### Manual Verification
- Deploy the app to a device/emulator to ensure the theme is correctly applied during startup.
