package com.example.mydeskrobot.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.mydeskrobot.R

@Composable
fun TranslationModeIndicator(
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!active) return

    Icon(
        imageVector = Icons.Outlined.Translate,
        contentDescription = stringResource(R.string.cd_translation_mode),
        tint = MaterialTheme.colorScheme.secondary,
        modifier = modifier.size(24.dp),
    )
}
