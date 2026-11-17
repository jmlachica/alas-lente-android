package com.alaslente.app

import android.app.DatePickerDialog
import android.graphics.Bitmap
import android.net.Uri
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
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
import java.util.UUID

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
    var currentIndex by remember { mutableIntStateOf(initialIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))) }
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
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = colors.bg.copy(alpha = 0.97f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = post.alasLabel.ifBlank { "Alas-Lente" },
                            color = colors.accent,
                            fontSize = 16.sp,
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

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .background(colors.surface, CircleShape)
                            .border(1.dp, colors.border, CircleShape)
                    ) {
                        Text("✕", color = colors.text, fontSize = 14.sp)
                    }
                }

                // Controls Row (Counter + Zoom + Share Story)
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
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (activeItem.mediaType != "video") {
                            Row(
                                modifier = Modifier
                                    .background(colors.surface, CircleShape)
                                    .border(1.dp, colors.border, CircleShape)
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = {
                                        scale = (scale - 0.5f).coerceAtLeast(1f)
                                        if (scale == 1f) offset = Offset.Zero
                                    },
                                    enabled = scale > 1f,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("−", color = colors.text, fontFamily = FontFamily.Monospace)
                                }
                                TextButton(
                                    onClick = {
                                        scale = 1f
                                        offset = Offset.Zero
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text(
                                        "${(scale * 100).toInt()}%",
                                        color = colors.text,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                TextButton(
                                    onClick = { scale = (scale + 0.5f).coerceAtMost(3.5f) },
                                    enabled = scale < 3.5f,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("+", color = colors.text, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }

                        Button(
                            onClick = {
                                onDismiss()
                                onShareStory(post, currentIndex)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text(
                                "Share Story",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Media Stage
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
                        IconButton(
                            onClick = {
                                currentIndex = (currentIndex - 1 + items.size) % items.size
                            },
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 10.dp)
                                .size(38.dp)
                                .background(Color(0xC80F0F14), CircleShape)
                                .border(1.dp, Color(0x38FFFFFF), CircleShape)
                        ) {
                            Text("‹", color = Color.White, fontSize = 20.sp)
                        }

                        IconButton(
                            onClick = {
                                currentIndex = (currentIndex + 1) % items.size
                            },
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 10.dp)
                                .size(38.dp)
                                .background(Color(0xC80F0F14), CircleShape)
                                .border(1.dp, Color(0x38FFFFFF), CircleShape)
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
                            .background(colors.surface, RoundedCornerShape(12.dp))
                            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (post.caption.isNotBlank()) {
                            Text(text = post.caption, color = colors.text, fontSize = 13.sp)
                        }
                        if (post.location.isNotBlank()) {
                            Text(
                                text = "📍 ${post.location}",
                                color = colors.textMuted,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        if (items.size > 1) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                itemsIndexed(items) { idx, item ->
                                    AsyncImage(
                                        model = item.thumbnailUrl.ifBlank { item.mediaUrl },
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(width = 50.dp, height = 38.dp)
                                            .clip(RoundedCornerShape(7.dp))
                                            .border(
                                                width = 2.dp,
                                                color = if (idx == currentIndex) colors.accent else Color.Transparent,
                                                shape = RoundedCornerShape(7.dp)
                                            )
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
}

// 2. True 9:16 (1080x1920) Story / MyDay Card Generator Modal
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

    var slideIndex by remember { mutableIntStateOf(initialSlide.coerceIn(0, (items.size - 1).coerceAtLeast(0))) }
    var storyStyle by remember { mutableStateOf(StoryStyle.AMBIENT_DARK) }
    var renderedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isRendering by remember { mutableStateOf(true) }
    var statusTip by remember {
        mutableStateOf("Tip: Post link copies automatically—in IG Story, tap Sticker → Link → Paste over the card.")
    }

    LaunchedEffect(post.id, slideIndex, storyStyle) {
        isRendering = true
        renderedBitmap = renderStoryBitmap(context, post, slideIndex, storyStyle)
        isRendering = false
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface, RoundedCornerShape(18.dp))
                .border(1.dp, colors.border, RoundedCornerShape(18.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Story / MyDay (9:16)",
                    color = colors.text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (items.size > 1) {
                        OutlinedButton(
                            onClick = { slideIndex = (slideIndex + 1) % items.size },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, colors.border),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text(
                                "${slideIndex + 1}/${items.size} ↻",
                                color = colors.text,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { storyStyle = storyStyle.next() },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, colors.border),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text(storyStyle.nextLabel, color = colors.text, fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = onDismiss,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, colors.border),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("✕", color = colors.text, fontSize = 11.sp)
                    }
                }
            }

            // 9:16 Preview Box
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(235.dp)
                    .height(418.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0D0D12)),
                contentAlignment = Alignment.Center
            ) {
                val bmp = renderedBitmap
                if (isRendering || bmp == null) {
                    CircularProgressIndicator(color = colors.accent, modifier = Modifier.size(28.dp))
                } else {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Story 9:16 Preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Share & Save Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val bmp = renderedBitmap ?: return@Button
                        scope.launch {
                            shareStoryToApps(context, bmp, post)
                            statusTip = "✓ Exact post link copied! Paste it into your IG Story Link sticker."
                        }
                    },
                    enabled = !isRendering && renderedBitmap != null,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        if (isRendering) "Preparing..." else "Share to App",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                OutlinedButton(
                    onClick = {
                        val bmp = renderedBitmap ?: return@OutlinedButton
                        scope.launch {
                            val ok = saveStoryToGallery(context, bmp, post.id)
                            Toast.makeText(
                                context,
                                if (ok) "Saved 1080×1920 to Pictures/Alas-Lente" else "Could not save image",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    enabled = !isRendering && renderedBitmap != null,
                    border = BorderStroke(1.dp, colors.border),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save 1080×1920", color = colors.text, fontSize = 12.sp)
                }
            }

            Text(
                text = statusTip,
                color = colors.textMuted,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }
    }
}

// 3. Darkroom Admin Login Modal
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

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface, RoundedCornerShape(18.dp))
                .border(1.dp, colors.border, RoundedCornerShape(18.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Darkroom Access", color = colors.text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                TextButton(onClick = onDismiss) {
                    Text("✕", color = colors.text)
                }
            }

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email", fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = colors.border,
                    focusedTextColor = colors.text,
                    unfocusedTextColor = colors.text
                )
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password", fontSize = 12.sp) },
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    TextButton(onClick = { showPassword = !showPassword }) {
                        Text(
                            if (showPassword) "Hide" else "Show",
                            color = colors.accent,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = colors.border,
                    focusedTextColor = colors.text,
                    unfocusedTextColor = colors.text
                )
            )

            Button(
                onClick = {
                    if (email.isBlank() || password.isBlank()) return@Button
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
                enabled = !isSigningIn,
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Enter Darkroom", color = Color.White, fontWeight = FontWeight.SemiBold)
            }

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
}

// 4. Admin Publish / Edit Moment Modal (Up to 4 Reorderable Photos/Videos)
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
    var dateTaken by remember { mutableStateOf(editingPost?.dateLabel?.ifBlank { getManilaTodayMMDDYYYY() } ?: getManilaTodayMMDDYYYY()) }

    val initialTime24 = editingPost?.capturedTime?.ifBlank { getManilaNow24() } ?: getManilaNow24()
    val initParts = initialTime24.split(":")
    val initH24 = initParts.getOrNull(0)?.toIntOrNull() ?: 12
    val initMin = initParts.getOrNull(1)?.toIntOrNull() ?: 0

    var hour12 by remember { mutableIntStateOf(if (initH24 % 12 == 0) 12 else initH24 % 12) }
    var minute by remember { mutableIntStateOf(initMin) }
    var ampm by remember { mutableStateOf(if (initH24 >= 12) "PM" else "AM") }

    var mediaEntries by remember {
        mutableStateOf(
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

    Dialog(onDismissRequest = { if (!isPublishing) onDismiss() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .background(colors.surface, RoundedCornerShape(18.dp))
                .border(1.dp, colors.border, RoundedCornerShape(18.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isEditing) "Edit Moment" else "Publish New Moment",
                    color = colors.text,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                TextButton(onClick = { if (!isPublishing) onDismiss() }) {
                    Text("✕", color = colors.text)
                }
            }

            // Caption
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Caption", color = colors.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${caption.length} / $MAX_CAPTION_CHARS",
                        color = if (caption.length >= MAX_CAPTION_CHARS - 20) colors.accent else colors.textMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                OutlinedTextField(
                    value = caption,
                    onValueChange = { if (it.length <= MAX_CAPTION_CHARS) caption = it },
                    placeholder = { Text("Anong kwento ng oras na 'to?", fontSize = 13.sp) },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accent,
                        unfocusedBorderColor = colors.border,
                        focusedTextColor = colors.text,
                        unfocusedTextColor = colors.text
                    )
                )
            }

            // Time Taken (12-Hour Controls + Live Alas Preview)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.bg, RoundedCornerShape(10.dp))
                    .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = timeMeta.alasLabel,
                        color = colors.accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    TextButton(
                        onClick = {
                            val nowParts = getManilaNow24().split(":")
                            val h24 = nowParts[0].toInt()
                            hour12 = if (h24 % 12 == 0) 12 else h24 % 12
                            minute = nowParts[1].toInt()
                            ampm = if (h24 >= 12) "PM" else "AM"
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(26.dp)
                    ) {
                        Text("Now", color = colors.accent, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Hour Stepper
                    OutlinedButton(
                        onClick = { hour12 = if (hour12 == 12) 1 else hour12 + 1 },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, colors.border),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            hour12.toString().padStart(2, '0'),
                            color = colors.text,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(":", color = colors.textMuted, fontWeight = FontWeight.Bold)
                    // Minute Stepper
                    OutlinedButton(
                        onClick = { minute = (minute + 1) % 60 },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, colors.border),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            minute.toString().padStart(2, '0'),
                            color = colors.text,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    // AM / PM Toggle
                    Button(
                        onClick = { ampm = if (ampm == "AM") "PM" else "AM" },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(ampm, color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Date Taken
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = dateTaken,
                    onValueChange = { dateTaken = it.take(10) },
                    label = { Text("Date (MM-DD-YYYY)", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accent,
                        unfocusedBorderColor = colors.border,
                        focusedTextColor = colors.text,
                        unfocusedTextColor = colors.text
                    )
                )
                OutlinedButton(
                    onClick = {
                        val parts = dateTaken.split("-")
                        val m = (parts.getOrNull(0)?.toIntOrNull() ?: 11) - 1
                        val d = parts.getOrNull(1)?.toIntOrNull() ?: 17
                        val y = parts.getOrNull(2)?.toIntOrNull() ?: 2026
                        DatePickerDialog(context, { _, year, month, day ->
                            dateTaken = "${(month + 1).toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}-$year"
                        }, y, m, d).show()
                    },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, colors.border)
                ) {
                    Text("📅", fontSize = 15.sp)
                }
            }

            // Location
            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                label = { Text("Location (Optional)", fontSize = 12.sp) },
                placeholder = { Text("e.g. Intramuros, Manila", fontSize = 13.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = colors.border,
                    focusedTextColor = colors.text,
                    unfocusedTextColor = colors.text
                )
            )

            // Photos / Videos Picker & Order Grid
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Photos / Videos", color = colors.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${mediaEntries.size} / $MAX_MEDIA_ITEMS selected",
                        color = colors.textMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                OutlinedButton(
                    onClick = { mediaPicker.launch("*/*") },
                    border = BorderStroke(1.dp, colors.border),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Select Photos / Videos (Max 4)", color = colors.text, fontSize = 12.sp)
                }

                if (mediaEntries.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        mediaEntries.forEachIndexed { idx, entry ->
                            val model: Any? = entry.uri
                                ?: entry.existingItem?.thumbnailUrl?.ifBlank { entry.existingItem.mediaUrl }

                            Box(
                                modifier = Modifier
                                    .size(width = 120.dp, height = 92.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.Black)
                                    .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                            ) {
                                AsyncImage(
                                    model = model,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )

                                // #1..#4 Badge
                                Text(
                                    text = "#${idx + 1}",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(5.dp)
                                        .background(colors.accent, RoundedCornerShape(5.dp))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                )

                                // Remove Button
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(5.dp)
                                        .size(20.dp)
                                        .background(Color(0xD00B0B0E), CircleShape)
                                        .clickable {
                                            mediaEntries = mediaEntries.filterIndexed { i, _ -> i != idx }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("✕", color = Color.White, fontSize = 10.sp)
                                }

                                // Left / Right Reorder Controls
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(5.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (idx > 0) {
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .background(Color(0xCC0B0B0E), RoundedCornerShape(5.dp))
                                                .clickable {
                                                    val list = mediaEntries.toMutableList()
                                                    val moved = list.removeAt(idx)
                                                    list.add(idx - 1, moved)
                                                    mediaEntries = list
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("‹", color = Color.White, fontSize = 12.sp)
                                        }
                                    }
                                    if (idx < mediaEntries.lastIndex) {
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .background(Color(0xCC0B0B0E), RoundedCornerShape(5.dp))
                                                .clickable {
                                                    val list = mediaEntries.toMutableList()
                                                    val moved = list.removeAt(idx)
                                                    list.add(idx + 1, moved)
                                                    mediaEntries = list
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("›", color = Color.White, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Submit Button
            Button(
                onClick = {
                    if (mediaEntries.isEmpty()) {
                        statusMsg = "Please select at least 1 photo or video."
                        return@Button
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
                enabled = !isPublishing,
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isEditing) "Save Changes" else "Upload & Publish",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }

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
}