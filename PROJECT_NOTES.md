# 7K Launcher – Project Notes (Living Doc)

Single source of truth for objectives, principles, themes, UX decisions, and active TODOs. Keep this synced as features evolve.

For the full design master plan, see `README.md` → "UI/UX Blueprint".

## Objectives
- Apply aesthetic, functional glass UI across launcher (dock, sidebar, search, Stan chat) with blur on Android 12+.
- Ship a minimal but polished experience: fluid, consistent, customizable.

## Core Principles
- Minimalism first; remove clutter
- Consistency with 7K palette and typography
- Fluidity: smooth transitions, no abrupt jumps
- Personalization: themes, icon size/labels, widgets

## Color System (authoritative)
See `README.md` → “7K Launcher Color System” for full palette, light/dark mappings, and UI applications.

## Typography
- Headings (Time/Date): Montserrat Bold
- Labels/UI: Inter Regular/Medium

## Glass Strategy (Adaptive)
- Light mode: white glass 30–40% + blur
- Dark mode: dark glass #13262F ~40% + blur
- Implementation: themed colors in `res/values(-night)/`, shared drawable `res/drawable/glass_panel.xml`, runtime blur via `RenderEffect` on API 31+

## Current Implementation Touchpoints
- `res/drawable/glass_panel.xml` – base glass visual
- `res/layout/activity_launcher.xml` – dock/sidebar/search/selection glass
- `res/layout/stan_home.xml` – chat list now also on glass; chat bar glass
- `res/layout/item_chat_message.xml` – bubble layout for chat messages
- `res/drawable/chat_bubble_user.xml`, `res/drawable/chat_bubble_bot.xml` – translucent bubble styles
- `res/values/themes.xml`, `res/values-night/themes.xml` – DayNight with correct system bar icon contrast
- `LauncherActivity.toggleAppDrawer()` – smooth animated open/close
- `LauncherActivity.updateDrawerGesture()` – interactive drag-to-close with snapping
- `LauncherActivity.applyGlassBlurIfPossible(view, radius)` – runtime blur helper
- `StanHomeFragment.onViewCreated()` – chat bar blur on 12+, smoother list animations
- `StanHomeFragment.ChatAdapter` – applies bubble backgrounds and alignment
- `WebAppActivity` – viewport fit-to-screen, TEXT_AUTOSIZING, force-dark alignment

## Dock Behavior
- First launch shows 4 apps by default
- After user selection, dock is uncapped (no hard limit)
- Alignment/padding handled by `applyDockPositionAndPadding()` in `LauncherActivity.kt`

## Recent Changes
- Implemented DayNight with proper status/navigation bar icon contrast
- Added translucent message bubbles and smoother item animations to Stan chat
- Added glass panel behind Stan chat list for readability
- Implemented smooth animated open/close and interactive drag-to-close with snapping for app drawer
- Improved PWAs in `WebAppActivity` with fit-to-screen viewport, text autosizing, and dark-mode alignment

## Active TODOs (sync with planning)
- Completed
  - DayNight with proper status/navigation bar icon contrast (`values/` + `values-night/themes.xml`)
  - Stan chat: translucent message bubbles and visible text; smoother item animations
  - Glass panel behind Stan chat list for readability
  - App drawer: smooth animated open/close and interactive drag-to-close with snapping
  - PWAs in `WebAppActivity`: fit-to-screen viewport, text autosizing, dark-mode alignment
- In Progress
  - Styling baseline: vector icons, brand palette tokens, typography cleanup (Inter/Montserrat)
- Planned
  - Add runtime blur (RenderEffect) behind Stan chat glass panel on API 31+
  - Custom chat ItemAnimator: subtle slide/fade on new messages
  - Sidebar swipe overlay: interactive drag + animations
  - Sidebar quick toggles: expand Wi‑Fi, Bluetooth, Flashlight (stripe)
  - Settings: expose sidebar quick toggles as additional features
  - Dock overhaul: rounded glass polish, active glow, context menu polish
  - App Drawer: rounded icon cards, pill search with blur, swipeable tabs polish
  - Gestures: swipe-down global search; long‑press dock quick menu; horizontal page gestures
  - Gestures: left/right actions (e.g., open Settings or Notes)
  - Animations: dock/sidebar fade+slide; app launch scale+blur; search expand
  - Theme controls: optional in‑app light/dark toggle overriding system
  - Night mode schedule: auto switch based on time
  - Widgets: Today card (time/date/weather) + optional AI suggestion; Music widget glass card; Quick Notes
  - Minimal widget support: clock + calendar
  - App Drawer: favorites/frequently used section
  - App Drawer: recent apps row
  - Folders: creation in app drawer and dock
  - App privacy: hide/lock apps
  - App labels: allow rename/custom labels
  - Custom icon support: upload or choose icon pack
  - Performance: smooth scrolling + fast app launch
  - Dock apps: customizable swipe‑up gestures
  - Haptic feedback: gestures and taps
  - Search history: faster repeat access
  - Onboarding: minimal "tips & tricks" overlay for first‑time users
  - Developer settings: hidden menu for testing features
  - Quick toggle: launch 7K ecosystem apps (one‑tap)
  - Wallpaper picker: include solid colors from 7K palette
  - Mode Switcher: study/relax/game layouts that adjust UI
  - Sidebar: battery + network indicator widget
  - PWA polish: offline handling, theme-color per mode, viewport tuning
  - Testing matrix: verify DayNight bars, chat translucency/scroll, drawer drag+snap across devices
  - Documentation: README Roadmap + concise changelog of recent UI/theming work
  - Extras: 7K Pulse subtle glow; permissions audit

