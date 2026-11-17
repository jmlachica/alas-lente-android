package com.alaslente.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlin.math.min

private val okHttpClient = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .writeTimeout(60, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .build()

suspend fun uploadMediaToCloudinary(context: Context, uri: Uri): MediaItem = withContext(Dispatchers.IO) {
    val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: throw IllegalStateException("Could not read selected file.")

    val ext = when {
        mimeType.contains("png") -> "png"
        mimeType.contains("webp") -> "webp"
        mimeType.contains("mp4") -> "mp4"
        mimeType.contains("quicktime") || mimeType.contains("mov") -> "mov"
        else -> "jpg"
    }

    val requestBody = MultipartBody.Builder()
        .setType(MultipartBody.FORM)
        .addFormDataPart(
            "file",
            "alas_lente_${System.currentTimeMillis()}.$ext",
            bytes.toRequestBody(mimeType.toMediaTypeOrNull())
        )
        .addFormDataPart("upload_preset", CLOUDINARY_UPLOAD_PRESET)
        .build()

    val request = Request.Builder()
        .url("https://api.cloudinary.com/v1_1/$CLOUDINARY_CLOUD_NAME/auto/upload")
        .post(requestBody)
        .build()

    okHttpClient.newCall(request).execute().use { response ->
        val bodyStr = response.body?.string().orEmpty()
        val json = JSONObject(bodyStr)
        if (!response.isSuccessful) {
            val errMsg = json.optJSONObject("error")?.optString("message")
                ?: "Cloudinary upload failed (${response.code})"
            throw IllegalStateException(errMsg)
        }

        val isVideo = json.optString("resource_type") == "video"
        val secureUrl = json.getString("secure_url")
        val thumbUrl = if (isVideo) {
            secureUrl.replace(Regex("\\.[^/.]+$"), ".jpg")
        } else {
            secureUrl
        }

        MediaItem(
            mediaUrl = secureUrl,
            thumbnailUrl = thumbUrl,
            mediaType = if (isVideo) "video" else "image",
            publicId = json.optString("public_id").takeIf { it.isNotBlank() }
        )
    }
}

suspend fun renderStoryBitmap(
    context: Context,
    post: MomentPost,
    slideIndex: Int,
    style: StoryStyle
): Bitmap = withContext(Dispatchers.IO) {
    val width = 1080
    val height = 1920
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    val items = post.resolvedMediaItems()
    val activeItem = items.getOrNull(slideIndex) ?: items.firstOrNull()
    val imageUrl = activeItem?.thumbnailUrl?.ifBlank { activeItem.mediaUrl } ?: ""

    val sourceBitmap: Bitmap? = if (imageUrl.isNotBlank()) {
        try {
            val loader = ImageLoader(context)
            val req = ImageRequest.Builder(context)
                .data(imageUrl)
                .allowHardware(false)
                .build()
            val result = loader.execute(req)
            if (result is SuccessResult) result.image.toBitmap() else null
        } catch (_: Exception) {
            null
        }
    } else null

    val isLight = style == StoryStyle.AMBIENT_LIGHT

    // 1. Background Fill + Blurred Atmospheric Photo
    canvas.drawColor(
        when (style) {
            StoryStyle.AMBIENT_DARK -> Color.parseColor("#0D0D12")
            StoryStyle.AMBIENT_LIGHT -> Color.parseColor("#EBE6DA")
            StoryStyle.MATTE_DARK -> Color.parseColor("#0B0B0F")
        }
    )

    if (style == StoryStyle.MATTE_DARK) {
        paint.shader = LinearGradient(
            0f, 0f, width * 0.4f, height.toFloat(),
            Color.parseColor("#191820"),
            Color.parseColor("#0B0B0F"),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = RadialGradient(
            width * 0.5f, height * 0.25f, 750f,
            Color.argb(24, 240, 148, 70),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null
    } else if (sourceBitmap != null) {
        val small = Bitmap.createScaledBitmap(sourceBitmap, 72, 128, true)
        val blurred = fastBoxBlur(small, 6)
        canvas.drawBitmap(blurred, null, RectF(-80f, -80f, width + 80f, height + 80f), paint)

        paint.shader = if (isLight) {
            LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                intArrayOf(
                    Color.argb(180, 246, 243, 236),
                    Color.argb(148, 246, 243, 236),
                    Color.argb(200, 240, 235, 224)
                ),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        } else {
            LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                intArrayOf(
                    Color.argb(150, 8, 8, 12),
                    Color.argb(115, 8, 8, 12),
                    Color.argb(180, 8, 8, 12)
                ),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null
    }

    // 2. Compute Card Dimensions Inside 9:16 Safe Zone
    val cardMarginX = 72f
    val cardWidth = width - (cardMarginX * 2)
    val cardPad = 38f
    val innerWidth = cardWidth - (cardPad * 2)

    val maxMediaHeight = 760f
    val mediaHeight = if (sourceBitmap != null && sourceBitmap.width > 0) {
        val aspect = sourceBitmap.height.toFloat() / sourceBitmap.width.toFloat()
        min(maxMediaHeight, innerWidth * aspect).coerceAtLeast(320f)
    } else {
        520f
    }

    val captionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (isLight) Color.parseColor("#151517") else Color.parseColor("#F3F2EE")
        textSize = 34f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }

    val captionLayout: StaticLayout? = if (post.caption.isNotBlank()) {
        StaticLayout.Builder.obtain(
            post.caption, 0, post.caption.length, captionPaint, innerWidth.toInt()
        )
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(10f, 1f)
            .setMaxLines(4)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
    } else null

    val headerH = 44f
    val footerH = 34f
    val gap = 26f
    val captionBlockH = if (captionLayout != null) captionLayout.height + gap else 0f
    val cardHeight = cardPad + headerH + gap + mediaHeight + captionBlockH + gap + footerH + cardPad

    val pillHeight = 52f
    val totalContentHeight = cardHeight + 36f + pillHeight
    val cardTop = ((height - totalContentHeight) / 2f).coerceAtLeast(190f)
    val cardRect = RectF(cardMarginX, cardTop, width - cardMarginX, cardTop + cardHeight)

    // Card Shadow & Background
    paint.style = Paint.Style.FILL
    paint.color = if (isLight) Color.argb(238, 255, 255, 255) else Color.argb(228, 18, 18, 24)
    canvas.drawRoundRect(cardRect, 52f, 52f, paint)

    // Card Border
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 3f
    paint.color = if (isLight) Color.argb(28, 20, 20, 24) else Color.argb(42, 255, 255, 255)
    canvas.drawRoundRect(cardRect, 52f, 52f, paint)
    paint.style = Paint.Style.FILL

    // 3. Card Header: Rec Dot + Alas Label + Time Pill
    var cursorY = cardTop + cardPad
    val leftX = cardMarginX + cardPad
    val rightX = width - cardMarginX - cardPad

    val accentColor = if (isLight) Color.parseColor("#C86D1F") else Color.parseColor("#F09446")
    paint.color = accentColor
    canvas.drawCircle(leftX + 10f, cursorY + 20f, 10f, paint)

    val alasPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accentColor
        textSize = 31f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    val timeText = post.formatted12h.ifBlank { post.capturedTime }
    val timePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (isLight) Color.parseColor("#27272A") else Color.parseColor("#F3F2EE")
        textSize = 26f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    val timePillW = timePaint.measureText(timeText) + 44f
    val timePillRect = RectF(rightX - timePillW, cursorY - 4f, rightX, cursorY + 42f)
    paint.color = if (isLight) Color.argb(18, 20, 20, 24) else Color.argb(26, 255, 255, 255)
    canvas.drawRoundRect(timePillRect, 999f, 999f, paint)
    canvas.drawText(timeText, timePillRect.left + 22f, cursorY + 28f, timePaint)

    val maxAlasW = (timePillRect.left - (leftX + 34f) - 16f).coerceAtLeast(100f)
    val ellipsizedAlas = TextUtils.ellipsize(
        post.alasLabel.ifBlank { "Alas-Lente" },
        alasPaint,
        maxAlasW,
        TextUtils.TruncateAt.END
    ).toString()
    canvas.drawText(ellipsizedAlas, leftX + 32f, cursorY + 30f, alasPaint)

    cursorY += headerH + gap

    // 4. Uncropped Photo Frame
    val mediaRect = RectF(leftX, cursorY, rightX, cursorY + mediaHeight)
    paint.color = if (isLight) Color.argb(12, 0, 0, 0) else Color.argb(70, 0, 0, 0)
    canvas.drawRoundRect(mediaRect, 34f, 34f, paint)

    if (sourceBitmap != null) {
        canvas.save()
        val clipPath = Path().apply { addRoundRect(mediaRect, 34f, 34f, Path.Direction.CW) }
        canvas.clipPath(clipPath)

        val scale = min(
            mediaRect.width() / sourceBitmap.width.toFloat(),
            mediaRect.height() / sourceBitmap.height.toFloat()
        )
        val drawW = sourceBitmap.width * scale
        val drawH = sourceBitmap.height * scale
        val drawLeft = mediaRect.centerX() - (drawW / 2f)
        val drawTop = mediaRect.centerY() - (drawH / 2f)
        canvas.drawBitmap(
            sourceBitmap,
            null,
            RectF(drawLeft, drawTop, drawLeft + drawW, drawTop + drawH),
            paint
        )
        canvas.restore()
    }

    cursorY += mediaHeight + gap

    // 5. Caption
    if (captionLayout != null) {
        canvas.save()
        canvas.translate(leftX, cursorY)
        captionLayout.draw(canvas)
        canvas.restore()
        cursorY += captionLayout.height + gap
    }

    // 6. Card Footer (Location + Date)
    val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (isLight) Color.argb(190, 21, 21, 23) else Color.argb(195, 243, 242, 238)
        textSize = 25f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
    }
    val locText = if (post.location.isNotBlank()) "📍 ${post.location}" else "Alas-Lente Archive"
    canvas.drawText(locText, leftX, cursorY + 24f, footerPaint)

    if (post.dateLabel.isNotBlank()) {
        val dateW = footerPaint.measureText(post.dateLabel)
        canvas.drawText(post.dateLabel, rightX - dateW, cursorY + 24f, footerPaint)
    }

    // 7. Bottom Brand Pill
    val pillTop = cardRect.bottom + 36f
    val brandText = "ALAS-LENTE  •  BAWAT ORAS MAY KWENTO"
    val brandPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (isLight) Color.argb(205, 24, 24, 27) else Color.argb(220, 244, 243, 239)
        textSize = 22f
        letterSpacing = 0.08f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    val brandTextW = brandPaint.measureText(brandText)
    val pillW = brandTextW + 68f
    val pillLeft = (width - pillW) / 2f
    val pillRect = RectF(pillLeft, pillTop, pillLeft + pillW, pillTop + pillHeight)

    paint.color = if (isLight) Color.argb(200, 255, 255, 255) else Color.argb(170, 14, 14, 19)
    canvas.drawRoundRect(pillRect, 999f, 999f, paint)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 2f
    paint.color = if (isLight) Color.argb(26, 20, 20, 24) else Color.argb(38, 255, 255, 255)
    canvas.drawRoundRect(pillRect, 999f, 999f, paint)
    paint.style = Paint.Style.FILL

    canvas.drawText(brandText, pillLeft + 34f, pillTop + 34f, brandPaint)

    bitmap
}

private fun fastBoxBlur(src: Bitmap, radius: Int): Bitmap {
    val w = src.width
    val h = src.height
    val pixels = IntArray(w * h)
    src.getPixels(pixels, 0, w, 0, 0, w, h)
    val temp = IntArray(w * h)

    repeat(2) {
        for (y in 0 until h) {
            for (x in 0 until w) {
                var r = 0
                var g = 0
                var b = 0
                var count = 0
                for (dx in -radius..radius) {
                    val nx = (x + dx).coerceIn(0, w - 1)
                    val c = pixels[y * w + nx]
                    r += Color.red(c)
                    g += Color.green(c)
                    b += Color.blue(c)
                    count++
                }
                temp[y * w + x] = Color.rgb(r / count, g / count, b / count)
            }
        }
        for (x in 0 until w) {
            for (y in 0 until h) {
                var r = 0
                var g = 0
                var b = 0
                var count = 0
                for (dy in -radius..radius) {
                    val ny = (y + dy).coerceIn(0, h - 1)
                    val c = temp[ny * w + x]
                    r += Color.red(c)
                    g += Color.green(c)
                    b += Color.blue(c)
                    count++
                }
                pixels[y * w + x] = Color.rgb(r / count, g / count, b / count)
            }
        }
    }

    return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
        setPixels(pixels, 0, w, 0, 0, w, h)
    }
}

suspend fun saveStoryToGallery(context: Context, bitmap: Bitmap, postId: String): Boolean =
    withContext(Dispatchers.IO) {
        try {
            val filename = "alas-lente-story-$postId-${System.currentTimeMillis()}.png"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_PICTURES}/Alas-Lente"
                    )
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return@withContext false

            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
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

suspend fun shareStoryToApps(context: Context, bitmap: Bitmap, post: MomentPost) {
    // 1. Copy exact post permalink to clipboard for IG Story Link sticker
    val postUrl = "$SITE_BASE_URL/?post=${post.id}"
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Alas-Lente Moment", postUrl))

    // 2. Write 1080x1920 PNG to cache and share via Android Share Sheet
    val fileUri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "shared_stories").apply { mkdirs() }
        val file = File(dir, "alas-lente-story-${post.id}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, fileUri)
        putExtra(Intent.EXTRA_TEXT, postUrl)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, "Share Alas-Lente Story"))
}