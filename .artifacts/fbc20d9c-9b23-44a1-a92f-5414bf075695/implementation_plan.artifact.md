# Implementation Plan - File Manager Fixes and Enhancements

This plan addresses several UI issues, a crash, and functional requirements for the file manager application.

## User Review Required

> [!IMPORTANT]
> - **Open With Choice:** Removing `Intent.createChooser` in `openFile` will allow the system to show the "Just once / Always" choice, but it will no longer force a chooser if a default app is already set. If no default is set, the system resolver will appear automatically.
> - **Top Bar Actions:** I will modify `HomeTopBar` to ensure Search and Layout buttons remain accessible or are correctly integrated when selection mode is active, as requested.

## Proposed Changes

### UI & Layout Fixes

#### [MODIFY] [HomeScreen.kt](file:///C:/Users/pc/StudioProjects/File-manager2/app/src/main/java/com/baiel/expressivefiles/ui/screens/HomeScreen.kt)
- Fix the crash by coercing `fabOffset` to be non-negative.
- Ensure `padding(bottom = fabOffset.coerceAtLeast(0.dp))` is used.

#### [MODIFY] [HomeBottomActionStrip.kt](file:///C:/Users/pc/StudioProjects/File-manager2/app/src/main/java/com/baiel/expressivefiles/ui/screens/home/HomeBottomActionStrip.kt)
- Remove the text summary ("X selected", "Tap X to deselect all") from the bottom action strip to leave only the function icons.

#### [MODIFY] [HomeTopBar.kt](file:///C:/Users/pc/StudioProjects/File-manager2/app/src/main/java/com/baiel/expressivefiles/ui/screens/home/HomeTopBar.kt)
- Restore Search and Layout buttons to the top bar. I will adjust `HomeTopBar` so these actions are visible even when items are selected, or clarify their visibility logic.

---

### Functional Enhancements

#### [MODIFY] [FileManagerRepository.kt](file:///C:/Users/pc/StudioProjects/File-manager2/app/src/main/java/com/baiel/expressivefiles/data/FileManagerRepository.kt)
- Update `openFile` to NOT use `Intent.createChooser`. This allows the Android system resolver to show "Just once" and "Always" options.

#### [MODIFY] [FileCard.kt](file:///C:/Users/pc/StudioProjects/File-manager2/app/src/main/java/com/baiel/expressivefiles/ui/components/FileCard.kt)
- Improve video thumbnail reliability. I will verify if `VideoFrameDecoder` is correctly handling all formats and if the `videoFrameMicros` offset is optimal. I will also ensure `AsyncImage` is correctly configured to handle video files through Coil.

## Verification Plan

### Automated Tests
- Build the project to ensure no regressions in layout or logic.
- Verify `fabOffset` usage in `HomeScreen.kt`.

### Manual Verification
1. **Crash Fix:** Enter selection mode and ensure the app does not crash when the FAB animates.
2. **Bottom Strip:** Verify only icons are shown in the selection toolbar at the bottom.
3. **Top Bar:** Verify Search and Layout buttons are present.
4. **External App Choice:** Open a file and verify the system shows "Just once" and "Always" options.
5. **Video Thumbnails:** Check if video files show correct thumbnails instead of black squares.
