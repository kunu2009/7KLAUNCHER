# 7K Launcher — Current Feature Audit + Competitor Master Checklist

Status: Working audit (codebase-grounded)
Scope: Main launcher experience (not individual 7K apps)
Reference competitors: Nova Launcher, Lynx Launcher, Niagara, Smart Launcher, Lawnchair, Hyperion

---

## 1) Why this document

You asked to shift focus to the launcher itself and make it fully usable like top launchers.
This document does 3 things:

1. Lists **current 7K Launcher features** (from existing code/config).
2. Lists **everything a complete modern launcher should have** (basic → advanced).
3. Compares against competitors and identifies **what to build next**.

---

## 2) Current 7K Launcher capability audit (from code)

## A) Home screen foundation

- Home pages with `ViewPager2` model.
- Dedicated home content architecture (home pages + special pages).
- Long-press home options.
- Wallpaper support:
  - system wallpaper fallback,
  - gallery picker,
  - preloaded wallpaper selection,
  - default-first-run wallpaper behavior.
- Page data refresh hooks for fragments.

## B) App drawer

- Drawer implemented with page fragments + pager adapter.
- Sections include:
  - `All Apps`
  - `7K Apps`
  - additional user-defined sections
- Column customization (`3..7`).
- Sorting preferences and alphabet header support in settings.
- Empty-state handling per section.
- App long-press actions in drawer:
  - app info,
  - uninstall,
  - add to home/dock/sidebar.

## C) Dock + Sidebar

- Dock present with editable app set.
- Sidebar present with editable app set.
- Label show/hide toggle.
- Size/length/position controls for dock/sidebar in settings.
- Recents tracking and sidebar context actions.

## D) App management actions

- Launch installed apps + internal synthetic apps.
- Internal routing for built-in 7K modules.
- App shortcuts support via `LauncherApps` (where available).
- Uninstall / app info flows.
- Add/remove app to home/dock/sidebar flows.
- Hidden apps management hooks in settings.

## E) Gestures

- Gesture framework with configurable gesture types:
  - swipe up/down/left/right,
  - double tap,
  - long press,
  - edge gestures,
  - multi-touch gestures (pinch/two-finger variants scaffolded).
- Gesture-to-action mapping system exists.
- Gesture sensitivity settings exist.
- `EnhancedGestureManager` integration points in launcher.

## F) Widgets

- Widget host + manager setup exists.
- Add/remove widget flow support.
- Widget persistence keys and restore behavior.
- Widget-related management hooks in home options.

## G) Visual/theming system

- Theme framework (`ThemeManager`) and day/night-aware palette direction.
- Dynamic color and accent controls in settings.
- Glass/blur effect support with runtime gating.
- Icon size and label style controls.
- Icon pack selection entry points.

## H) Privacy + security controls

- App privacy manager integrated in enhanced settings.
- Privacy mode toggles.
- Hidden apps / locked apps management hooks.
- Biometric-required setting preference toggle.

## I) Backup/restore + settings ops

- Simple and enhanced backup managers present.
- Export/import backup flows in settings.
- Backup validation/info logic in enhanced backup path.
- Reset settings support.

## J) Performance controls

- Battery and RAM optimizer integrations.
- Performance-related toggles in settings.
- Animation scale / preload controls in enhanced settings.
- Auto clear cache threshold setting hooks.

## K) Global/system tools integration

- Settings links for icon packs, gestures, privacy, system monitor.
- Storage summary + cleanup actions (cache/temp/web data).
- Search source toggles (apps/contacts/web) present in settings.

## L) Maturity note (important)

Current codebase includes **many advanced hooks and options**, but some features are partially wired or placeholder-level in UX flow. Core direction is strong; production hardening and consistency pass is needed.

---

## 3) Competitor-grade master feature checklist (basic → advanced)

Use this as the complete target list for a “real launcher” comparable to Nova/Lynx class.

## Tier 0 — Absolute basics (must-have)

- Set as default launcher reliably.
- Smooth home screen with multiple pages.
- App drawer open/close gestures.
- App search in drawer.
- Drag-and-drop icon placement.
- Folder create/rename/delete.
- Dock with pinned apps.
- Widget placement/resizing.
- App info/uninstall shortcuts.
- Stable backup/restore of layout.

## Tier 1 — Standard customization (expected)

- Grid size per home and drawer.
- Icon size + label size + label lines.
- Hide app labels toggle.
- Icon pack support.
- Dock customization:
  - count,
  - layout,
  - pages,
  - background opacity.
