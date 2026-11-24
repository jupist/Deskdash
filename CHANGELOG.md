# Changelog

## 1.1.0

- **Remote control:** a phone running Deskdash on the same Wi-Fi becomes the tablet's remote. Its whole app shows and changes the tablet until you exit: rearrange tiles by holding one, tap a tile to change it, and open the tablet's settings for appearance, presets, important dates, weather, alarm and night mode. Pair once with a 6-digit code; the connection is encrypted and needs no account or server. It's off until you turn on *Settings › Remote control › Edit from another device* on the tablet.
- **Dedicated remote:** set a phone to work primarily as a remote and it opens on your tablets instead of its own dashboard, going straight into the tablet when there's only one and it's online.
- **Google Tasks and Todoist:** a Checklist tile can show a Google Tasks list or a Todoist project instead of its own items; ticking and adding saves to the service. Connect them in the new *Settings › Tasks* (Todoist with your API token).
- **Calendar events into Important dates:** tap an event in the agenda for its details, then *Add to Important dates* (with a category) to count down to it. It's kept as a copy, so it stays even if the event changes.
- **Screensaver:** Deskdash can be Android's screensaver: the dashboard, view-only, while charging (Settings › Display & power).
- **Backup:** export and import presets, layouts, settings, the alarm, important dates and iCal links as one file (Settings › Backup).
- **Music app choice:** Now playing asks which app it resumes (Spotify, YouTube Music, any player…) instead of assuming Spotify.
- **New tiles:** Favourite apps and Recent apps (recent needs usage access).
- **Weather:** use my location; hourly forecast for the next 24 hours; night icons after dark.
- **Editing:** dragging onto other tiles pushes them aside, live; tiles that didn't fit a screen can be put back in one tap; new tiles take the largest size that fits.
- Album art keeps its shape (wide video thumbnails aren't cropped).
- New app icon with a themed-icon layer; alarm buttons and the night clock follow your theme.
- Faster start: no blank first frame, including right after an update.
- Google events are kept offline between restarts.
- Runs on Android 8.0 and newer (was Android 11).
- Starts in landscape, setup included; portrait or auto-rotate only if you choose them in Settings › Dashboard.
- Countdowns are now called Important dates.
- Weather detail shows wind units, and its forecast columns line up.
- Layouts carried to another screen size (a phone in landscape, say) keep their arrangement with no gaps, and the top bar fits narrower screens.
- No pop-up messages: the tile picker dims tiles that won't fit and says how much space they need, and the alarm asks for full-screen alerts in its settings rather than jumping to a system page.
- Calmer wording throughout, without dot separators: Rings tmrw, Snoozed until 07:09, 3h ahead.
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
