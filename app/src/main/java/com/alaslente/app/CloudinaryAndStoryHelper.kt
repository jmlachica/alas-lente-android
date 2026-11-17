package com.alaslente.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Converts a raw Cloudinary URL into a fast CDN-resized WebP/AVIF URL for display,
 * while leaving the original raw URL untouched in Firestore.
 */
fun String.toOptimizedImageUrl(maxWidth: Int = 1080): String {
    if (isBlank() || !contains("res.cloudinary.com") || !contains("/image/upload/")) return this
    if (contains("/image/upload/f_auto")) return this
    return replace("/image/upload/", "/image/upload/f_auto,q_auto,w_$maxWidth,c_limit/")
}

/**
 * Streams the exact original file bytes from ContentResolver to Cloudinary
 * with zero downscaling and zero re-compression.
 */
suspend fun uploadMediaToCloudinary(context: Context, uri: Uri): MediaItem =
    withContext(Dispatchers.IO) {
        val contentResolver = context.contentResolver
        val mimeType = contentResolver.getType(uri) ?: "image/jpeg"
        val isVideo = mimeType.startsWith("video/")

        var originalFileName = "upload_${System.currentTimeMillis()}"
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) {
                    val name = cursor.getString(idx)
                    if (!name.isNullOrBlank()) originalFileName = name
                }
            }
        }

        val tempFile = File(context.cacheDir, "raw_${System.currentTimeMillis()}_$originalFileName")
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output, bufferSize = 64 * 1024)
                }
            } ?: throw IllegalStateException("Unable to read selected file.")

            val resourceType = if (isVideo) "video" else "image"
            val endpoint = "https://api.cloudinary.com/v1_1/$CLOUDINARY_CLOUD_NAME/$resourceType/upload"

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("upload_preset", CLOUDINARY_UPLOAD_PRESET)
                .addFormDataPart(
                    "file",
                    originalFileName,
                    tempFile.asRequestBody(mimeType.toMediaTypeOrNull())
                )
                .build()

            val request = Request.Builder()
                .url(endpoint)
                .post(requestBody)
                .build()

            val client = OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(180, TimeUnit.SECONDS)
                .readTimeout(180, TimeUnit.SECONDS)
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException("Cloudinary upload failed (${response.code}): $bodyStr")
                }

                val json = JSONObject(bodyStr)
                val secureUrl = json.getString("secure_url")
                val publicId = json.optString("public_id", "")

                val thumbUrl = if (isVideo) {
                    secureUrl
                        .replace("/video/upload/", "/video/upload/so_0,f_jpg,q_auto,w_1080/")
                        .replace(Regex("\\.(mp4|mov|webm|mkv|avi)$", RegexOption.IGNORE_CASE), ".jpg")
                } else {
                    secureUrl
                }

                MediaItem(
                    mediaUrl = secureUrl,
                    thumbnailUrl = thumbUrl,
                    mediaType = if (isVideo) "video" else "image",
                    publicId = publicId
                )
            }
        } finally {
            if (tempFile.exists()) tempFile.delete()
        }
    }

/**
 * Renders the 1080x1920 9:16 Story card matching the website output:
 * - Blurred ambient photo backdrop behind the card
 * - Crisp 100% opaque foreground photo sized to its natural aspect ratio
 * - Exact website typography, time pill, location/date footer, and bottom brand pill
 */
