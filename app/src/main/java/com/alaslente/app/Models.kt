package com.alaslente.app

import android.net.Uri
import androidx.compose.ui.graphics.Color

const val ADMIN_UID = "WTXSE615OseGNroYdtp5LX9BBBJ2"
const val CLOUDINARY_CLOUD_NAME = "i2whruq6"
const val CLOUDINARY_UPLOAD_PRESET = "alas_lente_uploads"
const val SITE_BASE_URL = "https://alas-lente.vercel.app"
const val MAX_MEDIA_ITEMS = 4
const val MAX_CAPTION_CHARS = 280

data class MediaItem(
    val mediaUrl: String,
    val thumbnailUrl: String,
    val mediaType: String = "image",
    val publicId: String? = null
)

data class MomentPost(
    val id: String,
    val mediaItems: List<MediaItem>,
    val mediaUrl: String,
    val thumbnailUrl: String,
    val mediaType: String,
    val caption: String,
    val location: String,
    val capturedTime: String,
    val alasLabel: String,
    val formatted12h: String,
    val timePeriod: String,
    val dateLabel: String
) {
    fun resolvedMediaItems(): List<MediaItem> {
        if (mediaItems.isNotEmpty()) return mediaItems
        if (mediaUrl.isBlank()) return emptyList()
        return listOf(
            MediaItem(
                mediaUrl = mediaUrl,
                thumbnailUrl = thumbnailUrl.ifBlank { mediaUrl },
                mediaType = mediaType.ifBlank { "image" }
            )
        )
    }
}

data class EditableMediaEntry(
    val id: String,
    val uri: Uri? = null,
    val existingItem: MediaItem? = null,
    val isVideo: Boolean = false
)

enum class StoryStyle(val label: String, val nextLabel: String) {
    AMBIENT_DARK("Ambient Dark", "Ambient Light"),
    AMBIENT_LIGHT("Ambient Light", "Matte Dark"),
    MATTE_DARK("Matte Dark", "Ambient Dark");

    fun next(): StoryStyle = entries[(ordinal + 1) % entries.size]
}

data class AlasColors(
    val isDark: Boolean,
    val bg: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val text: Color,
    val textMuted: Color,
    val border: Color,
    val accent: Color,
    val accentSoft: Color,
    val danger: Color
)

val DarkroomColors = AlasColors(
    isDark = true,
    bg = Color(0xFF0B0B0E),
    surface = Color(0xFF141419),
    surfaceAlt = Color(0xFF1D1D24),
    text = Color(0xFFF3F2EE),
    textMuted = Color(0xFF94939C),
    border = Color(0xFF282832),
    accent = Color(0xFFF09446),
    accentSoft = Color(0x24F09446),
    danger = Color(0xFFEF5B5B)
)

val LiwanagColors = AlasColors(
    isDark = false,
    bg = Color(0xFFF6F5F0),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFEEECE4),
    text = Color(0xFF151517),
    textMuted = Color(0xFF64635E),
    border = Color(0xFFDCD9CE),
    accent = Color(0xFFC86D1F),
    accentSoft = Color(0x1FC86D1F),
    danger = Color(0xFFC93B3B)
)