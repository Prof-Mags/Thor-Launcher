package com.thor.core.ui.component

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.model.ClockStyle
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The launcher's own status strip: clock and battery.
 *
 * A launcher draws edge to edge with the system bars transparent, so nothing
 * shows the time unless the launcher draws it. This is what the `clockStyle`
 * and `showStatusBar` settings control — before this existed they were stored
 * and read by nothing.
 */
@Composable
fun LauncherStatusBar(
    clockStyle: ClockStyle,
    visible: Boolean,
    modifier: Modifier = Modifier,
    /**
     * Where the strip sits inside the width it is given.
     *
     * Centred over the information panel, which is the only thing on that row.
     * Couch mode packs it against the right edge instead: it shares a navigation
     * bar with the profile cluster, and centring it there left the time floating
     * in the middle of its own empty column rather than in the corner where a
     * television puts a clock.
     */
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Center,
) {
    if (!visible) return

    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens
    val context = LocalContext.current

    var now by remember { mutableStateOf(Date()) }
    var batteryPercent by remember { mutableStateOf<Int?>(null) }
    var charging by remember { mutableStateOf(false) }

    // Ticking once a minute rather than once a second: the clock only ever
    // displays minutes, and a per-second timer would wake the UI 60x more often
    // for an identical frame.
    if (clockStyle != ClockStyle.HIDDEN) {
        LaunchedEffect(clockStyle) {
            while (true) {
                now = Date()
                delay(MINUTE_MS - (System.currentTimeMillis() % MINUTE_MS))
            }
        }
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                if (level >= 0 && scale > 0) {
                    batteryPercent = (level * 100) / scale
                }
                val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            }
        }
        // A sticky broadcast, so registering returns the current value at once
        // and no initial poll is needed.
        val sticky = context.registerReceiver(
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        )
        receiver.onReceive(context, sticky)

        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.spacing, vertical = 6.dp),
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        batteryPercent?.let { percent ->
            Icon(
                imageVector = if (charging) {
                    Icons.Rounded.BatteryChargingFull
                } else {
                    Icons.Rounded.BatteryFull
                },
                contentDescription = "Battery",
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = "$percent%",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(start = 3.dp, end = dimens.spacingSmall),
            )
        }

        val clockText = remember(now, clockStyle) { formatClock(now, clockStyle) }
        if (clockText != null) {
            Text(
                text = clockText,
                style = MaterialTheme.typography.labelMedium,
                color = colors.onBackground,
            )
        }
    }
}

/**
 * Formats the clock.
 *
 * `ANALOG` renders as text here rather than as a dial: a dial small enough to
 * sit in a status strip is unreadable, so it falls back to the 12-hour form
 * that carries the same information.
 */
private fun formatClock(date: Date, style: ClockStyle): String? {
    val pattern = when (style) {
        ClockStyle.HIDDEN -> return null
        ClockStyle.DIGITAL_12, ClockStyle.ANALOG -> "h:mm a"
        ClockStyle.DIGITAL_24 -> "HH:mm"
    }
    return SimpleDateFormat(pattern, Locale.getDefault()).format(date)
}

private const val MINUTE_MS = 60_000L
