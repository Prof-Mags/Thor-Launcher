<div align="center">

<img src="docs/banner.png" alt="Loki" width="880">

# Loki for the AYN Thor

Loki is an all-in-one launcher built exclusively for the AYN Thor, designed to solve problems instead of creating them. Every part of the experience has been built with performance, stability and ease of use in mind, delivering a fast, lightweight interface that takes full advantage of the Thor's unique dual-screen hardware.

Unlike traditional front-ends, Loki goes far beyond simply launching games. Stream films and shows directly from within the launcher, connect to your PC or other devices over your local network for game streaming, and switch seamlessly into a dedicated Couch Mode for a controller-focused living-room experience. Everything is integrated into a single, cohesive experience, so you never have to leave the launcher.

Loki features complete controller support across the entire interface, extensive customisation, modern dual-screen layouts, built-in media streaming, game streaming, and a growing collection of features designed specifically for the AYN Thor. The launcher is built to be extremely lightweight, highly optimised and responsive, ensuring smooth performance without unnecessary overhead.

One thing worth mentioning is that Loki was never intended to recreate the look or feel of the Nintendo 3DS. If you are looking for a launcher that closely resembles the 3DS interface, Cocoon is an excellent choice. Loki instead embraces a modern design philosophy with its own identity, focusing on usability, performance and innovation rather than copying the appearance of another system.

The goal of Loki is simple: create the most polished, feature-rich and reliable launcher available for the AYN Thor while remaining fast, intuitive and built from the ground up for the hardware it runs on.

<br>

