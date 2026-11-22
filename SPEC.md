# Tablet Dashboard App — Build Spec

Nov 21, 2026 · Aman Jayesh

## Overview

Build a native Android app that turns a Galaxy Tab A7 Lite into an always-on, landscape desk dashboard: time, weather, Google Calendar, manually entered upcoming dates, Spotify controls, an alarm with a one-tap toggle, ambient sound, volume and brightness sliders, a lock button and a ReadEra shortcut.

| Item | Value |
|---|---|
| Device | Galaxy Tab A7 Lite (SM-T220/T225), 8.7" LCD, 1340 × 800 px |
| OS | Android 14, One UI 6.1 |
| Hardware limits | MediaTek Helio P22T, 3 GB RAM: keep rendering and background work light |
| Orientation | Landscape only |
| Distribution | Sideloaded debug/release APK via adb, never the Play Store |
| Visual reference | `mockup.html` in this repo |

Build one milestone at a time (see the last section), testing on the tablet after each.

## Tech stack and project setup

Kotlin with Jetpack Compose, single-activity project, Gradle Kotlin DSL.

| Area | Choice |
|---|---|
| Language / UI | Kotlin, Jetpack Compose (custom theme, no stock Material look) |
| SDK levels | minSdk 30, targetSdk 35, compileSdk 35 |
| Settings storage | Jetpack DataStore (Preferences) |
| Upcoming dates storage | Room |
| Networking | OkHttp + kotlinx.serialization (weather only) |
| Audio | Media3 ExoPlayer for the rain loop; AudioTrack for generated white noise |
| Package name | com.desk.dashboard |

Setup on Fedora: JDK + adb; Android command-line tools in `~/Android/Sdk/cmdline-tools/latest/`; `ANDROID_HOME=$HOME/Android/Sdk`; `sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"`. No Android Studio; the Gradle wrapper downloads Gradle on first build.

**Performance rules:** no blur, shadows or gradients; only the clock and the media progress bar tick (once per second); weather refreshes every 30 minutes; recompose individual tiles, never the whole screen.

## Visual design

Match the mockup: flat dark tiles on a near-black frame, square corners, slate-blue and sand accents, squared monospace type. Dark only.

| Token | Hex | Used for |
|---|---|---|
| frame | #111114 | Screen background and gaps between tiles |
| tile | #1C1C1F | Default tile background |
| ink | #EEECE5 | Primary text, icons, slider thumbs |
| muted | #8B8B88 | Secondary text and labels |
| line | #2E2E33 | Dividers, outlines, slider tracks |
| slate | #4E6187 | Time tile fill, active states, progress fills |
| slate-light | #8EA3CC | Slate-colored text on dark tiles (e.g. dates within 3 days) |
| on-slate | #F3F1EA | Text on slate |
| sand | #AEADA3 | Ambient tile fill, weather bars |
| on-sand | #1C1C1F | Text and icons on sand |

Fonts (SIL OFL, bundled in `res/font`):
- **Share Tech Mono** for every large number and display text: clock, temperatures, alarm time, day numbers, track title.
- **Space Mono** (Regular) for labels and body text. Tile labels are 11 sp, uppercase, letter spacing 0.1 em.

Type scale: clock 96–112 sp, primary tile numbers 56–64 sp, secondary numbers 26–30 sp, body 13 sp, small meta 9–10 sp.

Layout: 12 dp outer padding and gaps, a 48 dp top bar, then a grid of 12 equal columns and 3 equal rows filling the rest. Tiles have 18 dp × 20 dp padding, 0 dp corner radius, no elevation.

| Tile | Columns | Rows | Fill |
|---|---|---|---|
| Time | 1–5 | 1 | slate |
| Calendar (today) | 6–12 | 1 | tile |
| Now playing | 1–4 | 2–3 | tile |
| Upcoming dates | 5–12 | 2 | tile |
| Alarm | 5–8 | 3 | tile |
| Weather | 9–11 | 3 | tile |
| Ambient (top half) / Reading (bottom half) | 12 | 3 | sand / tile |

Volume and brightness are compact sliders in the top bar (between "Updated" and Settings), not a tile. Ambient and Reading are half-height mini tiles stacked in column 12 (10 dp padding, 9 sp label, tap anywhere). This layout (changed in milestone 3) gives Today and Upcoming nearly the full width.

Density note: the mockup is drawn in CSS px at 1340 × 800, but the tablet renders at ~1.33 px/dp (≈ 1006 × 600 dp), so tiles have less room than in the mockup. Upcoming uses the mockup's two-line rows when they fit and falls back to one-line rows otherwise.

