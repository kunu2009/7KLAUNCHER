# 7K Launcher — Implementation Backlog

Status Legend: `Done` / `Partial` / `Missing`
Milestones: `M1 Core Reliability` → `M2 Power Features` → `M3 Premium Polish`

---

## Top-priority user-reported blockers

| ID | Feature | Status | Priority | Milestone | Acceptance Criteria | Test Cases |
|---|---|---|---|---|---|---|
| LCH-001 | Gesture action: **Launch App** target selection flow | Partial | P0 | M1 | Selecting `Launch App` always prompts app picker (if no target), selected app is shown and persisted per gesture, gesture launches selected app reliably. | Set gesture to Launch App, pick app, restart launcher, invoke gesture, verify app opens. |
| LCH-002 | Third-party widget add flow from home options | Partial | P0 | M1 | `Add Widget` opens system widget picker, supports third-party widgets, handles cancel/bind/configure safely (no orphan widget IDs). | Add Google/clock widget, cancel flow, retry, reboot, verify widget restored. |
| LCH-003 | Home drag/drop and icon sizing consistency | Partial | P0 | M1 | Drag/drop from drawer to home always adds once, no unexpected shrinking, icon size follows user icon-size preference. | Drag 10 apps across pages, compare size before/after drag, change icon size setting, verify update. |

---

## Competitor parity backlog (from audit checklist)

| ID | Area | Status | Priority | Milestone | Acceptance Criteria |
|---|---|---|---|---|---|
| LCH-010 | Drawer search and fast index polish | Partial | P1 | M2 | Fast, accurate filtering with alphabet jump and smooth scrolling. |
| LCH-011 | Folder UX (create/rename/add/remove) polish | Partial | P1 | M2 | One-tap folder actions, no data loss across restart. |
| LCH-012 | Home lock mode | Missing | P1 | M2 | Prevent accidental layout edits when enabled. |
| LCH-013 | Hidden apps + app lock full UX | Partial | P1 | M2 | Hidden/locked apps are configurable and consistently enforced. |
| LCH-014 | Notification badges parity | Missing | P1 | M2 | Dot/count badges configurable and stable. |
| LCH-015 | Drawer tabs/groups management UI | Partial | P2 | M2 | Users can create/reorder/manage groups easily. |
| LCH-016 | Universal search (apps/settings/web) | Partial | P2 | M2 | Unified search with source filters and quick actions. |
| LCH-020 | UI component modernization pass | Partial | P1 | M3 | Replace default/basic controls in launcher settings flows with cohesive premium components. |
| LCH-021 | Performance profile controls depth | Partial | P1 | M3 | Clear presets (battery/balanced/performance) with measurable runtime impact. |
| LCH-022 | Icon pack ecosystem breadth and fallback logic | Partial | P2 | M3 | Better icon pack compatibility and per-app fallback behavior. |
| LCH-023 | Onboarding/profile presets (Minimal/Standard/Power) | Missing | P2 | M3 | First-run wizard applies coherent starter profile. |

---

## Latest implementation update (this sprint)