**[Download](https://github.com/Prof-Mags/Thor-Launcher/releases)** · Android 10+ · GPL-3.0

<br>

</div>

<div align="center">

## Getting started

Download the APK from [Releases](https://github.com/Prof-Mags/Thor-Launcher/releases), install it, open it once, and set it as your home app when Loki offers. Your games, saves and emulators are untouched, and you can uninstall at any time without losing any of them.

A short intro plays the first time and once ever, not on every restart. Loki then shows you what it would like permission for, and none of it is required: setting Loki as the home app is what makes it open when you press Home, and granting the accessibility permission is what turns on the pointer and lets Loki type into other apps. Declining either leaves a launcher that still works, with those two features switched off.

A walkthrough then takes you through everything the launcher does, moving across both panels as it goes — a step about a settings category opens that category on the information panel while the step's own card stays on the grid panel beside you. Replay it any time from **Settings → About → Replay the walkthrough**. Importing an extension later plays a short walkthrough of just the part you added.

<br>

</div>

<div align="center">

## Two screens, one launcher

One panel holds the grid: your icons, your menus, the section bar along the bottom edge. The other holds information: artwork, details, ratings, genres, release dates, developer and publisher, screenshots and play time for whatever the cursor is currently on. Which panel is which is yours to choose in **Settings → System & accessibility → Dual screen**, and swapping them is also one of the tiles in the shortcut panel.

Launch a game and it takes the grid's panel, while the other panel stays on the launcher. Touch a panel to give it the controller — tap the game to play, tap the launcher to browse, any time you like. Pressing Home gives a panel back to Loki from either screen, because Loki registers a home activity on both displays rather than only on the default one. An entry's context menu can send it to the other panel instead, leaving the grid where it is, so you can decide per launch which screen a game lands on.

Five screen modes are available. Automatic uses the second display when there is one. Dual display forces it. Split single display divides one screen into a top and bottom half, which is the fallback when only one display is present. Single screen uses the bottom surface alone and turns the information panel into an overlay sheet. Couch mode moves everything to the top screen and holds the bottom panel dark.

Both screens come back correctly from sleep, and every surface is drawn against a fixed canvas rather than the screen's raw density, so the interface holds its proportions across Smallest Width, resolution and density changes.

<br>

### Couch mode

Couch mode is for the Thor sitting in a dock with the controller in your hands rather than the device, and it is a different interface rather than the handheld one rearranged. Everything moves to the top screen and the bottom panel goes dark — not dimmed, and not left showing a stale grid, because Loki holds that panel black so the system cannot light it with its own wallpaper.

The dashboard is built from horizontal content rails rather than a grid, so there are no empty cells: continue playing, favourites, the selected platform, apps and your own collections each get a shelf that sizes itself to the cards on it. LT and RT switch systems in place and immediately replace the game shelf without opening the generated platform folders. Sections run along the top, where a television puts them, giving controller-first navigation between Home, Movies, Shows, Stream and Settings.

The focused title crossfades into cinematic backdrop drift, platform-coloured ambience and scraped logo treatment, and games get a full page of their own rather than a cramped sheet, with a long press raising one menu instead of several. The header carries the configured clock and live battery status without duplicating any polling logic. Films and shows are split into separate tabs, each a proper catalogue with category rails, and both Movies and PC streaming switch to dedicated single-screen dashboards so the darkened handheld panel is never needed to finish an action.

Eight backgrounds are drawn specifically for a television across a room rather than a panel at arm's length: Ridges, Aurora, Drift, Horizon, Embers, Pulse, a setting that matches the launcher's own wallpaper, and a plain solid colour. All of them are drawn rather than decoded, so none costs memory or a decode on a screen left open, and each is tinted by the highlighted system's accent so the room shifts as the cursor crosses from one console to another.

Couch mode builds its rails directly from the library and caches its platform shelves, so switching systems never walks the handheld grid, and handheld density never leaks into the television interface. Nothing is taken away either — the app drawer, menus, keyboard, search and settings all work exactly as they do on the handheld layout. Couch UI size is adjustable from 75% to 125% with touch or the controller, so the television interface can be tuned for viewing distance without changing the handheld layout at all.

<br>

</div>

<div align="center">

## The grid

Every icon stays where you put it. Empty cells stay empty, and nothing reflows. Placements survive rescans, so moving a ROM or reinstalling an app keeps its cell.

Hold **A** to pick an icon up, move it, and press **A** again to drop it. Pinch to change density between eight presets running from three columns by two rows up to eight by five, each with its own spacing and padding so every size the pinch can reach is one that was designed rather than merely computed. **L2** and **R2** turn pages, **X** favourites whatever is selected, and **Y** raises the context menu.

Icons take one of five shapes — square, rounded, squircle, circle or hexagon — and the selection cursor traces whichever shape the cell it is standing on has. The default library order is yours to set from ten choices: title, platform, release date, rating, last played, play time, times played, date added, file size, or a custom order you arrange by hand.

The context menu is a grid of half-width tiles, so a game's eleven actions are on screen at once, with the full name and description of whichever tile holds the cursor shown on one line underneath. From it you can launch, launch on either specific screen, favourite, add to or remove from the grid, file into a folder or a new folder, edit the entry, pick a different emulator, hide it, open its app info, uninstall it, or delete it.

<br>

### Folders

Folders hold entries, scrape their own artwork, and can be made from any entry's menu. Scanned games are filed into one folder per system, so adding a console costs the grid a single cell instead of three hundred, and anything you move out afterwards stays where you put it. A folder holding forty games pages through them using the same cell geometry as the home grid rather than inventing a second kind of layout. Folders draw as a stack that fans out of the icon, as a two-by-two preview of the first four children, or as a single glyph on a coloured shell.

<br>

### Widgets

Long-press an empty cell to add a widget. Loki hosts ordinary Android app widgets through a proper widget host, which is the part that owns and preserves their ids across restarts, and asks for the platform's consent dialog and runs a provider's own configuration screen where one is declared.

Alongside those, Loki draws five widgets from your own library, which app widgets cannot do because nothing outside the launcher has ever heard of it: Continue Playing shows the last few games you played ready to start, Spotlight gives the last game its artwork at full size, Favourites shows what you have starred, Library counts how many games you have and how long you have played them, and Clock shows the time and date.

Widgets resize and move in edit mode, with on-screen buttons as well as the controller, since the panel they run on is a touchscreen. A widget is a single stop for the cursor and consumes every cell it covers, and a resize can be abandoned with Back rather than merely stopped at whatever size it had reached.

<br>

</div>

<div align="center">

## Controls

**A** launches, and holding it picks an icon up. **B** goes back. **X** favourites. **Y** opens the context menu. **L1** and **R1** step through screenshots, **L2** and **R2** turn pages. Clicking either stick raises the shortcut panel, **Start** opens the side panel, **Select** opens the app drawer, and **Guide** goes Home. Holding a trigger speeds up whatever else you press. A paired keyboard works too, using WASD, E, F, Tab, Enter and Escape.

Two controller profiles ship — the Loki default and one with A and B swapped — and both apply live without a restart. Stick sensitivity is a slider, applied as an inverse dead zone so a more sensitive stick registers a direction at a smaller deflection, and it is bounded at both ends so no setting can make the stick unusable. Navigation can wrap at the edges or stop dead, the edge of a page can turn to the next one, and touch input on the grid can be turned off entirely so only the pad drives it.

The AYN button's firmware does not send anything an app can read, so Loki cannot use it — the stick clicks do the same job and work everywhere. If a button ever seems dead, **Settings → About → Button tester** shows exactly what it sends.

<br>

### The shortcut panel

Clicking either stick raises twelve tiles: the app drawer, search across the whole library, Loki's settings, swap screens, toggle couch mode, rescan the library, record both panels, record the real screen, the Wi-Fi panel, Bluetooth settings, the volume panel, and Android settings. Anything needing a privileged permission is deliberately absent, because a tile that silently does nothing is worse than no tile at all.

<br>

</div>

<div align="center">

## The pointer

The pointer exists for everything a gamepad cannot press: login forms, store pages, emulator settings. Hold **Start + Select** together to raise and lower it, after turning it on in **Settings → Controls → Pointer**.

The left stick moves the cursor, the right stick scrolls, **A** clicks, **X** long-presses, **B** goes back, **Y** opens the keyboard, and **L1** and **R1** scroll a full page. Every one of those bindings is remappable, and each button can instead be set to do nothing, scroll up or down, or turn the pointer off. Cursor speed, acceleration and size are all adjustable, the cursor can span both displays or stay on one, and button presses can be passed through to the app underneath rather than consumed.

To use the pointer outside Loki, enable Loki's accessibility service — that is the only route an unprivileged app has to a cursor that works in other apps, since placing a window over another app and clicking inside it needs gesture dispatch, and reading controller buttons while that app has focus needs accessibility key events.

<br>

### Typing into other apps

Tap a text field in any app, press **Y**, and Loki's keyboard appears on the panel you are holding while the text lands in the field on the other screen. This needs the accessibility permission, and apps that do not use standard Android text fields will not accept it.

<br>

</div>

<div align="center">

## The keyboard

Loki brings its own keyboard, because Android's appears on the wrong screen on this device. The D-pad moves over the keys, **A** presses, **B** deletes or closes when the field is empty, **X** types a space, **Y** shifts, **L2** and **R2** switch between letters and symbols, and **Start** finishes. Every key is a touch target as well. A clipboard sheet sits alongside it, so text can be copied out of a field and pasted back into another.

<br>

</div>

<div align="center">

## Your games

Loki knows **47 systems** and recognises **87 emulators**, each handed a ROM the way that emulator expects. Add the consoles you own and pick an emulator for each in **Settings → Games & artwork → Platforms**.

Emulators are matched by what they descend from rather than by an exact package id, so forks and variants are recognised rather than missed. Standalone emulators are preferred over RetroArch and Lemuroid, so a DS game goes to melonDS or DraStic if you have one and to a multi-core front-end only when you do not. Among those known are Azahar, Citra, Panda3DS, melonDS, DraStic, NooDS, DuckStation, AetherSX2, aPS3e, RPCS3, Dolphin, Cemu, PPSSPP, Vita3K, Flycast, Redream, Yaba Sanshiro, Strato, Eden, Citron, Sudachi, MAME4droid, Mupen64Plus FZ, MasterGear, fMSX, iNES, ColEm, Speccy, ScummVM, the John and `.emu` families, Pizza Boy, My Boy!, and Winlator for Windows games — including the dual-screen Winlator fork, which is offered first on a device with two screens. Emulator choice is made per system or per game, and the picker is a dialog in which any installed app can be selected for the cases no list will ever cover.

Point Loki at your ROMs from **Settings → Games & artwork → Extra ROM folders**, or give a folder to a specific platform. Loki scans what you grant, matching files by extension and reading inside `zip`, `7z`, `rar` and `chd` archives. Duplicate detection and version grouping are on by default, system apps are hidden unless you ask for them, and installed apps can be added to the grid or left out of it. Missing files are flagged rather than deleted, so an unmounted SD card does not lose anything, and hidden entries can be shown again whenever you want them back.

Scanning, scraping and play-time tracking all run in the background as scheduled work rather than blocking the launcher. Play time is recorded per game and shown on the information panel, and it survives Loki being killed mid-game, which is normal on this device when memory is tight.

<br>

</div>

<div align="center">

## Artwork and details

Five sources are merged field by field, and anything you edit by hand is never overwritten. Wikidata supplies developer, publisher, dates and series and needs nothing at all. ScreenScraper supplies titles, credits, genres, dates and artwork, and an account of your own is enough to use it. IGDB supplies descriptions, ratings and completion times, authenticated through Twitch with no approval step. SteamGridDB supplies square grid artwork with your API key. RAWG supplies descriptions, ratings, screenshots and completion times with your API key. A source without a key is skipped rather than being fatal, and each artwork slot asks whichever provider is good at it.

Files are identified by what they are rather than by what they were named: CRC, MD5 and SHA1 hashes are sent to ScreenScraper so a badly named ROM still matches. When the match is uncertain you can choose it by hand through a two-stage dialog — which game, then which cover — driven entirely by the controller, with no countdown answering for you. Asking during a full library scrape is off by default so a library-wide pass runs unattended, while scraping a single system always asks.

Completion times from IGDB and RAWG are measured against your own play time and drawn as progress bars in the information panel and in couch mode. Each game keeps a cover and up to three screenshots, and the bumpers step between them. Artwork can also be imported from another launcher's media folder, with Loki matching its files back to your library.

Platform artwork can be imported from an icon pack; Loki ships none of its own, but does draw a coherent set of platform highlight icons that can be turned off if an imported pack should take over. Cover art and backdrop art for any individual game or platform can also be picked from a file by hand.

<br>

### Achievements

RetroAchievements is fully integrated. Sign in with your username and API key in **Settings → Games & artwork → Achievements**, and Loki shows both what you have earned and what is still left in a set, rather than only what has been done. Hardcore-only mode is available, and achievement data refreshes in the background alongside the rest of the library.

<br>

</div>

<div align="center">

## Profiles

More than one person can use the device. Each profile owns its own settings file, its own library database and its own avatar, held in a directory named after a generated id — which is why renaming a profile is free and two people called "Guest" cannot collide on disk. Add, rename, recolour, give a picture to, switch between and remove profiles from **Settings → Profiles**. The last profile cannot be deleted, since the launcher has to load something, and removing the active one falls to the most recently used of those left.

Loki can also mirror the system notification shade into its own panel once notification access is granted in Android's settings, which no app can grant itself.

<br>

</div>

<div align="center">

## Films and shows

Films and shows are an extension, added with [`movies.json`](extensions/) as described below. Open **Movies** from the section bar at the bottom of the grid, or from the top bar in couch mode where films and shows are two separate tabs over one catalogue.

Browsing needs nothing. Playing needs a source, and there are two kinds. Loki speaks the Stremio addon protocol, so pasting an addon's URL and pressing Test is enough — install links, manifest URLs and `stremio://` links are all accepted. Loki also speaks Torznab, which Jackett, Prowlarr and NZBHydra all expose, and searches those itself with no addon in between. An indexer needs a name of your choosing, its Torznab endpoint and the API key from its dashboard, and pressing Test runs a real search so a wrong key or an unreachable indexer tells you now rather than silently finding nothing later. Indexers on your own network work over plain HTTP, while everything remote is HTTPS.

Real-Debrid and TorBox are both supported for turning a torrent result into an instant stream. Paste the token and press Check. Without one, sources are listed but will not open.

Source selection can be automatic or left to you, with a preferred resolution from SD up to 4K, an optional preference for HDR, a maximum file size, a cached-only filter, preferred languages, and an option to avoid dubbed releases. Playback resumes where you left off, plays the next episode automatically after a countdown you can set or turn off, skips by an interval you choose, and lets you pick a different source without leaving the title, along with the audio track, subtitle language and playback speed. Progressive files, HLS and DASH are all handled.

<br>

</div>

<div align="center">

## Game streaming

PC streaming is an extension, added with [`stream.json`](extensions/) as described below. Open **Stream** from the section bar and Loki finds PCs on your network running [Sunshine](https://github.com/LizardByte/Sunshine), announced over mDNS so the common case needs no configuration at all. Run Sunshine on your PC, open Stream, and PCs appear automatically — or add one by address if discovery cannot reach it. Enter the PIN into Sunshine, and pick something to play. PCs can be renamed and removed, and Loki's own name in Sunshine's client list is yours to set.

While streaming, the other panel becomes a trackpad and keyboard, which is the only way to type into a streamed desktop since Android's own keyboard cannot render there. Leave a stream with **Back**, or by holding **Start + Select + L1 + R1** together.

Resolution, frame rate and bitrate are all configurable, with 1080p60 at 20 Mbps as the default because bitrate decides whether motion looks like the game or like a smear far more than resolution does. The video codec can be left automatic — offering everything the device can decode in hardware and letting the host choose — or pinned to H.264, HEVC or AV1. Audio can be stereo or surround, the PC can be left silent or kept playing sound, and the host can be allowed or forbidden to rewrite the game's own graphics settings. Couch mode gives streaming a television section of its own, with an on-screen keyboard drawn as a card and an in-app explanation of how all of it works.

<br>

</div>

<div align="center">

## Making it yours

Fourteen themes ship, arranged on four shelves by what colour they commit to rather than by brightness. Material, Obsidian, Slate and Linen are neutral, where the greys are the design and the accent stays quiet. Ember, Citrine, Sakura and Vapor are warm. Nocturne, Aurora, Orchid and Terminal are cool. One Dark and Palenight are taken from code editors, and share the mid-toned ground that makes an editor palette what it is rather than the near-black of the rest.

Every theme is available light or dark, or following Android's own setting including its schedule, because palettes are generated from a recipe rather than written down as a table of hex values — Ember light and Ember dark are the same recipe resolved against a different ground, so neither had to be drawn by hand and neither can drift from the other. Contrast is a real dial with four positions, held to a WCAG ratio rather than checked against one afterwards, so raising it moves text only as far as it has to go and the ground keeps its colour at every level. Colour intensity, accent hue shift, a pure-black mode for OLED panels, a hand-picked accent override, and Android's own dynamic colour are all available on top.

What panels are made of is separate from what colour they are. Surfaces can be flat, raised, tinted or glass, with their own depth and grain amount, and corners can be square everywhere, rounded everywhere, or left to each theme's own radius.

Wallpaper is set per panel, and can be a still image of your own or one of nine drawn effects: waves, mesh, aurora, bokeh, particles, starfield, gradient drift, parallax, or an adaptive one that takes its hue from the selected game's platform accent so the background shifts as the cursor moves across systems. A static setting turns the effect off, a dim slider controls how far the wallpaper is pushed back, and the selected game's own video preview can play as the background instead.

The selection cursor draws as a ring, a fill, corner brackets, an underline or a spotlight, and animates by breathing, pulsing, rotating, shimmering, or not at all, with an adjustable glow. Typeface can be system, rounded, monospace, pixel or serif, and text scales independently of the interface. Motion has four characters — fluid, smooth, snappy and mechanical — with a separate transition speed multiplier on top. The clock can be hidden, twelve-hour, twenty-four-hour or analog. The status bar and the page indicators can each be turned off, folders and the information panel each have their own styles, and the information panel can be drawn as an opaque card with a defined edge or blended into the artwork with no visible edge at all.

Interface sounds are a master switch with separate navigation and launch categories and a volume of their own, and haptics have an intensity slider beside them.

<br>

### Accessibility

High contrast, large text and reduced motion each have their own switch, alongside four colour vision modes — protanopia, deuteranopia, tritanopia and greyscale — and a multiplier on every touch target size. Reduced motion settles animated backgrounds to a fixed composition rather than removing them, so nothing disappears when it is turned on.

<br>

### Performance

If it feels slow, **Settings → System & accessibility → Performance** turns blur, animated wallpapers and video previews off together in one switch, and animations and blur each have an individual toggle beside it. The page prefetch radius keeps neighbouring grid pages composed so paging is instant, and the top screen can be kept awake while the launcher is in front.

<br>

</div>

<div align="center">

## Recording

There are two recordings, and they capture different things, which is why they are two tiles rather than one tile with a setting behind it.

Recording the launcher draws both panels into one video inside a dual-screen console body, saved to `Movies/Loki`. It records the launcher rather than the device, because Android only lets an app capture the default display, so a running game will not appear and there is no audio.

Recording the screen mirrors the real display and keeps going into a game. It needs the platform's consent dialog and runs in a foreground service so it survives you leaving the launcher, and its notification carries the controls — which is the only way to stop what you started once you are inside a game.

<br>

</div>

<div align="center">

## Extensions

Films and shows and PC streaming are optional. They ship inside the app but stay switched off, so a launcher you only want for your ROMs has no section in the bar, no settings category and no pages for either. Adding one is a small file rather than a download: [`movies.json`](extensions/movies.json) adds browsing films and shows, finding sources and playing them, and [`stream.json`](extensions/stream.json) adds finding PCs on your network and streaming from them. Both are attached to every release, and are unchanged since v1.1.0, so there is no need to re-import if you already have them.

Save the file anywhere on the device — the Downloads folder is fine — then use **Settings → System & accessibility → Extensions → Import an extension**. The section appears at once, nothing is fetched, and it works offline. Remove it from the same page and everything it added disappears, with its settings kept in case you add it back.

It is not a licence key. Anyone can write one in a text editor; it is a way of saying which parts of the launcher you want. There is more in [extensions/README](extensions/).

<br>

</div>

<div align="center">

## Settings

Settings is two levels — a rail of categories, each holding a short list of pages small enough to fit a screen, so opening one shows all of it at once rather than asking you to walk thirty rows with a D-pad.

Profiles covers who is signed in, and the name, picture and colour of whoever that is. Personalization covers theme and colour, surfaces, wallpaper, interface and the home grid and selection cursor. Games & artwork covers platforms, extra ROM folders, scanning, sorting, metadata and scraping, platform artwork and achievements. Films & shows covers sources and accounts, and playback. PC streaming covers picture, controls and PCs. Controls covers navigation, the pointer, and feedback. System & accessibility covers dual screen, performance, extensions and accessibility. About covers version and device information, the default launcher prompt, replaying the walkthrough, logging, the button tester and a reset.

Categories belonging to an extension you have not enabled are absent from the rail entirely rather than shown greyed out, along with every page under them, and their settings are kept in case the extension comes back. Every row works with the controller.

<br>

</div>

<div align="center">

## Not built yet

Desktop Mode is planned. Mouse and keyboard while streaming is partial — the controller and the trackpad work, but full pointer and text paths are not wired. HDR while streaming is accepted but not applied. Two controller profiles ship and apply live, but there is no remapping editor. Cloud sync and backup are modelled with no transport behind them. Collections have tables but no screens. Smart folders work, but their queries can only be written in code. A theme editor, plugins and third-party extensions are not started.

Two finished features are switched off rather than missing. The floating dock — five assignable slots, its placements, and its whole settings page — is kept intact behind a flag while the bottom nav bar owns the bottom edge of the grid panel. The profile and notification cluster on the information panel is likewise held back while that corner is settled, though couch mode still draws it and profiles themselves are entirely unaffected.

There are also known limits worth stating plainly. Emulator package names drift between releases, so an unknown one falls back to a generic open. Emulators needing a real file path can only reach primary storage. And once an app is on a panel, Loki cannot tell whether it is still there — press **Home** to take the panel back.

<br>

</div>

<div align="center">

## Building it yourself

```bash
./gradlew assembleRelease
./gradlew test
```

JDK 17, plus the NDK and CMake for the streaming core:

```bash
sdkmanager "ndk;27.0.12077973" "cmake;3.22.1"
```

The original design document — the dual-screen architecture and the rules behind it — is kept at [docs/DESIGN.md](docs/DESIGN.md). It was written under the old THOR name and has not been maintained since, so read it as history rather than as a description of the code as it stands.

<br>

## Licence

**GPL-3.0** — see [LICENSE](LICENSE).

The streaming core is [Moonlight](https://github.com/moonlight-stream/moonlight-android), which is GPL, so Loki is too. Anyone given a build is entitled to its source.

Loki is not affiliated with AYN, Stremio, Real-Debrid, TorBox, RetroAchievements, or any emulator or indexer project.

</div>
