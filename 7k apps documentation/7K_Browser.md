# 7K Browser — Product & Engineering Documentation

Status: Planning (MVP + Production)
Owner: 7K Apps Product/Engineering
Platform: Android-first (current), cross-platform-ready design

---

## 1) Product goal

Build a **fast, privacy-respecting, offline-capable browser** that feels premium and integrated with 7K Launcher ecosystem.

Core promise:
- Faster for daily browsing tasks than heavy mainstream browsers on mid-range devices.
- Better offline reading + note capture workflows than default browser apps.
- Cleaner UX than ad-heavy browser clones.

---

## 2) Why hybrid Flutter + Native (Kotlin) for this app

Given current codebase and your request, best architecture is:

- **Flutter UI layer** for beautiful, modern, consistent UX and fast iteration.
- **Kotlin browser engine layer** for WebView, downloads, intents, storage, performance tuning.
- **Platform channel bridge** for commands/events between Flutter and Kotlin.

### Why this is better than pure Flutter for browser

Pure Flutter web rendering wrappers are weaker than native WebView control for:
- secure settings control,
- download manager hooks,
- cookie/session handling,
- performance tuning and lifecycle handling.

So hybrid gives:
- Flutter-level UI quality,
- Native-level browser reliability.

---

## 3) Target users and use cases

### Primary users
- Students/researchers using 7K ecosystem.
- Productivity users who save lots of content for offline reading.
- Privacy-conscious users wanting lightweight browser behavior.

### Key user jobs
- Search quickly and open tabs without lag.
- Save content for offline reading.
- Move content to 7K Notes / Tasks quickly.
- Keep browsing private without complex setup.

---

## 4) Competitor benchmark (practical)

## Main competitors
- Brave
- Firefox
- Samsung Internet
- Chrome (default baseline)

## Competitor strengths we must match
- Smooth tab handling
- Stable downloads
- Reader mode (where available)
- Password/autofill quality
- Dark mode handling

## Gaps we can beat
- Too many menus/toggles
- Weak offline workflow integration
- Heavy startup or memory usage on lower-end phones
- Poor integration with personal productivity stack

## 7K Browser differentiators
- One-tap "Save to Offline Pack" + "Send to 7K Notes"
- Quick actions from launcher (search, private tab, saved pages)
- Minimal telemetry design + local-first controls
- Better offline reading and highlight export than generic browsers

---

## 5) UX direction (non-generic, premium)

Reference style direction:
- Dark premium surfaces
- Strong card hierarchy
- Clean spacing and high-contrast readability
- Minimal visual noise, clear primary actions

### Core screen set
1. Home / New tab dashboard
2. Active tab web view
3. Tab switcher
4. Saved offline pages
5. Browser settings
6. Privacy center

### Navigation model
- Bottom nav (4 items): Home, Tabs, Offline, Profile/Settings
- Floating center quick action (new tab/search)
- Gesture support: swipe between tabs (optional phase 2)

### Visual tokens
- Background: deep slate/indigo darks
- Accent: 7K violet/blue
- CTA buttons: rounded pill style
- Cards: 12–16dp radius, subtle elevation/contrast

---

## 6) Feature plan

## MVP scope (ship-first)

### Browsing essentials
- URL/search bar with suggestions (history + bookmarks)
- Multi-tab support
- Back/forward/refresh/share
- Basic download manager list

### Privacy essentials
- Incognito mode
- Clear browsing data (history/cache/cookies)
- Tracker-light mode toggle (basic)

### Offline essentials
- Save page for offline (HTML snapshot or readability text)
- Offline pages list with search

### 7K ecosystem integration
- "Send page to 7K Notes"
- "Create task from page" (title + URL)

### UX essentials
- Fast startup dashboard
- Clean tab cards
- Empty states with guided actions

## Production-ready full scale (phase 2+)

### Advanced browsing
- Tab groups
- Reader mode (font, width, theme controls)
- Desktop/mobile UA toggle per site
- Smart autofill (local vault integration-ready)

### Advanced downloads/files
- Download categories
- Resume/retry failed downloads
- Open with File Forge integration

### Advanced privacy/security
- Site permission manager
- HTTPS upgrade mode
- Fingerprinting resistance toggles (where feasible)
- Per-site cookie controls

### Intelligence layer
- Smart summaries for saved pages
- Topic folders (auto-classification local-first)
- Reading time + revisit reminders

### Productivity superpowers
- Highlight and annotate saved pages
- Export highlights to Notes
- Share page snippet to Tasks Commander

### Performance tuning
- Lazy tab restore
- Background tab memory policies
- Resource preloading controls

---

## 7) Technical architecture

## Module split

- `browser-core-native` (Kotlin)
  - WebView management
  - downloads
  - cookie/cache controls
  - site permissions

- `browser-ui` (Flutter)
  - all screens and components
  - navigation
  - themes
  - UI state representation

- `bridge` (platform channel)
  - commands: openUrl, closeTab, saveOffline, clearData, etc.
  - events: pageLoaded, downloadProgress, tabUpdated, securityWarnings

## Storage model

- Local DB (Room in Kotlin or drift/sqlite in Flutter)
  - tabs metadata
  - history
  - bookmarks
  - offline pages index
  - site settings

- File storage
  - offline page content packs
  - downloaded files

## Data contracts (example)

Tab model:
- id
- url
- title
- faviconPath
- lastVisited
- isIncognito

Offline page model:
- id
- sourceUrl
- title
- savedAt
- contentPath
- tags

---

## 8) MVP backlog (implementation order)

1. Hybrid scaffold
   - Flutter host screen launched from existing app entry
   - platform channel handshake
2. Core tab engine
   - create/open/close/switch tabs
3. Navigation controls
   - address/search bar + nav controls
4. Offline save v1
   - save current page metadata + content snapshot
5. Notes/Tasks integration hooks
6. Settings + privacy v1
7. QA + performance tuning

---

## 9) Success metrics

## Product metrics
- Daily active users for Browser
- Average session duration
- Offline saves per user
- Notes/Tasks integrations initiated from Browser

## Quality metrics
- cold start time target: < 1.2s on target mid-range device
- crash-free sessions: > 99.5%
- tab restore success rate: > 99%

## UX metrics
- time to first page load
- taps-to-save-offline
- taps-to-send-to-notes/tasks

---

## 10) Risks and mitigation

## Risk: Hybrid complexity
Mitigation:
- strict interface contract for bridge
- small native API surface
- feature flags for staged rollout

## Risk: Offline save quality inconsistencies
Mitigation:
- fallback modes (full snapshot, simplified reader text)
- robust metadata + retry handling

## Risk: Performance regressions with many tabs
Mitigation:
- tab lifecycle policy
- cap active webviews and serialize background tabs

---

## 11) Release phases

## Phase A — MVP (4–6 weeks)
- core browser + tabs + offline save + basic privacy + 7K integrations

## Phase B — Stability and polish (2–3 weeks)
- crash fixes, UX polish, performance tuning, instrumentation

## Phase C — Production scale (6–10 weeks)
- reader mode, tab groups, advanced privacy, smarter offline and productivity features

---

## 12) Definition of done

MVP is done when:
- all MVP features above are implemented and tested
- app is stable under 50-tab stress scenario (with lifecycle strategy)
- offline save and restore works consistently
- Notes and Tasks integration paths are fully functional
- design aligns with 7K premium UI system

---

## 13) Immediate next implementation task

Create a technical design doc for the **Flutter ↔ Kotlin browser bridge**:
- method channel names
- payload schemas
- error handling
- async event stream design

This should be written before coding the hybrid migration.