## Critical Competitive Gaps (from COMPETITIVE_ANALYSIS.md)
- CRITICAL: Icon pack support system (Nova/Smart Launcher standard)
- CRITICAL: Gesture customization framework (modern UX expectation)
- CRITICAL: Backup & restore functionality (user retention essential)
- CRITICAL: App hiding/privacy features (hide/lock apps)
- IMPORTANT: Widget resizing system (power user feature)
- IMPORTANT: Folder customization (organization essential)
- IMPORTANT: Adaptive icons support (modern Android standard)
- IMPORTANT: Notification badges (information density)
- IMPORTANT: Custom grid sizes (layout flexibility)
- NICE-TO-HAVE: Scroll effects (visual polish)
- NICE-TO-HAVE: Animation customization (advanced theming)
- NICE-TO-HAVE: Subgrid positioning (precision layout)

## Decisions Log
- Dock: unlimited after user selection; default initial 4 (prefs key: `dock_initial_count`)
- Fonts: Montserrat (headings) + Inter (labels/UI)
- Glass: adaptive tint per mode; blur on API 31+

## Open Questions
- Night mode control: follow system vs in‑app toggle?
- Active glow color precedence: use Primary Accent per mode (Calypso/Chathams)?
- Sidebar quick toggles: scope (Wi‑Fi, BT, Torch, Music) and permissions fallback paths

## How to Update
- Keep this file concise—link to source files/READMEs for full details.
- When adding a feature, record: purpose, files touched, any prefs keys, and decisions.


---

## App: Calc Vault (Privacy Vault)

Goal: Make vault truly hide media by moving imports into app-private storage, removing originals from MediaStore/gallery, and providing an in-app viewer with secure access.

Planned TODOs
- Import/Hide flow
  - Use SAF picker to import photos/videos; copy to app-private dir (scoped storage) under `calcvault/` and then securely delete the source.
  - Remove from `MediaStore` after import to disappear from gallery. Verify on Android 10+ with scoped storage.
  - Create `.nomedia` in vault directories to prevent scanning.
- Secure storage and access
  - Protect vault with PIN and biometric (BiometricPrompt). Settings to require biometric every open.
  - Encrypt at-rest using AES-GCM per file. Stream encrypt/decrypt to avoid high memory usage.
  - Store thumbnails separately in private dir for fast browsing; do not expose to MediaStore.
- In-app Vault UI
  - Grid browser with folders, sort, multi-select, search.
  - Built-in image viewer and video player (ExoPlayer), never exporting temp files.
  - Actions: rename, move, share (temporary decrypt-to-cache), export/unhide (restore to user-picked folder + re-add to MediaStore).
- Migration
  - Detect previously “encrypted” items that became inaccessible; add a migration tool to re-import or re-encrypt into the new schema.
- Reliability
  - Robust deletion with SAF `DocumentFile.delete()` fallback, and error toasts when a source cannot be removed.
  - Background workers for long operations with progress UI and cancellation.
- Privacy polish
  - Quick-hide app switch; stealth icon/name option; screenshot blocking inside vault.

References
- Module path: `calcvault/`
- Key files to extend: `calcvault/src/main/AndroidManifest.xml`, Vault `Activity`/`Fragment` classes, storage helpers.

---

## App: 7K Studio (Photo & Video Editor)

Goal: Ship a full-fledged editor with modern photo and video tools, project autosave, and fast exports.

Planned TODOs
- ✅ **Done:** Crop/rotate/flip; aspect presets.
- Foundations
  - SAF-based project/media import; non-destructive edits; autosave drafts.
  - Shared render pipeline abstraction; GPU-accelerated filters where possible.
