# Google Calendar and Tasks sign-in

Deskdash can read Google Calendar three ways:

1. **On this device** (no setup): if your Google account is on the device, its calendars sync into Android's calendar and appear under *Settings › Calendars › On this device*.
2. **iCal link** (no setup): in Google Calendar on the web, open *Settings › your calendar › Integrate calendar › Secret address in iCal format*, and paste it into *Add iCal link*.
3. **Google sign-in**: reads Google Calendar directly with a read-only token. This needs an OAuth client for your build, set up once by whoever builds the APK.

## Setting up sign-in

1. In the [Google Cloud console](https://console.cloud.google.com/), create a project (or pick one).
2. *APIs & Services › Library*: enable **Google Calendar API**, and **Google Tasks API** for Checklist tiles that show a Google Tasks list.
3. *Google Auth Platform › Branding*: fill in the app name and your email. (The first visit asks you to *Get started*; choose **External** as the audience.)
4. *Google Auth Platform › Data access › Add or remove scopes*: add `https://www.googleapis.com/auth/calendar.readonly` and `https://www.googleapis.com/auth/tasks` (paste them under *Manually add scopes* if they aren't listed), then **Save**.
5. *Google Auth Platform › Audience*: leave the publishing status on **Testing** and add each Google account that will sign in under **Test users**. A testing-mode app works for up to 100 test users without Google's verification; Google shows a "hasn't verified this app" notice at sign-in, and *Continue* gets past it.
6. *Google Auth Platform › Clients › Create client*: type **Android**, package name `com.desk.dashboard`, and the **SHA-1** of the key that signs your APK:

   ```bash
   ./gradlew signingReport
   ```

   Use the `release` variant's SHA-1 for release builds (the debug SHA-1 for debug builds; you can add both as separate clients).

There's nothing to paste into the app: Google matches the client by package name and signing certificate. Install the build, then *Settings › Calendars › Connect Google account*.

If sign-in says *"This build isn't registered with Google yet (developer error)"*, the package name or SHA-1 doesn't match the client in step 6. If it says access was denied or the app is blocked, check the scopes in step 4 and that your account is a test user in step 5.

## Google Tasks

A Checklist tile can show one of your Google Tasks lists instead of its own items: connect Google Tasks once in *Settings › Tasks*, then in the tile's settings set *Items from* to **Google Tasks** and pick a list. Ticking and adding items saves to Google, and changes made in the Tasks app show up within five minutes. It uses the same OAuth client as above, with the Tasks API turned on.
