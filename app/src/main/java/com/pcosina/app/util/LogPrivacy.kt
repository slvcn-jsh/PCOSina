package com.pcosina.app.util

import java.security.MessageDigest

fun safeUserLogScope(userId: String?): String {
    val token = userId?.trim().orEmpty()
    if (token.isEmpty()) return "user_scope=anonymous"
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(token.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        .take(10)
    return "user_scope=u:$digest"
}
