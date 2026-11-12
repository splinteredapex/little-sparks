# Little Sparks

Calm, offline learning games and puzzles for a young child. One static page, no accounts, no ads, no tracking.

Settings and progress stay in the browser on the device. The grown-ups panel opens by holding the button at the bottom of the home screen for 3 seconds.

Hosted with GitHub Pages. To install on a tablet: open the site in the browser, then use the browser menu's "Add to home screen" or "Install app".

## Android app

`android/` holds a small app shell for Android and Amazon Fire tablets. It opens the online version full screen (so updates arrive on their own), falls back to a copy built into the app when offline, and reads questions aloud with the tablet's own voice.

GitHub Actions builds it on every change and publishes the install file at:
https://github.com/splinteredapex/little-sparks/releases/latest/download/little-sparks.apk

The signing key is kept in this repository so every new version installs over the last one without losing progress. That suits a family app installed by hand; it would not suit an app in a public store.
