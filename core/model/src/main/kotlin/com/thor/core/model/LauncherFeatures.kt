package com.thor.core.model

/**
 * Parts of the launcher that are built but not currently shown.
 *
 * A flag here means "this works and is being kept, but is not what the launcher
 * does today" — not "this is unfinished". Anything genuinely unfinished belongs in
 * the README's list of what is not built, where it can be read rather than
 * discovered.
 *
 * Constants rather than settings, deliberately. These are decisions in progress
 * about the launcher's shape; a user-facing switch for one would have to be
 * designed, explained and navigated, and would present an unsettled question as a
 * preference.
 */
object LauncherFeatures {

    /**
     * Whether the floating dock is shown.
     *
     * Off: the bottom nav bar owns the bottom edge of the grid panel now. The dock
     * is kept whole behind this rather than deleted — its five assignable slots,
     * their placements in the database, `resolveDock` and the whole Dock settings
     * page still work, and the shape of the launcher is not settled enough to be
     * sure they are not wanted again.
     *
     * Read by both the panel that would draw it and the settings rail that would
     * offer its page, so switching this back on restores the feature completely
     * rather than restoring a bar with no way to configure it.
     */
    const val DOCK_ENABLED = false

    /**
     * Whether the top screen shows the profile and notification cluster.
     *
     * Off: that corner is quiet while the rest of the panel is being settled.
     * Couch mode draws the same cluster and is not covered by this — it has a
     * top bar with room for it, where the information panel does not.
     *
     * Everything behind it is intact either way: the shade, the listener service
     * and its permission prompt, the avatar and the switcher. Profiles are
     * entirely unaffected — they still own the settings and the library, and are
     * still managed from their own settings category.
     */
    const val TOP_SCREEN_PROFILE_CLUSTER = false
}
