# Loki Launcher

A dual-screen Android launcher for the **AYN Thor**.

The Thor has two screens, and most launchers treat that as a problem to work
around. Loki treats it as the point: the panel you are holding the controller
for shows a sparse, console-style icon grid, and the other panel shows
everything known about whatever the cursor is resting on — box art,
screenshots, developer, release year, how long you have played it.

It replaces your home screen. It scans your ROMs, launches them in the right
emulator, scrapes artwork for them, plays films, and streams games from your PC.

**Licence:** GPL-3.0. **Requires:** Android 10 or newer.

---

## Contents

- [Installing](#installing)
- [First run](#first-run)
- [The two screens](#the-two-screens)
- [The grid](#the-grid)
- [Controls](#controls)
- [The pointer](#the-pointer)
- [The keyboard](#the-keyboard)
- [Your game library](#your-game-library)
- [Artwork and metadata](#artwork-and-metadata)
- [Films and shows](#films-and-shows)
- [Streaming from your PC](#streaming-from-your-pc)
- [Themes and appearance](#themes-and-appearance)
- [Notifications](#notifications)
- [Recording](#recording)
- [Settings map](#settings-map)
- [What it cannot do](#what-it-cannot-do)

---

## Installing

1. Download the APK from [Releases](../../releases) and install it.
2. Open it once.
3. Set it as your home app — Loki offers this on first run, or **Android
   Settings → Apps → Default apps → Home app**.

Loki does not replace your existing launcher's data and can be uninstalled at any
time. Your games, saves and emulators are untouched.

---

## First run

Three things happen the first time you open Loki, and only the first time.

**A short intro plays.** It runs once, ever — not on every restart.

**A permission list appears** on the panel you are holding. Nothing on it is
required, and Loki works with none of it granted. Each one turns on a specific
part of the launcher, and each is granted in Android's own settings because no
app can grant them for itself:

| Permission | What it turns on |
|---|---|
| **Home app** | Loki opens when you press Home |
| **Accessibility** | The controller pointer, and typing into other apps |
| **Notification access** | Device notifications shown on the top screen |

**Then a walkthrough**, which takes you through both panels, the grid, the
controls, the keyboard, your library, films, streaming, the pointer and every
settings page. You can replay it any time from **Settings → About → Replay the
walkthrough**.

---

## The two screens

One panel is the **grid** — icons, the section bar, menus. The other is the
**information panel** — details about whatever is selected, the clock, and the
wallpaper.

Which physical screen gets which is up to you: **Settings → System →
Dual screen → Swap screens**. The same layout renders in either window, so
nothing is second-class.

**Running a game on one screen while using the launcher on the other** is what
the design exists for:

- Launching sends the game to the grid's panel. The other panel carries on
  showing the launcher.
- An entry's context menu can send it to the *other* panel instead, leaving the
  grid where it is and still navigable.
- **Touching a panel gives it the controller.** Tap the game to play, tap the
  launcher to browse, either direction, any time.
- **Home** is what gives a panel back to the launcher, from either screen.

If your device only exposes one screen, Loki falls back to a split single
display, and everything still works.

---

## The grid

**Every icon is where you put it.** An entry occupies the cell you place it in,
and an empty cell stays empty — nothing reflows when you add or remove
something. That is a deliberate departure from launchers that flow icons into a
list, and it is what makes rearranging feel like a console.

- **Move things** by holding **A** to pick an icon up, moving, and pressing
  **A** to drop it. Dropping onto an occupied cell picks up whatever was there
  so you can re-home it, rather than silently swapping the two.
- **Pinch** to snap between eight density presets, from 3×2 to 8×5. Each carries
  its own spacing so no density feels crowded.
- **Pages** are fixed grids. The size of your library changes the *number* of
  pages, never the cost of drawing one — ten thousand games cost the same per
  frame as ten. **L2** and **R2** turn pages.
- **Placements survive rescans.** An entry's identity comes from stable facts,
  so reinstalling an app or moving a ROM keeps its cell.
- **Icons** take one of five shapes — square, rounded, squircle, circle, hexagon
  — and the selection cursor traces whichever shape the cell actually has.

### Folders

Folders hold entries, scrape their own artwork, and can be made from the Start
panel or any entry's context menu.

**Scanned games are filed into a folder per system** rather than scattered
across pages, so adding a console brings in hundreds of games and costs the grid
one cell. Anything you move out or rearrange stays where you put it through
every later scan.

---

## Controls

| Input | Action |
|---|---|
| D-pad / left stick | Move the cursor |
| **A** | Launch — *hold* to pick the icon up |
| **B** | Back / close |
| **X** | Toggle favourite |
| **Y** | Context menu |
| **L1 / R1** | Previous / next screenshot for the selected game |
| **L2 / R2** | Previous / next page |
| **Stick click** | Shortcut panel |
| **Start** | Start panel |
| **Select** | App drawer |
| **Guide / Home** | Home |
| Triggers held | Accelerates whatever else you press |
| W A S D, E, F, Tab, Enter, Esc | Keyboard equivalents |

Three details you will feel rather than see: held directions repeat on Loki's own
schedule rather than Android's much slower one; **A** dispatches on *release*,
because holding it means "pick this up"; and pushing the stick past the dead zone
gives one clean direction rather than a flood of diagonals.

### The shortcut panel

Click either stick for quick access to the app drawer, search, settings, swap
screens, rescan, recording, Wi-Fi, Bluetooth, volume and Android settings.

It offers only what an ordinary app can genuinely do. Brightness, rotation and
the notification shade need permissions a launcher cannot hold, so a tile for
them could only pretend.

### The AYN button

Its firmware reports no code that reaches an app. A short press never arrives,
and a long press also powers the bottom panel off — that is firmware behaviour on
a button the vendor owns, and no installable launcher can intercept it. **The
stick clicks are the binding that works everywhere.**

If a button seems to do nothing, **Settings → About → Button tester** reports the
keycode, device and current binding of anything you press. A code that never
appears there is being taken by the system before Loki sees it.

---

## The pointer

A handheld running Android is always one tap away from something no gamepad can
press — a login form, a store page, an emulator's own settings.

**Hold Start + Select** to raise a cursor you drive with the stick. It is off by
default; turn it on in **Settings → Controls → Pointer**.

| Input | Action |
|---|---|
| Left stick | Move the cursor |
| **Right stick** | Scroll |
| **A** | Click |
| **X** | Long press |
| **B** | Back |
| **Y** | Open the keyboard |
| **L1 / R1** | Scroll a page |

**To use it outside Loki**, enable Loki's accessibility service. That is the only
route an ordinary app has to a cursor that works over other apps — clicking
inside another app needs gesture dispatch, and reading controller buttons while
that app has focus needs key filtering. Both are accessibility APIs.

**Typing into other apps** works too. Tap a text field — a browser's address bar,
a login box — press **Y**, and Loki's keyboard appears on the panel you are
holding while the text goes into the field on the other screen. This needs the
accessibility permission, which allows reading window contents; Loki uses it only
to find the field you tapped and fill it in, and stores nothing. Fields that do
not use standard Android text controls will not accept it.

---

## The keyboard

Loki brings its own on-screen keyboard, because it cannot use Android's. An
Android keyboard is drawn on the screen that owns the focused window, so on this
device it appeared on the wrong panel or never appeared at all.

Loki's is part of the launcher: it renders wherever the grid does, in your theme,
with the same cursor, sounds and haptics as everything else.

| Input | Key |
|---|---|
| D-pad | Move over the keys |
| **A** | Press the key under the cursor |
| **B** | Delete, or close when the field is empty |
| **X** | Space |
| **Y** | Shift — latched for one character |
| **L2 / R2** | Letters ⟷ symbols |
| **Start** | Done |

Every key is also a touch target. Typing on one screen while the field fills in
on the other is what two screens are for.

---

## Your game library

### 1. Add your systems

**Settings → Games & artwork → Platforms**

Loki knows **47 platforms** out of the box, each with its file extensions, accent
colour and scraper ids. Only the systems you add are offered when assigning a
game, so add the ones you own.

Each platform gets the emulator it launches with. **62 emulators** are
recognised, and each is handed a ROM the way that particular emulator expects —
a content URI, a real filesystem path, or an explicit component. An unrecognised
emulator falls back to a generic open request, which works for many but not all.

### 2. Point it at your ROMs

**Settings → Games & artwork → Extra ROM folders**, or per-platform folders on
the Platforms page.

Loki scans the folders you grant, matching files to systems by extension and
looking inside `zip`, `7z`, `rar` and `chd` archives.

Files that vanish are **flagged rather than deleted**, so an unmounted SD card
does not throw away everything known about what was on it.

### 3. Let it scrape

**Settings → Games & artwork → Metadata & scraping**

Scanning, scraping and play-time bookkeeping all run in the background.

### Play time

Launches and play time are recorded per entry and shown on the information
panel. A session opens when you launch something and is credited when you come
back — or when the device sleeps, which on a handheld is how play usually ends.
It survives the launcher being killed mid-game, which is normal when memory is
tight.

---

## Artwork and metadata

Four providers supply details and artwork. They are merged **field by field**,
because none is best at everything, and **anything you edit by hand is never
overwritten** by a later scrape.

| Provider | Supplies | Needs |
|---|---|---|
| **Wikidata** | Developer, publisher, dates, series | **Nothing** — works out of the box |
| **ScreenScraper** | Titles, developer, publisher, genres, dates, artwork | A developer key compiled into the build |
| **SteamGridDB** | Square grid artwork | An API key you enter in settings |
| **RAWG** | Descriptions, genres, credits, ratings, screenshots | An API key you enter in settings |

A provider without credentials is skipped, never fatal. Each entry keeps one
cover plus up to three screenshots, which the information panel rotates through
and the bumpers step between.

**Platform artwork** can also be imported from an icon pack — **Settings → Games
& artwork → Platform artwork**. Loki ships none; which pack you install is up to
you.

---

## Films and shows

Reached from the section bar along the bottom of the grid panel. It browses a
catalogue, finds sources for a title, and plays them.

Browsing needs nothing at all. Playing needs sources, and there are two kinds.

### URL-based addons

**Settings → Films & shows → Sources & accounts → Add a URL-based addon**

Loki speaks the Stremio addon protocol, so any addon that serves streams works.
Paste the addon's install or manifest URL and press **Test** — it asks the addon
for a stream it certainly has, which is the only way to tell a working addon from
a URL that merely looks right.

All the usual URL forms are accepted: a manifest URL copied from a browser, the
`stremio://` link an install button produces, and the configured form that
carries its options in the path.

Loki ships no addons. Which you install is your choice and your responsibility.

### Torrent indexers

**Settings → Films & shows → Sources & accounts → Add an indexer**

Loki speaks **Torznab**, which is what **Jackett**, **Prowlarr** and **NZBHydra**
all expose. It searches these itself — there is no addon in between.

For each indexer you add:

| Field | What to enter |
|---|---|
| **Name** | Whatever you want to call it |
| **URL** | The Torznab endpoint, e.g. `http://192.168.1.10:9117/api/v2.0/indexers/rarbg/results/torznab` |
| **API key** | The key from your Jackett or Prowlarr dashboard |

Then press **Test**, which runs a real search and tells you what came back. An
indexer that is unreachable, misconfigured or wrong about its key says so here
rather than silently returning nothing later.

Indexers on your own network are reached over plain HTTP, which Loki allows
specifically because that is how Jackett and Prowlarr serve by default. Anything
remote — the catalogue, debrid, addons, artwork — is HTTPS.

Which indexers you search is your decision and your responsibility.

### Real-Debrid

**Settings → Films & shows → Sources & accounts → Real-Debrid token**

A debrid account turns a torrent result into an instant stream. Without it,
sources are listed but cannot be opened — which is where most of the reliability
comes from.

Press **Check Real-Debrid** to confirm the token works and the account is active.
A token that is present but expired looks exactly like a working one otherwise,
and the symptom it produces — sources listed, nothing ever opening — points
nowhere near this screen.

### Playback

**Settings → Films & shows → Playback** decides which source is chosen
automatically and how it plays. The player handles progressive files, HLS and
DASH, remembers where you were, and lets you pick a different source without
leaving the title.

---

## Streaming from your PC

Reached from the section bar. Loki finds PCs running **Sunshine** on your
network, pairs with one by PIN, lists what it can stream with box art, and plays
it — video, audio and controller — on a vendored Moonlight core.

1. Install and run [Sunshine](https://github.com/LizardByte/Sunshine) on your PC.
2. Open **Stream** in Loki. PCs on the network appear automatically; you can also
   add one by address.
3. Pick a PC, and enter the PIN it shows into Sunshine's web interface.
4. Choose something to play.

**While a stream is running, the other panel becomes a trackpad and keyboard.**
That is the only way to type into a streamed desktop at all, because Android's
own keyboard cannot render on the second display. Tap the keyboard to give it the
controller; **B** hands the pad back to the game.

**To leave a stream:** press Back, or hold Start, Select, L1 and R1 together.

Resolution, frame rate, bitrate and codec are in **Settings → PC streaming →
Picture**. What your PC is told to call this handheld is under **PCs**.

---

## Themes and appearance

**Fifteen themes** — five dark, five colourful, five light. A theme is far more
than a colour swap: each carries its own accent pair, corner radius, motion
character, font, sound pack, and the way its panels are actually drawn.

| Dark | Colourful | Light |
|---|---|---|
| Material *(default)* | Neon | Daylight |
| Midnight | Cyber | Meridian |
| Obsidian | Ember | Paper |
| OLED | Lagoon | Cherry |
| Slate | Orchid | Sherbet |

Also configurable:

- **Wallpapers** per panel — a picture, an animated effect, or a video preview of
  whatever game is selected. Ten animated styles, including one that takes its
  hue from the highlighted game's system.
- **Corner style** — square, rounded, or each theme's own radius. One answer for
  the whole interface, so nothing is rounded on its own.
- **Cursor** style and idle animation, clock style, text scale, page indicators,
  folder style, icon shape.
- **Sounds and haptics**, with their own volume and intensity.

**If it feels slow:** *Settings → System → Performance* turns the expensive
effects off together — blur, animated wallpapers, video previews and shadows.
Themes degrade rather than break; a glass theme without a blurred backdrop
becomes a tinted one rather than an unreadable transparent sheet.

**Accessibility** — contrast, reduced motion, text size and colour-vision modes —
is under *Settings → System → Accessibility*.

---

## Notifications

With notification access granted, the top screen can show what your device is
notifying about while you play. Loki reports the *grant* and the *connection*
separately, because the grant survives an update and the connection does not — a
page that conflated them would claim to be working while showing nothing.

**Settings → System → Notifications.**

---

## Recording

The shortcut panel's **Record** tile captures both panels into one video, laid
out inside a dual-screen console body, saved to `Movies/Loki`.

Be clear about what this is: **it records the launcher, not the device.** Android
only lets an app capture the default display, so a game running on a panel is
another app's window that this cannot see, and it does not appear. It is useful
for showing the launcher off and no use for capturing gameplay. There is no
audio, and recording ends if Loki's process does.

---

## Settings map

| Category | Pages |
|---|---|
| **Personalization** | Theme, Wallpaper, Interface, Home grid, Selection cursor |
| **Games & artwork** | Platforms, Extra ROM folders, Scanning, Sorting, Metadata & scraping, Platform artwork |
| **Films & shows** | Sources & accounts, Playback |
| **PC streaming** | Picture, Controls, PCs |
| **Controls** | Navigation, Pointer, Feedback |
| **System & accessibility** | Dual screen, Performance, Accessibility, Notifications |
| **About** | Version info, default launcher, replay walkthrough, verbose logging, button tester, reset |

Every row is reachable from the controller.

---

## What it cannot do

Stated plainly, so nothing here is a surprise.

- **Mouse and keyboard while streaming** are partly there — the controller and
  the trackpad work; the full pointer and text paths the protocol also carries
  are not wired.
- **HDR while streaming** is accepted but not applied, so it stays off rather
  than producing a washed-out picture.
- **Controller remapping** has no editor. Two profiles ship and apply live, but
  custom bindings cannot be edited on-device.
- **Cloud sync and backup** are modelled but have no transport.
- **Collections and achievements** have tables but no screens.
- **Smart folders** evaluate correctly but can only be created in code.
- **Plugin framework, theme editor, widget hosting** are not built.
- **Emulator package names drift** between releases; an unrecognised emulator
  falls back to a generic open request.
- **Emulators needing a real filesystem path** can only open ROMs on primary
  shared storage.
- **Once an app has been sent to a panel**, Loki cannot tell whether it is still
  running there — no unprivileged API reports another app's windows. Press Home
  to take the panel back.

---

## Building it yourself

```bash
./gradlew assembleRelease      # release APK
./gradlew test                 # unit tests
./gradlew :app:installDebug    # install to a connected device
```

Needs JDK 17, and the NDK plus CMake for the streaming core:

```bash
sdkmanager "ndk;27.0.12077973" "cmake;3.22.1"
```

Optional ScreenScraper developer credentials go in `local.properties`:

```properties
thor.screenscraper.devId=yourDevId
thor.screenscraper.devPassword=yourDevPassword
```

Without them ScreenScraper reports itself unconfigured and is skipped; the other
three providers still work.

**How it is built and why** — the dual-screen architecture, the focus rules and
the invariants behind them — is documented in [docs/DESIGN.md](docs/DESIGN.md).

---

## Licence

**GNU General Public License v3.0** — see [LICENSE](LICENSE).

Chosen rather than defaulted to: the streaming section is built on
[Moonlight](https://github.com/moonlight-stream/moonlight-android), which is
GPL-3.0, and GPL is copyleft. Anyone given a Loki build is entitled to its
complete source under the same licence, and may modify and redistribute it.
Building it for yourself carries no obligation at all — the terms attach to
distribution.

Loki is not affiliated with AYN, Valve, Nintendo, Sony, Microsoft, Stremio,
Real-Debrid, or any emulator or indexer project.
