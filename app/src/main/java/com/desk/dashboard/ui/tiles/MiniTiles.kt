package com.desk.dashboard.ui.tiles

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.desk.dashboard.ui.Dash
import com.desk.dashboard.ui.DashIcons
import com.desk.dashboard.ui.Icon
import com.desk.dashboard.ui.Txt
import com.desk.dashboard.ui.Type

// Half-height, one-column tiles stacked in one grid cell: 10 dp padding, small label, tap anywhere.

private val miniLabel = Type.small.copy(letterSpacing = Type.label.letterSpacing)
private val miniValue = Type.display16.copy(fontSize = 15.sp, lineHeight = 16.sp)

@Composable
private fun MiniTile(
    label: String,
    fill: Color,
    ink: Color,
    modifier: Modifier,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(modifier.background(fill).clickable(onClick = onClick).padding(10.dp)) {
        Txt(label.uppercase(), miniLabel, ink)
        Spacer(Modifier.weight(1f))
        content()
    }
}

/** Ambient sound: Off → Noise → Rain. Sound itself arrives in milestone 7. */
@Composable
fun AmbientMini(modifier: Modifier = Modifier) {
    MiniTile("Ambient", Dash.sand, Dash.onSand, modifier, onClick = {}) {
        Txt("OFF", miniValue, Dash.onSand)
        Spacer(Modifier.height(5.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(3) { i ->
                Box(
                    Modifier.size(6.dp).clip(CircleShape)
                        .border(1.dp, Dash.onSand, CircleShape)
                        .background(if (i == 0) Dash.onSand else Dash.sand)
                )
            }
        }
    }
}

@Composable
fun ReadingMini(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    MiniTile("ReadEra", Dash.tile, Dash.ink, modifier, onClick = { openReadEra(context) }) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(Dash.ink), contentAlignment = Alignment.Center) {
            Icon(DashIcons.arrow, Dash.tile, 12.dp)
        }
    }
}

private const val READERA = "org.readera"

fun openReadEra(context: Context) {
    val launch = context.packageManager.getLaunchIntentForPackage(READERA)
    val intent = launch ?: Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$READERA"))
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$READERA"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