- Drawer customization:
  - sort modes,
  - sections/categories,
  - background style.
- Folder customization:
  - columns,
  - preview style,
  - background style.
- Gesture customization:
  - swipe up/down,
  - double tap,
  - long press.
- Notification badges (dots/count).

## Tier 2 — Power-user essentials (Nova-level expectations)

- Per-gesture custom actions + app launch mapping.
- Hidden apps with optional lock.
- Home screen lock (prevent accidental edits).
- Import/export settings and layout profiles.
- Per-app custom icon + custom label.
- Drawer tabs/folders/groups.
- Fast scrolling + alphabet index in drawer.
- App usage-based sorting options.
- Desktop lock + overlap widgets/icons toggle.
- Restore from previous version with migration safety.

## Tier 3 — Premium UX and productivity

- Adaptive animations and animation speed presets.
- Smart suggestions (time/context/usage-aware).
- Universal search (apps, contacts, web, settings shortcuts).
- One-hand mode options (reachable UI bias).
- Productivity surfaces:
  - smart dock suggestions,
  - quick actions panel,
  - recent tasks/apps card.
- Theming packs and presets.
- Automatic wallpaper-aware color harmony.

## Tier 4 — Security, reliability, and ecosystem polish

- Crash-safe state restore after reboot/update.
- Full backup integrity checks.
- Privacy dashboard (hidden/locked apps visibility).
- Permission-aware feature degradation (graceful fallbacks).
- Telemetry-free/local-first mode option.
- Onboarding wizard + profile presets (minimal, standard, power).

---

## 4) Competitor parity map

## Nova Launcher (reference power benchmark)

Strongest areas:
- Deep home/drawer/dock/folder customization
- Gesture mapping breadth
- Backup/import maturity
- Icon/theming flexibility

7K parity status:
- **Partially there** architecturally.
- Needs UI consistency + completeness + reliability pass to reach Nova-level trust.

## Lynx Launcher (minimal + clean benchmark)

Strongest areas:
- Simplicity and speed
- Clean layout and low cognitive load

7K parity status:
- 7K already has richer features; needs a polished “simple mode” profile for Lynx-style minimal UX.

## Niagara / Smart / Lawnchair class

Common strengths:
- Distinctive navigation model
- polished gestures and search flow
- refined animations and stability

7K parity status:
- Foundations exist; polish, discoverability, and end-to-end cohesion are the main gap.

---

## 5) Gap summary (what still blocks “top launcher” status)

## Functional completeness gaps

- Some settings are present but need full launcher-runtime effect propagation.
- Hidden/locked app UX needs complete production flow.
- Drawer/category model needs stronger user-facing management UX.
- Notification badge system parity should be verified and hardened.

## UX consistency gaps

- Multiple feature areas implemented across old/new paths (needs unification).
- Premium visual language should be consistent across home, drawer, settings, dialogs.
- Onboarding/first-run guidance for major features is limited.

## Reliability gaps

- Need regression pass across:
  - reboot,
  - app update,
  - permission denied states,
  - backup/restore edge cases,
  - low-memory behavior.

---

## 6) Recommended execution order (launcher-first roadmap)

## Phase 1 — “Usable like normal launcher” (non-negotiable)

1. Hard-verify Tier 0 features with QA checklist.
2. Ensure settings actually reflect in runtime everywhere.
3. Stabilize drag/drop + folders + widgets + backup restore.
4. Make default launcher flow and fallback UX robust.

## Phase 2 — “Nova baseline parity”

1. Complete gesture mapping UX and action editor.
2. Finalize hidden/locked apps full experience.
3. Strengthen drawer organization and search behavior.
4. Finish per-app customization polish.

## Phase 3 — “Premium differentiation”

1. Smart suggestions and contextual surfaces.
2. High-quality animation system and profiles.
3. Minimal mode (Lynx-style) + Power mode (Nova-style) profile switch.

---

## 7) Definition of done for launcher focus shift

You can consider launcher “competitor-ready” when:

- Tier 0 and Tier 1 are fully stable and consistent.
- Tier 2 critical items (gestures, backup, hidden/locked, drawer power features) are complete.
- UX is coherent across all main surfaces (home/drawer/settings).
- Restore/reboot/update flows are reliable with no layout loss.

---

## 8) Next document to create immediately

After this audit, create:

- `7K_Launcher_Implementation_Backlog.md`

with:
- feature-by-feature status (`Done / Partial / Missing`),
- task IDs,
- owner,
- acceptance criteria,
- test cases per feature,
- release milestone mapping.
