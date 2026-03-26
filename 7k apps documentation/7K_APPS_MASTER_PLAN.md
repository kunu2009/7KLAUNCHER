# 7K Apps Master Catalog (Language + UX + Competitor Blueprint)

This is the execution-grade plan for all 7K apps, focused on:
- best language per app,
- consistent but premium UI/UX,
- offline-first features,
- real competitor benchmarking,
- unique value to beat mainstream apps for practical daily use.

## Scope and exclusions

Per request, this plan **excludes deep redesign** of these three apps:
- `7K Launcher`
- `7K Settings`
- `7K Calculator`

All other existing/in-progress/planned apps are fully covered below.

## Status legend
- ✅ Existing in project
- 🚧 In progress (being built now)
- 💡 Planned idea

---

## 1) Cross-app language strategy (final)

### Primary stack rules
- **Kotlin (native Android)** for device/system-heavy apps, file/media utilities, widgets, battery/privacy modules.
- **Flutter** for premium UI-led apps where UX quality and future cross-platform expansion matter.
- **Offline HTML/CSS/JS** for content-heavy apps that need very fast updates and tiny runtime cost.
- **Python** only as optional embedded engine (NLP/search/scoring), never primary UI stack.

### Why this mix is efficient
- Fastest performance path where needed (Kotlin).
- Fastest interface innovation where needed (Flutter).
- Fastest content shipping path (offline web modules).
- Controlled complexity by using one host runtime (launcher) + clean contracts.

---

## 2) 7K unified UI/UX system (non-generic, premium)

Every app must follow this shared style language to feel premium and coherent:

### Design DNA
- **Mood**: calm, intelligent, precise, tactile.
- **Visual identity**: deep navy + slate + soft neutral + one app accent.
- **Surface model**: layered cards, subtle texture/noise, restrained glass only where useful.
- **Typography**: strong heading weight + compact readable body.
- **Motion**: short, purposeful transitions (180–260ms), no flashy over-animation.

### Layout rules
- 8dp spacing grid, 12dp and 16dp corner families.
- Top bar pattern: title + 1 primary action + overflow.
- Primary action anchored bottom-right or sticky bottom bar (depending on app type).
- Search/filter consistently top or sticky header.

### Interaction rules
- One-thumb first: key actions reachable in bottom half.
- Long-press consistently opens quick actions.
- Empty states always show:
	1) what this app does,
	2) first action button,
	3) one sample use case.

### Performance + offline rules
- App startup target: < 900ms warm start for utility apps.
- Full core workflows available offline.
- Local-first DB, async sync/export later.
- No heavy animations during scroll lists.

---

## 3) Detailed app-by-app plan (all apps except excluded 3)

## Existing apps

### 7K Enhanced Settings (✅)
- **Language**: Kotlin
- **UI/UX**: control-center dashboard, grouped cards, “safe defaults” labels.
- **Must-have features**: profile presets (performance/battery/focus), searchable settings, change history + reset by section.
- **Competitors**: Nova Settings, Smart Launcher settings, One UI launcher settings.
- **Beat-them strategy**: simpler navigation + offline explainers + one-tap rollback.

### 7K Browser (✅)
- **Language**: Kotlin + WebView + JS bridge utilities
- **UI/UX**: distraction-free tab strip, quick actions sheet, reader mode.
- **Must-have features**: offline page save, ad-light mode, private tab vault, smart download organizer.
- **Competitors**: Brave, Firefox, Samsung Internet.
- **Beat-them strategy**: ultra-fast local start, integrated launcher search, zero-account offline workflow.

### 7K Study (✅)
- **Language**: Flutter UI + Kotlin plugin bridge for local files/notifications
- **UI/UX**: module cards, spaced repetition cues, progress heatmaps.
- **Must-have features**: offline syllabus packs, revision planner, timed tests, mistakes notebook.
- **Competitors**: Notion study templates, Anki, Quizlet, RemNote.
- **Beat-them strategy**: all-free offline packs + integrated 7K Law/flashcards + exam mode.

### 7K Studio (✅)
- **Language**: Kotlin core media pipeline (retain native)
- **UI/UX**: template-first editor, quick presets, pro panel toggles.
- **Must-have features**: batch export queue, custom preset save, undo history, before/after compare.
- **Competitors**: Snapseed, Lightroom Mobile, VN, CapCut (basic edits).
- **Beat-them strategy**: no-login offline editing + fast export + reusable templates across apps.

### 7K Notes (✅)
- **Language**: Kotlin now; optional Flutter front layer later
- **UI/UX**: notebook + tags + timeline hybrid.
- **Must-have features**: pinned blocks, markdown-lite, checklist blocks, local encryption option.
- **Competitors**: Google Keep, Obsidian mobile, Simplenote.
- **Beat-them strategy**: simpler than Obsidian, richer than Keep, fully offline by default.

