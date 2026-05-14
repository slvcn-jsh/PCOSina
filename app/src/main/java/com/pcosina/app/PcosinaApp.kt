package com.pcosina.app

import android.app.Application
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import io.sentry.android.core.SentryAndroid
import com.pcosina.app.notifications.NotificationHelper

class PcosinaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
        val firebaseAppCheck = FirebaseAppCheck.getInstance()
        if (BuildConfig.DEBUG) {
            firebaseAppCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
        } else {
            firebaseAppCheck.installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
        }
        firebaseAppCheck.setTokenAutoRefreshEnabled(BuildConfig.PCOSINA_SEND_APP_CHECK)

        if (BuildConfig.SENTRY_DSN.isNotBlank()) {
            SentryAndroid.init(this) { options ->
                options.dsn = BuildConfig.SENTRY_DSN
                options.release = "pcosina-android@${BuildConfig.VERSION_NAME}"
                options.environment = BuildConfig.APP_ENVIRONMENT
                options.tracesSampleRate = if (BuildConfig.DEBUG) 1.0 else 0.1
            }
        }
    }
}
