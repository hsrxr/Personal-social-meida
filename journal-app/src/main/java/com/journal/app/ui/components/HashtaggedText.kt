package com.journal.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight

/**
 * Body text with #hashtags tinted in the primary color. Shared by the journal and
 * feed surfaces so hashtag styling stays consistent.
 */
@Composable
fun HashtaggedText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    val primary = MaterialTheme.colorScheme.primary
    val annotated = buildAnnotatedString {
        text.split(" ").forEachIndexed { index, token ->
            if (index > 0) append(" ")
            if (token.startsWith("#") && token.length > 1) {
                pushStyle(SpanStyle(color = primary, fontWeight = FontWeight.SemiBold))
                append(token)
                pop()
            } else {
                append(token)
            }
        }
    }
    Text(text = annotated, style = style, modifier = modifier)
}
