# THOR

> **Superseded.** This is the original design document, written while the
> launcher was still called THOR. It describes an earlier state of the project —
> the status section below counts 103 tests across 14 test classes, and both
> numbers have long since moved — and it has not been kept in step with the
> code. [README.md](../README.md) is the current description; where the two
> disagree, the README is right and this file is history.

A dual-screen Android launcher built for the **AYN Thor** handheld.

The Thor has two panels. THOR treats that as the point rather than as a
complication: the panel you are holding the controller for shows a sparse,
page-based icon grid in the spirit of the 3DS HOME Menu, and the other panel
shows an information surface that reacts to whatever the cursor is sitting on —
box art, screenshots, developer, release year, play time. Both are driven from
one state holder, so they can never disagree, and either panel can become the
interactive one when an app takes the other.

It replaces the system launcher: `CATEGORY_HOME`, controller-first navigation, a
ROM scanner, an emulator launcher, and a metadata scraper.

---

## Status

Builds and runs. `./gradlew assembleDebug assembleRelease test lintVitalRelease`
is green, with no compiler warnings and 103 unit tests across 14 test classes.

It is a working launcher, not a finished product. Read
[What is not built](#what-is-not-built) before planning around it.

---

## What it does

### The two panels

| | |
|---|---|
| **Grid panel** | Icon grid, bottom nav bar, Start panel, app drawer, context menus, the shortcut panel and the keyboard |
| **Info panel** | Detail view for the selected entry, clock, status bar, animated or video background; hosts settings, search and the entry editor |

Which physical panel gets which is a setting (**Display → Swap screens**), and the
*same composables* render in either window — the second panel is not a parallel
implementation.

Three layouts are resolved against the hardware actually attached:

| Mode | Behaviour |
|---|---|
| `DUAL_DISPLAY` | Info panel in the activity's window; grid projected onto the second panel through `Presentation` |
| `SPLIT_SINGLE` | One display split into two stacked surfaces — the fallback, and what makes the launcher testable on an ordinary phone |
| `SINGLE` | Grid only; the info surface's overlays are raised over it |

`AUTO` picks the first when a second panel exists and the second when it does not.

### Running an app on one panel and the launcher on the other

This is the behaviour the dual-screen model exists for, and it is worth stating
exactly:

- Launching an entry sends it to the **grid's home panel** — the one your display
  settings assign the grid to. That assignment is fixed; it never follows the
  launcher around.
- The grid stands down with its panel, and the info panel carries on looking the
  way it always does. Home brings the grid back.
- Launching onto the **other** panel — from an entry's context menu — leaves the
  grid where it is, still navigable, while the app covers the info panel.
- Touching a panel gives the controller to whatever is on it. Tap the game to
  play, tap the launcher to browse, in either direction, at any time.
- **Home** is what gives a panel back to the launcher, from either display — see
  [Home is per display](#home-is-per-display). Nothing else does: every other
  candidate signal (window focus, top-resumed status) is also produced by simply
  touching the launcher while you are still playing, and acting on those is what used
  to evict running apps.

### The grid

Placement is **explicit and sparse**: an entry occupies the cell you put it in,
and an empty cell stays empty. That is a deliberate departure from the usual
"flow items into a list" launcher grid, and it is what makes rearranging feel like
a console rather than like an app drawer.

- A page is a fixed `columns × rows` matrix, so library size affects the *number of
  pages*, never the amount of composed UI. Ten thousand games cost the same per
  frame as ten.
- Pinch snaps between **eight layout presets**, from 3×2 ("Huge") to 8×5
  ("Densest"), each carrying its own spacing and page padding so no density ever
  reads as crowded.
- Placements survive rescans. Entry ids are derived from stable facts
  (`app:<package>:<user>`, `game:<platform>:<normalised-title>`) rather than being
  random, so reinstalling an app or moving a ROM keeps its cell.
- Placements carry no foreign key — they can point at an app, a game, a folder or a
  shortcut — so orphans are pruned after every scan.
- **Edit mode** picks an icon up, moves it, and drops it. Dropping onto an occupied
  cell picks up the displaced entry so it can be re-homed, rather than silently
  swapping.
- **Folders** hold entries, scrape their own artwork, and can be created from the
  Start panel or from any entry's context menu. Smart folders evaluate a live query
  against the library.
- **Scanned games are filed into a folder per platform**, not scattered across pages.
  Adding a system can bring in hundreds of ROMs at once, and the grid gains one cell
  for it rather than one per game. Only games with no placement are filed, and each
  folder's id is derived from its platform — so anything you move out, rearrange or
  rename stays that way through every later scan.
- Icons take one of five shapes (square, rounded, squircle, circle, hexagon), and
  the selection cursor traces the shape the cell actually has.

### Sections

The bottom of the grid panel is a **three-tab nav bar** — Stream, Home, Movies —
with Home in the middle because it is the one you return to constantly and the
centre of the bottom edge is where a thumb already rests.

It is reachable both ways, which is the point. Touch taps a tab; the controller
walks into the bar by pressing Down past the bottom row of the grid, moves with
Left and Right, commits with A, and leaves upward with Up or B. Moving is not
selecting — you cross the bar to look before you press, the same as in the theme
gallery — so holding Right does not tear down and rebuild three sections on the way.

**Both sections are real.** Movies browses, resolves sources and plays them.
Stream finds PCs on the network, pairs with Sunshine by PIN, lists what each one
can stream with its box art, and plays it — video, audio and controller — on
[the vendored Moonlight core](core/moonlight/).

Both are themed, navigable, focus-managed, and treated by the bar exactly as Home
is. Where something is missing, the screen names it — a blank page is
indistinguishable from one whose content failed to load.

The **dock** it replaces is hidden rather than deleted. Its five assignable action
slots, their placements, and its settings page all still work behind
`LauncherFeatures.DOCK_ENABLED`; the nav bar steps aside if it is switched back on.
Only one of the two can own the bottom edge of a panel this size, and a section
switcher earns it over five actions that had somewhere else to live — and that,
unlike the dock, the controller could always reach.

### Controls

`ControllerInputRouter` turns raw key and motion events into logical commands.
Both of the launcher's windows dispatch into the same router, so whichever panel
holds focus drives one cursor.

| Input | Action |
|---|---|
| D-pad / left stick / hat | Move the cursor |
| A | Launch — hold to pick the icon up |
| B | Back / close |
| Y | Context menu |
| X | Toggle favourite |
| L1 / R1 | Previous / next screenshot for the selected game |
| L2 / R2 | Previous / next page |
| Stick click (L3 / R3) | Shortcut panel |
| Start | Start panel |
| Select | App drawer |
| Guide / Home | Home |
| Triggers held | Accelerates whatever else you press |
| W A S D, E, F, Tab, Enter, Esc | Keyboard equivalents, so the launcher is fully operable from a paired keyboard or an emulator |

Three details make navigation feel right, and all three live in the router rather
than in the UI:

- **Custom auto-repeat.** Android's key repeat rate is a system setting and far too
  slow for grid navigation, so held directions are re-emitted on the profile's own
  schedule and system repeats are swallowed.
- **Long-press promotion.** Confirm dispatches on *release*, because holding it
  means "pick up this icon". The press is deferred, never duplicated.
- **Analog edge detection.** A stick past the dead zone produces one press then
  repeats, not a flood at the sensor's sample rate. The dominant axis wins, so a
  diagonal push yields one clean direction.

Routing suspends only while a *platform* IME is up — a system dialog over the
launcher — because nothing at that level can tell a gamepad press from a keyboard
press. THOR's own fields never suspend it: they are typed into by THOR's keyboard,
which this router drives.

**The shortcut panel** is a quick-access sheet over the grid — app drawer, search,
THOR settings, swap screens, rescan, screen recording, Wi-Fi, Bluetooth, volume and
Android settings.
It deliberately offers only things an unprivileged app can genuinely do; brightness,
rotation and the notification shade need permissions a launcher cannot hold, so a
tile for them could only pretend.

**The AYN button.** Its firmware reports no vendor keycode. A short press never
reaches the launcher, and a long press injects `SHIFT_RIGHT` from a virtual
device, which THOR binds to the shortcut panel. The same press also powers the
bottom panel off — that is firmware behaviour on a button the vendor owns, and no
app-level launcher can intercept or countermand it. The stick clicks are the
binding that works everywhere.

**System → Diagnostics** ships a button tester that reports the keycode, device
name and current binding of anything you press. A code that never appears there is
being consumed above the launcher and is unreachable from an app.

### The keyboard

THOR has its own on-screen keyboard, because it cannot use the platform's. An IME is
drawn by the system on the display that owns the focused window, and the launcher's
interactive surface is a `Presentation` on the second panel — so the keyboard came
up on the wrong screen or, in practice, never appeared at all. A keyboard that is
simply part of the launcher's composition has no such problem: it renders wherever
the grid renders, in the current theme, with the same cursor, sounds and haptics as
every other surface.

It is an **input method, not a search box**. Every text field in the launcher —
search, the settings keys and account names, the entry editor — is a `ThorInputField`
rather than a platform one. Tapping or confirming a field claims THOR's own text
focus, which raises the keyboard square across the bottom of the panel you are
holding; what you type fills the field in live on whichever panel *it* is drawn on.
Typing on one screen while the thing you are filling in updates on the other is what
two screens are for.

That indirection is forced by the hardware. Platform focus and the IME both follow
the *window*, so a field on one panel and a keyboard on the other cannot be connected
by the platform at all. Here they are connected by state: a field says it is the one
being typed into, the keyboard edits a buffer, and whatever holds focus receives every
keystroke.

The layout is the familiar phone one — three QWERTY rows with shift and backspace
flanking `zxcvbnm`, then `?123 , space . ⏎` along the bottom — because that is the
arrangement thumbs already know.

| Input | Key |
|---|---|
| D-pad | Move over the keys — rows wrap top to bottom, columns clamp inside a row |
| A | Press the key under the cursor |
| B | Delete, or close when the field is already empty |
| X | Space |
| Y | Shift — latched for one character, as a phone's is |
| L2 / R2 | Switch between letters and symbols |
| Start | Done — closes the keyboard and hands the controller on |

Every key is also a touch target, including shift and the layer switch, because the
panel is a touchscreen and a keyboard whose only shift is a shoulder button would be
unusable with a thumb.

**Search** and the **dock's keyboard slot** open the search screen, which claims text
focus as it appears, so the keyboard comes up with it — and its results stay empty
until you type. Nothing else raises it: a keyboard needs a field to type into, and
only a field can say which one.

It types into THOR's own fields only, and the dock slot dismisses it again. Typing
into *another app* is not something a launcher can do from its own window — that needs
a system IME, which Android draws on the display of the app being typed into rather
than on the other panel.

### Library

- **Apps** are enumerated through `LauncherApps`, including work-profile entries.
- **ROMs** are scanned from directories you grant through the storage access
  framework, matched to platforms by file extension, with archive containers
  (`zip`, `7z`, `rar`, `chd`) handled across all of them.
- **25 built-in platforms**, each with its maker, year, accent colour, file
  extensions and scraper ids.
- **38 emulator specs** map a platform to an installed emulator and to the way that
  particular emulator expects to be handed a ROM — content URI, real filesystem
  path, or an explicit component. Unrecognised emulators fall back to a generic
  `VIEW` intent.
- Scanning, metadata fetching and playtime settling run as WorkManager jobs.
- Play time and launch counts are recorded per entry; a session opens on launch and
  is credited when the launcher comes back, including after being killed mid-game.

### Metadata and artwork

Providers are deliberately narrow: they search, and they return candidates.
Ranking, merging and conflict resolution happen once, in `MetadataAggregator`. The
merge is **field-by-field**, because no single provider is best at everything. Two
hard rules apply: a candidate below the confidence floor contributes *nothing*,
and a field you have edited by hand is never overwritten.

| Provider | Supplies | Credentials |
|---|---|---|
| **ScreenScraper** | Titles, developer, publisher, genres, dates, artwork | Developer pair compiled into the build (below); a user account is optional and only raises quota |
| **SteamGridDB** | Artwork only — 1:1 grid art for cells | API key, entered in settings |
| **Wikidata** | Developer, publisher, dates, series | **None** — works on a fresh install |
| **RAWG** | Descriptions, genres, credits, ratings, screenshots | API key, entered in settings |

Grid cells use true square box art rather than icons, and each entry keeps one
cover plus up to three screenshots, which the info panel rotates through (or you
cycle with the bumpers). A provider without credentials is skipped, never fatal.

### Themes and appearance

**20 bundled themes** (16 dark, 4 light), including Steam, PlayStation, Xbox,
Switch and 3DS presets.

A theme is more than a colour swap, and the part that carries most of that is the
**surface treatment**: how a panel is actually drawn, rather than what colour it
is. Each theme declares one — a style (flat, raised, tinted or glass) plus the
measurements behind it: border weight and opacity, the brightness of the lit top
edge, shadow depth, and how far the accent tints each step up the elevation ramp.

That is what separates the presets. A Switch panel is an opaque card with a real
shadow under it; a Vision panel is a thin lit sheet; a Retro panel is a flat
rectangle with a hard outline and no depth at all. With only a radius and an alpha
to describe them, all three came out as the same rounded box in different colours —
which is why themes with their effects turned off used to be nearly
indistinguishable from one another.

Every panel in the launcher goes through one modifier, `Modifier.thorSurface`, so a
component gets its theme's treatment without knowing any of them exist. Themes also
carry a **background depth**: a wash graduating the base toward the accent down the
panel, so a theme still reads as itself with every effect switched off.

Treatments degrade rather than break. Where the backdrop cannot be blurred — pre-API
31, or performance mode — a glass treatment falls back to a tinted one and dials its
specular edge back, because a lit edge over an unblurred background reads as a
rendering fault; and shadows go entirely in performance mode, being the one part
that costs a render pass per panel. Each theme also carries its own accent pair,
corner radius, motion character, font and sound pack.

Theme resolution folds user overrides, accessibility settings and performance mode
together in one place. Notably, when blur is unavailable — pre-API 31, or
performance mode — surface alpha is pushed toward opaque, because a glass theme
without a blurred backdrop is just an unreadable transparent sheet.

Also configurable: wallpapers per panel (static, animated, or video previews
behind the selected game), cursor style and idle animation, clock style, text
scale, page indicators, and a performance mode that turns the expensive effects
off together.

### Recording

The shortcut panel's **Record** tile captures both panels into one video, laid out
inside a dual-screen console body, saved to `Movies/THOR`.

It is not a screen recorder, and it could not be one. `MediaProjection` — what every
screen recorder uses — captures the *default* display and only that one; there is no
public API to point it at a secondary panel, so on this hardware it can never see the
bottom screen. So the launcher does not capture a screen at all: it creates a private
`VirtualDisplay` whose output is the video encoder's input surface, and **draws itself
a third time** onto it — the same two surfaces the panels show, from the same state,
inside the mock-up.

The consequence is worth stating: this records **the launcher**, not the device. A game
running on a panel is another app's window on a display this cannot see, so it does not
appear — which is fine for showing the launcher off, and no use for capturing gameplay.
There is no audio, and the recording ends if the launcher's process does.

### Start-up

A cold start plays a short intro: an accent hairline opens from the centre, the THOR
bolt lands on a chime with the wordmark settling under it, and the launcher is
revealed. One animation drives **both panels**, so the two windows run it as a single
thing rather than as two that happen to look alike.

Once per *process*, which is the point — the state lives in the view model, so it
survives rotation and display changes and dies with the process. A launcher is
returned to dozens of times a day, and pressing Home must never replay it. Any button
or a tap skips it, and reduced-motion or performance mode collapses it to a brief
fade.

### Feedback

19 bundled UI samples — navigate, confirm, back, pick up, drop, page, folder,
drawer, launch, error, success, start-up — plus haptics, both with their own volume and
intensity settings. Sounds are routed as media rather than as system
sonification, because the sonification stream is muted on this device.

### Settings

17 pages under six categories, all navigable from the controller:

| Category | Pages |
|---|---|
| **Appearance** | Theme, Wallpaper, Grid, Dock, Cursor, Interface |
| **Library** | Platforms, Extra ROM directories, Scanning, Metadata & providers, Sorting |
| **Controls** | Navigation, Feedback |
| **Display** | Dual screen, Performance |
| **System** | Accessibility, Diagnostics |
| **About** | Version and device information |

---

## Getting started

### Requirements

| | |
|---|---|
| JDK | 17 |
| Gradle | 8.11.1 (via the wrapper) |
| Android Gradle Plugin | 8.9.1 |
| Kotlin | 2.1.20 |
| compileSdk / targetSdk | 35 |
| minSdk | 29 |
| NDK | 27.0.12077973 |
| CMake | 3.22.1 |

The NDK and CMake are for [`core:moonlight`](core/moonlight/), the vendored
GameStream core the Stream section is built on. Install them with:

```
sdkmanager "ndk;27.0.12077973" "cmake;3.22.1"
```

### `local.properties`

```properties
sdk.dir=/path/to/Android/Sdk

# Optional: ScreenScraper developer credentials.
# Register at https://www.screenscraper.fr and request a developer key.
# These are read at build time into BuildConfig so they never enter source
# control. Without them the ScreenScraper provider reports itself unconfigured
# and is skipped; the other three providers still work.
thor.screenscraper.devId=yourDevId
thor.screenscraper.devPassword=yourDevPassword
```

They can also be passed as Gradle properties (`-Pthor.screenscraper.devId=…`) in CI.

### Building

```bash
./gradlew assembleDebug          # debug APK
./gradlew assembleRelease        # release APK, R8 + resource shrinking
./gradlew test                   # unit tests
./gradlew lintVitalRelease       # release-blocking lint
./gradlew :app:installDebug      # install to a connected device
```

### First run

1. Install, then set THOR as the default launcher — **Settings → Apps → Default
   apps → Home app**.
2. **Library → Extra ROM directories** to grant your ROM folders.
3. **Library → Platforms** to add the systems you own; only added platforms are
   offered when assigning a game.
4. **Library → Metadata & providers** for any API keys you have. Nothing is
   required — Wikidata needs no key.
5. If the two surfaces appear on the wrong panels, **Display → Swap screens**.

---

## Architecture

Fourteen Gradle modules, wired by convention plugins in `build-logic` so SDK
levels, Compose setup and annotation processing are declared exactly once.

```
app                     Activity, manifest, dual-screen wiring, DI root
├── feature/home        Grid, dock, drawer, shortcut panel, launcher state
├── feature/topscreen   Info panel: game / folder / app detail views
├── feature/settings    Settings overlay (17 pages)
├── feature/search      Global search
├── data                Repositories, scanners, launching, metadata providers, sync
└── core/
    ├── model           Pure-Kotlin domain types (no Android dependencies)
    ├── common          Dispatchers, logging, title normalisation
    ├── database        Room entities, DAOs, migrations
    ├── datastore       Serialised user settings
    ├── designsystem    Theme engine, motion, materials, modifiers
    ├── display         Display topology and the secondary-screen presentation
    ├── input           Controller mapping and the input router
    └── ui              Shared Compose primitives, artwork loading, feedback
```

Dependencies point downward only. `:core:model` is a plain JVM module, which keeps
the domain types free of Android imports and instantly unit-testable.

**Stack:** Kotlin 2.1, Compose with Material 3, MVVM with a single state holder per
surface, Hilt, Room (11 entities, schema version 2, hand-written migrations),
DataStore with kotlinx.serialization, WorkManager, OkHttp, Coil, Media3.

### The second window is independent

The launcher's second panel is a `Presentation`, and the single most damaging thing
that can be done to it is to hang it off the activity. It used to borrow the
activity's lifecycle owner *and* compose as a subcomposition of the activity's, which
meant that when an app opened on the activity's **own** display and covered it:

- the activity reached `STOPPED`, so every `collectAsStateWithLifecycle` feeding the
  second panel stopped collecting, and
- the activity's `Recomposer` paused its frame clock, so the second panel stopped
  producing frames at all.

The second screen therefore froze solid the moment anything opened on the first one,
and only recovered when the launcher came back. On a device whose entire point is
running something on one screen while using the other, that is not a bug to work
around — it is the wrong ownership. So:

- the presentation owns its **own lifecycle and saved-state registry**, resumed while
  it is showing and destroyed when it is dismissed;
- it runs its **own recomposer**, which is why the caller re-provides the theme and
  composition locals on that side of the boundary;
- it shares only the activity's **`ViewModelStore`**, because the two panels are one
  launcher reading one state object; and
- the shell collects state **without** the lifecycle, since a launcher's state is
  wanted for as long as either of its windows exists.

### One state, two compositions

Sharing the activity's `ViewModelStore` is what lets both panels read one
`LauncherViewModel` with no cross-window message passing to fall behind. The state
objects themselves are snapshots, and snapshots do not care which composition reads
them — so a selection change invalidates both panels even though each is recomposed
by its own recomposer.

The presentation also has to be handed `LocalActivityResultRegistryOwner` and
`LocalOnBackPressedDispatcherOwner` explicitly, because the default lookup walks
`LocalContext` — and a presentation's context comes from `createDisplayContext`, which
does not wrap the activity.

### Focus

One rule, derived rather than tracked, because tracking it in three places is what
made it unreliable:

- The **active surface** is whatever the user is working on: an open overlay if there
  is one, otherwise the surface last touched.
- The **window holding that surface** is the one that takes key focus, and the
  controller goes to it exclusively. The other panel keeps running and ignores the
  pad until it is touched.
- **Overlays claim focus by existing.** The keyboard, settings, search, the entry
  editor — each is drawn on a known surface, so opening one moves the controller
  there with no handover to arrange, and closing one returns it to the last touched
  surface with no "previous focus" to keep in step.
- **A launch yields the claim**, so the app arriving on a display wins focus rather
  than fighting the launcher panel the user touched to start it. The next touch takes
  it straight back.
- **So does losing focus while asking for it.** The launcher sees touches that land
  on its own surfaces and none that land on an app, so reaching for a running app was
  invisible to it and it kept holding the pad. A window losing focus it had requested
  is the missing signal — evidence that something else was given focus rather than a
  guess about why — and the launcher stands down on it.
- **The info panel is a focus target in its own right.** It has no cursor, so taking
  the controller there draws an edge and the buttons act on the entry it is showing:
  left and right change screenshot, A launches it, B hands the pad back to the grid.
  A panel that can take focus and then do nothing with it is indistinguishable from
  one that never took it.

The derivation is done during composition, where those values are current, and read
into the input router through `rememberUpdatedState`. The router's collector is
started once and keyed on the router alone, so it holds whatever its lambda captured:
snapshot delegates stay live because the closure captures the holder, but a plain
derived `val` freezes at its first-composition value. The active surface was one, and
froze on `BOTTOM` at the first frame — so touch moved window focus, correctly, while
input went to the grid regardless.

### Home is per display

Android delivers `CATEGORY_HOME` to the **focused display's** home activity. A
launcher that declares only a primary home never hears a Home press made while the
second panel is focused — the system starts its own default launcher on that panel
instead, which is the stock home screen appearing where THOR should be, with no way
to tell THOR that Home was pressed at all.

So THOR declares a `SECONDARY_HOME` activity as well. It draws nothing and does
nothing but report the press, so the running launcher can take its panel back — and
because it stays, it is also what sits behind THOR's own presentation on that display:
when the presentation stands down for a launched app, what is uncovered is a black
panel belonging to THOR rather than somebody else's home screen.

It is a separate activity on purpose. `LauncherActivity` is `singleTask`, so claiming
the role there would have the system move the launcher's one instance onto the second
display.

Two things about it are load-bearing, and both were wrong.

**It listens for `CATEGORY_SECONDARY_HOME`, not `CATEGORY_HOME`.** Android does not
deliver the same intent to the two home roles: a press on the default display resolves
through `getHomeIntent()` and carries `CATEGORY_HOME`, while a press on a secondary
display resolves through `getSecondaryHomeIntent()` and carries
`CATEGORY_SECONDARY_HOME`. This activity is registered for the second and receives
only the second, so the original check for `CATEGORY_HOME` could never once be true.
The press was made, the activity was started, and the running launcher was never told
— which from the front is a Home button that does nothing on one of the two screens.

**It is `singleTop`, not `singleTask`.** `singleTask` means "be the root of the task
with my affinity", and with no explicit affinity this inherited the package's — the
one `LauncherActivity` already roots on the *default* display. Two `singleTask`
activities competing for one affinity is not something the system can satisfy across
two displays, and a secondary home it cannot place in the right display's home task is
one it gives up on, falling back to the platform's own: the stock home screen, on the
panel THOR should own. `singleTop` does no affinity-based task reuse, so the system
places it wherever it is starting the home task, and a repeated press still arrives at
`onNewIntent` because a home activity is already at the top of its task. This matches
AOSP's own `SecondaryDisplayLauncher`.

### Hard-won invariants

These are the rules that stop the two-window design from failing in ways that each
took several attempts to diagnose. Breaking one of them looks like "the launcher
froze" or "the Android launcher appeared on my other screen":

- **The second window's lifetime is its own.** Hanging it off the activity's
  lifecycle or composition freezes it whenever anything opens on the *other* display.
  See [The second window is independent](#the-second-window-is-independent).
- **Nothing the second window depends on may be a value computed in the activity's
  composition.** This is the same failure one level up, and it outlived the fix
  above. Compose pauses a composition's frame clock at `STOPPED`, so while an app
  covers the activity's display that composition computes nothing further — and the
  second window's *content* was fine, because it reads snapshot state through its own
  recomposer, while the second window's **focusability** was a `Boolean` parameter
  derived up there. It froze at whatever it was when the app launched. The panel
  stayed lit, kept animating, received touches, wrote its state — and the derivation
  that would have acted on them never ran again, so the screen in the user's hands
  answered no button until Home brought the activity back. Presence and focus are now
  passed as lambdas and applied from a `snapshotFlow` on a coroutine, which keeps
  running when recomposition does not. Anything else that has to work while an app is
  on the other display has the same requirement.
- **A launch stands down only the panel it is arriving on.** Standing the second
  panel down for an app opening on the activity's display gave away the controller to
  a window that was not competing for it — which is the freeze above arriving by a
  second route. `LauncherEffect.Launched` carries the panel; `LauncherFocus` decides.
- **The panel is handed over before the app is started, not after.** A `Presentation`
  sits above application windows, so starting an app first means it arrives
  *underneath* a window that is still there, with the window manager deciding focus
  between the two.
- **The launch display id comes from the shell**, not from a second guess at which
  display is the secondary one. Any extra display the system reports — a recorder, a
  cast target, a vendor overlay — makes those two different answers, and the app goes
  where nobody is looking.
- **The presentation stands down only while an app occupies its panel** —
  identified by the launch that put it there, never inferred from focus or
  top-resumed status. Dismissing it on pause instead uncovers whatever the system
  keeps behind it on that display, which is its own default launcher.
- **Launch targets are explicit.** `LaunchTarget.DEFAULT` resolves to the
  *activity's* display, which is not necessarily where the grid is; that is how an
  app once opened behind the grid with input going to it.
- **System panels open on the main panel only**, for the same reason — an activity
  sent to the secondary panel renders behind the presentation.
- **Resume, not focus, means an app exited.** Window focus and top-resumed status
  are both handed over by touching the launcher's other panel, which is exactly
  what the user does *while still playing*.

### Testing

```bash
./gradlew test
```

103 tests over the logic where a mistake is silent rather than loud: grid geometry,
spacing and pinch presets, playback state, artwork sets, theme ramps, ROM title
normalisation, scraper match confidence, display-mode resolution, settings
migration, Room migrations, cursor movement over the shortcut panel and the
on-screen keyboard, and **which window holds the controller** — the last of those
being the rule this design has had to re-derive most often, and the one whose
failures are hardest to see, because a launcher that is drawn correctly and answers
no button looks exactly like one that is working.

---

## What is not built

Stated plainly, because the original specification is far larger than what is
here. Nothing below is stubbed or faked — it is simply absent.

- **Mouse and keyboard input while streaming.** The controller is wired; the
  pointer and text paths the protocol also carries are not, so a streamed desktop
  can be watched but not driven.
- **Stream quality settings UI.** Resolution, frame rate and bitrate are modelled
  and used, but can only be changed in code — there is no screen for them.
- **HDR while streaming.** Announced by hosts that support it and accepted, but
  the metadata is not applied, so it stays off rather than producing a washed-out
  picture.
- **Controller remapping UI.** The profile model supports custom bindings and ships
  two profiles (default, swapped A/B), and they are applied live from settings —
  but there is no screen to edit them or to switch between them. The button tester
  is the only controls diagnostic.
- **Cloud sync and WebDAV backup**, and backup/restore execution. The settings
  model exists; there is no transport.
- **Plugin framework, theme editor, icon packs, widget hosting.**
- **Collections UI.** Tables and DAOs exist; no screen.
- **Achievements.** Fields and tables exist; the RetroAchievements client was
  removed rather than left enabled and empty, because an enabled provider with no
  client is indistinguishable, from the grid, from one that found no match.
- **Smart-folder editor.** Evaluation works; queries must be created in code.
- **Further metadata providers** (IGDB, MobyGames, LaunchBox). The provider
  interface and DI multibinding make each a self-contained addition.
- **Manual downloading and viewing.**
- **Instrumented UI tests**, and screen-reader labelling is incomplete across the
  settings panes.

### Known limitations

- Emulator package and activity names drift between releases; an unrecognised
  emulator falls back to a generic `VIEW` intent, which works for many but not all.
- Emulators that require a real filesystem path can only open ROMs on primary
  shared storage.
- Free-form touch dragging with live reflow is not implemented; rearranging is
  cursor pick-up/drop and long-press.
- The platform soft keyboard does not render on the secondary display, which is why
  [the launcher ships its own](#the-keyboard). The one surface still using real text
  fields is the entry editor, which is why it lives on the activity's own window —
  the one display where an IME does appear.
- If the second panel is not exposed as a presentation display, `SPLIT_SINGLE` is
  the working fallback.
- Once an app has been sent to a panel, the launcher cannot tell whether it is
  still running there — no unprivileged API reports another app's windows. Press
  Home to take the panel back.

---

## Licence

**GNU General Public License v3.0** — see [LICENSE](LICENSE).

Chosen rather than defaulted to. The Stream section is built on
[Moonlight](https://github.com/moonlight-stream/moonlight-android), whose client
and its `moonlight-common-c` core are both GPL-3.0, and GPL is copyleft: linking
it in makes the combined work GPL-3.0 too. That is the whole of the reasoning,
and it was a decision rather than an accident, because it is not reversible once
a build has been shared.

What it means in practice:

- Anyone given a THOR build is entitled to its complete source, under the same
  licence, and may modify and redistribute it.
- THOR cannot later be relicensed as closed source while it contains GPL code.
- Building it for yourself and never distributing it carries no obligation at
  all; the terms attach to distribution.

The rest of the dependency tree — AndroidX, Compose, OkHttp, Coil, Media3 — is
Apache 2.0, which is permissive and imposes nothing beyond keeping the notices.
