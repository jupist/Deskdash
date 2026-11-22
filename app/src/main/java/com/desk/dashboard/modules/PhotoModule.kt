package com.desk.dashboard.modules

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.desk.dashboard.board.Module
import com.desk.dashboard.board.Size
import com.desk.dashboard.board.TileScope
import com.desk.dashboard.ui.Dash
import com.desk.dashboard.ui.DashIcons
import com.desk.dashboard.ui.Icon
import com.desk.dashboard.ui.MiniButton
import com.desk.dashboard.ui.Segmented
import com.desk.dashboard.ui.SettingRow
import com.desk.dashboard.ui.SwitchRow
import com.desk.dashboard.ui.Txt
import com.desk.dashboard.ui.Type
import com.desk.dashboard.ui.pressable
import com.desk.dashboard.ui.tc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

object PhotoModule : Module(
    "photos", "Photo frame", "A slideshow from a folder on the device.",
    DashIcons.image, Size(4, 3), Size(1, 1),
) {
    @Composable
    override fun Content(t: TileScope) {
        val context = LocalContext.current
        val folder = t.strOrNull("folder")?.let(Uri::parse)
        val pick = rememberFolderPicker(t)
        if (folder == null) {
            Column(Modifier.fillMaxSize().pressable({ pick() }), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                Icon(DashIcons.image, tc.muted, 26.dp)
                Spacer(Modifier.height(8.dp))
                Txt("Choose a photo folder", Type.bodySmall, tc.muted)
            }
            return
        }
        val shuffle = t.bool("shuffle", true)
        val photos by produceList(context, folder, shuffle)
        var index by remember { mutableIntStateOf(0) }
        val seconds = t.int("every", 60)
        LaunchedEffect(photos, seconds) {
            while (photos.size > 1) {
                delay(seconds * 1000L)
                index = (index + 1) % photos.size
            }
        }
        BoxWithConstraints(Modifier.fillMaxSize().pressable({ if (photos.isNotEmpty()) index = (index + 1) % photos.size }, 0.99f)) {
            val px = with(LocalDensity.current) { maxOf(maxWidth, maxHeight).roundToPx() }
            val uri = photos.getOrNull(index)
            var image by remember { mutableStateOf<ImageBitmap?>(null) }
            LaunchedEffect(uri, px) { image = uri?.let { decode(context, it, px) } }
            if (photos.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Txt("No photos in that folder", Type.meta, tc.muted) }
            }
            Crossfade(image, animationSpec = tween(900), label = "photo") { img ->
                if (img != null) Image(
                    img, null, Modifier.fillMaxSize(),
                    contentScale = if (t.str("fit", "crop") == "crop") ContentScale.Crop else ContentScale.Fit,
                )
            }
        }
    }

    @Composable
    private fun produceList(context: Context, folder: Uri, shuffle: Boolean) = androidx.compose.runtime.produceState(emptyList<Uri>(), folder, shuffle) {
        value = withContext(Dispatchers.IO) { listImages(context, folder) }.let { if (shuffle) it.shuffled() else it }
    }

    private fun listImages(context: Context, tree: Uri): List<Uri> = runCatching {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val out = mutableListOf<Uri>()
        context.contentResolver.query(
            children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                if (c.getString(1)?.startsWith("image/") == true) out += DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0))
            }
        }
        out.take(500)
    }.getOrDefault(emptyList())

    /** Decodes a photo no larger than needed for [maxPx] (keeps memory low on big photos). */
    private suspend fun decode(context: Context, uri: Uri, maxPx: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.RGB_565 }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }?.asImageBitmap()
        }.getOrNull()
    }

    @Composable
    private fun rememberFolderPicker(t: TileScope): () -> Unit {
        val context = LocalContext.current
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                t.set("folder", uri.toString())
            }
        }
        return { launcher.launch(null) }
    }

    @Composable
    override fun Settings(t: TileScope) {
        val pick = rememberFolderPicker(t)
        SettingRow("Folder", t.strOrNull("folder")?.let { Uri.parse(it).lastPathSegment?.substringAfterLast(':') } ?: "None") {
            MiniButton("Choose", Dash.ink, Dash.accent) { pick() }
        }
        SettingRow("Change every") {
            Segmented(listOf(15, 60, 300, 900), t.int("every", 60), { if (it < 60) "${it}s" else "${it / 60}m" }) { t.set("every", it) }
        }
        SettingRow("Framing") {
            Segmented(listOf("crop", "fit"), t.str("fit", "crop"), { if (it == "crop") "Fill" else "Fit" }) { t.set("fit", it) }
        }
        SwitchRow("Shuffle", checked = t.bool("shuffle", true), onToggle = { t.set("shuffle", !t.bool("shuffle", true)) })
    }
}