### 7K Utility (✅)
- **Language**: Kotlin
- **UI/UX**: command board with category chips.
- **Must-have features**: quick toggles, intent shortcuts, diagnostic summary.
- **Competitors**: device quick settings, all-in-one toolbox apps.
- **Beat-them strategy**: faster launcher-integrated actions + zero ad bloat.

### 7K Games (✅)
- **Language**: Flutter (Flame) for mini games
- **UI/UX**: arcade hub with instant launch and tiny download footprint.
- **Must-have features**: offline scoreboards, weekly challenges, achievement badges.
- **Competitors**: mini-game hubs, casual puzzle bundles.
- **Beat-them strategy**: no ads, offline-first, quick-play under 30s sessions.

### 7K Widgets (✅)
- **Language**: Kotlin
- **UI/UX**: widget preview gallery, resize handles, smart suggestions.
- **Must-have features**: per-widget theming, transparent mode, profile-based layouts.
- **Competitors**: KWGT ecosystem, OEM widgets.
- **Beat-them strategy**: one-tap setup with launcher-native matching style.

### 7K AppStore (✅)
- **Language**: Kotlin
- **UI/UX**: curated cards, categories, update badge simulation for internal modules.
- **Must-have features**: install/open/manage internal apps, changelog cards, featured packs.
- **Competitors**: app gallery models, mini app hubs.
- **Beat-them strategy**: fully offline catalog + zero telemetry dependency.

### 7K Calendar (✅)
- **Language**: Flutter
- **UI/UX**: agenda + month hybrid, color blocks, quick add natural form.
- **Must-have features**: offline events/tasks merge, repeat rules, local reminders.
- **Competitors**: Google Calendar, Any.do calendar.
- **Beat-them strategy**: no-account offline scheduling + better task integration with 7K Tasks.

### 7K Music (✅)
- **Language**: Kotlin
- **UI/UX**: clean local library, queue-first playback.
- **Must-have features**: folder-based discovery, smart playlists offline, sleep timer.
- **Competitors**: AIMP, Musicolet, Poweramp.
- **Beat-them strategy**: simpler UI + deep launcher shortcuts + lightweight RAM footprint.

### 7K Weather (✅)
- **Language**: Kotlin
- **UI/UX**: compact cards, radar-style visuals (cached), severe alert card.
- **Must-have features**: offline last-known forecast, location presets, weather widgets.
- **Competitors**: AccuWeather, Today Weather.
- **Beat-them strategy**: privacy-respecting minimal data + offline fallback reliability.

### 7K Law Prep (✅)
- **Language**: Offline HTML/CSS/JS + Kotlin host wrapper
- **UI/UX**: chapter map, case notes, quiz mode.
- **Must-have features**: offline syllabus docs, bookmarks, speed revision mode.
- **Competitors**: legal prep portals, coaching apps.
- **Beat-them strategy**: fully offline access + integrated notes/tasks loop.

### 7K Itihaas (✅)
- **Language**: Offline HTML/CSS/JS + Kotlin host wrapper
- **UI/UX**: timeline-first learning, map overlays, quick fact cards.
- **Must-have features**: topic packs, timeline quizzes, revision bookmarks.
- **Competitors**: history learning apps/sites.
- **Beat-them strategy**: no-internet study mode + faster revision workflows.

### 7K Polyglot (✅)
- **Language**: Flutter + optional Python micro-engine (phrase scoring)
- **UI/UX**: lesson streak board, pronunciation cards, phrase drills.
- **Must-have features**: offline lessons, spaced repetition, conversation templates.
- **Competitors**: Duolingo, Memrise, Drops.
- **Beat-them strategy**: true offline progression + practical phrase packs + no paywall on basics.

### 7K Eco (✅)
- **Language**: Offline HTML/CSS/JS + Kotlin wrapper
- **UI/UX**: concept map + chart-based summaries.
- **Must-have features**: offline explainers, MCQ drills, formula sheets.
- **Competitors**: economics prep apps/sites.
- **Beat-them strategy**: compressed learning blocks + low-data mode.

### 7K Life (✅)
- **Language**: Flutter
- **UI/UX**: wellness dashboard with daily cards and gentle progress cues.
- **Must-have features**: habit check-ins, mood tracker, reflection journal.
- **Competitors**: Finch, Fabulous, Daylio.
- **Beat-them strategy**: offline privacy-first life stack + integrated tasks/notes.

---

## In-progress apps (build-start set)