suspend fun renderStoryBitmap(
    context: Context,
    post: MomentPost,
    slideIndex: Int,
    storyStyle: StoryStyle
): Bitmap = withContext(Dispatchers.IO) {
    val width = 1080
    val height = 1920
    val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)

    val isLight = storyStyle == StoryStyle.AMBIENT_LIGHT
    val isMatte = storyStyle == StoryStyle.MATTE_DARK

    // 1. Solid base background
    val baseBgColor = when (storyStyle) {
        StoryStyle.AMBIENT_DARK -> AndroidColor.parseColor("#0D0D12")
        StoryStyle.AMBIENT_LIGHT -> AndroidColor.parseColor("#EBE6DA")
        StoryStyle.MATTE_DARK -> AndroidColor.parseColor("#0B0B0F")
    }
    canvas.drawColor(baseBgColor)

    // 2. Load photo as software ARGB_8888 bitmap
    val items = post.resolvedMediaItems()
    val activeItem = items.getOrNull(slideIndex) ?: items.firstOrNull()
    val rawUrl = activeItem?.thumbnailUrl?.ifBlank { activeItem.mediaUrl }.orEmpty()
    val fetchUrl = rawUrl.toOptimizedImageUrl(1440)

    val photoBitmap: Bitmap? = if (fetchUrl.isNotBlank()) {
        try {
            val loader = SingletonImageLoader.get(context)
            val request = ImageRequest.Builder(context)
                .data(fetchUrl)
                .allowHardware(false)
                .build()
            val result = loader.execute(request)
            (result as? SuccessResult)?.image?.toBitmap()
        } catch (_: Exception) {
            null
        }
    } else {
        null
    }

    // 3. Draw blurred ambient photo backdrop (behind everything else)
    if (!isMatte && photoBitmap != null && photoBitmap.width > 0 && photoBitmap.height > 0) {
        val blurredBg = createBlurredCoverBitmap(photoBitmap, width, height, scaleMultiplier = 1.18f)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            alpha = 255
        }
        canvas.drawBitmap(blurredBg, 0f, 0f, bgPaint)
        blurredBg.recycle()
    }

    // 4. Draw translucent atmosphere gradient over the backdrop ONLY
    val scrimColors = when {
        isMatte -> intArrayOf(
            AndroidColor.parseColor("#FF191820"),
            AndroidColor.parseColor("#FF0B0B0F")
        )
        isLight -> intArrayOf(
            AndroidColor.parseColor("#ADF6F3EC"),
            AndroidColor.parseColor("#8CF6F3EC"),
            AndroidColor.parseColor("#C2F0EBE0")
        )
        else -> intArrayOf(
            AndroidColor.parseColor("#8F08080C"),
            AndroidColor.parseColor("#6B08080C"),
            AndroidColor.parseColor("#AD08080C")
        )
    }
    val scrimPositions = if (isMatte) floatArrayOf(0f, 1f) else floatArrayOf(0f, 0.5f, 1f)
    val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            scrimColors,
            scrimPositions,
            Shader.TileMode.CLAMP
        )
    }
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)

    // 5. Calculate dynamic card height based on photo aspect ratio and caption length
    val cardWidth = 880f
    val cardLeft = (width - cardWidth) / 2f
    val cardPad = 36f
    val innerWidth = cardWidth - (cardPad * 2f) // 808f

    val cardBgColor = if (isLight) AndroidColor.parseColor("#EBFFFFFF") else AndroidColor.parseColor("#E0121218")
    val cardBorderColor = if (isLight) AndroidColor.parseColor("#1A141418") else AndroidColor.parseColor("#29FFFFFF")
    val accentColor = if (isLight) AndroidColor.parseColor("#C86D1F") else AndroidColor.parseColor("#F09446")
    val textColor = if (isLight) AndroidColor.parseColor("#18181B") else AndroidColor.parseColor("#F4F3EF")
    val mutedTextColor = if (isLight) AndroidColor.parseColor("#C718181B") else AndroidColor.parseColor("#C7F4F3EF")
    val pillBgColor = if (isLight) AndroidColor.parseColor("#12141418") else AndroidColor.parseColor("#1AFFFFFF")

    val headerHeight = 52f
    val sectionGap = 26f

    // Fit image frame cleanly to the photo's natural aspect ratio (no sidebars)
    val imgFrameWidth: Float
    val imgFrameHeight: Float
    if (photoBitmap != null && photoBitmap.width > 0 && photoBitmap.height > 0) {
        val ratio = photoBitmap.height.toFloat() / photoBitmap.width.toFloat()
        val naturalHeight = innerWidth * ratio
        if (naturalHeight > 920f) {
            imgFrameHeight = 920f
            imgFrameWidth = (920f / ratio).coerceIn(480f, innerWidth)
        } else {
            imgFrameWidth = innerWidth
            imgFrameHeight = naturalHeight.coerceAtLeast(360f)
        }
    } else {
        imgFrameWidth = 0f
        imgFrameHeight = 0f
    }

    val captionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        textSize = 34f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }
    val captionText = post.caption.trim()
    val captionLayout: StaticLayout? = if (captionText.isNotEmpty()) {
        StaticLayout.Builder.obtain(captionText, 0, captionText.length, captionPaint, innerWidth.toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(10f, 1f)
            .setMaxLines(4)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
    } else {
        null
    }

    val footerHeight = 40f

    var totalCardHeight = cardPad + headerHeight
    if (imgFrameHeight > 0f) totalCardHeight += sectionGap + imgFrameHeight
    if (captionLayout != null) totalCardHeight += sectionGap + captionLayout.height.toFloat()
    totalCardHeight += sectionGap + footerHeight + cardPad

    val badgeGap = 28f
    val badgeHeight = 54f
    val totalBlockHeight = totalCardHeight + badgeGap + badgeHeight
    val cardTop = ((height - totalBlockHeight) / 2f).coerceAtLeast(80f)
    val cardBottom = cardTop + totalCardHeight

    // 6. Draw card surface & border
    val cardRect = RectF(cardLeft, cardTop, cardLeft + cardWidth, cardBottom)
    val cardCorner = 44f
    val cardFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = cardBgColor
        style = Paint.Style.FILL
    }
    val cardStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = cardBorderColor
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }
    canvas.drawRoundRect(cardRect, cardCorner, cardCorner, cardFillPaint)
    canvas.drawRoundRect(cardRect, cardCorner, cardCorner, cardStrokePaint)

    var cursorY = cardTop + cardPad
    val contentLeft = cardLeft + cardPad
    val contentRight = cardLeft + cardWidth - cardPad

    // 7. Draw Header Row (Orange dot + Alas label on left, Time pill on right)
    val dotRadius = 11f
    val headerCenterY = cursorY + (headerHeight / 2f)
    val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accentColor
        style = Paint.Style.FILL
    }
    canvas.drawCircle(contentLeft + dotRadius, headerCenterY, dotRadius, dotPaint)

    val timeStr = post.formatted12h.ifBlank { post.capturedTime }
    val timePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        textSize = 27f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    val timeTextWidth = timePaint.measureText(timeStr)
    val timePillPadH = 22f
    val timePillWidth = timeTextWidth + (timePillPadH * 2f)
    val timePillRect = RectF(
        contentRight - timePillWidth,
        cursorY + 2f,
        contentRight,
        cursorY + headerHeight - 2f
    )
    val timePillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = pillBgColor
        style = Paint.Style.FILL
    }
    canvas.drawRoundRect(timePillRect, 999f, 999f, timePillPaint)

    val timeFontMetrics = timePaint.fontMetrics
    val timeBaseline = timePillRect.centerY() - (timeFontMetrics.ascent + timeFontMetrics.descent) / 2f
    canvas.drawText(timeStr, timePillRect.left + timePillPadH, timeBaseline, timePaint)

    val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accentColor
        textSize = 33f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val maxLabelWidth = (timePillRect.left - 18f) - (contentLeft + dotRadius * 2f + 16f)
    val alasText = TextUtils.ellipsize(
        post.alasLabel.ifBlank { "Alas-Lente" },
        labelPaint,
        maxLabelWidth.coerceAtLeast(120f),
        TextUtils.TruncateAt.END
    ).toString()
    val labelFm = labelPaint.fontMetrics
    val labelBaseline = headerCenterY - (labelFm.ascent + labelFm.descent) / 2f
    canvas.drawText(alasText, contentLeft + dotRadius * 2f + 16f, labelBaseline, labelPaint)

    cursorY += headerHeight

    // 8. Draw Foreground Photo at 100% FULL OPACITY (alpha = 255)
    if (photoBitmap != null && imgFrameHeight > 0f) {
        cursorY += sectionGap
        val imgLeft = cardLeft + (cardWidth - imgFrameWidth) / 2f
        val imgRect = RectF(imgLeft, cursorY, imgLeft + imgFrameWidth, cursorY + imgFrameHeight)
        val imgCorner = 26f

        val clipPath = Path().apply {
            addRoundRect(imgRect, imgCorner, imgCorner, Path.Direction.CW)
        }

        canvas.save()
        canvas.clipPath(clipPath)

        // Fresh 100% opaque Paint so background/card layers never wash out the photo
        val crispPhotoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            alpha = 255
            xfermode = null
            colorFilter = null
        }

        val srcW = photoBitmap.width.toFloat()
        val srcH = photoBitmap.height.toFloat()
        val scale = maxOf(imgRect.width() / srcW, imgRect.height() / srcH)
        val scaledW = srcW * scale
        val scaledH = srcH * scale
        val drawLeft = imgRect.left + (imgRect.width() - scaledW) / 2f
        val drawTop = imgRect.top + (imgRect.height() - scaledH) / 2f
        val destRect = RectF(drawLeft, drawTop, drawLeft + scaledW, drawTop + scaledH)

        canvas.drawBitmap(photoBitmap, null, destRect, crispPhotoPaint)
        canvas.restore()

        cursorY += imgFrameHeight
    }

    // 9. Draw Caption
    if (captionLayout != null) {
        cursorY += sectionGap
        canvas.save()
        canvas.translate(contentLeft, cursorY)
        captionLayout.draw(canvas)
        canvas.restore()
        cursorY += captionLayout.height.toFloat()
    }

    // 10. Draw Footer Row (Location on left, Date on right)
    cursorY += sectionGap
    val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = mutedTextColor
        textSize = 26f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
    }
    val footerFm = footerPaint.fontMetrics
    val footerBaseline = (cursorY + footerHeight / 2f) - (footerFm.ascent + footerFm.descent) / 2f

    val dateStr = post.dateLabel.trim()
    val dateWidth = if (dateStr.isNotEmpty()) footerPaint.measureText(dateStr) else 0f
    if (dateStr.isNotEmpty()) {
        canvas.drawText(dateStr, contentRight - dateWidth, footerBaseline, footerPaint)
    }

    val rawLoc = if (post.location.isNotBlank()) "📍 ${post.location.trim()}" else "Alas-Lente Archive"
    val maxLocWidth = (contentRight - dateWidth - 20f) - contentLeft
    val locStr = TextUtils.ellipsize(
        rawLoc,
        footerPaint,
        maxLocWidth.coerceAtLeast(120f),
        TextUtils.TruncateAt.END
    ).toString()
    canvas.drawText(locStr, contentLeft, footerBaseline, footerPaint)

    // 11. Draw Bottom Brand Pill
    val brandText = "ALAS-LENTE  •  BAWAT ORAS MAY KWENTO"
    val brandPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = mutedTextColor
        textSize = 23f
        letterSpacing = 0.06f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
    }
    val brandTextW = brandPaint.measureText(brandText)
    val brandPillW = brandTextW + 56f
    val brandPillLeft = (width - brandPillW) / 2f
    val brandPillTop = cardBottom + badgeGap
    val brandPillRect = RectF(brandPillLeft, brandPillTop, brandPillLeft + brandPillW, brandPillTop + badgeHeight)

    val brandPillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (isLight) AndroidColor.parseColor("#C7FFFFFF") else AndroidColor.parseColor("#A60E0E13")
        style = Paint.Style.FILL
    }
    canvas.drawRoundRect(brandPillRect, 999f, 999f, brandPillPaint)
    canvas.drawRoundRect(brandPillRect, 999f, 999f, cardStrokePaint)

    val brandFm = brandPaint.fontMetrics
    val brandBaseline = brandPillRect.centerY() - (brandFm.ascent + brandFm.descent) / 2f
    canvas.drawText(brandText, brandPillRect.left + 28f, brandBaseline, brandPaint)

    output
}

