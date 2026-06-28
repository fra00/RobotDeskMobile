package com.example.mydeskrobot.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.mydeskrobot.R
import com.example.mydeskrobot.presentation.settings.AttentionDomainEditorFormState
import com.example.mydeskrobot.presentation.settings.AttentionDomainSensitivityOption
import com.example.mydeskrobot.presentation.settings.AttentionDomainTriggerType
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttentionDomainEditorDialog(
    form: AttentionDomainEditorFormState,
    isEditing: Boolean,
    errorMessage: String?,
    onFormChange: (AttentionDomainEditorFormState) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (isEditing) {
                    stringResource(R.string.attention_domain_edit_title)
                } else {
                    stringResource(R.string.attention_domain_add_title)
                },
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = form.displayName,
                    onValueChange = { onFormChange(form.copy(displayName = it)) },
                    label = { Text(stringResource(R.string.attention_domain_name_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = form.description,
                    onValueChange = { onFormChange(form.copy(description = it)) },
                    label = { Text(stringResource(R.string.attention_domain_description_label)) },
                    placeholder = { Text(stringResource(R.string.attention_domain_description_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )

                Spacer(modifier = Modifier.height(12.dp))

                TriggerTypeDropdown(
                    selected = form.triggerType,
                    onSelected = { onFormChange(form.copy(triggerType = it)) },
                )

                if (form.triggerType == AttentionDomainTriggerType.DAILY) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.attention_domain_trigger_hour, form.triggerHour),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Slider(
                        value = form.triggerHour.toFloat(),
                        onValueChange = { onFormChange(form.copy(triggerHour = it.roundToInt())) },
                        valueRange = 0f..23f,
                        steps = 22,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (form.triggerType == AttentionDomainTriggerType.WEEKLY) {
                    Spacer(modifier = Modifier.height(8.dp))
                    WeekdayDropdown(
                        selected = form.triggerDayOfWeek,
                        onSelected = { onFormChange(form.copy(triggerDayOfWeek = it)) },
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                SensitivityDropdown(
                    selected = form.sensitivity,
                    onSelected = { onFormChange(form.copy(sensitivity = it)) },
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.attention_domain_requires_presence),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Switch(
                        checked = form.requiresPresenceCheck,
                        onCheckedChange = { onFormChange(form.copy(requiresPresenceCheck = it)) },
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.attention_domain_can_use_camera),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Switch(
                        checked = form.canUseCamera,
                        onCheckedChange = { onFormChange(form.copy(canUseCamera = it)) },
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.attention_domain_enabled_label),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Switch(
                        checked = form.enabled,
                        onCheckedChange = { onFormChange(form.copy(enabled = it)) },
                    )
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) {
                Text(stringResource(R.string.settings_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_cancel))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TriggerTypeDropdown(
    selected: AttentionDomainTriggerType,
    onSelected: (AttentionDomainTriggerType) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = when (selected) {
        AttentionDomainTriggerType.DAILY -> stringResource(R.string.attention_domain_trigger_daily)
        AttentionDomainTriggerType.WEEKLY -> stringResource(R.string.attention_domain_trigger_weekly)
        AttentionDomainTriggerType.EVENT_PHOTO -> stringResource(R.string.attention_domain_trigger_event_photo)
        AttentionDomainTriggerType.EVENT_ROOM -> stringResource(R.string.attention_domain_trigger_event_room)
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.attention_domain_trigger_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            AttentionDomainTriggerType.entries.forEach { type ->
                DropdownMenuItem(
                    text = {
                        Text(
                            when (type) {
                                AttentionDomainTriggerType.DAILY ->
                                    stringResource(R.string.attention_domain_trigger_daily)
                                AttentionDomainTriggerType.WEEKLY ->
                                    stringResource(R.string.attention_domain_trigger_weekly)
                                AttentionDomainTriggerType.EVENT_PHOTO ->
                                    stringResource(R.string.attention_domain_trigger_event_photo)
                                AttentionDomainTriggerType.EVENT_ROOM ->
                                    stringResource(R.string.attention_domain_trigger_event_room)
                            },
                        )
                    },
                    onClick = {
                        onSelected(type)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SensitivityDropdown(
    selected: AttentionDomainSensitivityOption,
    onSelected: (AttentionDomainSensitivityOption) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = when (selected) {
        AttentionDomainSensitivityOption.LOW -> stringResource(R.string.attention_domain_sensitivity_low)
        AttentionDomainSensitivityOption.MEDIUM -> stringResource(R.string.attention_domain_sensitivity_medium)
        AttentionDomainSensitivityOption.HIGH -> stringResource(R.string.attention_domain_sensitivity_high)
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.attention_domain_sensitivity_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            AttentionDomainSensitivityOption.entries.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            when (option) {
                                AttentionDomainSensitivityOption.LOW ->
                                    stringResource(R.string.attention_domain_sensitivity_low)
                                AttentionDomainSensitivityOption.MEDIUM ->
                                    stringResource(R.string.attention_domain_sensitivity_medium)
                                AttentionDomainSensitivityOption.HIGH ->
                                    stringResource(R.string.attention_domain_sensitivity_high)
                            },
                        )
                    },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeekdayDropdown(
    selected: Int,
    onSelected: (Int) -> Unit,
) {
    val weekdays = listOf(
        java.util.Calendar.MONDAY to R.string.attention_domain_weekday_monday,
        java.util.Calendar.TUESDAY to R.string.attention_domain_weekday_tuesday,
        java.util.Calendar.WEDNESDAY to R.string.attention_domain_weekday_wednesday,
        java.util.Calendar.THURSDAY to R.string.attention_domain_weekday_thursday,
        java.util.Calendar.FRIDAY to R.string.attention_domain_weekday_friday,
        java.util.Calendar.SATURDAY to R.string.attention_domain_weekday_saturday,
        java.util.Calendar.SUNDAY to R.string.attention_domain_weekday_sunday,
    )
    var expanded by remember { mutableStateOf(false) }
    val label = stringResource(weekdays.first { it.first == selected }.second)

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.attention_domain_weekday_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            weekdays.forEach { (day, labelRes) ->
                DropdownMenuItem(
                    text = { Text(stringResource(labelRes)) },
                    onClick = {
                        onSelected(day)
                        expanded = false
                    },
                )
            }
        }
    }
}