Round elements are limited to the circular action buttons (34 dp), media controls (40 dp, play 52 dp) and toggle switches.

## Dashboard tiles

Each tile reads from one source and has an empty or error state, so the screen never shows a blank box.

- **Top bar.** 3 × 3 dot icon at left, then battery ("84% · charging", from ACTION_BATTERY_CHANGED) and "Updated HH:mm" (last successful weather fetch). In the middle: Vol and Light sliders. At right: outlined uppercase Settings and Lock buttons.
- **Time.** 24-hour HH:mm in Share Tech Mono, weekday and date ("SAT" / "21 NOV") at right. Recompose only on minute changes, aligned to the start of each minute.
- **Weather.** Current temp, condition word (WMO code), today's high/low, five bars for the next five days' highs with weekday labels; today's bar slate, rest sand. Source: Open-Meteo `api.open-meteo.com/v1/forecast` (no key) with `current`, `daily=temperature_2m_max,temperature_2m_min,weather_code`. Refresh every 30 min and on resume; on failure keep cached data shown in muted color.
- **Calendar (today).** Next three events from now until end of tomorrow: time, title, relative label for the next one ("in 25 min", "now"). Source: CalendarContract.Instances filtered to calendars chosen in Settings. Tap opens the event in the Calendar app. Empty: "Nothing else today".
- **Upcoming dates.** Manually entered dates sorted soonest first, first three plus "+N more". Row: day number and month, category tag, title, optional time and note, days left ("today", "tomorrow", "6d"). Rows within 3 days use slate-light for days-left. Entries disappear the day after their date.
- **Now playing.** Album art, title, artist, progress bar with elapsed/total, prev / play-pause / next. Source: MediaSessionManager.getActiveSessions() via a NotificationListenerService; prefer Spotify (com.spotify.music), else most recently active. Controls via MediaController.TransportControls. Empty: "Nothing playing" with a button that launches Spotify.
- **Alarm.** The app's own alarm: time, repeat days (M T W T F S S chips), toggle. On schedules next occurrence with AlarmManager.setAlarmClock(); off cancels. When it fires, show a full-screen alarm activity (over lock screen) with large Stop and Snooze, playing the chosen sound on the alarm stream. Reschedule after reboot and after each ring.
- **Ambient.** Each tap cycles Off → Noise → Rain → Off, shown as large text plus three step dots. Noise is soft pink/brown noise generated live (stereo); rain is a real recording (CC BY-SA, see CREDITS.md) prepared offline as a seamless ~45 s loop, decoded once and looped in the same AudioTrack. 1 s fade in/out. Stops when the app leaves the foreground.
- **Controls (top bar).** Volume (STREAM_MUSIC via AudioManager) and brightness (Settings.System.SCREEN_BRIGHTNESS, mode manual, plus window brightness for instant change) sliders with numeric readouts.
- **Reading.** "ReadEra" with a circular arrow button; launches org.readera via getLaunchIntentForPackage, else opens its Play Store page.
- **Lock.** DevicePolicyManager.lockNow() with a device admin receiver using only force-lock. (Alternative: accessibility service GLOBAL_ACTION_LOCK_SCREEN, keeps fingerprint unlock.)

## Settings screen

Full screen from the top bar's Settings button, closes with Done; every change saves immediately. Same tiles/colors/fonts: wide left column for upcoming dates, right column for the rest.

**Upcoming dates.** List of all entries (date, title, Edit, Delete) above an add/edit form. Delete asks for confirmation.

| Field | Type | Rules |
|---|---|---|
| Title | Text | Required, ≤ 40 chars |
| Date | Date picker | Required, today or later |
| Time | Time picker | Optional |
| Category | Dropdown | Exam, Deadline, Personal, Trip, Other (default Other) |
| Note | Text | Optional, ≤ 60 chars |

- **Alarm.** Time picker, repeat-day chips (none = rings once), sound (system ringtone picker, default alarm), snooze (5 / 9 / 10 / 15 min, default 9).
- **Calendar.** Checkboxes per device calendar grouped by account; all Google calendars on by default.
- **Weather.** City search via `geocoding-api.open-meteo.com/v1/search`, storing name/lat/lon; units °C/°F (default °C).
- **Night mode.** Switch, start/end (default 23:00–07:00), night brightness slider (default 2%).
- **Display.** Keep screen on while charging (default on); use as home app (opens system default-home setting).
- **Permissions.** One row per permission: Granted, or a Grant button opening the right system screen.

## System behavior

