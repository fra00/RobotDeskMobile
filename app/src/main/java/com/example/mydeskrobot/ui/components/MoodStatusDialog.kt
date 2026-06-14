package com.example.mydeskrobot.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mydeskrobot.R
import com.example.mydeskrobot.domain.mood.MoodReason
import com.example.mydeskrobot.domain.mood.MoodValenceMapper
import com.example.mydeskrobot.presentation.conversation.MoodUiState

@Composable
fun MoodStatusDialog(
    moodState: MoodUiState,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.mood_status_title))
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.mood_status_scale_label),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                ValenceScaleBar(
                    valence = moodState.valence,
                    baseline = moodState.baseline,
                )
                Text(
                    text = stringResource(
                        R.string.mood_status_valence_value,
                        MoodValenceMapper.formatValence(moodState.valence),
                        MoodValenceMapper.formatValence(moodState.baseline),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(
                        R.string.mood_status_bounds,
                        MoodValenceMapper.formatValence(moodState.valenceMin),
                        MoodValenceMapper.formatValence(moodState.valenceMax),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                HorizontalDivider()

                MoodInfoSection(
                    title = stringResource(R.string.mood_status_wellbeing_section),
                    lines = buildList {
                        add(
                            stringResource(
                                R.string.mood_status_base_emotion,
                                moodState.baseEmotion.name.lowercase(),
                                (moodState.baseIntensity * 100).toInt(),
                            ),
                        )
                        moodState.reason?.let { reason ->
                            add(
                                stringResource(
                                    R.string.mood_status_reason,
                                    formatMoodReason(reason),
                                ),
                            )
                        }
                        add(
                            stringResource(
                                R.string.mood_status_duration,
                                moodState.durationMinutes,
                            ),
                        )
                        add(
                            stringResource(
                                R.string.mood_status_idle,
                                moodState.idleMinutes,
                            ),
                        )
                        if (moodState.recentDeltas.isNotEmpty()) {
                            add(stringResource(R.string.mood_status_recent_events))
                            moodState.recentDeltas.forEach { delta ->
                                add("  • ${delta.deltaFormatted} ${delta.event}")
                            }
                        }
                    },
                )

                if (moodState.ephemeralEmotion != null) {
                    HorizontalDivider()
                    MoodInfoSection(
                        title = stringResource(R.string.mood_status_ephemeral_section),
                        lines = buildList {
                            add(
                                stringResource(
                                    R.string.mood_status_ephemeral_emotion,
                                    moodState.ephemeralEmotion.name.lowercase(),
                                    ((moodState.ephemeralIntensity ?: 0.5f) * 100).toInt(),
                                ),
                            )
                            moodState.ephemeralRemainingSeconds?.let { seconds ->
                                add(
                                    stringResource(
                                        R.string.mood_status_ephemeral_ttl,
                                        seconds,
                                    ),
                                )
                            }
                            add(stringResource(R.string.mood_status_ephemeral_note))
                        },
                    )
                }

                HorizontalDivider()
                MoodInfoSection(
                    title = stringResource(R.string.mood_status_display_section),
                    lines = listOf(
                        stringResource(
                            R.string.mood_status_display_emotion,
                            moodState.displayEmotion.name.lowercase(),
                            (moodState.displayIntensity * 100).toInt(),
                        ),
                    ),
                )

                HorizontalDivider()
                Text(
                    text = stringResource(R.string.mood_status_prompt_section),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = moodState.promptSnapshot,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.mood_status_close))
            }
        },
    )
}

@Composable
private fun MoodInfoSection(
    title: String,
    lines: List<String>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        lines.forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ValenceScaleBar(
    valence: Float,
    baseline: Float,
    modifier: Modifier = Modifier,
) {
    val markerColor = MaterialTheme.colorScheme.primary
    val baselineColor = MaterialTheme.colorScheme.outline
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Column(modifier = modifier.fillMaxWidth()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
        ) {
            val markerSize = 12.dp
            val baselineWidth = 2.dp
            val trackHeight = 6.dp
            val centerY = maxHeight / 2

            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(y = centerY - trackHeight / 2)
                    .fillMaxWidth()
                    .height(trackHeight)
                    .clip(CircleShape)
                    .background(trackColor),
            )

            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(
                        x = maxWidth * positionOnScale(baseline) - baselineWidth / 2,
                        y = centerY - 10.dp,
                    )
                    .width(baselineWidth)
                    .height(20.dp)
                    .background(baselineColor),
            )

            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(
                        x = maxWidth * positionOnScale(valence) - markerSize / 2,
                        y = centerY - markerSize / 2,
                    )
                    .size(markerSize)
                    .clip(CircleShape)
                    .background(markerColor),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = "-1", style = MaterialTheme.typography.labelMedium)
            Text(text = "0", style = MaterialTheme.typography.labelMedium)
            Text(text = "+1", style = MaterialTheme.typography.labelMedium)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.mood_status_scale_negative),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.mood_status_scale_baseline_legend),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.mood_status_scale_positive),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun positionOnScale(value: Float): Float =
    ((value.coerceIn(-1f, 1f) + 1f) / 2f)

@Composable
private fun formatMoodReason(reason: MoodReason): String = when (reason) {
    MoodReason.EYE_POKE -> stringResource(R.string.mood_reason_eye_poke)
    MoodReason.USER_APOLOGY -> stringResource(R.string.mood_reason_user_apology)
    MoodReason.IDLE_LONG -> stringResource(R.string.mood_reason_idle_long)
    MoodReason.IDLE_VERY_LONG -> stringResource(R.string.mood_reason_idle_very_long)
    MoodReason.NIGHT_TIME -> stringResource(R.string.mood_reason_night_time)
    MoodReason.POSITIVE_INTERACTION -> stringResource(R.string.mood_reason_positive_interaction)
    MoodReason.NEGATIVE_INTERACTION -> stringResource(R.string.mood_reason_negative_interaction)
    MoodReason.TASK_COMPLETED -> stringResource(R.string.mood_reason_task_completed)
    MoodReason.REMINDER_URGENT -> stringResource(R.string.mood_reason_reminder_urgent)
    MoodReason.USER_RETURNED -> stringResource(R.string.mood_reason_user_returned)
    MoodReason.HEARTBEAT_SUPPRESSED -> stringResource(R.string.mood_reason_heartbeat_suppressed)
}
