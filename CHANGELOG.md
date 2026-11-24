# Changelog

## 1.1.0

- **Phone control:** change the tablet from a phone running Deskdash on the same Wi-Fi: move, resize, add and remove tiles, change any tile's settings, write notes and to-dos, switch presets, add and remove countdowns, and set the alarm. Pair once with a 6-digit code; the connection is encrypted and needs no account or server (Settings › Phone control). Off until you turn it on.
- **Google Tasks:** a Checklist tile can show one of your Google Tasks lists; ticking and adding items saves to Google.
- **Screensaver:** Deskdash can be Android's screensaver: the dashboard, view-only, while charging (Settings › Display & power).
- **Backup:** export and import presets, layouts, settings, the alarm, countdowns and iCal links as one file (Settings › Backup).
- **Music app choice:** Now playing asks which app it resumes (Spotify, YouTube Music, any player…) instead of assuming Spotify.
- **New tiles:** Favourite apps and Recent apps (recent needs usage access).
- **Weather:** use my location; hourly forecast for the next 24 hours; night icons after dark.
- **Editing:** dragging onto other tiles pushes them aside, live; tiles that didn't fit a screen can be put back in one tap; new tiles take the largest size that fits.
- Album art keeps its shape (wide video thumbnails aren't cropped).
- New app icon with a themed-icon layer; alarm buttons and the night clock follow your theme.
- Faster start: no blank first frame, including right after an update.
- Google events are kept offline between restarts.
- Runs on Android 8.0 and newer (was Android 11).
- Weather detail shows wind units, and its forecast columns line up.
- Layouts carried to another screen size (a phone in landscape, say) keep their arrangement with no gaps, and the top bar fits narrower screens.
- Settings: the alarm can be turned on from its settings page; matching pickers for units and snooze; theme cards share one row; clearer permission descriptions.

## 1.0.0

The dashboard becomes customizable and works on any Android screen.

**Layout**
- Tiles are modules on a grid sized to the screen (about 80 dp per cell), in portrait or landscape.
- Long-press a tile to rearrange: drag to move (drop on a same-size tile to swap), drag the corner to resize, × to remove, tap for the tile's settings. Undo while editing.
- Add tiles from a picker. Each tile has a style (plain, accent, alt), an optional label, and its own settings.
- Presets: save layouts and switch between them. Each screen size keeps its own arrangement; a new size starts from the closest one.

**New tiles**
- Timer (countdown, stopwatch, focus/pomodoro), rings like the alarm even with the screen off.
- Checklist, sticky note, world clocks, system status, photo frame, quote of the day, volume/brightness controls, app shortcut.

**Calendars**
- Agenda shows up to 14 days from device calendars, iCal links, imported .ics files and Google sign-in (see `docs/GOOGLE_CALENDAR.md`), with calendar colours.

**Look**
- Themes: Slate, Forest, Ember, Graphite and your wallpaper's colours, each in dark or light (or following the system), with an optional pure-black background.
- Corner styles, spacing, text size, and three font sets plus your own font files.

**Everything else**
- Burn-in protection for OLED screens: the picture shifts a few pixels every couple of minutes (the night clock further), and optionally an alternating checkerboard lights only half the pixels at a time, swapping every 30 seconds, so solid areas wear evenly too.
- Settings rebuilt as a two-pane screen (drill-in pages on phones), with first-run setup.
- Weather: 7-day detail view, feels-like, humidity, wind, rain chance; search by neighbourhood.
- Night screen can show the next calendar event and seconds.
- Release builds are shrunk (about 4.6 MB) and can be signed with your own key via `keystore.properties`.

## 0.1

First version for one tablet: fixed 12 × 3 layout with clock, calendar, countdowns, weather, now playing, alarm, ambient sound and a ReadEra shortcut.
