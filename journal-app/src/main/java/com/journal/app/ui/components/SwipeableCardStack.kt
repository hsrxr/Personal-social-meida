package com.journal.app.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * A Tinder-style swipeable card stack.
 *
 * @param items the data items to display as cards
 * @param onSwipeLeft called when a card is swiped left (skip)
 * @param onSwipeRight called when a card is swiped right (like/say hi)
 * @param cardContent composable for each visible card
 * @param maxVisibleCards number of cards visible in the stack (default 3)
 */
@Composable
fun <T> SwipeableCardStack(
    items: List<T>,
    onSwipeLeft: (T) -> Unit,
    onSwipeRight: (T) -> Unit,
    maxVisibleCards: Int = 3,
    cardContent: @Composable (T) -> Unit,
) {
    if (items.isEmpty()) return

    // Which item is on top; when it's swiped away we advance
    var currentIndex by remember(items) { mutableStateOf(0) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var isAnimatingOut by remember { mutableStateOf(false) }

    val swipeThreshold = 300f

    // Only show indices >= currentIndex, limited to maxVisibleCards
    val visibleIndices = (currentIndex until (currentIndex + maxVisibleCards).coerceAtMost(items.size)).toList()

    Box(modifier = Modifier.fillMaxSize()) {
        // Render from bottom to top so top card gets pointer events
        visibleIndices.reversed().forEach { index ->
            val isTop = index == currentIndex
            val stackOffset = (index - currentIndex) * 8
            val stackScale = 1f - (index - currentIndex) * 0.04f

            val animatedOffsetY by animateDpAsState(if (!isTop) (stackOffset).dp else 0.dp)
            val animatedScale by animateFloatAsState(stackScale)

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (!isTop) ((index - currentIndex) * 8).dp else 0.dp)
                    .offset { IntOffset(dragOffsetX.roundToInt(), animatedOffsetY.roundToPx()) }
                    .scale(animatedScale)
                    .then(
                        if (isTop) {
                            Modifier.pointerInput(Unit) {
                                detectHorizontalDragGestures(
                                    onDragEnd = {
                                        if (dragOffsetX > swipeThreshold) {
                                            isAnimatingOut = true
                                            onSwipeRight(items[index])
                                            currentIndex++
                                        } else if (dragOffsetX < -swipeThreshold) {
                                            isAnimatingOut = true
                                            onSwipeLeft(items[index])
                                            currentIndex++
                                        }
                                        dragOffsetX = 0f
                                        isAnimatingOut = false
                                    },
                                    onHorizontalDrag = { _, dragAmount ->
                                        dragOffsetX += dragAmount
                                    },
                                )
                            }
                        } else Modifier
                    )
                    .rotate(
                        if (isTop) (dragOffsetX / 20f) else 0f
                    )
                    .background(
                        MaterialTheme.colorScheme.surface,
                        RoundedCornerShape(16.dp),
                    ),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = if (isTop) 6.dp else (2 - (index - currentIndex) * 2).coerceAtLeast(0).dp,
                ),
            ) {
                cardContent(items[index])
            }
        }

        // Empty state when all cards swiped
        if (currentIndex >= items.size) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                androidx.compose.material3.Text(
                    text = "No more echoes for now.\nCheck back later 👋",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
