package com.alaslente.app

import android.graphics.Bitmap
import android.net.Uri
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

// Reusable Website-Styled Modal Shell (.modal-backdrop + .modal-box)
@Composable
fun WebsiteModalShell(
    colors: AlasColors,
    maxWidth: Dp = 380.dp,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.82f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = maxWidth)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.border, RoundedCornerShape(18.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp),
                content = content
            )
        }
    }
}

// Reusable Compact .btn from style.css
@Composable
fun WebsiteButton(
    text: String,
    colors: AlasColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
    enabled: Boolean = true,
    horizontalPadding: Dp = 12.dp,
    verticalPadding: Dp = 8.dp,
    fontSizeSp: Int = 12,
    mono: Boolean = false
) {
    val bgColor = when {
        !enabled -> colors.surfaceAlt.copy(alpha = 0.55f)
        isPrimary -> colors.accent
        else -> colors.surfaceAlt
    }
    val borderColor = if (isPrimary) colors.accent else colors.border
    val textColor = when {
        !enabled -> colors.textMuted
        isPrimary -> Color.White
        else -> colors.text
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = fontSizeSp.sp,
            fontWeight = if (isPrimary) FontWeight.SemiBold else FontWeight.Medium,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            maxLines = 1
        )
    }
}

// Reusable Website Input matching .form-group input / textarea
@Composable
fun WebsiteTextInput(
    value: String,
    onValueChange: (String) -> Unit,
    colors: AlasColors,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    minHeight: Dp = 42.dp,
    mono: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardType: KeyboardType = KeyboardType.Text,
    trailing: (@Composable () -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .defaultMinSize(minHeight = minHeight)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.bg)
            .border(
                width = 1.dp,
                color = if (isFocused) colors.accent else colors.border,
                shape = RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 13.dp, vertical = 10.dp),
        verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top
    ) {
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty() && placeholder.isNotEmpty()) {
                Text(
                    text = placeholder,
                    color = colors.textMuted.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                visualTransformation = visualTransformation,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                cursorBrush = SolidColor(colors.accent),
                textStyle = TextStyle(
                    color = colors.text,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { isFocused = it.isFocused }
            )
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(8.dp))
            trailing()
        }
    }
}

