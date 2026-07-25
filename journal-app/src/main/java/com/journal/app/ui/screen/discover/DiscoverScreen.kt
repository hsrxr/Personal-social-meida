package com.journal.app.ui.screen.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.journal.app.data.model.FeedPost
import com.journal.app.ui.components.AudioPlayerBar
import com.journal.app.ui.components.HashtaggedText
import com.journal.app.ui.components.MatchBadge
import com.journal.app.ui.components.PhotoRow
import com.journal.app.ui.components.SwipeableCardStack
import com.journal.app.util.DateFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    onSearchClick: () -> Unit = {},
    onAvatarClick: (String) -> Unit = {},
    onConversationClick: (String) -> Unit = {},
    viewModel: DiscoverViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    uiState.sayHiConfirmation?.let { message ->
        LaunchedEffect(message) {
            snackbarHostState.showSnackbar(message)
            viewModel.consumeConfirmation()
        }
    }

    // Navigate to conversation when sayHi creates one
    uiState.navigateToConversationId?.let { convId ->
        LaunchedEffect(convId) {
            onConversationClick(convId)
            viewModel.consumeNavigation()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Echoes", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "Swipe right to say hi, left to skip",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSearchClick) {
                        Icon(Icons.Default.Search, contentDescription = "Find similar posts")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.posts.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No echoes today.\nCheck back tomorrow 👋",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                SwipeableCardStack(
                    items = uiState.posts,
                    onSwipeLeft = { post -> viewModel.skipPost(post) },
                    onSwipeRight = { post -> viewModel.sayHiAndShare(post) },
                ) { post ->
                    SwipeablePostContent(
                        post = post,
                        onTap = { viewModel.openPost(post) },
                        onAvatarClick = {
                            if (post.authorId.isNotEmpty()) onAvatarClick(post.authorId)
                        },
                    )
                }
            }
        }
    }

    uiState.selectedPost?.let { post ->
        PostDetailSheet(
            post = post,
            onDismiss = viewModel::dismissPost,
            onSayHi = { viewModel.sayHiAndShare(post) },
        )
    }
}

@Composable
private fun SwipeablePostContent(
    post: FeedPost,
    onTap: () -> Unit,
    onAvatarClick: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Author row
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (post.authorAvatarUrl != null) {
                    AsyncImage(
                        model = post.authorAvatarUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                    )
                } else {
                    Icon(Icons.Outlined.Person, contentDescription = null)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = post.authorName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = DateFormatter.formatRelative(post.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            MatchBadge(percent = post.matchPercent)
        }

        // Match reason
        Text(
            text = post.matchReason,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
        )

        // Post text
        HashtaggedText(text = post.text)

        // Photos
        if (post.imageUrls.isNotEmpty()) {
            PhotoRow(imageUrls = post.imageUrls)
        }

        // Audio
        if (post.audioUrl != null || post.audioDurationMs != null) {
            AudioPlayerBar(
                durationMs = post.audioDurationMs ?: 0L,
                audioUrl = post.audioUrl,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Tap for details hint
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Tap for details",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PostDetailSheet(
    post: FeedPost,
    onDismiss: () -> Unit,
    onSayHi: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Match Details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            MatchBadge(percent = post.matchPercent)

            Text(
                text = "Why you matched",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(post.matchReason, style = MaterialTheme.typography.bodyLarge)

            // Your matching post
            if (!post.matchedMyPostText.isNullOrBlank()) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    ),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Your matching post",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(post.matchedMyPostText, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            HashtaggedText(text = post.text)

            Button(
                onClick = onSayHi,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Say Hi / High Five 🖐")
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
