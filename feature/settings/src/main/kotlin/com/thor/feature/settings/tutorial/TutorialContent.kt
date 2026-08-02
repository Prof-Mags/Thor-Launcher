package com.thor.feature.settings.tutorial

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Monitor
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.ui.graphics.vector.ImageVector
import com.thor.feature.settings.SettingsCategory
import com.thor.feature.settings.SettingsPage

/**
 * One screen of the walkthrough.
 *
 * @param hint the controls that apply to what is being described, shown apart
 *   from the prose because a button map read as a sentence is a button map
 *   nobody remembers.
 */
data class TutorialPage(
    val title: String,
    val body: String,
    val hint: String? = null,
    /** Which part of the device the diagram should light for this page. */
    val focus: TutorialFocus = TutorialFocus.NONE,
)

/** A group of pages about one part of the launcher. */
data class TutorialChapter(
    val title: String,
    val icon: ImageVector,
    val pages: List<TutorialPage>,
    /**
     * What the diagram lights for this chapter's pages.
     *
     * Set per chapter rather than per page because a chapter is already "the part
     * of the device this is about" — repeating it on every page would be the same
     * value written thirty times, and thirty chances for one of them to disagree
     * with the prose beside it. A page that needs its own can still say so.
     */
    val focus: TutorialFocus = TutorialFocus.NONE,
)

/**
 * The first-run walkthrough.
 *
 * Content rather than a scripted tour of the real screens, and that is a
 * deliberate limit worth stating: a tour that drove the launcher itself would
 * have to open every overlay, place a cursor on rows it does not own and hold
 * both panels in a known state through twenty-five settings pages — brittle in
 * exactly the places this launcher is already hardest to get right. This
 * explains every surface instead, from one place, and can be re-read at any
 * time from System → Tutorial.
 *
 * The settings chapter is *generated* from [SettingsCategory] and
 * [SettingsPage] rather than written out. Twenty-five pages copied by hand into
 * prose is twenty-five things that quietly stop being true — a page renamed, a
 * page added, a page hidden behind a feature flag — and a walkthrough that
 * describes a screen the user cannot find is worse than one that omits it.
 */
object ThorTutorial {