// 1. Full-Screen Lightbox Viewer with Zoom, Pan & Video Support
@Composable
fun LightboxDialog(
    post: MomentPost,
    initialIndex: Int,
    colors: AlasColors,
    onDismiss: () -> Unit,
    onShareStory: (MomentPost, Int) -> Unit
) {
    val items = remember(post) { post.resolvedMediaItems() }
    var currentIndex by remember {
        mutableIntStateOf(initialIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)))
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val activeItem = items.getOrNull(currentIndex) ?: return

    LaunchedEffect(currentIndex) {
        scale = 1f
        offset = Offset.Zero
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.bg.copy(alpha = 0.96f))
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = post.alasLabel.ifBlank { "Alas-Lente" },
                        color = colors.accent,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    val sub = listOf(
                        post.formatted12h.ifBlank { post.capturedTime },
                        post.dateLabel
                    ).filter { it.isNotBlank() }.joinToString(" • ")
                    Text(
                        text = sub,
                        color = colors.textMuted,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(colors.surface)
                        .border(1.dp, colors.border, CircleShape)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✕", color = colors.text, fontSize = 13.sp)
                }
            }

            // Controls Row (Counter + Zoom Pill + Share Story Pill)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (items.size > 1) {
                    Text(
                        text = "${currentIndex + 1} / ${items.size}",
                        color = colors.textMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (activeItem.mediaType != "video") {
                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(colors.surface)
                                .border(1.dp, colors.border, CircleShape)
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable(enabled = scale > 1f) {
                                        scale = (scale - 0.5f).coerceAtLeast(1f)
                                        if (scale == 1f) offset = Offset.Zero
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    "−",
                                    color = if (scale > 1f) colors.text else colors.textMuted.copy(alpha = 0.4f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable {
                                        scale = 1f
                                        offset = Offset.Zero
                                    }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    "${(scale * 100).toInt()}%",
                                    color = colors.text,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable(enabled = scale < 3.5f) {
                                        scale = (scale + 0.5f).coerceAtMost(3.5f)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    "+",
                                    color = if (scale < 3.5f) colors.text else colors.textMuted.copy(alpha = 0.4f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(colors.accent)
                            .clickable {
                                onDismiss()
                                onShareStory(post, currentIndex)
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Share Story",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Lightbox Stage
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (colors.isDark) Color(0xFF050507) else colors.surfaceAlt)
                    .border(1.dp, colors.border, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (activeItem.mediaType == "video") {
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                val mc = MediaController(ctx)
                                mc.setAnchorView(this)
                                setMediaController(mc)
                                setVideoURI(Uri.parse(activeItem.mediaUrl))
                                setOnPreparedListener { mp ->
                                    mp.isLooping = true
                                    start()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    AsyncImage(
                        model = activeItem.mediaUrl,
                        contentDescription = post.caption,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(currentIndex) {
                                detectTapGestures(
                                    onDoubleTap = {
                                        if (scale == 1f) {
                                            scale = 2f
                                        } else {
                                            scale = 1f
                                            offset = Offset.Zero
                                        }
                                    }
                                )
                            }
                            .pointerInput(currentIndex) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    val nextScale = (scale * zoom).coerceIn(1f, 3.5f)
                                    scale = nextScale
                                    offset = if (nextScale > 1f) {
                                        Offset(offset.x + pan.x, offset.y + pan.y)
                                    } else {
                                        Offset.Zero
                                    }
                                }
                            }
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offset.x
                                translationY = offset.y
                            }
                    )
                }

                if (items.size > 1) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 12.dp)
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xC70F0F14))
                            .border(1.dp, Color(0x38FFFFFF), CircleShape)
                            .clickable {
                                currentIndex = (currentIndex - 1 + items.size) % items.size
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("‹", color = Color.White, fontSize = 20.sp)
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 12.dp)
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xC70F0F14))
                            .border(1.dp, Color(0x38FFFFFF), CircleShape)
                            .clickable {
                                currentIndex = (currentIndex + 1) % items.size
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("›", color = Color.White, fontSize = 20.sp)
                    }
                }
            }

            // Bottom Caption & Thumbnails
            if (post.caption.isNotBlank() || post.location.isNotBlank() || items.size > 1) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (post.caption.isNotBlank()) {
                        Text(text = post.caption, color = colors.text, fontSize = 14.sp, lineHeight = 20.sp)
                    }
                    if (post.location.isNotBlank()) {
                        Text(
                            text = "📍 ${post.location}",
                            color = colors.textMuted,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    if (items.size > 1) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            itemsIndexed(items) { idx, item ->
                                val active = idx == currentIndex
                                AsyncImage(
                                    model = item.thumbnailUrl.ifBlank { item.mediaUrl },
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(width = 46.dp, height = 36.dp)
                                        .clip(RoundedCornerShape(7.dp))
                                        .border(
                                            width = 2.dp,
                                            color = if (active) colors.accent else Color.Transparent,
                                            shape = RoundedCornerShape(7.dp)
                                        )
                                        .graphicsLayer { alpha = if (active) 1f else 0.55f }
                                        .clickable { currentIndex = idx }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// 2. True 9:16 (1080x1920) Story / MyDay Modal (Instant Live Preview + 1080x1920 Export)
@Composable
fun StoryModalDialog(
    post: MomentPost,
    initialSlide: Int,
    colors: AlasColors,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val items = remember(post) { post.resolvedMediaItems() }

    var slideIndex by remember {
        mutableIntStateOf(initialSlide.coerceIn(0, (items.size - 1).coerceAtLeast(0)))
    }
    var storyStyle by remember { mutableStateOf(StoryStyle.AMBIENT_DARK) }
    var isExporting by remember { mutableStateOf(false) }
    var copiedTip by remember { mutableStateOf(false) }

    val activeItem = items.getOrNull(slideIndex) ?: items.firstOrNull()
    val imgUrl = activeItem?.thumbnailUrl?.ifBlank { activeItem.mediaUrl }.orEmpty()

    WebsiteModalShell(colors = colors, maxWidth = 380.dp, onDismiss = onDismiss) {
        // Modal Top Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Story / MyDay (9:16)",
                color = colors.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (items.size > 1) {
                    WebsiteButton(
                        text = "${slideIndex + 1}/${items.size} ↻",
                        colors = colors,
                        onClick = { slideIndex = (slideIndex + 1) % items.size },
                        horizontalPadding = 9.dp,
                        verticalPadding = 6.dp,
                        fontSizeSp = 11,
                        mono = true
                    )
                }

                WebsiteButton(
                    text = storyStyle.nextLabel,
                    colors = colors,
                    onClick = { storyStyle = storyStyle.next() },
                    horizontalPadding = 10.dp,
                    verticalPadding = 6.dp,
                    fontSizeSp = 11
                )

                WebsiteButton(
                    text = "✕",
                    colors = colors,
                    onClick = onDismiss,
                    horizontalPadding = 10.dp,
                    verticalPadding = 6.dp,
                    fontSizeSp = 11
                )
            }
        }

        // Live Interactive 9:16 Story Canvas Preview
        val isLight = storyStyle == StoryStyle.AMBIENT_LIGHT
        val isMatte = storyStyle == StoryStyle.MATTE_DARK
        val canvasBg = when (storyStyle) {
            StoryStyle.AMBIENT_DARK -> Color(0xFF0D0D12)
            StoryStyle.AMBIENT_LIGHT -> Color(0xFFEBE6DA)
            StoryStyle.MATTE_DARK -> Color(0xFF0B0B0F)
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .widthIn(max = 270.dp)
                .fillMaxWidth(0.84f)
                .aspectRatio(9f / 16f)
                .clip(RoundedCornerShape(18.dp))
                .background(canvasBg),
            contentAlignment = Alignment.Center
        ) {
            if (!isMatte && imgUrl.isNotBlank()) {
                AsyncImage(
                    model = imgUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(1.15f)
                        .blur(24.dp)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        when {
                            isMatte -> Brush.verticalGradient(
                                listOf(Color(0xFF191820), Color(0xFF0B0B0F))
                            )
                            isLight -> Brush.verticalGradient(
                                listOf(
                                    Color(0xADF6F3EC),
                                    Color(0x8CF6F3EC),
                                    Color(0xC2F0EBE0)
                                )
                            )
                            else -> Brush.verticalGradient(
                                listOf(
                                    Color(0x8F08080C),
                                    Color(0x6B08080C),
                                    Color(0xAD08080C)
                                )
                            )
                        }
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 34.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterVertically)
            ) {
                val cardBg = if (isLight) Color(0xEBFFFFFF) else Color(0xE0121218)
                val cardBorder = if (isLight) Color(0x1A141418) else Color(0x29FFFFFF)
                val cardAccent = if (isLight) Color(0xFFC86D1F) else Color(0xFFF09446)
                val cardText = if (isLight) Color(0xFF18181B) else Color(0xFFF4F3EF)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(cardBg)
                        .border(1.dp, cardBorder, RoundedCornerShape(14.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(cardAccent, CircleShape)
                            )
                            Text(
                                text = post.alasLabel.ifBlank { "Alas-Lente" },
                                color = cardAccent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (isLight) Color(0x12141418) else Color(0x1AFFFFFF))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = post.formatted12h.ifBlank { post.capturedTime },
                                color = cardText,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    if (imgUrl.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (isLight) Color(0x0A000000) else Color(0x40000000)),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = imgUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 185.dp)
                            )
                        }
                    }

                    if (post.caption.isNotBlank()) {
                        Text(
                            text = post.caption,
                            color = cardText,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (post.location.isNotBlank()) "📍 ${post.location}" else "Alas-Lente Archive",
                            color = cardText.copy(alpha = 0.78f),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (post.dateLabel.isNotBlank()) {
                            Text(
                                text = post.dateLabel,
                                color = cardText.copy(alpha = 0.78f),
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (isLight) Color(0xC7FFFFFF) else Color(0xA60E0E13))
                        .border(1.dp, cardBorder, CircleShape)
                        .padding(horizontal = 9.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "ALAS-LENTE  •  BAWAT ORAS MAY KWENTO",
                        color = cardText.copy(alpha = 0.85f),
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.4.sp
                    )
                }
            }
        }

        // Action Buttons Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            WebsiteButton(
                text = if (isExporting) "Preparing..." else "Share Story",
                colors = colors,
                isPrimary = true,
                enabled = !isExporting,
                verticalPadding = 10.dp,
                onClick = {
                    isExporting = true
                    scope.launch {
                        try {
                            val bmp = renderStoryBitmap(context, post, slideIndex, storyStyle)
                            copiedTip = true
                            shareStoryToApps(context, bmp, post)
                        } finally {
                            isExporting = false
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            )

            WebsiteButton(
                text = "Save 1080×1920",
                colors = colors,
                enabled = !isExporting,
                verticalPadding = 10.dp,
                onClick = {
                    isExporting = true
                    scope.launch {
                        try {
                            val bmp = renderStoryBitmap(context, post, slideIndex, storyStyle)
                            val ok = saveStoryToGallery(context, bmp, post.id)
                            Toast.makeText(
                                context,
                                if (ok) "Saved 1080×1920 to Pictures/Alas-Lente" else "Could not save image",
                                Toast.LENGTH_SHORT
                            ).show()
                        } finally {
                            isExporting = false
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }

        // Helper Tip
        Text(
            text = if (copiedTip) {
                "✓ Exact post link copied! In IG Story, tap Sticker → Link → Paste over the card."
            } else {
                "Tip: Post link copies automatically—in IG Story, tap Sticker → Link → Paste over the card to make it tappable."
            },
            color = if (copiedTip) colors.accent else colors.textMuted,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// 3. Darkroom Admin Login Modal (#loginModal)
@Composable
fun LoginModalDialog(
    colors: AlasColors,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf("") }
    var isSigningIn by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    WebsiteModalShell(colors = colors, maxWidth = 380.dp, onDismiss = onDismiss) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Darkroom Access",
                color = colors.text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            WebsiteButton(
                text = "✕",
                colors = colors,
                onClick = onDismiss,
                horizontalPadding = 11.dp,
                verticalPadding = 7.dp
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("Email", color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            WebsiteTextInput(
                value = email,
                onValueChange = { email = it },
                colors = colors,
                keyboardType = KeyboardType.Email
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("Password", color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            WebsiteTextInput(
                value = password,
                onValueChange = { password = it },
                colors = colors,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardType = KeyboardType.Password,
                trailing = {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.surfaceAlt)
                            .border(1.dp, colors.border, RoundedCornerShape(6.dp))
                            .clickable { showPassword = !showPassword }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (showPassword) "Hide" else "Show",
                            color = colors.textMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            )
        }

        WebsiteButton(
            text = if (isSigningIn) "Signing in..." else "Enter Darkroom",
            colors = colors,
            isPrimary = true,
            enabled = !isSigningIn,
            verticalPadding = 11.dp,
            onClick = {
                if (email.isBlank() || password.isBlank()) return@WebsiteButton
                isSigningIn = true
                statusMsg = "Signing in..."
                scope.launch {
                    try {
                        val res = FirebaseAuth.getInstance()
                            .signInWithEmailAndPassword(email.trim(), password)
                            .await()
                        if (res.user?.uid == ADMIN_UID) {
                            statusMsg = ""
                            onSuccess()
                        } else {
                            FirebaseAuth.getInstance().signOut()
                            statusMsg = "Unauthorized account."
                        }
                    } catch (e: Exception) {
                        statusMsg = "Login failed: ${e.localizedMessage}"
                    } finally {
                        isSigningIn = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        if (statusMsg.isNotBlank()) {
            Text(
                text = statusMsg,
                color = colors.accent,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

// 4. Admin Publish / Edit Moment Modal (#uploadModal)
@Composable
fun UploadEditModalDialog(
    editingPost: MomentPost?,
    colors: AlasColors,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isEditing = editingPost != null

    var caption by remember { mutableStateOf(editingPost?.caption ?: "") }
    var location by remember { mutableStateOf(editingPost?.location ?: "") }
    var dateTaken by remember {
        mutableStateOf(
            editingPost?.dateLabel?.ifBlank { getManilaTodayMMDDYYYY() } ?: getManilaTodayMMDDYYYY()
        )
    }

    val initialTime24 = editingPost?.capturedTime?.ifBlank { getManilaNow24() } ?: getManilaNow24()
    val initParts = initialTime24.split(":")
    val initH24 = initParts.getOrNull(0)?.toIntOrNull() ?: 12
    val initMin = initParts.getOrNull(1)?.toIntOrNull() ?: 0

    var hour12 by remember { mutableIntStateOf(if (initH24 % 12 == 0) 12 else initH24 % 12) }
    var minute by remember { mutableIntStateOf(initMin) }
    var ampm by remember { mutableStateOf(if (initH24 >= 12) "PM" else "AM") }

    var showTimePicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    var mediaEntries by remember {
        mutableStateOf<List<EditableMediaEntry>>(
            editingPost?.resolvedMediaItems()?.map { item ->
                EditableMediaEntry(
                    id = UUID.randomUUID().toString(),
                    existingItem = item,
                    isVideo = item.mediaType == "video"
                )
            } ?: emptyList()
        )
    }

    var statusMsg by remember { mutableStateOf("") }
    var isPublishing by remember { mutableStateOf(false) }

    val selectedTime24 = remember(hour12, minute, ampm) {
        var h24 = hour12 % 12
        if (ampm == "PM") h24 += 12
        "${h24.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
    }
    val formatted12Display = remember(hour12, minute, ampm) {
        "${hour12.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')} $ampm"
    }
    val timeMeta = remember(selectedTime24) { getTimeMetadata(selectedTime24) }

    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val remaining = MAX_MEDIA_ITEMS - mediaEntries.size
            if (remaining <= 0) {
                statusMsg = "Maximum of $MAX_MEDIA_ITEMS items reached."
            } else {
                val added = uris.take(remaining).map { uri ->
                    val mime = context.contentResolver.getType(uri).orEmpty()
                    EditableMediaEntry(
                        id = UUID.randomUUID().toString(),
                        uri = uri,
                        isVideo = mime.startsWith("video/")
                    )
                }
                mediaEntries = mediaEntries + added
                statusMsg = if (uris.size > remaining) {
                    "Only $MAX_MEDIA_ITEMS items allowed per post—extra files skipped."
                } else ""
            }
        }
    }

    WebsiteModalShell(
        colors = colors,
        maxWidth = 480.dp,
        onDismiss = { if (!isPublishing) onDismiss() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isEditing) "Edit Moment" else "Publish New Moment",
                    color = colors.text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                WebsiteButton(
                    text = "✕",
                    colors = colors,
                    onClick = { if (!isPublishing) onDismiss() },
                    horizontalPadding = 11.dp,
                    verticalPadding = 7.dp
                )
            }

            // 1. Caption
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Caption", color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "${caption.length} / $MAX_CAPTION_CHARS",
                        color = if (caption.length >= MAX_CAPTION_CHARS - 20) colors.accent else colors.textMuted,
                        fontSize = 11.sp,
                        fontWeight = if (caption.length >= MAX_CAPTION_CHARS - 20) FontWeight.SemiBold else FontWeight.Normal,
                        fontFamily = FontFamily.Monospace
                    )
                }
                WebsiteTextInput(
                    value = caption,
                    onValueChange = { if (it.length <= MAX_CAPTION_CHARS) caption = it },
                    colors = colors,
                    placeholder = "Anong kwento ng oras na 'to?",
                    singleLine = false,
                    minHeight = 76.dp
                )
            }

            // 2. Time Taken & 3. Date Taken
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text("Time Taken", color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.bg)
                            .border(
                                1.dp,
                                if (showTimePicker) colors.accent else colors.border,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { showTimePicker = true }
                            .padding(start = 12.dp, end = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatted12Display,
                            color = colors.text,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace
                        )
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(if (showTimePicker) colors.accentSoft else Color.Transparent),
                            contentAlignment = Alignment.Center
                        ) {
                            ClockVectorIcon(
                                color = if (showTimePicker) colors.accent else colors.textMuted
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Date Taken", color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("MM-DD-YYYY", color = colors.textMuted, fontSize = 10.sp)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.bg)
                            .border(
                                1.dp,
                                if (showDatePicker) colors.accent else colors.border,
                                RoundedCornerShape(10.dp)
                            )
                            .padding(start = 12.dp, end = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicTextField(
                            value = dateTaken,
                            onValueChange = { raw ->
                                val digits = raw.filter { it.isDigit() }.take(8)
                                dateTaken = when {
                                    digits.length > 4 -> "${digits.substring(0, 2)}-${digits.substring(2, 4)}-${digits.substring(4)}"
                                    digits.length > 2 -> "${digits.substring(0, 2)}-${digits.substring(2)}"
                                    else -> digits
                                }
                            },
                            singleLine = true,
                            cursorBrush = SolidColor(colors.accent),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            textStyle = TextStyle(
                                color = colors.text,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(if (showDatePicker) colors.accentSoft else Color.Transparent)
                                .clickable { showDatePicker = true },
                            contentAlignment = Alignment.Center
                        ) {
                            CalendarVectorIcon(
                                color = if (showDatePicker) colors.accent else colors.textMuted
                            )
                        }
                    }
                }
            }

            // 4. Location (Optional)
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Location", color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("(Optional)", color = colors.textMuted, fontSize = 12.sp)
                }
                WebsiteTextInput(
                    value = location,
                    onValueChange = { location = it },
                    colors = colors,
                    placeholder = "e.g. Intramuros, Manila"
                )
            }

            // 5. Photos / Videos (Max 4, 2-Column Reorder Grid)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Photos / Videos", color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "${mediaEntries.size} / $MAX_MEDIA_ITEMS selected",
                        color = colors.textMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.bg)
                        .dashedBorder(1.5.dp, colors.border, 12.dp)
                        .clickable { mediaPicker.launch("*/*") }
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = "Tap to select photos or videos (max 4)",
                            color = colors.textMuted,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Use ‹ › buttons below to arrange their order",
                            color = colors.textMuted.copy(alpha = 0.78f),
                            fontSize = 11.sp
                        )
                    }
                }

                if (mediaEntries.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        mediaEntries.chunked(2).forEachIndexed { rowIdx, rowEntries ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowEntries.forEachIndexed { colIdx, entry ->
                                    val idx = rowIdx * 2 + colIdx
                                    val model: Any? = entry.uri
                                        ?: entry.existingItem?.thumbnailUrl?.ifBlank { entry.existingItem.mediaUrl }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(4f / 3f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color.Black)
                                            .border(2.dp, colors.border, RoundedCornerShape(10.dp))
                                    ) {
                                        AsyncImage(
                                            model = model,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )

                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopStart)
                                                .padding(6.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(colors.accent)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "#${idx + 1}",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }

                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(6.dp)
                                                .size(22.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xD10B0B0E))
                                                .clickable {
                                                    mediaEntries = mediaEntries.filterIndexed { i, _ -> i != idx }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("✕", color = Color.White, fontSize = 11.sp)
                                        }

                                        Row(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(6.dp),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            if (idx > 0) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(22.dp)
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(Color(0xCC0B0B0E))
                                                        .border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(6.dp))
                                                        .clickable {
                                                            val list = mediaEntries.toMutableList()
                                                            val moved = list.removeAt(idx)
                                                            list.add(idx - 1, moved)
                                                            mediaEntries = list
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text("‹", color = Color.White, fontSize = 13.sp)
                                                }
                                            }
                                            if (idx < mediaEntries.lastIndex) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(22.dp)
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(Color(0xCC0B0B0E))
                                                        .border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(6.dp))
                                                        .clickable {
                                                            val list = mediaEntries.toMutableList()
                                                            val moved = list.removeAt(idx)
                                                            list.add(idx + 1, moved)
                                                            mediaEntries = list
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text("›", color = Color.White, fontSize = 13.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                                if (rowEntries.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            // Submit Button
            WebsiteButton(
                text = if (isEditing) "Save Changes" else "Upload & Publish",
                colors = colors,
                isPrimary = true,
                enabled = !isPublishing,
                verticalPadding = 12.dp,
                onClick = {
                    if (mediaEntries.isEmpty()) {
                        statusMsg = "Please keep or select at least 1 photo or video (up to 4)."
                        return@WebsiteButton
                    }
                    isPublishing = true
                    scope.launch {
                        try {
                            val finalItems = mutableListOf<MediaItem>()
                            val newCount = mediaEntries.count { it.uri != null }
                            var uploaded = 0

                            for (entry in mediaEntries) {
                                if (entry.existingItem != null) {
                                    finalItems.add(entry.existingItem)
                                } else if (entry.uri != null) {
                                    uploaded++
                                    statusMsg = "Uploading $uploaded of $newCount to Cloudinary..."
                                    finalItems.add(uploadMediaToCloudinary(context, entry.uri))
                                }
                            }

                            val payload = hashMapOf<String, Any>(
                                "mediaItems" to finalItems.map { item ->
                                    hashMapOf(
                                        "mediaUrl" to item.mediaUrl,
                                        "thumbnailUrl" to item.thumbnailUrl,
                                        "mediaType" to item.mediaType,
                                        "publicId" to item.publicId
                                    )
                                },
                                "mediaUrl" to finalItems.first().mediaUrl,
                                "thumbnailUrl" to finalItems.first().thumbnailUrl,
                                "mediaType" to finalItems.first().mediaType,
                                "caption" to caption.trim(),
                                "location" to location.trim(),
                                "capturedTime" to selectedTime24,
                                "alasLabel" to timeMeta.alasLabel,
                                "formatted12h" to timeMeta.formatted12h,
                                "timePeriod" to timeMeta.period,
                                "dateLabel" to dateTaken.trim()
                            )

                            val db = FirebaseFirestore.getInstance()
                            if (isEditing && editingPost != null) {
                                statusMsg = "Updating moment in Firestore..."
                                payload["updatedAt"] = FieldValue.serverTimestamp()
                                db.collection("posts").document(editingPost.id).update(payload).await()
                            } else {
                                statusMsg = "Saving moment to Firestore..."
                                payload["createdAt"] = FieldValue.serverTimestamp()
                                db.collection("posts").add(payload).await()
                            }

                            onSaved()
                        } catch (e: Exception) {
                            statusMsg = "Error: ${e.localizedMessage}"
                        } finally {
                            isPublishing = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            if (statusMsg.isNotBlank()) {
                Text(
                    text = statusMsg,
                    color = colors.accent,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }

    if (showTimePicker) {
        TimePickerPopupDialog(
            hour12 = hour12,
            minute = minute,
            ampm = ampm,
            alasPreview = timeMeta.alasLabel,
            colors = colors,
            onHourChange = { hour12 = it },
            onMinuteChange = { minute = it },
            onAmPmChange = { ampm = it },
            onNow = {
                val nowParts = getManilaNow24().split(":")
                val h24 = nowParts[0].toInt()
                hour12 = if (h24 % 12 == 0) 12 else h24 % 12
                minute = nowParts[1].toInt()
                ampm = if (h24 >= 12) "PM" else "AM"
            },
            onDismiss = { showTimePicker = false }
        )
    }

    if (showDatePicker) {
        DatePickerPopupDialog(
            selectedMMDDYYYY = dateTaken,
            colors = colors,
            onSelectDate = { dateTaken = it },
            onDismiss = { showDatePicker = false }
        )
    }
}

@Composable
private fun TimePickerPopupDialog(
    hour12: Int,
    minute: Int,
    ampm: String,
    alasPreview: String,
    colors: AlasColors,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
    onAmPmChange: (String) -> Unit,
    onNow: () -> Unit,
    onDismiss: () -> Unit
) {
    val hours = remember { (1..12).toList() }
    val minutes = remember { (0..59).toList() }
    val hourListState = rememberLazyListState(initialFirstVisibleItemIndex = (hour12 - 1).coerceIn(0, 11))
    val minListState = rememberLazyListState(initialFirstVisibleItemIndex = minute.coerceIn(0, 59))

    WebsiteModalShell(colors = colors, maxWidth = 280.dp, onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = alasPreview,
                color = colors.accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            HorizontalDivider(thickness = 1.dp, color = colors.border)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "HR",
                    color = colors.textMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                LazyColumn(
                    state = hourListState,
                    modifier = Modifier.height(164.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    itemsIndexed(hours) { _, h ->
                        val selected = h == hour12
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) colors.accent else Color.Transparent)
                                .clickable { onHourChange(h) }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = h.toString().padStart(2, '0'),
                                color = if (selected) Color.White else colors.text,
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "MIN",
                    color = colors.textMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                LazyColumn(
                    state = minListState,
                    modifier = Modifier.height(164.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    itemsIndexed(minutes) { _, m ->
                        val selected = m == minute
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) colors.accent else Color.Transparent)
                                .clickable { onMinuteChange(m) }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = m.toString().padStart(2, '0'),
                                color = if (selected) Color.White else colors.text,
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "AM/PM",
                    color = colors.textMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Column(
                    modifier = Modifier.height(164.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    listOf("AM", "PM").forEach { period ->
                        val selected = period == ampm
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) colors.accent else Color.Transparent)
                                .clickable { onAmPmChange(period) }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = period,
                                color = if (selected) Color.White else colors.text,
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(thickness = 1.dp, color = colors.border)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            WebsiteButton(
                text = "Now",
                colors = colors,
                onClick = onNow,
                horizontalPadding = 10.dp,
                verticalPadding = 5.dp,
                fontSizeSp = 11
            )
            WebsiteButton(
                text = "Done",
                colors = colors,
                isPrimary = true,
                onClick = onDismiss,
                horizontalPadding = 12.dp,
                verticalPadding = 5.dp,
                fontSizeSp = 11
            )
        }
    }
}

@Composable
private fun DatePickerPopupDialog(
    selectedMMDDYYYY: String,
    colors: AlasColors,
    onSelectDate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    fun parseDate(str: String): LocalDate? {
        val parts = str.split("-")
        if (parts.size != 3) return null
        val m = parts[0].toIntOrNull() ?: return null
        val d = parts[1].toIntOrNull() ?: return null
        val y = parts[2].toIntOrNull() ?: return null
        return try {
            LocalDate.of(y, m, d)
        } catch (_: Exception) {
            null
        }
    }

    fun formatDate(date: LocalDate): String {
        val mm = date.monthValue.toString().padStart(2, '0')
        val dd = date.dayOfMonth.toString().padStart(2, '0')
        return "$mm-$dd-${date.year}"
    }

    val todayDate = remember { parseDate(getManilaTodayMMDDYYYY()) ?: LocalDate.now() }
    val selectedDate = parseDate(selectedMMDDYYYY) ?: todayDate
    var viewMonth by remember { mutableStateOf(YearMonth.from(selectedDate)) }

    val monthTitle = remember(viewMonth) {
        val name = viewMonth.month.name.lowercase().replaceFirstChar { it.uppercase() }
        "$name ${viewMonth.year}"
    }

    val calendarCells = remember(viewMonth) {
        val firstOfMonth = viewMonth.atDay(1)
        val firstWeekday = firstOfMonth.dayOfWeek.value % 7
        val startDate = firstOfMonth.minusDays(firstWeekday.toLong())
        val totalCells = if (firstWeekday + viewMonth.lengthOfMonth() <= 35) 35 else 42
        (0 until totalCells).map { startDate.plusDays(it.toLong()) }
    }

    WebsiteModalShell(colors = colors, maxWidth = 300.dp, onDismiss = onDismiss) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(colors.surfaceAlt)
                    .border(1.dp, colors.border, RoundedCornerShape(7.dp))
                    .clickable { viewMonth = viewMonth.minusMonths(1) },
                contentAlignment = Alignment.Center
            ) {
                Text("‹", color = colors.text, fontSize = 14.sp)
            }

            Text(
                text = monthTitle,
                color = colors.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )

            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(colors.surfaceAlt)
                    .border(1.dp, colors.border, RoundedCornerShape(7.dp))
                    .clickable { viewMonth = viewMonth.plusMonths(1) },
                contentAlignment = Alignment.Center
            ) {
                Text("›", color = colors.text, fontSize = 14.sp)
            }
        }

        HorizontalDivider(thickness = 1.dp, color = colors.border)

        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa").forEach { dayName ->
                Text(
                    text = dayName,
                    color = colors.textMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            calendarCells.chunked(7).forEach { week ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    week.forEach { cellDate ->
                        val isSelected = cellDate == selectedDate
                        val isToday = cellDate == todayDate
                        val isOtherMonth = cellDate.month != viewMonth.month

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) colors.accent else Color.Transparent)
                                .border(
                                    width = if (isToday || isSelected) 1.dp else 0.dp,
                                    color = if (isToday || isSelected) colors.accent else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    onSelectDate(formatDate(cellDate))
                                    viewMonth = YearMonth.from(cellDate)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = cellDate.dayOfMonth.toString(),
                                color = when {
                                    isSelected -> Color.White
                                    isOtherMonth -> colors.textMuted.copy(alpha = 0.45f)
                                    else -> colors.text
                                },
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(thickness = 1.dp, color = colors.border)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            WebsiteButton(
                text = "Today",
                colors = colors,
                onClick = {
                    onSelectDate(formatDate(todayDate))
                    viewMonth = YearMonth.from(todayDate)
                },
                horizontalPadding = 10.dp,
                verticalPadding = 5.dp,
                fontSizeSp = 11
            )
            WebsiteButton(
                text = "Done",
                colors = colors,
                isPrimary = true,
                onClick = onDismiss,
                horizontalPadding = 12.dp,
                verticalPadding = 5.dp,
                fontSizeSp = 11
            )
        }
    }
}

private fun Modifier.dashedBorder(width: Dp, color: Color, cornerRadius: Dp): Modifier =
    this.then(
        Modifier.drawWithContent {
            drawContent()
            val stroke = Stroke(
                width = width.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
            )
            drawRoundRect(
                color = color,
                style = stroke,
                cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx())
            )
        }
    )

@Composable
private fun ClockVectorIcon(color: Color) {
    Canvas(modifier = Modifier.size(15.dp)) {
        val stroke = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(color = color, style = stroke)
        drawLine(color = color, start = center, end = Offset(center.x, center.y - size.height * 0.25f), strokeWidth = 1.6.dp.toPx(), cap = StrokeCap.Round)
        drawLine(color = color, start = center, end = Offset(center.x + size.width * 0.2f, center.y + size.height * 0.1f), strokeWidth = 1.6.dp.toPx(), cap = StrokeCap.Round)
    }
}

@Composable
private fun CalendarVectorIcon(color: Color) {
    Canvas(modifier = Modifier.size(15.dp)) {
        val stroke = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
        val top = size.height * 0.16f
        drawRoundRect(
            color = color,
            topLeft = Offset(0f, top),
            size = Size(size.width, size.height - top),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            style = stroke
        )
        drawLine(color = color, start = Offset(size.width * 0.3f, 0f), end = Offset(size.width * 0.3f, size.height * 0.28f), strokeWidth = 1.6.dp.toPx(), cap = StrokeCap.Round)
        drawLine(color = color, start = Offset(size.width * 0.7f, 0f), end = Offset(size.width * 0.7f, size.height * 0.28f), strokeWidth = 1.6.dp.toPx(), cap = StrokeCap.Round)
        drawLine(color = color, start = Offset(0f, size.height * 0.44f), end = Offset(size.width, size.height * 0.44f), strokeWidth = 1.5.dp.toPx())
    }
}