package com.pcosina.app

import android.app.Application
import io.sentry.android.core.SentryAndroid
import com.pcosina.app.notifications.NotificationHelper

class PcosinaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)

        if (BuildConfig.SENTRY_DSN.isNotBlank()) {
            SentryAndroid.init(this) { options ->
                options.dsn = BuildConfig.SENTRY_DSN
                options.release = "pcosina-android@${BuildConfig.VERSION_NAME}"
                options.environment = if (BuildConfig.DEBUG) "debug" else "production"
                options.tracesSampleRate = if (BuildConfig.DEBUG) 1.0 else 0.1
            }
        }
    }
}
