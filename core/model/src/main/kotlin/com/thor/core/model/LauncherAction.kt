package com.thor.core.model

import kotlinx.serialization.Serializable

/**
 * Something the launcher can do in response to input.
 *
 * Actions are the indirection that lets the dock, the side menu, controller
 * bindings and pinned shortcuts all be user-remappable without any of those
 * surfaces knowing about each other.
 */
@Serializable
sealed interface LauncherAction {

    /** Open THOR's settings overlay on the top screen. */
    @Serializable data object OpenSettings : LauncherAction

    /** Open the full app drawer. */
    @Serializable data object OpenAppDrawer : LauncherAction

    /** Open global search with the keyboard focused. */
    @Serializable data object OpenSearch : LauncherAction

    /** Slide in the Start side menu. */
    @Serializable data object OpenSideMenu : LauncherAction

    /** Return to page 0 of the grid and clear any open folder. */
    @Serializable data object GoHome : LauncherAction

    /** Show the system recents / task switcher. */
    @Serializable data object ShowRecents : LauncherAction

    /** Toggle the on-screen keyboard. */
    @Serializable data object ToggleKeyboard : LauncherAction

    /** Open the system gallery. */
    @Serializable data object OpenGallery : LauncherAction

    /** Open the power menu overlay. */
    @Serializable data object OpenPowerMenu : LauncherAction

    /** Enter grid edit mode. */
    @Serializable data object EditGrid : LauncherAction

    /** Trigger a library scan. */
    @Serializable data object ScanLibrary : LauncherAction

    /** Launch a specific installed application. */
    @Serializable
    data class LaunchApp(val packageName: String, val activityName: String? = null) : LauncherAction

    /** Launch a specific library entry. */
    @Serializable
    data class LaunchEntry(val entryId: String) : LauncherAction

    /** Jump straight to a named collection or smart folder. */
    @Serializable
    data class OpenFolder(val folderId: String) : LauncherAction

    /** Open a settings category directly. */
    @Serializable
    data class OpenSettingsCategory(val categoryId: String) : LauncherAction
}

/**
 * The five dock slots, in their shipped order.
 *
 * Every slot is user-replaceable; these are only the defaults applied on first
 * run and by "reset dock".
 */
object DefaultDock {
    val ACTIONS: List<LauncherAction> = listOf(
        LauncherAction.OpenSettings,
        LauncherAction.OpenGallery,
        LauncherAction.ToggleKeyboard,
        LauncherAction.ShowRecents,
        LauncherAction.OpenAppDrawer,
    )

    val LABELS: List<String> = listOf(
        "Settings", "Gallery", "Keyboard", "Recents", "Apps",
    )

    const val SLOT_COUNT = 5
}
