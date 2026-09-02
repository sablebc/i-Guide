# i-Guide Android TV App — Initial Build

## Project Setup (already done)
- **Package:** `com.kmsma.i_guide`
- **Language:** Java
- **Min SDK:** 23 (Marshmallow)
- **Target SDK:** 37
- **Build:** Kotlin DSL (build.gradle.kts)
- **Dependencies already in gradle:** Leanback 1.0.0, ExoPlayer 2.19.1 (core + ui + hls), OkHttp 4.12.0, Gson 2.10.1, ConstraintLayout 2.1.4, AppCompat 1.6.1, Material 1.10.0

## Target Hardware
- MyGica ATV329X (Amlogic S905X, 2GB RAM, Android 7.1)
- D-pad remote only — no touch input
- Display: 1080i CRT via HDMI-to-component

## Backend Services (LAN only, no auth tokens needed for now)
- **Tunarr** at `http://192.168.2.100:8010` — provides live TV channel streams
  - M3U playlist: `http://192.168.2.100:8010/api/m3u`
  - XMLTV guide: `http://192.168.2.100:8010/api/xmltv.xml`
  - Channel list: `GET /api/channels` — returns JSON array of channel objects with `id`, `name`, `number`, `icon`, `streamMode`
  - HLS stream per channel: `http://192.168.2.100:8010/stream/channels/{channelId}?streamMode=hls`
- **Jellyfin** at `http://192.168.2.100:8096` — provides media metadata, images, library data
  - No API key configured yet — hardcode the base URL for now, auth can be added later

## What This App Is
A retro-styled TV guide and live TV player for Android TV, modeled after the Cogeco i-Guide cable interface from the early 2000s. It is a **thin client** — all data comes from Tunarr and Jellyfin APIs. No local media logic, no sorting, no scheduling. The app renders what the APIs provide and plays live streams.

The app must be fully navigable with a D-pad remote (arrow keys + OK/Select + Back). No touch targets, no mouse affordances.

## First Deliverable: Live Player + Flip Bar Overlay
Build the minimum viable app: tune to a live TV channel and display a Flip Bar overlay showing what's currently on.

