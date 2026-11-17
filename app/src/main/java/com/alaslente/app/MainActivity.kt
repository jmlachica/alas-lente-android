package com.alaslente.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private val FILTER_PERIODS = listOf(
    "all" to "Lahat (All)",
    "madaling-araw" to "Madaling Araw (12AM–5AM)",
    "umaga" to "Umaga (6AM–11AM)",
    "hapon" to "Hapon (12PM–5PM)",
    "gabi" to "Gabi (6PM–11PM)"
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AlasLenteApp()
        }
    }
}

@Composable
fun AlasLenteApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("alas_prefs", Context.MODE_PRIVATE) }

    var isDark by remember { mutableStateOf(prefs.getString("alas-theme", "dark") == "dark") }
    val colors = if (isDark) DarkroomColors else LiwanagColors

    var liveClockText by remember { mutableStateOf(getLiveManilaClockText()) }
    var activePeriod by remember { mutableStateOf("all") }
    var allPosts by remember { mutableStateOf<List<MomentPost>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var isAdmin by remember {
        mutableStateOf(FirebaseAuth.getInstance().currentUser?.uid == ADMIN_UID)
    }

    // Active Modals
    var showLoginModal by remember { mutableStateOf(false) }
    var showCreateModal by remember { mutableStateOf(false) }
    var editingPost by remember { mutableStateOf<MomentPost?>(null) }
    var deletingPost by remember { mutableStateOf<MomentPost?>(null) }
    var lightboxState by remember { mutableStateOf<Pair<MomentPost, Int>?>(null) }
    var storyState by remember { mutableStateOf<Pair<MomentPost, Int>?>(null) }

    // Live Manila Clock ticking every 15 seconds (matching time.js)
    LaunchedEffect(Unit) {
        while (true) {
            liveClockText = getLiveManilaClockText()
            delay(15_000L)
        }
    }

    // Auth State Listener
    DisposableEffect(Unit) {
        val auth = FirebaseAuth.getInstance()
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            isAdmin = firebaseAuth.currentUser?.uid == ADMIN_UID
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    suspend fun loadPosts() {
        try {
            errorMessage = null
            val snap = FirebaseFirestore.getInstance()
                .collection("posts")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .await()

            allPosts = snap.documents.map { doc ->
                val rawItems = doc.get("mediaItems") as? List<*>
                val parsedItems = rawItems?.mapNotNull { entry ->
                    val map = entry as? Map<*, *> ?: return@mapNotNull null
                    val mUrl = map["mediaUrl"]?.toString().orEmpty()
                    if (mUrl.isBlank()) return@mapNotNull null
                    MediaItem(
                        mediaUrl = mUrl,
                        thumbnailUrl = map["thumbnailUrl"]?.toString().orEmpty().ifBlank { mUrl },
                        mediaType = map["mediaType"]?.toString().orEmpty().ifBlank { "image" },
                        publicId = map["publicId"]?.toString()
                    )
                } ?: emptyList()

                val fallbackUrl = doc.getString("mediaUrl").orEmpty()
                val fallbackThumb = doc.getString("thumbnailUrl").orEmpty().ifBlank { fallbackUrl }

                MomentPost(
                    id = doc.id,
                    mediaItems = parsedItems,
                    mediaUrl = fallbackUrl,
                    thumbnailUrl = fallbackThumb,
                    mediaType = doc.getString("mediaType").orEmpty().ifBlank { "image" },
                    caption = doc.getString("caption").orEmpty(),
                    location = doc.getString("location").orEmpty(),
                    capturedTime = doc.getString("capturedTime").orEmpty(),
                    alasLabel = doc.getString("alasLabel").orEmpty(),
                    formatted12h = doc.getString("formatted12h").orEmpty(),
                    timePeriod = doc.getString("timePeriod").orEmpty(),
                    dateLabel = doc.getString("dateLabel").orEmpty()
                )
            }
        } catch (e: Exception) {
            errorMessage = e.localizedMessage ?: "Could not load posts yet."
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadPosts()
    }

    val visiblePosts = remember(allPosts, activePeriod) {
        if (activePeriod == "all") allPosts
        else allPosts.filter { it.timePeriod == activePeriod }
    }

    Scaffold(
        containerColor = colors.bg,
        topBar = {
            // Sticky Site Header matching index.html & style.css
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.bg.copy(alpha = 0.94f))
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Brand + Live Manila Clock
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .shadow(6.dp, CircleShape, ambientColor = colors.accent, spotColor = colors.accent)
                                    .background(colors.accent, CircleShape)
                            )
                            Text(
                                text = "ALAS-LENTE",
                                color = colors.text,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.3).sp
                            )
                        }
                        Text(
                            text = liveClockText,
                            color = colors.textMuted,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.4.sp
                        )
                    }

                    // Header Action Pills
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!isAdmin) {
                            HeaderPillButton(
                                label = "Darkroom",
                                colors = colors,
                                onClick = { showLoginModal = true }
                            )
                        } else {
                            HeaderPillButton(
                                label = "Sign Out",
                                colors = colors,
                                onClick = { FirebaseAuth.getInstance().signOut() }
                            )
                        }

                        HeaderPillButton(
                            label = if (isDark) "☀️ Liwanag" else "🌙 Dilim",
                            colors = colors,
                            onClick = {
                                isDark = !isDark
                                prefs.edit().putString("alas-theme", if (isDark) "dark" else "light").apply()
                            }
                        )
                    }
                }
                HorizontalDivider(thickness = 1.dp, color = colors.border)
            }
        },
        floatingActionButton = {
            if (isAdmin) {
                FloatingActionButton(
                    onClick = { showCreateModal = true },
                    containerColor = colors.accent,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.size(54.dp)
                ) {
                    Text("+", fontSize = 26.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Time-of-Day Filter Bar
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(FILTER_PERIODS) { (periodKey, label) ->
                    val isSelected = activePeriod == periodKey
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (isSelected) colors.accentSoft else colors.surface)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) colors.accent else colors.border,
                                shape = CircleShape
                            )
                            .clickable { activePeriod = periodKey }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) colors.accent else colors.textMuted,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }

            // Feed States
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Loading frames...",
                            color = colors.textMuted,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                errorMessage != null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = errorMessage ?: "Could not load posts yet.",
                            color = colors.danger,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                visiblePosts.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No frames recorded for this time slot yet.",
                            color = colors.textMuted,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        items(visiblePosts, key = { it.id }) { post ->
                            ViewfinderPostCard(
                                post = post,
                                isAdmin = isAdmin,
                                colors = colors,
                                onOpenLightbox = { slideIdx ->
                                    lightboxState = post to slideIdx
                                },
                                onShareStory = { slideIdx ->
                                    storyState = post to slideIdx
                                },
                                onEditPost = {
                                    editingPost = post
                                },
                                onDeletePost = {
                                    deletingPost = post
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Lightbox Modal
    lightboxState?.let { (post, slideIdx) ->
        LightboxDialog(
            post = post,
            initialIndex = slideIdx,
            colors = colors,
            onDismiss = { lightboxState = null },
            onShareStory = { targetPost, targetSlide ->
                storyState = targetPost to targetSlide
            }
        )
    }

    // 9:16 Story / MyDay Modal
    storyState?.let { (post, slideIdx) ->
        StoryModalDialog(
            post = post,
            initialSlide = slideIdx,
            colors = colors,
            onDismiss = { storyState = null }
        )
    }

    // Admin Login Modal
    if (showLoginModal) {
        LoginModalDialog(
            colors = colors,
            onDismiss = { showLoginModal = false },
            onSuccess = { showLoginModal = false }
        )
    }

    // Admin Create / Edit Moment Modal
    if (showCreateModal || editingPost != null) {
        UploadEditModalDialog(
            editingPost = editingPost,
            colors = colors,
            onDismiss = {
                showCreateModal = false
                editingPost = null
            },
            onSaved = {
                showCreateModal = false
                editingPost = null
                scope.launch { loadPosts() }
            }
        )
    }

    // Admin Delete Confirmation Dialog
    deletingPost?.let { target ->
        AlertDialog(
            onDismissRequest = { deletingPost = null },
            containerColor = colors.surface,
            title = { Text("Remove Moment", color = colors.text, fontWeight = FontWeight.Bold) },
            text = { Text("Remove this post from Alas-Lente?", color = colors.textMuted) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val idToDelete = target.id
                        deletingPost = null
                        scope.launch {
                            FirebaseFirestore.getInstance()
                                .collection("posts")
                                .document(idToDelete)
                                .delete()
                                .await()
                            loadPosts()
                        }
                    }
                ) {
                    Text("Delete", color = colors.danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingPost = null }) {
                    Text("Cancel", color = colors.text)
                }
            }
        )
    }
}