    val CHAPTERS: List<TutorialChapter> = buildList {
        add(
            TutorialChapter(
                title = "Two screens",
                icon = Icons.Rounded.Monitor,
                focus = TutorialFocus.BOTH,
                pages = listOf(
                    TutorialPage(
                        title = "Welcome to Loki",
                        body = "Loki is a launcher built for a handheld with two " +
                            "screens, and it treats that as the point rather than " +
                            "as a complication.\n\nOne panel shows a sparse grid of " +
                            "icons. The other shows everything known about " +
                            "whatever the cursor is resting on — box art, " +
                            "screenshots, developer, release year, how long you " +
                            "have played it. Both are driven from one state, so " +
                            "they can never disagree.",
                    ),
                    TutorialPage(
                        title = "Either panel can be the one you drive",
                        body = "Touching a panel gives it the controller. Tap the " +
                            "game to play, tap the launcher to browse, in either " +
                            "direction, at any time.\n\nLaunching a game sends it " +
                            "to the grid's panel and leaves the other one showing " +
                            "the launcher, so you can keep browsing while " +
                            "something runs. An entry's context menu can send it " +
                            "to the other panel instead.",
                        hint = "Home gives a panel back to the launcher, from either screen",
                    ),
                    TutorialPage(
                        title = "If the panels look swapped",
                        body = "Which physical screen gets the grid and which gets " +
                            "the information panel is a setting, and the same " +
                            "layout renders in either window.\n\nIf they are the " +
                            "wrong way round on your device, that is the first " +
                            "thing to change.",
                        hint = "Settings → Display → Dual screen → Swap screens",
                    ),
                ),
            ),
        )

        add(
            TutorialChapter(
                title = "The grid",
                icon = Icons.Rounded.GridView,
                focus = TutorialFocus.GRID,
                pages = listOf(
                    TutorialPage(
                        title = "Every cell is where you put it",
                        body = "An entry occupies the cell you place it in, and an " +
                            "empty cell stays empty. Nothing reflows when you add " +
                            "or remove something.\n\nThat is a deliberate " +
                            "departure from launchers that flow icons into a " +
                            "list, and it is what makes rearranging feel like a " +
                            "console rather than an app drawer.",
                    ),
                    TutorialPage(
                        title = "Moving things around",
                        body = "Hold A on an entry to pick it up, move to the cell " +
                            "you want, and press A again to drop it. Dropping onto " +
                            "an occupied cell picks up whatever was there so you " +
                            "can re-home it, rather than silently swapping the " +
                            "two.\n\nPlacements survive rescans, because an " +
                            "entry's identity comes from stable facts rather than " +
                            "from a random id.",
                        hint = "Hold A to pick up  ·  A to drop  ·  B to cancel",
                    ),
                    TutorialPage(
                        title = "Density and pages",
                        body = "Pinch to snap between eight layout presets, from " +
                            "3×2 to 8×5, each with its own spacing so no density " +
                            "reads as crowded.\n\nA page is a fixed grid, so the " +
                            "size of your library changes the number of pages and " +
                            "never the cost of drawing one. Ten thousand games " +
                            "cost the same per frame as ten.",
                        hint = "L2 / R2 turn pages  ·  pinch to change density",
                    ),
                    TutorialPage(
                        title = "Folders",
                        body = "Folders hold entries, scrape their own artwork and " +
                            "can be made from the Start panel or any entry's " +
                            "context menu.\n\nScanned games are filed into a " +
                            "folder for their system rather than scattered across " +
                            "pages, so adding a console brings in hundreds of " +
                            "games and costs the grid one cell. Anything you move " +
                            "out or rearrange stays where you put it.",
                    ),
                ),
            ),
        )

        add(
            TutorialChapter(
                title = "Controls",
                icon = Icons.Rounded.SportsEsports,
                focus = TutorialFocus.CONTROLS,
                pages = listOf(
                    TutorialPage(
                        title = "The buttons",
                        body = "A launches, and holding it picks the icon up. B " +
                            "goes back. Y opens the context menu for whatever is " +
                            "selected, and X toggles it as a favourite.\n\nThe " +
                            "shoulders move through a game's screenshots on the " +
                            "information panel; the triggers turn pages.",
                        hint = "A launch · B back · X favourite · Y menu · L1/R1 shots · L2/R2 pages",
                    ),
                    TutorialPage(
                        title = "Panels you can summon",
                        body = "Start opens the Start panel. Select opens the app " +
                            "drawer. Clicking either stick opens the shortcut " +
                            "panel — app drawer, search, settings, swap screens, " +
                            "rescan, recording, Wi-Fi, Bluetooth, volume and " +
                            "Android settings.\n\nThe shortcut panel offers only " +
                            "what an ordinary app can genuinely do. Brightness and " +
                            "the notification shade need permissions a launcher " +
                            "cannot hold, so a tile for them could only pretend.",
                        hint = "Start · Select · stick click · Guide returns Home",
                    ),
                    TutorialPage(
                        title = "A keyboard also works",
                        body = "WASD moves, E confirms, F opens the menu, Tab and " +
                            "Enter and Escape do what you would expect. The whole " +
                            "launcher is operable from a paired keyboard, which is " +
                            "also what makes it usable from an emulator.",
                    ),
                    TutorialPage(
                        title = "The AYN button",
                        body = "Its firmware reports no code that reaches an app. A " +
                            "short press never arrives, and a long press also " +
                            "powers the bottom panel off — firmware behaviour on a " +
                            "button the vendor owns, which no installable launcher " +
                            "can intercept.\n\nThe stick clicks are the binding " +
                            "that works everywhere. If a button seems to do " +
                            "nothing, the button tester will tell you whether it " +
                            "reaches the launcher at all.",
                        hint = "Settings → System → Diagnostics → Button tester",
                    ),
                ),
            ),
        )

        add(
            TutorialChapter(
                title = "Typing",
                icon = Icons.Rounded.Keyboard,
                focus = TutorialFocus.KEYBOARD,
                pages = listOf(
                    TutorialPage(
                        title = "Loki brings its own keyboard",
                        body = "Android draws a keyboard on the screen that owns " +
                            "the focused window, which on this device means it " +
                            "arrived on the wrong panel or never appeared at " +
                            "all.\n\nSo the launcher has one of its own. It " +
                            "renders wherever the grid does, in your theme, with " +
                            "the same cursor and sounds as everything else.",
                    ),
                    TutorialPage(
                        title = "It types into fields, not into apps",
                        body = "Every text field in the launcher claims the " +
                            "keyboard when you tap or confirm it, and what you " +
                            "type fills the field in live — on whichever panel " +
                            "that field is drawn on. Typing on one screen while " +
                            "the thing you are filling in updates on the other is " +
                            "what two screens are for.\n\nTyping into another app " +
                            "needs a system keyboard, which is not something a " +
                            "launcher can provide from its own window.",
                        hint = "D-pad moves · A presses · Y shift · X space · B delete · Start done",
                    ),
                ),
            ),
        )

        add(
            TutorialChapter(
                title = "Your library",
                icon = Icons.AutoMirrored.Rounded.LibraryBooks,
                focus = TutorialFocus.GRID,
                pages = listOf(
                    TutorialPage(
                        title = "Start by adding your systems",
                        body = "Loki knows 47 platforms out of the box, each with " +
                            "its file extensions, its accent colour and its " +
                            "scraper ids. Only the systems you add are offered " +
                            "when assigning a game, so add the ones you " +
                            "own.\n\nEach platform can be given the emulator it " +
                            "launches with; 62 emulators are recognised and " +
                            "handed a ROM the way each one expects.",
                        hint = "Settings → Games & artwork → Platforms",
                    ),
                    TutorialPage(
                        title = "Point it at your ROMs",
                        body = "Grant the folders your games live in and Loki " +
                            "scans them, matching files to systems by extension " +
                            "and looking inside zip, 7z, rar and chd " +
                            "archives.\n\nFiles that vanish are flagged rather " +
                            "than deleted, so an unmounted SD card does not throw " +
                            "away everything known about what was on it.",
                        hint = "Settings → Games & artwork → Extra ROM folders",
                    ),
                    TutorialPage(
                        title = "Artwork and details",
                        body = "Four providers supply metadata and artwork, and " +
                            "they are merged field by field because none is best " +
                            "at everything. Wikidata needs no account at all; the " +
                            "others take a key you can add in " +
                            "settings.\n\nAnything you edit by hand is never " +
                            "overwritten by a later scrape.",
                        hint = "Settings → Games & artwork → Metadata & scraping",
                    ),
                    TutorialPage(
                        title = "Play time",
                        body = "Launches and play time are recorded per entry and " +
                            "shown on the information panel. A session opens when " +
                            "you launch something and is credited when you come " +
                            "back or when the device sleeps — including if the " +
                            "launcher was killed while the game ran, which is " +
                            "normal on a handheld.",
                    ),
                ),
            ),
        )

        add(
            TutorialChapter(
                title = "Films & shows",
                icon = Icons.Rounded.Movie,
                focus = TutorialFocus.NAV_BAR,
                pages = listOf(
                    TutorialPage(
                        title = "Movies is a real section",
                        body = "It browses a catalogue, finds sources for a title " +
                            "and plays them. Where something is missing the screen " +
                            "says so, because a blank page is indistinguishable " +
                            "from one whose content failed to load.",
                        hint = "The nav bar along the bottom of the grid panel",
                    ),
                    TutorialPage(
                        title = "What it needs",
                        body = "Sources come from URL-based addons — one pasted " +
                            "link each — and from Torznab indexers. A debrid " +
                            "account turns a result into something playable and is " +
                            "where most of the reliability comes from.\n\nNone of " +
                            "it is required to browse — only to play.",
                        hint = "Settings → Films & shows → Sources & accounts",
                    ),
                ),
            ),
        )

        add(
            TutorialChapter(
                title = "PC streaming",
                icon = Icons.Rounded.Cast,
                focus = TutorialFocus.NAV_BAR,
                pages = listOf(
                    TutorialPage(
                        title = "Stream finds PCs on your network",
                        body = "It discovers machines running Sunshine, pairs with " +
                            "one by PIN, lists what it can stream with its box art " +
                            "and plays it — video, audio and controller — on a " +
                            "vendored Moonlight core.",
                        hint = "The nav bar → Stream",
                    ),
                    TutorialPage(
                        title = "The second screen becomes a trackpad",
                        body = "While a stream is running, the other panel is a " +
                            "trackpad and a keyboard. That is the only way to type " +
                            "into a streamed desktop at all: Android's own " +
                            "keyboard cannot render on the second display.\n\nTap " +
                            "the keyboard to give it the controller, and press B " +
                            "to hand the pad back to the game.",
                        hint = "Back leaves the stream · or hold Start, Select, LB and RB",
                    ),
                ),
            ),
        )

        add(
            TutorialChapter(
                title = "The pointer",
                icon = Icons.Rounded.Mouse,
                focus = TutorialFocus.POINTER,
                pages = listOf(
                    TutorialPage(
                        title = "A cursor for what a pad cannot reach",
                        body = "A handheld running Android is always one tap away " +
                            "from something no gamepad can press — a login form, a " +
                            "store page, an emulator's own settings.\n\nPointer " +
                            "mode gives you a cursor driven by the stick. It is " +
                            "off by default, because a pointer that appears " +
                            "unbidden mid-game is worse than one you have to " +
                            "switch on.",
                        hint = "Start + Select together raises and lowers it",
                    ),
                    TutorialPage(
                        title = "Using it",
                        body = "The left stick moves the cursor and the right " +
                            "stick scrolls. A clicks, X long-presses, B goes back " +
                            "and Y opens the keyboard; the shoulders scroll a page " +
                            "at a time.\n\nTo use it outside the launcher it needs " +
                            "Loki's accessibility service enabled — that is the " +
                            "only route an ordinary app has to a cursor that works " +
                            "over other apps.",
                        hint = "Settings → Controls → Pointer",
                    ),
                ),
            ),
        )

        add(
            TutorialChapter(
                title = "Making it yours",
                icon = Icons.Rounded.Palette,
                focus = TutorialFocus.BOTH,
                pages = listOf(
                    TutorialPage(
                        title = "Fifteen themes",
                        body = "Five colourful, five dark and five light. A theme " +
                            "is more than a colour swap — each carries its own " +
                            "accent pair, corner radius, motion character, font, " +
                            "sound pack and the way its panels are actually " +
                            "drawn.\n\nYou start on Material, over the Waves " +
                            "wallpaper, with square corners.",
                        hint = "Settings → Personalization → Theme",
                    ),
                    TutorialPage(
                        title = "Wallpapers, cursors and shapes",
                        body = "Each panel can have its own wallpaper — a picture, " +
                            "an animated effect, or a video preview of whatever " +
                            "game is selected. Icons take one of five shapes, and " +
                            "the selection cursor traces whichever shape the cell " +
                            "actually has.\n\nCorner style is one answer for the " +
                            "whole interface, so nothing is rounded on its own.",
                        hint = "Settings → Personalization → Wallpaper, Home grid, Cursor",
                    ),
                    TutorialPage(
                        title = "If it feels slow",
                        body = "Performance mode turns the expensive effects off " +
                            "together — blur, animated wallpapers, video previews " +
                            "and shadows. Themes degrade rather than break: a " +
                            "glass theme without a blurred backdrop becomes a " +
                            "tinted one instead of an unreadable transparent " +
                            "sheet.\n\nReduced motion and contrast settings live " +
                            "under Accessibility.",
                        hint = "Settings → Display → Performance",
                    ),
                ),
            ),
        )

        add(settingsChapter())

        add(
            TutorialChapter(
                title = "That's it",
                icon = Icons.Rounded.Waves,
                pages = listOf(
                    TutorialPage(
                        title = "You're set up",
                        body = "Add your systems, point Loki at your ROMs, and let " +
                            "it scrape. Everything else can wait until you want " +
                            "it.\n\nThis walkthrough stays available — nothing " +
                            "here is a one-time decision, and none of it has to be " +
                            "remembered now.",
                        hint = "Settings → System → Tutorial to read this again",
                    ),
                ),
            ),
        )
    }

