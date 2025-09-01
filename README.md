# 7K Launcher

A custom Android launcher with a modern UI featuring a dock, sidebar, and app drawer.

## Features

### Core Launcher
- Modern, customizable home screen with wallpaper support
- Dock for frequently used apps
- Right sidebar for quick app access
- Swipe-up app drawer
- Basic widget support

### 7K App Ecosystem
- **To-Do List:** A fully functional to-do page on your home screen. Add, edit, and delete tasks. Mark items as complete with a strikethrough.
- **7K Browser:** A lightweight, private browser with enhanced tracking protection.
- **Calc Vault:** Securely hide your private photos and videos behind a biometric or PIN lock.
- **7K Studio:** A budding photo editor with a powerful image cropper that supports cropping, rotating, and scaling.

## Getting Started

1. Open the project in Android Studio
2. Wait for Gradle sync to complete
3. Build the project (Build > Make Project)
4. Run on an Android device or emulator

## Setting as Default Launcher

1. Install the app on your device
2. Go to Settings > Apps > Default Apps > Home app
3. Select "7K Launcher"

## Project Structure

- `app/src/main/java/com/sevenk/launcher/` - Main source code
- `app/src/main/res/layout/` - Layout files
- `app/src/main/res/values/` - Resources (colors, strings, dimensions, etc.)

## Requirements

- Android Studio Arctic Fox or later
- Android SDK 26 or higher
- Gradle 7.0.0 or higher

## License

This project is open source and available under the MIT License.

## 7K Launcher Color System

Light Mode

- Background: Timberwolf (#d3d0cb)
- Dock & Sidebar (Glass): White at 40% opacity + blur
- Primary Accent: Chathams Blue (#17557b)
- Secondary Accent: Calypso (#366e8d)
- Text Primary: Gable Green (#13262f)
- Text Secondary: Rolling Stone (#7a7c7d)
- Borders/Dividers: Edward (#9e9f9f)

Dark Mode

- Background: Gable Green (#13262f)
- Dock & Sidebar (Glass): #ffffff at 10–15% opacity + blur
- Primary Accent: Calypso (#366e8d)
- Secondary Accent: Chathams Blue (#17557b)
- Text Primary: Timberwolf (#d3d0cb)
- Text Secondary: Regent Gray (#828f9a)
- Borders/Dividers: Bombay (#a9abae)

UI/UX Applications

- Dock: Glassmorphism with background blur; Timberwolf tint (light) / #ffffff 15% (dark)
- Sidebar (Quick Toggles): Same glass base; add accent glow (Calypso/Chathams) when active
- App Drawer Background: Semi‑transparent blur overlay (light → Timberwolf tint, dark → Gable Green tint)
- Search Bar: Pill glass with Calypso highlight ring on focus
- Widgets: Clock/Date large Timberwolf text (light) or Calypso glow (dark); Music Player with blurred album art background and Chathams Blue buttons

## Typography

- Headings (Time/Date): Montserrat Bold
- App Labels & UI Text: Inter Regular/Medium

## Glass Tint Strategy

- Adaptive Glass: Light mode uses light glass (white 30–40% opacity). Dark mode uses dark glass (#13262F at ~40% opacity). Both apply blur on Android 12+.

## Dock Behavior

- Default shows 4 apps on first launch. After the user selects dock apps, the dock is uncapped (users can add more). Alignment/padding centers or aligns based on user preference.

## UI/UX Blueprint

This is the master plan for structure, style, and interactions. See `PROJECT_NOTES.md` for active TODOs derived from this.

### 1) Layout Structure

- Home Screen
  - Top (Widget Zone): Large Clock (Montserrat Bold), Date + Weather (Inter). Optional AI Suggestion card (glass with accent highlight)
  - Center (App Grid): 4x5 or 5x5 adaptive grid. Icons inside rounded glass cards. Long‑press → glass context menu with accent glow
  - Bottom (Dock): 4–5 pinned apps on a glass blur panel with 20–24px radius
  - Right Sidebar (Quick Tools): Vertical pill glass strip for Wi‑Fi, Bluetooth, Music, Flashlight
- App Drawer (Swipe Up): Semi‑transparent blurred overlay; pill glass search bar; tabs (Work | Games | Social | Utilities); 5–6 column grid
- Gestures: Swipe down → Global Search (apps + web + AI). Swipe up → Drawer. Swipe left/right → Pages. Long‑press dock/sidebar → context menu

### 2) Colors

- Light Mode
  - Background: Timberwolf (#d3d0cb)
  - Glass: White @ ~35% + blur(20px)
  - Text: Primary Gable Green (#13262f), Secondary Rolling Stone (#7a7c7d)
  - Accents: Primary Chathams Blue (#17557b), Secondary Calypso (#366e8d)
- Dark Mode
  - Background: Gable Green (#13262f)
  - Glass: #13262f @ ~40% + blur(20px)
  - Text: Primary Timberwolf (#d3d0cb), Secondary Regent Gray (#828f9a)
  - Accents: Primary Calypso (#366e8d), Secondary Chathams Blue (#17557b)

### 3) Typography

- Clock: Montserrat Bold 48–64
- Date: Inter Regular 20
- App Labels: Inter Medium 14
- Sidebar/Dock Labels: Inter Regular 12
- Search Bar: Inter Regular 16

### 4) Glassmorphism Rules

- Base Tint: adaptive (light white glass, dark #13262F)
- Blur: 20–30px
- Border: 1px rgba(255,255,255,0.15)
- Shadow: subtle inner shadow for depth
- Active: glow with Calypso/Chathams

### 5) Animations

- App Launch: scale up + background blur
- Sidebar/Dock: fade + slide in
- Search Bar: expand on focus
- Widget Refresh: subtle fade

### 6) Advanced UX Features

- Adaptive glass tint per mode
- AI‑Smart Dock (contextual app suggestions)
- Haptic feedback (if supported)
- Customizable widgets (Notes, Music, Calendar)
