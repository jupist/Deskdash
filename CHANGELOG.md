# Changelog

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
- Burn-in protection for OLED screens: the picture shifts a few pixels every couple of minutes, the night clock further.
- Settings rebuilt as a two-pane screen (drill-in pages on phones), with first-run setup.
- Weather: 7-day detail view, feels-like, humidity, wind, rain chance; search by neighbourhood.
- Night screen can show the next calendar event and seconds.
- Release builds are shrunk (about 4.6 MB) and can be signed with your own key via `keystore.properties`.

## 0.1

First version for one tablet: fixed 12 × 3 layout with clock, calendar, countdowns, weather, now playing, alarm, ambient sound and a ReadEra shortcut.
