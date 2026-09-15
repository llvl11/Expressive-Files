# Implementation Plan - Fix Crash, New Loading Indicator, and UI Tweaks

This plan addresses a crash in the deletion dialog, implements a new expressive loading indicator for archive operations, and refines the paste button's width.

## User Review Required

> [!IMPORTANT]
> The existing `ArchiveProgressDialog` will be completely removed as requested. The new loading indicator will be a squiggly line animation floating above the "+" button (FAB).

## Proposed Changes

### [Component] UI Components & Screens

#### [MODIFY] [NewItemDialog.kt](file:///C:/Users/pc/StudioProjects/File-manager2/app/src/main/java/com/baiel/expressivefiles/ui/components/NewItemDialog.kt)
- Add safety check for `pendingDeleteItems` to prevent crash when accessing `.first()` on an empty list.

#### [DELETE] [ArchiveProgressDialog.kt](file:///C:/Users/pc/StudioProjects/File-manager2/app/src/main/java/com/baiel/expressivefiles/ui/components/ArchiveProgressDialog.kt)
- Remove the old, non-visible progress dialog.

#### [MODIFY] [HomeScreen.kt](file:///C:/Users/pc/StudioProjects/File-manager2/app/src/main/java/com/baiel/expressivefiles/ui/screens/HomeScreen.kt)
- Remove usage of `ArchiveProgressDialog`.
- Integrate the new `SquigglyLoadingIndicator` above the `HomeFab`.

#### [MODIFY] [HomeTopBar.kt](file:///C:/Users/pc/StudioProjects/File-manager2/app/src/main/java/com/baiel/expressivefiles/ui/screens/home/HomeTopBar.kt)
- Remove the top bar's compression indicator.

#### [MODIFY] [HomeBottomActionStrip.kt](file:///C:/Users/pc/StudioProjects/File-manager2/app/src/main/java/com/baiel/expressivefiles/ui/screens/home/HomeBottomActionStrip.kt)
- Adjust the "Paste" button width to be narrower.

#### [NEW] [SquigglyLoadingIndicator.kt](file:///C:/Users/pc/StudioProjects/File-manager2/app/src/main/java/com/baiel/expressivefiles/ui/components/SquigglyLoadingIndicator.kt)
- Implement a custom Canvas-based squiggly line animation.
- Show a popup/tooltip with progress details when active.

## Verification Plan

### Automated Tests
- N/A (UI focused changes)

### Manual Verification
1. **Delete Crash:** Select a file, click delete, and ensure the dialog shows up without crashing. Verify deletion still works.
2. **Loading Indicator:** Perform a compression or extraction. Observe the squiggly line above the FAB and the progress popup.
3. **Paste Button:** Copy a file and check the "Paste" button's width in the bottom strip.