- Photo editor (MVP)
  - Crop/rotate/flip; aspect presets.
  - Adjustments: exposure, contrast, saturation, warmth, highlights/shadows, vignette.
  - Filters with live preview; intensity slider.
  - Draw/markup, text tool (fonts, colors), stickers; blur brush.
- Video editor (MVP)
  - Trim, split, merge; timeline with clips.
  - Speed control; mute/volume; background music track; fade in/out.
  - Text/overlay stickers; filters LUTs; frame export.
  - Export presets (720p/1080p), bitrate control; progress UI and cancel.
- UX & Files
  - Project browser with thumbnails; recent/open/save-as.
  - Share/export flows to gallery or user-picked folder (SAF) and `MediaStore` insert.
  - Undo/redo stack; history panel.
- Performance
  - Prefetch and caching of thumbnails; worker threads for rendering; avoid UI jank.
  - Consider FFmpeg for video ops; GPUImage/RenderScript alternatives for photo filters (device support fallback).
- Onboarding & Docs
  - First-run tutorial; tooltips; keyboard shortcuts (if applicable).

References
- Module path: integrated in launcher app for now; consider dedicated module later.
- New packages: `com.sevenk.studio.*` for editor components.


---

## App: 7K Calculator

Goal: Polished calculator UX that also serves as the disguised entry point to Calc Vault (if installed), with history and scientific functions.

Planned TODOs
- Core
  - Basic ops: +, −, ×, ÷, %, parentheses; decimal precision handling.
  - Scientific mode: sin/cos/tan, ln/log, exp, x^y, sqrt, pi/e, memory (MC/MR/M+/M−).
  - Expression parser with precedence and error states; large-number formatting.
- UX
  - Responsive keypad layouts (portrait/landscape); haptic feedback; tap/hold to repeat.
  - Calculation history with swipe-to-delete and re-use by tap.
  - Theming consistent with 7K glass style; adaptive DayNight.
- Integrations
  - If Calc Vault present: optional hidden passcode entry sequence to open `VaultActivity`.
  - Share/copy result; paste detection.

References
- Module: part of launcher app or separate `calculator/` later.
- Existing disguised keypad in `calcvault/` can be refactored/shared.

---

## App: 7K Settings

Goal: Centralize launcher settings with clean categories and immediate visual feedback.

Planned TODOs
- Structure
  - Sections: Appearance, Home Pages, Dock & Sidebar, App Drawer, Gestures, Privacy, Advanced.
  - Use `PreferenceFragmentCompat` with custom glass cards and headers.
- Appearance
  - Icon size + label toggle; font selections; color accents; wallpaper pick.
  - Glass intensity and blur radius (API 31+) with live preview.
- Home & Drawer
  - Grid size, page order (TODO page, Stan AI), favorites, folders.
  - Search options: provider, history toggle, quick actions.
- Gestures
  - Swipe-down search, double-tap lock, edge swipe actions, long‑press dock.
- Privacy
  - Hide/lock apps, Calc Vault integration, biometric gate options.
- Advanced
  - Backup/restore, developer options, logging controls.

References
- Package: `com.sevenk.launcher.settings` or separate module later.
- Ties into existing prefs keys in `LauncherActivity.kt` and adapters.

---

## App: 7K Browser (Minimal)

Goal: Lightweight, privacy-first browser with essentials and glass UI.

Planned TODOs
- ✅ **Enhanced:** Tracking protection via settings (JS/cookies/tpc toggles per site); clear data.
- Core
  - WebView-based tab manager; incognito mode; per-tab back/forward; pull-to-refresh.
  - URL bar with suggestions; search engine selection.
- Privacy
  - Tracking protection via settings (JS/cookies/tpc toggles per site); clear data.
  - Incognito isolation: no history/cookies; themed incognito UI.
- UX
  - Basic download handling; file chooser; dark mode support; reader mode (MVP simplified).
  - Share to launcher; open in external browser.

References
- Package: `com.sevenk.browser.*` inside main app initially.
- Consider migrating to a dedicated module if complexity grows.

---

## App: 7K Notepad

Goal: Fast, simple notes with local storage, search, and optional vault-backed secure notes.

Planned TODOs
- Core
  - Local Room database for notes; title/body/timestamps; pin/star; trash.
  - Full-text search; tag support; sorting and filters.
- UX
  - Minimal editor with markdown-lite preview; checklist mode; share/export.
  - Widgets: quick add note; recent notes glass widget.
- Privacy
  - Secure notes: store body encrypted using Calc Vault’s key or separate MasterKey; biometric unlock.

References
- Package: `com.sevenk.notepad.*` initially in main app.
- Optional integration with Calc Vault for secure notes storage.

