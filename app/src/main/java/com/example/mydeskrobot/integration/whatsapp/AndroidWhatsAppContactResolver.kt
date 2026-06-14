package com.example.mydeskrobot.integration.whatsapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.mydeskrobot.domain.messaging.WhatsAppUriBuilder
import com.example.mydeskrobot.domain.telephony.ContactNameAliases
import com.example.mydeskrobot.domain.telephony.ContactNameMatcher

/**
 * Finds WhatsApp contacts and groups synced into the device contacts provider.
 */
class AndroidWhatsAppContactResolver(
    private val context: Context,
) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    fun search(query: String, preferGroup: Boolean): List<WhatsAppTargetMatch> {
        if (!hasPermission()) return emptyList()

        val terms = ContactNameAliases.expandTerms(query).toList()
        if (terms.isEmpty()) return emptyList()

        val nameClauses = terms.joinToString(" OR ") {
            "${ContactsContract.Data.DISPLAY_NAME} LIKE ?"
        }
        val selection = buildString {
            append("${ContactsContract.Data.MIMETYPE} IN (?, ?)")
            append(" AND (")
            append(nameClauses)
            append(")")
        }
        val args = arrayOf(MIME_PROFILE, MIME_GROUP) + terms.map { "%$it%" }.toTypedArray()

        val matches = mutableListOf<WhatsAppTargetMatch>()
        val seen = mutableSetOf<String>()

        try {
            context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(
                    ContactsContract.Data.DISPLAY_NAME,
                    ContactsContract.Data.DATA1,
                    ContactsContract.Data.MIMETYPE,
                ),
                selection,
                args,
                ContactsContract.Data.DISPLAY_NAME,
            )?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(ContactsContract.Data.DISPLAY_NAME)
                val dataIdx = cursor.getColumnIndex(ContactsContract.Data.DATA1)
                val mimeIdx = cursor.getColumnIndex(ContactsContract.Data.MIMETYPE)
                if (nameIdx < 0 || dataIdx < 0 || mimeIdx < 0) return@use

                while (cursor.moveToNext()) {
                    val displayName = cursor.getString(nameIdx).orEmpty().trim()
                    val data = cursor.getString(dataIdx).orEmpty().trim()
                    val mime = cursor.getString(mimeIdx).orEmpty()
                    if (displayName.isBlank() || data.isBlank()) continue

                    val chatType = when (mime) {
                        MIME_GROUP -> WhatsAppChatType.GROUP
                        MIME_PROFILE -> WhatsAppChatType.CONTACT
                        else -> continue
                    }
                    if (preferGroup && chatType != WhatsAppChatType.GROUP) continue
                    if (!preferGroup && chatType == WhatsAppChatType.GROUP) {
                        // still include groups when searching by name — scoring handles preference
                    }

                    val sendId = WhatsAppUriBuilder.normalizeSendId(data)
                    if (sendId.length < 5) continue

                    val score = ContactNameMatcher.score(displayName, query) +
                        if (preferGroup && chatType == WhatsAppChatType.GROUP) 0.1f else 0f
                    if (score < MIN_SCORE) continue

                    val key = "$chatType|$sendId"
                    if (!seen.add(key)) continue

                    matches += WhatsAppTargetMatch(
                        displayName = displayName,
                        sendId = sendId,
                        chatType = chatType,
                        source = WhatsAppTargetSource.CONTACTS,
                        score = score.coerceAtMost(1f),
                    )
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "READ_CONTACTS denied during WhatsApp lookup", e)
        } catch (e: Exception) {
            Log.e(TAG, "WhatsApp contact lookup failed: ${e.message}", e)
        }

        return matches.sortedByDescending { it.score }
    }

    companion object {
        private const val TAG = "WhatsAppContacts"
        private const val MIN_SCORE = 0.55f
        private const val MIME_PROFILE = "vnd.android.cursor.item/vnd.com.whatsapp.profile"
        private const val MIME_GROUP = "vnd.android.cursor.item/vnd.com.whatsapp.group"
    }
}
