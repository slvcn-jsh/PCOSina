package com.pcosina.app.ui.util

import android.content.Context

object LegalAcceptance {
    const val VERSION = "2026_09_06"

    private const val PREFS_NAME = "pcosina_legal"

    private fun cleanUid(uid: String): String = uid.trim().replace(Regex("[^A-Za-z0-9_-]"), "_")

    private fun termsKey(uid: String): String = "terms_accepted_${VERSION}_${cleanUid(uid)}"
    private fun termsAcceptedAtKey(uid: String): String = "terms_accepted_at_${VERSION}_${cleanUid(uid)}"
    private fun privacyKey(uid: String): String = "privacy_accepted_${VERSION}_${cleanUid(uid)}"
    private fun privacyAcceptedAtKey(uid: String): String = "privacy_accepted_at_${VERSION}_${cleanUid(uid)}"
    private fun medicalDisclaimerKey(uid: String): String = "medical_disclaimer_accepted_${VERSION}_${cleanUid(uid)}"

    fun hasAccepted(context: Context, uid: String?): Boolean {
        val userId = uid?.trim().orEmpty()
        if (userId.isBlank()) return false
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(termsKey(userId), false) &&
            prefs.getBoolean(privacyKey(userId), false) &&
            prefs.getBoolean(medicalDisclaimerKey(userId), false)
    }

    fun accept(context: Context, uid: String, acceptedAt: Long = System.currentTimeMillis()) {
        val userId = uid.trim()
        if (userId.isBlank()) return
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(termsKey(userId), true)
            .putLong(termsAcceptedAtKey(userId), acceptedAt)
            .putBoolean(privacyKey(userId), true)
            .putLong(privacyAcceptedAtKey(userId), acceptedAt)
            .putBoolean(medicalDisclaimerKey(userId), true)
            .apply()
    }
}
