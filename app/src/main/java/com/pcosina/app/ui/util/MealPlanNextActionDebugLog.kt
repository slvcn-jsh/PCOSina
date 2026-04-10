package com.pcosina.app.ui.util

import android.util.Log
import com.pcosina.app.BuildConfig
import com.pcosina.app.util.safeUserLogScope

object MealPlanNextActionDebugLog {
    private const val MaxEntries = 64
    private val lock = Any()
    private val entries = mutableListOf<String>()

    fun record(actionType: String, networkState: String, userId: String) {
        if (!BuildConfig.DEBUG) return
        val line = "action=$actionType network=$networkState ${safeUserLogScope(userId)}"
        synchronized(lock) {
            entries.add(line)
            if (entries.size > MaxEntries) {
                entries.removeAt(0)
            }
        }
        Log.i("MealPlanNextActionDebug", line)
    }

    fun snapshot(): List<String> = synchronized(lock) { entries.toList() }

    fun clear() {
        synchronized(lock) { entries.clear() }
    }
}
