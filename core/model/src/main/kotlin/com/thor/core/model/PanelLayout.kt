package com.thor.core.model

/**
 * Fixed dimensions of the grid panel's furniture, in dp.
 *
 * Here rather than beside the composable that draws them because more than one
 * feature has to agree about them, and the ones that disagreed did so silently.
 * The walkthrough's spotlight described the section bar as "the bottom sixteen
 * percent of the panel" while the bar itself was [NAV_BAR_HEIGHT] dp — two
 * independent numbers for one strip of screen, which on a tall panel put the
 * highlight at roughly twice the height of the thing it was pointing at.
 *
 * A layout constant, not a setting: see [LauncherFeatures] for why the launcher's
 * unsettled shape is decided in code rather than offered as a preference.
 */
object PanelLayout {

    /**
     * Height of the section bar along the bottom of the grid panel.
     *
     * Kept tight: this is permanent furniture on a handheld panel, and every dp
     * of it comes out of the grid above. The grid reserves exactly this much
     * clearance, and only when the bar is actually drawn — which is only when an
     * extension has given it a second section to switch between.
     */
    const val NAV_BAR_HEIGHT = 52
}
