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

1. Replace remaining generic dialogs in `LauncherActivity` (`homeScreen/homePager` long-press + add dialog + dock/sidebar multi-select editor).
2. Replace remaining generic selector in `GestureSettingsActivity` app-target picker.
3. Replace `TodoPageFragment` add/edit task dialog with custom glass input sheet.
4. Start modern spinner migration in `AppPrivacyActivity`, `SettingsActivity`, `EnhancedSettingsActivity` (drop `android.R.layout.simple_spinner_*` adapters).
5. Run build + smoke validation and update this backlog statuses.

---

## Remaining generic UI checklist (scan pass)

Status: `Open` (prioritized)

### P0 — Launcher core surfaces

- `app/src/main/java/com/sevenk/launcher/LauncherActivity.kt`
  - `homeScreen.setOnLongClickListener` still uses `AlertDialog.Builder` Home Options menu.
  - `homePager.setOnLongClickListener` still uses `AlertDialog.Builder` Home Options menu.
  - `showAddDialog(app)` still uses `AlertDialog.Builder` action list.
  - `openAppPicker(forDock)` still uses `AlertDialog.Builder.setMultiChoiceItems`.

### P1 — Main launcher feature pages

- `app/src/main/java/com/sevenk/launcher/GestureSettingsActivity.kt`
  - Launch-app target picker still uses `AlertDialog.Builder` list.
- `app/src/main/java/com/sevenk/launcher/TodoPageFragment.kt`
  - Add/Edit to-do dialog still uses `AlertDialog.Builder` input dialog.
- `app/src/main/java/com/sevenk/launcher/SettingsActivity.kt`
  - `openManageListDialog(...)` still uses `AlertDialog.Builder.setMultiChoiceItems`.
  - `showWallpaperStorePicker()` still uses `AlertDialog.Builder` list picker.

### P2 — Spinner modernization (non-dialog generic controls)

- `app/src/main/java/com/sevenk/launcher/AppPrivacyActivity.kt`
  - Uses `android.R.layout.simple_spinner_item` / `simple_spinner_dropdown_item`.
- `app/src/main/java/com/sevenk/launcher/GestureSettingsActivity.kt`
  - Uses `android.R.layout.simple_spinner_item` / `simple_spinner_dropdown_item`.
- `app/src/main/java/com/sevenk/launcher/settings/EnhancedSettingsActivity.kt`
  - Theme/Grid/Drawer style adapters still use `android.R.layout.simple_spinner_*`.

### P3 — Ecosystem apps under launcher package

- Remaining `AlertDialog.Builder` usages exist in:
  - `app/src/main/java/com/sevenk/launcher/ecosystem/BudgetGuardianActivity.kt`
  - `app/src/main/java/com/sevenk/launcher/ecosystem/CalendarActivity.kt`
  - `app/src/main/java/com/sevenk/launcher/ecosystem/FileForgeActivity.kt`
  - `app/src/main/java/com/sevenk/launcher/ecosystem/MusicActivity.kt`
  - `app/src/main/java/com/sevenk/launcher/ecosystem/TasksCommanderActivity.kt`
