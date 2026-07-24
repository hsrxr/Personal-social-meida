package com.journal.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.journal.app.data.model.Tag
import com.journal.app.data.model.TagType

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagEditor(
    tags: List<Tag>,
    suggestedTags: List<Tag>,
    onTagAdded: (String) -> Unit,
    onTagRemoved: (Tag) -> Unit,
    modifier: Modifier = Modifier,
) {
    var newTagText by remember { mutableStateOf("") }
    var showInput by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Text(
            text = "Tags",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        // Existing tags
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            tags.forEach { tag ->
                InputChip(
                    selected = false,
                    onClick = { onTagRemoved(tag) },
                    label = { Text("#${tag.name}") },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Remove tag",
                            // 45° rotation would indicate "remove" — using Add as close approximation
                        )
                    },
                )
            }
            // Suggested tags (not yet selected)
            suggestedTags.filter { s -> tags.none { it.name == s.name } }.forEach { tag ->
                FilterChip(
                    selected = false,
                    onClick = { onTagAdded(tag.name) },
                    label = { Text("#${tag.name}") },
                )
            }
        }

        // Add tag input
        if (showInput) {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                OutlinedTextField(
                    value = newTagText,
                    onValueChange = { newTagText = it },
                    placeholder = { Text("Tag name") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    trailingIcon = {
                        FilterChip(
                            selected = false,
                            onClick = {
                                if (newTagText.isNotBlank()) {
                                    onTagAdded(newTagText.trim())
                                    newTagText = ""
                                    showInput = false
                                }
                            },
                            label = { Text("Add") },
                        )
                    },
                )
            }
        } else {
            FilterChip(
                selected = false,
                onClick = { showInput = true },
                label = { Text("+ Add tag") },
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
