package com.example.mydeskrobot.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.mydeskrobot.R
import com.example.mydeskrobot.domain.llm.LlmProvider
import com.example.mydeskrobot.presentation.settings.LlmSettingsFormState

@Composable
fun LlmSettingsDialog(
    form: LlmSettingsFormState,
    isSaving: Boolean,
    isTesting: Boolean,
    feedbackMessage: String?,
    feedbackIsError: Boolean,
    onProviderChange: (LlmProvider) -> Unit,
    onFormChange: (LlmSettingsFormState) -> Unit,
    onSave: () -> Unit,
    onTest: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.llm_settings_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                ProviderChips(
                    selected = form.provider,
                    onSelected = onProviderChange,
                )
                Spacer(modifier = Modifier.height(12.dp))

                when (form.provider) {
                    LlmProvider.LM_STUDIO -> LmStudioFields(form, onFormChange)
                    LlmProvider.GEMINI -> GeminiFields(form, onFormChange)
                }

                feedbackMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (feedbackIsError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onTest,
                    enabled = !isSaving && !isTesting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.settings_test_connection))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSave,
                enabled = !isSaving && !isTesting,
            ) {
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

@Composable
private fun ProviderChips(
    selected: LlmProvider,
    onSelected: (LlmProvider) -> Unit,
) {
    Column {
        FilterChip(
            selected = selected == LlmProvider.LM_STUDIO,
            onClick = { onSelected(LlmProvider.LM_STUDIO) },
            label = { Text(stringResource(R.string.llm_provider_lm_studio)) },
        )
        Spacer(modifier = Modifier.height(4.dp))
        FilterChip(
            selected = selected == LlmProvider.GEMINI,
            onClick = { onSelected(LlmProvider.GEMINI) },
            label = { Text(stringResource(R.string.llm_provider_gemini)) },
        )
    }
}

@Composable
private fun LmStudioFields(
    form: LlmSettingsFormState,
    onFormChange: (LlmSettingsFormState) -> Unit,
) {
    OutlinedTextField(
        value = form.baseUrl,
        onValueChange = { onFormChange(form.copy(baseUrl = it)) },
        label = { Text(stringResource(R.string.llm_field_base_url)) },
        placeholder = { Text(stringResource(R.string.llm_field_base_url_hint)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = form.textModel,
        onValueChange = { onFormChange(form.copy(textModel = it)) },
        label = { Text(stringResource(R.string.llm_field_text_model)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = form.visionModel,
        onValueChange = { onFormChange(form.copy(visionModel = it)) },
        label = { Text(stringResource(R.string.llm_field_vision_model)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = form.apiKey,
        onValueChange = { onFormChange(form.copy(apiKey = it)) },
        label = { Text(stringResource(R.string.llm_field_api_key)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
    )
}

@Composable
private fun GeminiFields(
    form: LlmSettingsFormState,
    onFormChange: (LlmSettingsFormState) -> Unit,
) {
    OutlinedTextField(
        value = form.apiKey,
        onValueChange = { onFormChange(form.copy(apiKey = it)) },
        label = { Text(stringResource(R.string.llm_field_api_key)) },
        placeholder = { Text(stringResource(R.string.llm_field_api_key_hint)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = form.textModel,
        onValueChange = { onFormChange(form.copy(textModel = it)) },
        label = { Text(stringResource(R.string.llm_field_text_model)) },
        placeholder = { Text(stringResource(R.string.llm_gemini_model_hint)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = form.visionModel,
        onValueChange = { onFormChange(form.copy(visionModel = it)) },
        label = { Text(stringResource(R.string.llm_field_vision_model)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}
