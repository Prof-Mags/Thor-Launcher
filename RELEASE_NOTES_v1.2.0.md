## Loki v1.2.0

109 commits since v1.1.0 · 340 files changed · +47,051 / −9,908

### Couch mode — rebuilt as a television interface

The largest area of work in this release; effectively a rewrite.

- A full TV dashboard layout, drawn against its own background rather than the launcher's
- Every section driven end-to-end from the controller, with keyboard and mouse support alongside
- Games get a full page of their own instead of a cramped sheet, and a long press raises one menu rather than several
- Movies and Shows split into separate tabs, each built as a proper catalogue with category rails
- Shelves size themselves to the cards on them; the posters got back the room the featured card was holding
- Search moved out of the rail; systems counted rather than collections
- An opening sequence, and an announcement when the launcher switches into couch mode

### Streaming

- **TorBox** added alongside Real-Debrid for turning a torrent into a stream
- Moonlight PC streaming gains a television section of its own, an on-screen keyboard drawn as a card, removable PCs, and an in-app explanation of how any of it works

### Widgets

An entirely new subsystem.

- App widgets through a proper `AppWidgetHost` — the part that owns and preserves ids across restarts
- **Launcher-drawn widgets** built from your own library: Continue Playing, Spotlight, Favourites, Library, Clock
- Long-press an empty grid cell to add one
- Resize and move in edit mode, with on-screen buttons, since the panel it runs on is a touchscreen
- A widget is a single stop for the cursor and consumes every cell it covers

### Metadata and scraping

- **IGDB** added as a provider, authenticated through Twitch with no approval step
- **Hash matching** — CRC, MD5 and SHA1 sent to ScreenScraper, so a file is identified by what it is rather than by what it was named
- **Choose the match by hand** — a two-stage dialog: which game, then which cover, driven by the controller, with no countdown answering for you
- **Completion times** from IGDB and RAWG, measured against your play time as progress bars in the info panel and in couch mode
- Artwork import from another launcher's media folder
- Each artwork slot asks the provider that is good at it
- A ScreenScraper account on its own is now enough to scrape

### RetroAchievements

Full integration, including what is still left in a set rather than only what has been done.

### Emulators

- Recognised by what they descend from rather than by an exact package id, so forks and variants match
- Cemu added; 86 emulators known, up from 81
- Emulator selection moved into a dialog, with any installed app selectable for the ones no list will ever cover
- **Games no longer tell you to go and open the emulator yourself** — the launch is tried first, and the advice only appears if every attempt fails

### Profiles

Multiple profiles, each with its own settings and library, and a notification panel that asks for the permissions it needs.

### Display and scaling

Every surface is now drawn against a fixed canvas rather than the screen's raw dp, so the interface holds its proportions across Smallest Width, resolution and density changes — in couch mode and dual-screen alike. Both screens also come back correctly from sleep.

### Appearance

- The app mark redrawn as the dual-screen device, and again so it survives being small
- All platform highlight icons redrawn as one coherent set
- Palettes generated rather than written down by hand
- **Info panel style**: a solid card, or blended so there is no visible edge at all
- The open-folder banner removed, and grid size, spacing and icon size made identical inside folders, outside them, and in the drawer

### Fixes

- The launcher crashing instantly on start
- Every emulator reported as missing after one failed system call
- Widgets deleted by the startup scan
- The scrape prompt being invisible while Settings was open
- The A button doing nothing in the emulator dialog
- Descriptions cut mid-word, and synopses cut short by the scraper
- A large blank gap between the description and the platform highlights
- The scanner importing everything that happened to sit beside a ROM

### Extensions

`movies.json` and `stream.json` are attached below. Movies & TV and PC streaming ship inside the launcher but stay switched off until one is imported — until then there is no section in the bar, no settings category and no pages. Download the file for the part you want, then **Settings → System → Extensions → Import an extension**. Nothing is downloaded and both work offline. Removing one takes everything it added away again and keeps your settings in case you add it back.

Unchanged from v1.1.0, so there is no need to re-import if you already have them.

### Notes

Completion times come from IGDB or RAWG only — ScreenScraper carries no such field. Add IGDB credentials or a RAWG key in Metadata settings and run a scrape; the pass now picks up games that were already scraped before the field existed.

"Ask during a full scrape" is off by default, so a library-wide scrape runs unattended. Scraping a single system still always asks.

The APK is signed with the debug key, as v1.0.0 and v1.1.0 were.