### 7K Smart Notes+ (🚧)
- **Language**: Kotlin (reuse current Notes base)
- **UI/UX**: block editor + workspace tabs.
- **Must-have features**: templates, backlinking, smart filters, table blocks.
- **Competitors**: Notion, Obsidian.
- **Beat-them strategy**: faster mobile-first offline editing + no account required.

### 7K Tasks Commander (🚧)
- **Language**: Kotlin
- **UI/UX**: command-center Kanban + quick capture.
- **Must-have features**: Eisenhower matrix, recurring tasks, deadline heatmap, offline reminders.
- **Competitors**: Todoist, TickTick, Microsoft To Do.
- **Beat-them strategy**: full free offline advanced planning + one-tap launcher actions.

### 7K File Forge (🚧)
- **Language**: Kotlin
- **UI/UX**: dual-pane file manager (optional), action chips, preview drawer.
- **Must-have features**: batch rename, archive tools, secure folder, quick share profiles.
- **Competitors**: Solid Explorer, Files by Google, MiXplorer.
- **Beat-them strategy**: power actions without clutter + fully offline + no ads.

### 7K Budget Guardian (🚧)
- **Language**: Flutter UI + Kotlin local data service
- **UI/UX**: envelope budget cards + trend charts + alerts.
- **Must-have features**: zero-based budgeting, category limits, recurring expenses, CSV export.
- **Competitors**: Wallet, YNAB, Money Manager.
- **Beat-them strategy**: free offline pro budgeting + no subscription lock for core features.

### 7K Privacy Shield (🚧)
- **Language**: Kotlin
- **UI/UX**: risk score dashboard, permission timeline, one-tap guidance.
- **Must-have features**: permission audits, sensitive app watchlist, privacy checklist automation.
- **Competitors**: privacy/security tool suites.
- **Beat-them strategy**: actionable local advice, simple scoring, no fear-based dark patterns.

### 7K Battery Doctor (🚧)
- **Language**: Kotlin
- **UI/UX**: battery flow charts + drain culprit cards.
- **Must-have features**: consumption insights, profile presets, charging care reminders.
- **Competitors**: AccuBattery-like apps, OEM battery utilities.
- **Beat-them strategy**: faster suggestions integrated with launcher performance profiles.

### 7K Studio Templates (🚧)
- **Language**: Kotlin
- **UI/UX**: template grid, mood tags, instant preview.
- **Must-have features**: photo/video preset bundles, one-tap apply, favorite collections.
- **Competitors**: Canva template galleries, CapCut template feeds.
- **Beat-them strategy**: offline template packs + direct handoff into 7K Studio editor.

### 7K Offline First AppStore (🚧)
- **Language**: Kotlin
- **UI/UX**: local catalog, smart filtering, curated bundles.
- **Must-have features**: offline metadata packs, update simulation/version notes, bundle installer.
- **Competitors**: mini app hubs.
- **Beat-them strategy**: guaranteed offline availability + category intelligence for launcher users.

---

## Planned apps (next waves)

### Productivity

#### 7K Journal Vault (💡)
- **Language**: Flutter
- **Features**: timeline journaling, mood tags, encrypted lock mode, prompt cards.
- **Competitors**: Day One, Journey.
- **Unique edge**: free offline encrypted journaling + cross-link to Notes/Tasks.

#### 7K Habit Forge (💡)
- **Language**: Flutter
- **Features**: streaks, habit stacks, reminder windows, habit score analytics.
- **Competitors**: Habitica, HabitBull, Loop Habit Tracker.
- **Unique edge**: streak recovery logic + no ads + integrated life planner.

#### 7K Read Later Offline (💡)
- **Language**: Offline HTML/JS + Kotlin clipping service
- **Features**: article save, clean reading mode, highlight/notes, folder packs.
- **Competitors**: Pocket, Instapaper.
- **Unique edge**: completely offline archive + local full-text search.

#### 7K Voice Notes Pro (💡)
- **Language**: Kotlin
- **Features**: voice capture, background recording, silence trim, tag + transcribe (optional local).
- **Competitors**: Dolby On, voice memo apps.
- **Unique edge**: offline voice-to-note pipeline into Smart Notes+.

### Security + Device

#### 7K Permission Radar (💡)
- **Language**: Kotlin
- **Features**: permission map, high-risk alerts, weekly reports.
- **Competitors**: built-in permission manager tools.
- **Unique edge**: plain-language recommendations and one-screen control flow.

#### 7K Network Guard (💡)
- **Language**: Kotlin
- **Features**: app data monitor, suspicious traffic flags, profile-based restrictions.
- **Competitors**: NetGuard-type tools.
- **Unique edge**: launcher-integrated quick lockdown modes.

#### 7K App Lock Lite (💡)
- **Language**: Kotlin
- **Features**: lock by app/category/time window, stealth mode, fake crash mode.
- **Competitors**: AppLock variants.
- **Unique edge**: lightweight battery impact + emergency unlock flow.