    /** Every page of the walkthrough, in order, for the progress indicator. */
    val PAGES: List<Pair<TutorialChapter, TutorialPage>> =
        CHAPTERS.flatMap { chapter -> chapter.pages.map { chapter to it } }

    /**
     * The settings tour, built from the real menu.
     *
     * One walkthrough page per category, listing that category's pages with the
     * summaries the settings screen itself shows. Read from [SettingsPage.forCategory]
     * so a page hidden behind a feature flag is hidden here too — describing a
     * screen the user cannot open would be worse than not mentioning it.
     */
    private fun settingsChapter(): TutorialChapter {
        val pages = SettingsCategory.entries
            .filter { it.visible }
            .mapNotNull { category ->
                val entries = SettingsPage.forCategory(category)
                if (entries.isEmpty()) return@mapNotNull null

                TutorialPage(
                    title = category.title,
                    body = buildString {
                        append(category.summary)
                        append("\n")
                        entries.forEach { page ->
                            append("\n• ")
                            append(page.title)
                            append(" — ")
                            append(page.summary.replaceFirstChar { it.lowercase() })
                        }
                    },
                )
            }

        return TutorialChapter(
            title = "Every setting",
            icon = Icons.Rounded.Tune,
            pages = listOf(
                TutorialPage(
                    title = "How settings are arranged",
                    body = "Settings is two levels: a category holds a short list " +
                        "of pages, and a page holds the controls. No category " +
                        "holds more than four pages, so a page fits a screen and " +
                        "opening one shows all of it at once.\n\nEvery row is " +
                        "reachable from the controller, and the next few screens " +
                        "list everything there is.",
                    hint = "Stick click → Settings, or the Start panel",
                ),
            ) + pages,
        )
    }
}