- ✅ LCH-001 improved: Gesture `Launch App` now supports explicit target-app selection and persistence in gesture settings.
- ✅ LCH-002 improved: Widget add flow now handles picker/bind/configure cancel paths safely and cleans orphan widget IDs.
- ✅ LCH-003 improved: Home icon sizing now follows launcher icon-size preference consistently.
- ✅ LCH-013 improved: Enhanced settings now includes direct hidden/locked app management entry, lock timeout control, and auth mode selection.
- ✅ LCH-020 improved: Settings interaction polish added with dynamic privacy summaries and clearer stateful controls.
- ✅ UX polish: Sidebar/Dock/Home/App Drawer long-press option menus moved away from generic list dialogs to custom glass action sheet style.
- ✅ UX polish: ToDo widget config input and action buttons now use glass-themed styling (no plain generic input/button look).
- ✅ UX polish: App icon options and app shortcuts surfaces now use custom glass action sheets (removed generic option popups).
- ✅ LCH-011 improved: Home folder interactions (rename/new/add-to-existing/open) migrated off generic dialogs to custom bottom-sheet/glass flows.
- ✅ UX polish: Drawer section editing (`Add Section` / `Reset Defaults`) and new section input now use custom launcher glass sheets.
- ✅ LCH-013 improved: Locked-app PIN unlock and global PIN setup now use custom glass input sheets (no generic auth popups).
- ✅ UX polish: Enhanced privacy settings choice dialogs (`App Lock Timeout`, `Authentication Mode`) now use launcher glass action sheets.
- ✅ UX polish: Enhanced settings `Reset Settings`, `Restore Backup` confirmation, and `About` dialog are now custom glass message sheets.
- ✅ UX polish: `LauncherActivity` P0 dialog migration completed — Home long-press options, add-surface dialog, and dock/sidebar editor now use custom glass sheets.
- ✅ UX polish: `GestureSettingsActivity` launch-app target picker now uses a custom glass action sheet.
- ✅ UX polish: `TodoPageFragment` add/edit task flow now uses a custom glass input sheet (no generic alert input dialog).
- ✅ LCH-002 reliability fix: Widget bind/configure result handling now falls back to pending widget ID and clears pending state, fixing cases where widget permission succeeds but widget does not render.
- ✅ LCH-003 reliability fix: Drag/drop drop-to-home index corrected for special pages and move-removal is now target-safe (prevents app disappearance on failed/invalid drop).
- ✅ Dock/Sidebar UX fix: Dock item rendering now uses dedicated compact layout + dynamic icon view sizing; dock height now scales with icon size and label visibility to prevent icon cropping/truncation.
- ✅ Home interaction fix: Added robust long-press fallback via gesture detector to reliably open Home Options.
- ✅ Ecosystem UX uplift: `TasksCommanderActivity` dialogs (set reminder, add/edit task, actions, open-tasks list) migrated to custom glass bottom sheets.
- ✅ Ecosystem UX uplift: `FileForgeActivity` dialogs (create/open/save, rename, tags) migrated to custom glass input sheets.
- ✅ Ecosystem UX uplift: `BudgetGuardianActivity` amount/category dialogs migrated to custom glass form sheets.
- ✅ Ecosystem UX uplift: `CalendarActivity` note editor migrated to custom glass input sheet.
- ✅ Ecosystem UX uplift: `MusicActivity` playlist creation migrated to custom glass input sheet.
- ✅ Functional polish: `TasksCommanderActivity` performance summary text fixed (`Open N missions`) to remove formatting glitch.
- ✅ Launcher UX uplift: `SettingsActivity` multi-select app management (`Manage Hidden/Dock/Sidebar Apps`) migrated from generic multi-choice alert dialog to custom glass multi-select sheet.
- ✅ Launcher UX uplift: `SettingsActivity` preloaded wallpaper picker migrated from generic list alert dialog to custom glass action sheet.
- ✅ Spinner modernization: `AppPrivacyActivity`, `GestureSettingsActivity`, and `EnhancedSettingsActivity` now use custom glass spinner item/dropdown layouts instead of `android.R.layout.simple_spinner_*`.
- ✅ LCH-003 regression fix: Home long-press fallback is now scope-aware (restricted to home pager area) and no longer triggers from dock/sidebar or app-icon long-press interactions.
- ✅ LCH-003 regression fix: Drag-drop from drawer to Home now force-hides drawer immediately during drag and applies target-first move logic, restoring reliable drop behavior.
- ✅ LCH-003 responsive polish: Home grid + right insets now adapt to screen width and sidebar visibility to reduce icon-cell distortion on compact devices.
- ✅ Dock readability polish: Dock/sidebar labels now support centered multi-line rendering and dock height now scales with compact-screen heuristics to reduce clipping.

UI/UX master framework file added:
- `7k apps documentation/7K_UI_UX_Master_Framework.mf`

---

## Ownership and execution notes

- Owner: Launcher Core
- Build gate: `:app:assembleDebug` must pass per merged batch.
- QA gate for each P0 item:
  - cold start + warm start
  - rotate screen
  - reboot device
  - app update scenario
  - permission denied/cancel flow

---

## Immediate sprint plan (next commits)

1. Run launcher smoke tests across settings and privacy flows after spinner/layout modernization.
2. Continue scan for remaining generic controls in non-launcher ecosystem modules.
3. Keep build + regression validation gate (`:app:assembleDebug`) for each migration wave.

---

## Remaining generic UI checklist (scan pass)

Status: `Open` (prioritized)

### P0 — Launcher core surfaces

- `app/src/main/java/com/sevenk/launcher/LauncherActivity.kt`
  - (completed in prior waves) Home options / add-surface / dock-sidebar editor are now custom glass sheets.
  - (completed in current wave) home long-press trigger is now scoped to home-page region to avoid cross-surface misfires.

### P1 — Main launcher feature pages

- `app/src/main/java/com/sevenk/launcher/GestureSettingsActivity.kt`
  - Launch-app target picker still uses `AlertDialog.Builder` list.
- `app/src/main/java/com/sevenk/launcher/TodoPageFragment.kt`
  - Add/Edit to-do dialog still uses `AlertDialog.Builder` input dialog.
- `app/src/main/java/com/sevenk/launcher/SettingsActivity.kt`
  - (completed in current wave) custom glass sheets now used for package multi-select and wallpaper picker.

### P2 — Spinner modernization (non-dialog generic controls)

- `app/src/main/java/com/sevenk/launcher/AppPrivacyActivity.kt`
  - (completed in current wave) uses custom `item_glass_spinner_selected` / `item_glass_spinner_dropdown`.
- `app/src/main/java/com/sevenk/launcher/GestureSettingsActivity.kt`
  - (completed in current wave) uses custom `item_glass_spinner_selected` / `item_glass_spinner_dropdown`.
- `app/src/main/java/com/sevenk/launcher/settings/EnhancedSettingsActivity.kt`
  - (completed in current wave) Theme/Grid/Drawer style adapters now use custom glass spinner layouts.

### P3 — Ecosystem apps under launcher package

- Remaining `AlertDialog.Builder` usages exist in:
  - (none identified in the previously flagged `BudgetGuardian/Calendar/FileForge/Music/TasksCommander` set)
  - keep scanning newly added ecosystem modules for regressions.