@Composable
private fun HeaderPillButton(
    label: String,
    colors: AlasColors,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(colors.surface)
            .border(1.dp, colors.border, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = colors.text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ViewfinderPostCard(
    post: MomentPost,
    isAdmin: Boolean,
    colors: AlasColors,
    onOpenLightbox: (Int) -> Unit,
    onShareStory: (Int) -> Unit,
    onEditPost: () -> Unit,
    onDeletePost: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val items = remember(post) { post.resolvedMediaItems() }
    val pagerState = rememberPagerState(pageCount = { items.size.coerceAtLeast(1) })
    var copied by remember { mutableStateOf(false) }

    val accentCornerColor = colors.accent.copy(alpha = 0.78f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(14.dp))
            // Signature Viewfinder L-shaped corner brackets from style.css
            .drawWithContent {
                drawContent()
                val inset = 8.dp.toPx()
                val len = 12.dp.toPx()
                val stroke = 2.dp.toPx()

                // Top-Left Bracket
                drawLine(accentCornerColor, Offset(inset, inset), Offset(inset + len, inset), stroke, StrokeCap.Square)
                drawLine(accentCornerColor, Offset(inset, inset), Offset(inset, inset + len), stroke, StrokeCap.Square)

                // Top-Right Bracket
                drawLine(accentCornerColor, Offset(size.width - inset - len, inset), Offset(size.width - inset, inset), stroke, StrokeCap.Square)
                drawLine(accentCornerColor, Offset(size.width - inset, inset), Offset(size.width - inset, inset + len), stroke, StrokeCap.Square)
            }
    ) {
        // Media Wrap
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .background(Color(0xFF09090C))
        ) {
            if (items.isNotEmpty()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val item = items[page]
                    val displayUrl = if (item.mediaType == "video") item.thumbnailUrl else item.mediaUrl
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { onOpenLightbox(page) },
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = displayUrl,
                            contentDescription = post.caption,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (item.mediaType == "video") {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Color(0xB80B0B0E), CircleShape)
                                    .border(1.5.dp, colors.accent, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("▶", color = Color.White, fontSize = 18.sp)
                            }
                        }
                    }
                }
            }

            // Top-Left Expand Button
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 16.dp, top = 12.dp)
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xB80B0B0E))
                    .border(1.dp, Color(0x29FFFFFF), RoundedCornerShape(8.dp))
                    .clickable { onOpenLightbox(pagerState.currentPage) },
                contentAlignment = Alignment.Center
            ) {
                Text("⤢", color = Color(0xFFF7F6F2), fontSize = 13.sp)
            }

            // Top-Right Multi-Slide Counter Pill
            if (items.size > 1) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 18.dp, top = 12.dp)
                        .background(Color(0xBF0B0B0E), CircleShape)
                        .border(1.dp, Color(0x24FFFFFF), CircleShape)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1} / ${items.size}",
                        color = Color(0xFFF7F6F2),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Bottom-Left Time Badge
            val timeBadgeText = post.formatted12h.ifBlank { post.capturedTime }
            if (timeBadgeText.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                        .background(Color(0xC70B0B0E), RoundedCornerShape(6.dp))
                        .border(1.dp, Color(0x24FFFFFF), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = timeBadgeText,
                        color = Color(0xFFF7F6F2),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Bottom-Right Carousel Dots
            if (items.size > 1) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    repeat(items.size) { idx ->
                        val active = idx == pagerState.currentPage
                        Box(
                            modifier = Modifier
                                .size(if (active) 7.dp else 6.dp)
                                .background(
                                    color = if (active) colors.accent else Color(0x73FFFFFF),
                                    shape = CircleShape
                                )
                        )
                    }
                }
            }
        }

        // Post Body
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            // Alas Label + Date Label
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = post.alasLabel,
                    color = colors.accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = post.dateLabel,
                    color = colors.accent.copy(alpha = 0.72f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            if (post.caption.isNotBlank()) {
                Text(
                    text = post.caption,
                    color = colors.text,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }

            if (post.location.isNotBlank()) {
                Text(
                    text = "📍 ${post.location}",
                    color = colors.textMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            HorizontalDivider(thickness = 1.dp, color = colors.border)

            // Copy Link & Share Story Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val shareUrl = "$SITE_BASE_URL/?post=${post.id}"
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Alas-Lente Post", shareUrl))
                        copied = true
                        scope.launch {
                            delay(1800L)
                            copied = false
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = colors.surfaceAlt),
                    border = BorderStroke(1.dp, colors.border),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (copied) "Copied!" else "Copy Link",
                        color = colors.text,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Button(
                    onClick = { onShareStory(pagerState.currentPage) },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Share Story",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Admin Edit / Delete Row
            if (isAdmin) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onEditPost,
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = colors.accentSoft),
                        border = BorderStroke(1.dp, colors.accent),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(vertical = 7.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Edit", color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = onDeletePost,
                        border = BorderStroke(1.dp, colors.danger),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(vertical = 7.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Delete", color = colors.danger, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}