# Loki extensions

Loki ships with **Movies & TV** and **PC streaming** built in but switched off.
Until one is added, the launcher has no trace of it: no section in the bar, no
settings category, no pages.

Importing the file for one turns it on. Everything it needs is already in the
app, so nothing is downloaded and it works offline.

## Adding one

1. Download the `.json` file for the part you want
2. Open **Settings → System → Extensions**
3. Choose **Import an extension** and pick the file

The section appears immediately. Remove it from the same page, and everything it
added disappears again — your settings for it are kept in case you add it back.

| File | Adds |
|:--|:--|
| [`movies.json`](movies.json) | Browse films and shows, find sources, play them |
| [`stream.json`](stream.json) | Find PCs on your network and stream from them |

## What the file is

Three lines. Only `extension` is read; the rest is there so the file explains
itself if you open it in a year.

```json
{
  "extension": "movies",
  "name": "Movies & TV",
  "version": 1
}
```

**It is not a licence key.** Anyone can write one of these in a text editor, and
the parser is deliberately forgiving so a hand-typed file works. It is a way of
saying "I want this part of the launcher", not a lock on it.

## Why it works this way

Android has no plugin mechanism a sideloaded app can use. Feature delivery needs
Google Play; a separate app cannot draw inside Loki's own panels, so a section
would open as an app switch rather than staying in the launcher; and loading code
out of another APK fights compile-time dependency injection and the native
streaming core.

Shipping everything and revealing it on request is the one arrangement that keeps
each section properly integrated where it is wanted, and completely absent where
it is not.