private fun createBlurredCoverBitmap(
    source: Bitmap,
    targetWidth: Int,
    targetHeight: Int,
    scaleMultiplier: Float
): Bitmap {
    val smallW = (targetWidth / 24).coerceAtLeast(24)
    val smallH = (targetHeight / 24).coerceAtLeast(42)
    val tiny = Bitmap.createBitmap(smallW, smallH, Bitmap.Config.ARGB_8888)
    val tinyCanvas = Canvas(tiny)

    val srcW = source.width.toFloat()
    val srcH = source.height.toFloat()
    val scale = maxOf(smallW / srcW, smallH / srcH) * scaleMultiplier
    val drawW = srcW * scale
    val drawH = srcH * scale
    val left = (smallW - drawW) / 2f
    val top = (smallH - drawH) / 2f

    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    tinyCanvas.drawBitmap(source, null, RectF(left, top, left + drawW, top + drawH), paint)

    val blurred = Bitmap.createScaledBitmap(tiny, targetWidth, targetHeight, true)
    tiny.recycle()
    return blurred
}

suspend fun saveStoryToGallery(context: Context, bitmap: Bitmap, postId: String): Boolean =
    withContext(Dispatchers.IO) {
        try {
            val filename = "AlasLente_${postId}_${System.currentTimeMillis()}.png"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Alas-Lente")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext false

            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            } ?: return@withContext false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

fun shareStoryToApps(context: Context, bitmap: Bitmap, post: MomentPost) {
    // 1. Copy exact post link to clipboard automatically
    val postLink = "$SITE_BASE_URL/?post=${post.id}"
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("Alas-Lente Post Link", postLink))

    // 2. Write PNG to cache & launch Android Share Sheet
    val shareDir = File(context.cacheDir, "shared_stories").apply { mkdirs() }
    val imageFile = File(shareDir, "story_${post.id}_${System.currentTimeMillis()}.png")
    imageFile.outputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    }

    val contentUri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        imageFile
    )

    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, contentUri)
        putExtra(Intent.EXTRA_TEXT, postLink)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    val chooser = Intent.createChooser(shareIntent, "Share Alas-Lente Story").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
}