package com.example.mydeskrobot.integration.telephony

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.mydeskrobot.domain.telephony.ContactNameAliases
import com.example.mydeskrobot.domain.telephony.ContactNameMatcher
import com.example.mydeskrobot.domain.telephony.PhoneNumberNormalizer

/**
 * Looks up phone numbers from the device contacts rubrica.
 */
class AndroidContactsPhoneResolver(
    private val context: Context,
) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    fun search(query: String): List<ContactPhoneMatch> {
        if (!hasPermission()) return emptyList()

        val terms = ContactNameAliases.expandTerms(query).toList()
        if (terms.isEmpty()) return emptyList()

        val selection = terms.joinToString(" OR ") {
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        }
        val args = terms.map { "%$it%" }.toTypedArray()

        val matches = mutableListOf<ContactPhoneMatch>()
        val seen = mutableSetOf<String>()

        try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                ),
                selection,
                args,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            )?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                if (nameIdx < 0 || numberIdx < 0) return@use

                while (cursor.moveToNext()) {
                    val displayName = cursor.getString(nameIdx).orEmpty().trim()
                    val rawNumber = cursor.getString(numberIdx).orEmpty().trim()
                    val number = PhoneNumberNormalizer.normalize(rawNumber) ?: continue
                    val score = ContactNameMatcher.score(displayName, query)
                    if (score < MIN_SCORE) continue

                    val key = "${displayName.lowercase()}|$number"
                    if (!seen.add(key)) continue

                    matches += ContactPhoneMatch(
                        displayName = displayName,
                        number = number,
                        source = ContactPhoneSource.CONTACTS,
                        score = score,
                    )
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "READ_CONTACTS denied during query", e)
        } catch (e: Exception) {
            Log.e(TAG, "Contact lookup failed: ${e.message}", e)
        }

        return matches.sortedByDescending { it.score }
    }

    companion object {
        private const val TAG = "AndroidContactsPhone"
        private const val MIN_SCORE = 0.55f
    }
}

enum class ContactPhoneSource {
    CONTACTS,
    MEMORY,
}

data class ContactPhoneMatch(
    val displayName: String,
    val number: String,
    val source: ContactPhoneSource,
    val score: Float,
)