#### 7K Storage Cleaner Pro (💡)
- **Language**: Kotlin
- **Features**: duplicate finder, junk scanner, media cleaner, app residual cleanup.
- **Competitors**: SD Maid style cleaners.
- **Unique edge**: safer “explain before delete” flow + fast batch operations.

### Creative + Media

#### 7K Studio LUT Lab (💡)
- **Language**: Kotlin core + optional Flutter control surface
- **Features**: LUT imports, curve editor, style packs, batch apply.
- **Competitors**: Lightroom LUT tools, pro filter apps.
- **Unique edge**: creator template marketplace-ready architecture (offline first initially).

#### 7K Thumbnail Maker (💡)
- **Language**: Flutter
- **Features**: canvas layers, text effects, social size presets, export presets.
- **Competitors**: Canva, PixelLab.
- **Unique edge**: creator-first speed templates + offline rendering.

#### 7K Poster Maker (💡)
- **Language**: Flutter
- **Features**: poster templates, grids, typography packs, brand kits.
- **Competitors**: Canva, Adobe Express.
- **Unique edge**: free offline templates and instant local export.

#### 7K Audio Cutter (💡)
- **Language**: Kotlin
- **Features**: waveform trim, fade in/out, merge/split, ringtone export.
- **Competitors**: Lexis Audio Editor, MP3 cutter apps.
- **Unique edge**: cleaner UX and faster processing on low-end devices.

### Learning + Knowledge

#### 7K GK Blitz (💡)
- **Language**: Flutter
- **Features**: quiz ladders, adaptive difficulty, daily challenge packs.
- **Competitors**: quiz prep apps.
- **Unique edge**: no-paywall offline question bank + performance analytics.

#### 7K Law Flashcards (💡)
- **Language**: Flutter
- **Features**: spaced repetition decks, case-law cards, exam modes.
- **Competitors**: Anki, quiz decks.
- **Unique edge**: syllabus-linked auto deck generation from 7K Law Prep.

#### 7K PDF Annotator (💡)
- **Language**: Kotlin
- **Features**: highlight, comment, signature, bookmark index, split/merge PDF.
- **Competitors**: Xodo, Adobe Reader mobile.
- **Unique edge**: lightweight offline annotation with study workflow integration.

#### 7K Offline Dictionary (💡)
- **Language**: Kotlin + optional Python indexing module
- **Features**: multilingual lexicon, phrase search, example sentences, word packs.
- **Competitors**: U-Dictionary, Oxford mobile apps.
- **Unique edge**: full offline packs + instant in-app lookup from Polyglot/Study.

---

## 4) Priority and migration phases

### Phase P1 (stabilize and raise quality now)
- 7K Browser
- 7K Studio
- 7K Notes
- 7K Tasks Commander
- 7K File Forge
- 7K Budget Guardian
- 7K Privacy Shield
- 7K Battery Doctor
- 7K Offline First AppStore

### Phase P2 (premium UX and cross-app intelligence)
- 7K Study
- 7K Smart Notes+
- 7K Calendar
- 7K Life
- 7K Polyglot
- 7K Studio Templates

### Phase P3 (expansion and monetization-ready modules)
- Journal Vault, Habit Forge, Read Later Offline, Voice Notes Pro
- Permission Radar, Network Guard, App Lock Lite, Storage Cleaner Pro
- LUT Lab, Thumbnail Maker, Poster Maker, Audio Cutter
- GK Blitz, Law Flashcards, PDF Annotator, Offline Dictionary

---

## 5) Monetization without harming offline value

Free core must remain powerful. Monetization can be layered as optional:
- Pro template packs (Studio/Thumbnail/Poster)
- Advanced analytics dashboards (Budget/Habit/Study)
- Premium export bundles (PDF/CSV/theme bundles)
- Cross-device sync add-on (optional, never mandatory for core)

Principle: **No paywall for basic offline utility.**

---

## 6) Engineering contracts for multi-language ecosystem

To keep quality high across Kotlin/Flutter/Web modules:
- Shared app contract JSON schemas (tasks, notes, budgets, templates)
- Standard deep link format (`sevenk://app/action/...`)
- Shared design token package (colors, spacing, typography)
- Common analytics events (local-first queue)
- Common export/import format (JSON + CSV)

---

## 7) Success criteria (what “up to the mark” means)

Each app release should pass:
- UX consistency checklist (layout/motion/typography/action placement)
- Offline workflow checklist (critical journey works with no internet)
- Performance checklist (startup, scroll smoothness, memory budget)
- Differentiation checklist (at least 2 features clearly better than top competitor)

This is the working baseline plan for all 7K apps going forward.
