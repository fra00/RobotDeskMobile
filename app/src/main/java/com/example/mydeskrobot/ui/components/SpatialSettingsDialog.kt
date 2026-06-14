package com.example.mydeskrobot.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.mydeskrobot.R
import com.example.mydeskrobot.presentation.settings.SpatialPlaceUi
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SpatialSettingsDialog(
    places: List<SpatialPlaceUi>,
    onLabelChange: (Long, String) -> Unit,
    onLandmarksChange: (Long, String) -> Unit,
    onSavePlace: (Long, String, String) -> Unit,
    onDeletePlace: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.spatial_settings_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = stringResource(R.string.spatial_settings_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (places.isEmpty()) {
                    Text(
                        text = stringResource(R.string.spatial_settings_empty),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    places.forEach { place ->
                        SpatialPlaceEditorRow(
                            place = place,
                            onLabelChange = { onLabelChange(place.id, it) },
                            onLandmarksChange = { onLandmarksChange(place.id, it) },
                            onSave = { onSavePlace(place.id, place.label, place.landmarks) },
                            onDelete = { onDeletePlace(place.id) },
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_close))
            }
        },
    )
}

@Composable
private fun SpatialPlaceEditorRow(
    place: SpatialPlaceUi,
    onLabelChange: (String) -> Unit,
    onLandmarksChange: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    val lastSeen = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALY)
        .format(Date(place.lastSeenAt))
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "#${place.id} · ${place.roomType} · $lastSeen",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = place.label,
            onValueChange = onLabelChange,
            label = { Text(stringResource(R.string.spatial_place_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = place.landmarks,
            onValueChange = onLandmarksChange,
            label = { Text(stringResource(R.string.spatial_place_landmarks)) },
            modifier = Modifier.fillMaxWidth(),
        )
        if (place.description.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = place.description,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onDelete) {
                Text(stringResource(R.string.spatial_place_delete))
            }
            TextButton(onClick = onSave) {
                Text(stringResource(R.string.settings_save))
            }
        }
    }
}