- **Full screen:** sensorLandscape; hide status/nav bars with WindowInsetsControllerCompat, swipe-to-reveal.
- **Keep awake:** FLAG_KEEP_SCREEN_ON only while charging (and setting on).
- **Boot:** via home-app mode (HOME category). Tapping the dot icon opens an alphabetical list of installed apps (always, not only in home-app mode; it is how you reach other apps when Desk is home).
- **Night mode:** between start/end, replace the grid with a large muted clock and next alarm time, window brightness at night level. Tap shows full dashboard for 30 s. Alarm still rings.
- **Offline:** weather keeps last data with "Updated" time; everything else works offline.
- **Samsung battery:** prompt once to allow unrestricted battery (system dialog) and open Device care › Battery, one tap from Background usage limits › Never sleeping apps (the list itself needs a system-only permission to open directly).

## Permissions

| Permission | Why | How granted |
|---|---|---|
| READ_CALENDAR | Calendar tile | Runtime dialog |
| Notification access | Spotify | ACTION_NOTIFICATION_LISTENER_SETTINGS |
| WRITE_SETTINGS | Brightness | ACTION_MANAGE_WRITE_SETTINGS |
| USE_EXACT_ALARM | Alarm | Manifest |
| POST_NOTIFICATIONS | Alarm notification | Runtime dialog |
| USE_FULL_SCREEN_INTENT | Alarm over lock screen | canUseFullScreenIntent(), else ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT |
| Device admin (force-lock) | Lock button | ACTION_ADD_DEVICE_ADMIN |
| RECEIVE_BOOT_COMPLETED | Reschedule alarm | Automatic |
| INTERNET | Weather | Automatic |
| WAKE_LOCK | Alarm ringing | Automatic |

To uninstall, deactivate device admin first.

## Data storage

Room table `upcoming_dates`: id (Long PK auto), title (String ≤40), date (LocalDate, ISO string), time (LocalTime?), category (EXAM, DEADLINE, PERSONAL, TRIP, OTHER), note (String? ≤60).

DataStore keys:

| Key | Type | Default |
|---|---|---|
| alarm_enabled | Boolean | false |
| alarm_time | String HH:mm | 07:00 |
| alarm_days | Set of weekday ints | Mon–Fri |
| alarm_sound_uri | String | System default alarm |
| snooze_minutes | Int | 9 |
| calendar_ids | Set of Long | All Google calendars |
| weather_city / weather_lat / weather_lon | String / Double / Double | Empty |
| temp_unit | String | celsius |
| weather_cache / weather_cached_at | JSON / epoch ms | Empty |
| night_enabled | Boolean | true |
| night_start / night_end | String HH:mm | 23:00 / 07:00 |
| night_brightness | Float 0–1 | 0.02 |
| keep_awake_charging | Boolean | true |

Ambient state is not saved; always starts Off.

## Build, install and milestones

`./gradlew assembleDebug`, then `adb install -r app/build/outputs/apk/debug/app-debug.apk` (USB or wireless debugging).

1. **Skeleton:** theme tokens, bundled fonts, landscape full-screen activity, top bar and 12 × 3 grid with placeholder tiles, live time tile.
2. **Settings and dates:** settings shell, Room table, add/edit/delete form, upcoming dates tile.
3. **Weather:** city search, forecast fetch, cache and "Updated" time, weather tile.
4. **Calendar:** permission flow, calendar picker, today tile.
5. **Now playing:** notification listener, Spotify session, controls, empty state.
6. **Alarm:** scheduling, toggle, full-screen ringing screen with Stop and Snooze, reboot rescheduling.
7. **Ambient, controls, ReadEra, lock.**
8. **System behavior:** keep awake while charging, night mode, home-app mode with app list, permissions section, battery-sleep prompt.

### Acceptance checklist

- [ ] Layout matches the mockup at 1340 × 800 with no clipped text
- [ ] Clock changes within one second of each new minute
- [ ] Weather shows cached data and its time when the hotspot is off
- [ ] A new Google Calendar event appears on the tile after sync
- [ ] Play, pause and skip control Spotify while it's in the background
- [ ] Alarm rings at the set time with the screen off and the app closed, and after a reboot
- [ ] Ambient button cycles Off, Noise, Rain, Off with no audible gap
- [ ] Brightness and volume sliders change the system values immediately
- [ ] Lock button locks the tablet; ReadEra opens from its tile
- [ ] Dashboard stays on while charging and dims at the night-mode start time
- [ ] Dates added in Settings appear sorted on the tile and disappear the day after
