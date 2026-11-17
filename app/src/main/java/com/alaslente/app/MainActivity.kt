package com.alaslente.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.google.firebase.firestore.FirebaseFirestore
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

data class MomentPost(
    val id: String,
    val imageUrl: String,
    val caption: String,
    val timeOfDay: String,
    val location: String
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
    var isDarkroom by remember { mutableStateOf(true) }
    var posts by remember { mutableStateOf<List<MomentPost>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val manilaNow = remember { ZonedDateTime.now(ZoneId.of("Asia/Manila")) }
    val formattedTime = remember {
        manilaNow.format(DateTimeFormatter.ofPattern("hh:mm a • MMM dd, yyyy"))
    }

    LaunchedEffect(Unit) {
        FirebaseFirestore.getInstance()
            .collection("posts")
            .get()
            .addOnSuccessListener { snapshot ->
                posts = snapshot.documents.mapNotNull { doc ->
                    val singleUrl = doc.getString("imageUrl")
                    val imageList = doc.get("images") as? List<*>
                    val firstUrl = singleUrl ?: imageList?.firstOrNull()?.toString() ?: ""
                    if (firstUrl.isBlank()) return@mapNotNull null

                    MomentPost(
                        id = doc.id,
                        imageUrl = firstUrl,
                        caption = doc.getString("caption") ?: doc.getString("title") ?: "",
                        timeOfDay = doc.getString("timeOfDay") ?: "",
                        location = doc.getString("location") ?: ""
                    )
                }
                isLoading = false
            }
            .addOnFailureListener { e ->
                errorMessage = e.localizedMessage ?: "Failed to load moments."
                isLoading = false
            }
    }

    val bgColor = if (isDarkroom) Color(0xFF0E0E10) else Color(0xFFF7F5F0)
    val cardColor = if (isDarkroom) Color(0xFF18181B) else Color(0xFFFFFFFF)
    val textColor = if (isDarkroom) Color(0xFFF4F4F5) else Color(0xFF18181B)
    val subTextColor = if (isDarkroom) Color(0xFFA1A1AA) else Color(0xFF52525B)

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor),
        color = bgColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ALAS-LENTE",
                        color = textColor,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "Manila • $formattedTime",
                        color = subTextColor,
                        fontSize = 12.sp
                    )
                }

                OutlinedButton(
                    onClick = { isDarkroom = !isDarkroom },
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        text = if (isDarkroom) "Liwanag" else "Darkroom",
                        color = textColor,
                        fontSize = 12.sp
                    )
                }
            }

            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = textColor)
                    }
                }
                errorMessage != null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = errorMessage ?: "", color = Color(0xFFEF4444))
                    }
                }
                posts.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = "No moments archived yet.", color = subTextColor)
                    }
                }
                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(posts, key = { it.id }) { post ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = cardColor),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    AsyncImage(
                                        model = post.imageUrl,
                                        contentDescription = post.caption,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(260.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                    )
                                    if (post.timeOfDay.isNotBlank() || post.location.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(
                                            text = listOf(post.timeOfDay, post.location)
                                                .filter { it.isNotBlank() }
                                                .joinToString(" • "),
                                            color = subTextColor,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    if (post.caption.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = post.caption,
                                            color = textColor,
                                            fontSize = 14.sp
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
}