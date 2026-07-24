package com.journal.app.ui.screen.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.journal.app.data.model.FeedPost
import com.journal.app.ui.components.FeedPostCard
import com.journal.app.ui.components.HashtaggedText
import com.journal.app.ui.components.MatchBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    onSearchClick: () -> Unit = {},
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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Echoes", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "Daily summaries from other users, ordered by matching degree.",
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
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(uiState.posts, key = { it.id }) { post ->
                    FeedPostCard(
                        post = post,
                        onClick = { viewModel.openPost(post) },
                        onSayHi = { viewModel.sayHi(post) },
                    )
                }
            }
        }
    }

    uiState.selectedPost?.let { post ->
        PostDetailSheet(
            post = post,
            onDismiss = viewModel::dismissPost,
            onSayHi = { viewModel.sayHi(post) },
        )
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
            Text("Tap into a post", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            MatchBadge(percent = post.matchPercent)
            Text(
                text = "Why you matched",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(post.matchReason, style = MaterialTheme.typography.bodyLarge)
            HashtaggedText(text = post.text)
            androidx.compose.material3.Button(
                onClick = onSayHi,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Say Hi / High Five 🖐")
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