### What to build:
1. **PlayerActivity** — full-screen Activity that launches on app start, uses ExoPlayer to play an HLS stream from Tunarr
2. **Flip Bar overlay** — a bottom-anchored overlay that appears over the live video when the user presses OK/Select or a channel-change key, and auto-dismisses after 5 seconds of inactivity
3. **Channel tuning** — D-pad up/down changes channels (cycles through the channel list fetched from Tunarr's API), D-pad left/right are reserved for future use
4. **Data fetching** — on launch, fetch the channel list from `GET http://192.168.2.100:8010/api/channels`, parse it, start playing the first channel's HLS stream

### Flip Bar visual spec:
- Bottom-anchored, overlaid on the live video surface
- Two stacked horizontal bars with a fade gradient above them blending into the video:
  - **Top bar** (purple gradient `#8a78cc` → `#6a55b4` → `#56439c`): program title in yellow bold (`#f8e030`) on the left, channel number + call sign and time range on the right in white
  - **Bottom bar** (steel-blue gradient `#5a76c8` → `#3d59ac` → `#33509e`): one-line program description/episode info on the left, "HD" badge on the right if applicable
- Font: PT Sans (bundle as an asset or use system sans-serif as fallback)
- Slide-up animation on appear (200ms ease-out), fade-out on dismiss
- Auto-dismiss after 5 seconds, resets timer on any D-pad input

### Channel data shape from Tunarr API (`GET /api/channels`):
```json
{
  "id": "5b299866-ef36-45da-95cb-ae82ca3635bd",
  "name": "Adult Swim",
  "number": 7,
  "icon": { "path": "https://...", ... },
  "streamMode": "hls",
  "programCount": 3238
}
```

### For "now playing" info on the Flip Bar:
For this first iteration, just show the channel name and number. Getting accurate "now playing" program title from the XMLTV or stream metadata is a follow-up task — don't block on it. Show placeholder text like the channel name for now.

### Key constraints:
- This is an Android TV app, NOT a phone app. Use the Leanback support library where appropriate but do NOT use default Leanback BrowseSupportFragment or its default theming — the UI is fully custom.
- All layouts must work at 1280×720 and scale to 1920×1080.
- The ExoPlayer surface should be the base layer, Flip Bar renders on top as a View overlay.
- Handle ExoPlayer lifecycle properly (release on pause/stop, re-init on resume).
- Use OkHttp for all API calls. Parse JSON with Gson.
- All network calls off the main thread.
- Back button should dismiss the Flip Bar if visible, otherwise exit the app (or show a confirmation).

### File structure to create:
```
app/src/main/java/com/kmsma/i_guide/
  PlayerActivity.java          — main activity, ExoPlayer + overlay management
  FlipBarView.java             — custom View for the flip bar overlay
  TunarrApiClient.java         — OkHttp client for Tunarr API calls
  Channel.java                 — data model for a channel
  ChannelManager.java          — holds channel list, tracks current channel index, handles up/down cycling

app/src/main/res/layout/
  activity_player.xml          — FrameLayout with PlayerView + FlipBar overlay
  view_flip_bar.xml            — layout for the flip bar (two gradient bars)

app/src/main/res/values/
  colors.xml                   — all the i-Guide color values
  strings.xml                  — app name, placeholder strings
  styles.xml                   — app theme (no title bar, fullscreen, dark background)

app/src/main/AndroidManifest.xml
  — single Activity (PlayerActivity), launcher intent
  — android:banner for TV launcher
  — uses-feature android.software.leanback (required=false)
  — uses-feature android.hardware.touchscreen (required=false)
  — INTERNET permission
```

### What NOT to build yet:
- No guide screens (Listings by Time, Listings by Channel, etc.)
- No Quick Menu, Main Menu, Mini Guide, Program Information, or Favourites
- No Jellyfin integration yet
- No XMLTV parsing
- No DVR, search, settings, or favourites functionality
- No remote button mapping beyond D-pad arrows, OK/Select, and Back

Build only the player + flip bar + channel switching. Everything else comes later.

## Full UI Context (for future reference — DO NOT build these screens yet)

The following documents the complete 8-screen UI design. This is provided so you understand the overall architecture and visual language. Only build what's specified in "First Deliverable" above.

Screens implemented (Quick Menu, Main Menu, Listings by Time, Listings by Channel, Flip Bar, Mini Guide, Program Information, Favourites) — all D-pad driven, PT Sans font, i-Guide-era glossy blue/purple visual style.

### Global layout
- Screen size reference: 1280×720 (16:9, TV-native)
- Font: PT Sans (400/700, plus italic 400/700). Fallback: Trebuchet MS, Tahoma, sans-serif.
- Background base: #0a1440 (near-navy)
- All screens are D-pad only
- Two screen categories:
  - Full-screen "guide" screens (Main Menu, Listings by Time, Listings by Channel, Program Information, Favourites): replace the whole display, have the top DIGITAL CABLE header bar.
  - Overlay screens (Quick Menu, Flip Bar, Mini Guide): render on top of live video.

### Header bar (shown on all full-screen guide screens)
- Height 42px, glossy blue gradient (#7aa0dc → #4a74c0 → #2f54a0 → #3a60b0)
- Left: "DIGITAL CABLE" logo, two stacked italic bold lines, white text
- Right: current time (live clock, bold white)

### Colors (exact values)
- Base navy background: #0a1440
- Header gradient: #7aa0dc → #4a74c0 → #2f54a0 → #3a60b0
- Purple info panel: #8a78c8 → #6a55b0 → #503f98
- Light-blue title bar: #6a8ad8 → #4a68c0 → #3a55b0
- Section/menu dark bar: #101838 → #0a1230
- Regular channel cell: #5a76c8 → #3d59ac → #33509e → #3a55a8
- Channel number cell: #4a63b4 → #32499c → #2a4090
- Movies category: #8a6cc8 → #6a4cac → #5c429e → #6448a4
- Sports category: #4c9868 → #35784c → #2e6c44 → #337249
- Kids category: #64a0d8 → #4680bc → #3c74b0 → #427ab4
- Selected/yellow: #fff268 → #f8dc28 → #e6c112 → #f0cc1e, text #101000
- Yellow accent: #f8e030
- Footer bar: #4a6ab8 → #35519c → #2a4488
- Live-TV overlay backdrop: #3a4a66 → #202c44 → #141c30
- Flip Bar purple: #8a78cc → #6a55b4 → #56439c
- Flip Bar steel-blue: #5a76c8 → #3d59ac → #33509e
