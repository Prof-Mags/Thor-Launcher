package com.thor.feature.stream.couch

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp

/**
 * How tall one card in the wall is, on a grid [gridHeight] high.
 *
 * Sized so that two rows are on screen, for the same reason the catalogue
 * guarantees two shelves: a wall that shows one row at a time is a list, and the
 * only way to survey a list is to walk it. The clamps keep a card recognisable on
 * a panel too short to honour that, and stop three PCs on a tall screen becoming
 * three billboards.
 */
internal fun couchHostCardHeight(gridHeight: Dp): Dp =
    ((gridHeight - CARD_GAP.dp - CARD_GROWTH.dp * 2) / VISIBLE_CARD_ROWS)
        .coerceIn(MIN_CARD.dp, MAX_CARD.dp)

/** How many PCs stand across the wall. Shared with the cursor's own arithmetic. */
internal const val STREAM_COUCH_COLUMNS = 3

/** The rows that should be reachable without scrolling. */
private const val VISIBLE_CARD_ROWS = 2

internal const val FIELD_ALPHA = 0.34f
internal const val HINT_ALPHA = 0.66f

internal const val SCREEN_INSET = 22
internal const val SCREEN_TOP_INSET = 14
internal const val SECTION_GAP = 12
internal const val LEGEND_HEIGHT = 24
internal const val LEGEND_GAP = 18

internal const val RAIL_WIDTH = 186
internal const val RAIL_ALPHA = 0.55f
internal const val RAIL_INSET = 12
internal const val RAIL_TOP_INSET = 16
internal const val RAIL_GAP = 6
internal const val RAIL_ROW_PADDING = 12
internal const val RAIL_ROW_PADDING_V = 8
internal const val RAIL_ICON_GAP = 10
internal const val RAIL_MARK = 34
internal const val RAIL_DESTINATION_ICON = 17
internal const val STATS_ALPHA = 0.7f
internal const val STATS_INSET = 12
internal const val STATS_GAP = 10
internal const val STAT_ICON = 18

private const val MIN_CARD = 132
private const val MAX_CARD = 218
internal const val CARD_GAP = 12
internal const val CARD_PADDING = 14
internal const val CARD_ALPHA = 0.86f
internal const val CARD_ICON = 46
internal const val CARD_PILL_GAP = 8
internal const val STATUS_DOT = 8
internal const val PAIRED_ICON = 15
internal const val RESTING_ART_ALPHA = 0.78f
/** Room around a card for the focus ring to sit in without being clipped. */
internal const val CARD_GROWTH = 3
internal const val ADD_CARD_ALPHA = 0.4f
internal const val ADD_MARK = 46
internal const val ADD_ICON = 26

internal const val HEADER_ACTION_WIDTH = 132
internal const val HEADER_HELP_WIDTH = 100
internal const val HEADER_ACTION_GAP = 6

internal const val BAND_PADDING = 13
internal const val BAND_GAP = 12
internal const val BAND_MARK = 44
internal const val BAND_ICON = 24
internal const val BAND_ACTION_WIDTH = 138
internal const val BAND_ACTION_GAP = 8
internal const val PIN_TRACKING = 6

internal const val EMPTY_PADDING = 24
internal const val EMPTY_MARK = 70
internal const val EMPTY_ICON = 36
internal const val EMPTY_ACTION_WIDTH = 232

internal const val FORM_GAP = 18
internal const val FORM_ROW_GAP = 10
internal const val SUBMIT_WIDTH = 168
internal const val HELP_WIDTH = 274
internal const val HELP_PADDING = 16
internal const val HELP_GAP = 11
internal const val HELP_NUMBER = 22
internal const val HELP_ART_HEIGHT = 116
internal const val HELP_ART_ICON = 56
internal const val HELP_MARKER = 3
internal const val HELP_BUTTON_WIDTH = 86

internal const val NAME_FIELD_ID = "stream-couch-host-name"
internal const val COUCH_ADDRESS_FIELD_ID = "stream-couch-host-address"
