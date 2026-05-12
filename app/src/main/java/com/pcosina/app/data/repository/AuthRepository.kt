package com.pcosina.app.data.repository

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.gson.Gson
import com.pcosina.app.BuildConfig
import com.pcosina.app.R
import com.pcosina.app.data.model.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

val Context.authDataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_prefs")

internal enum class AuthMessageAction {
    Login,
    SignUp,
    GoogleSignIn,
    PasswordReset,
    ResendVerification
}

internal enum class AuthFailureKind {
    InvalidCredentials,
    InvalidUser,
    UserCollision,
    WeakPassword,
    TooManyRequests,
    Network,
    Unknown
}

internal fun mapAuthFailureKindToMessage(action: AuthMessageAction, failureKind: AuthFailureKind): String {
    return when (action) {
        AuthMessageAction.Login -> when (failureKind) {
            AuthFailureKind.InvalidUser,
            AuthFailureKind.InvalidCredentials -> "Incorrect email or password. Try again."
            AuthFailureKind.TooManyRequests -> "Too many attempts right now. Please wait a bit and try again."
            AuthFailureKind.Network -> "Can't reach the internet right now. Check your connection and try again."
            AuthFailureKind.UserCollision,
            AuthFailureKind.WeakPassword,
            AuthFailureKind.Unknown -> "Couldn't sign you in right now. Please try again."
        }
        AuthMessageAction.SignUp -> when (failureKind) {
            AuthFailureKind.UserCollision -> "This email is already in use. Try logging in instead."
            AuthFailureKind.WeakPassword -> "Use a stronger password with at least 8 characters, a number, and a symbol."
            AuthFailureKind.InvalidCredentials -> "Enter a valid email address."
            AuthFailureKind.TooManyRequests -> "Too many attempts right now. Please wait a bit and try again."
            AuthFailureKind.Network -> "Can't reach the internet right now. Check your connection and try again."
            AuthFailureKind.InvalidUser,
            AuthFailureKind.Unknown -> "Couldn't create your account right now. Please try again."
        }
        AuthMessageAction.GoogleSignIn -> when (failureKind) {
            AuthFailureKind.InvalidCredentials -> "Google sign-in couldn't be verified. Please try again."
            AuthFailureKind.InvalidUser -> "This Google account isn't available for sign-in."
            AuthFailureKind.TooManyRequests -> "Too many attempts right now. Please wait a bit and try again."
            AuthFailureKind.Network -> "Can't reach the internet right now. Check your connection and try again."
            AuthFailureKind.UserCollision,
            AuthFailureKind.WeakPassword,
            AuthFailureKind.Unknown -> "Google sign-in didn't complete. Please try again."
        }
        AuthMessageAction.PasswordReset -> when (failureKind) {
            AuthFailureKind.InvalidUser -> "We couldn't find an account with that email."
            AuthFailureKind.InvalidCredentials -> "Enter a valid email address."
            AuthFailureKind.TooManyRequests -> "Too many attempts right now. Please wait a bit and try again."
            AuthFailureKind.Network -> "Can't reach the internet right now. Check your connection and try again."
            AuthFailureKind.UserCollision,
            AuthFailureKind.WeakPassword,
            AuthFailureKind.Unknown -> "Couldn't send the reset email right now. Please try again."
        }
        AuthMessageAction.ResendVerification -> when (failureKind) {
            AuthFailureKind.InvalidUser,
            AuthFailureKind.InvalidCredentials -> "Check your email and password, then try again."
            AuthFailureKind.TooManyRequests -> "Too many attempts right now. Please wait a bit and try again."
            AuthFailureKind.Network -> "Can't reach the internet right now. Check your connection and try again."
            AuthFailureKind.UserCollision,
            AuthFailureKind.WeakPassword,
            AuthFailureKind.Unknown -> "Couldn't send another verification email right now. Please try again."
        }
    }
}

internal fun mapAuthExceptionToMessage(action: AuthMessageAction, error: Exception): String {
    if (error is IllegalStateException && error.message?.isNotBlank() == true) {
        return error.message.orEmpty()
    }
    val failureKind = when (error) {
        is FirebaseAuthInvalidUserException -> AuthFailureKind.InvalidUser
        is FirebaseAuthInvalidCredentialsException -> AuthFailureKind.InvalidCredentials
        is FirebaseAuthUserCollisionException -> AuthFailureKind.UserCollision
        is FirebaseAuthWeakPasswordException -> AuthFailureKind.WeakPassword
        is FirebaseTooManyRequestsException -> AuthFailureKind.TooManyRequests
        is FirebaseNetworkException,
        is UnknownHostException -> AuthFailureKind.Network
        else -> AuthFailureKind.Unknown
    }
    return mapAuthFailureKindToMessage(action, failureKind)
}

class AuthRepository(private val context: Context) {
    data class OperatorAccessStatus(
        val allowed: Boolean = false,
        val roles: Set<String> = emptySet(),
        val message: String? = null,
        val actor: String? = null,
        val emailVerified: Boolean = false,
        val mfaVerified: Boolean = false
    )

    private data class OperatorAccessPayload(
        val allowed: Boolean = false,
        val roles: List<String> = emptyList(),
        val actor: String? = null,
        val emailVerified: Boolean = false,
        val mfaVerified: Boolean = false
    )

    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firebaseAppCheck: FirebaseAppCheck = FirebaseAppCheck.getInstance()
    private val operatorAccessClient: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val operatorAccessUrl = normalizeBaseUrl(BuildConfig.BASE_URL).trimEnd('/') + "/mobile/operator/access"

    private object Keys {
        val IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        val CURRENT_USER_EMAIL = stringPreferencesKey("current_user_email")
        val CURRENT_USER_UID = stringPreferencesKey("current_user_uid")
    }

    val sessionFlow: Flow<Session> = context.authDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            Session(
                isLoggedIn = preferences[Keys.IS_LOGGED_IN] ?: false,
                currentUserEmail = preferences[Keys.CURRENT_USER_EMAIL],
                currentUserUid = preferences[Keys.CURRENT_USER_UID]
            )
        }

    suspend fun getPersistedCurrentUserUid(): String? {
        val prefs = context.authDataStore.data.first()
        val isLoggedIn = prefs[Keys.IS_LOGGED_IN] ?: false
        val uid = prefs[Keys.CURRENT_USER_UID]?.trim().orEmpty()
        return uid.takeIf { isLoggedIn && it.isNotBlank() }
    }

    suspend fun syncSessionFromFirebase() {
        val currentUser = firebaseAuth.currentUser
        context.authDataStore.edit { prefs ->
            if (currentUser != null) {
                prefs[Keys.IS_LOGGED_IN] = true
                prefs[Keys.CURRENT_USER_EMAIL] = currentUser.email ?: ""
                prefs[Keys.CURRENT_USER_UID] = currentUser.uid
            } else {
                prefs[Keys.IS_LOGGED_IN] = false
                prefs.remove(Keys.CURRENT_USER_EMAIL)
                prefs.remove(Keys.CURRENT_USER_UID)
            }
        }
    }

    suspend fun getCurrentUserOperatorAccess(forceRefresh: Boolean = true): OperatorAccessStatus = withContext(Dispatchers.IO) {
        val currentUser = firebaseAuth.currentUser ?: return@withContext OperatorAccessStatus()
        if (BuildConfig.DEBUG) {
            Log.i(
                OperatorAccessLogTag,
                "Operator access check started url=$operatorAccessUrl " +
                    "email=${maskEmail(currentUser.email)} uid=${maskToken(currentUser.uid)}"
            )
        }
        val idToken = currentUser.getIdToken(forceRefresh).await().token
            ?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Couldn't verify operator access right now.")
        val appCheckToken = getOptionalAppCheckToken()
        val requestBuilder = Request.Builder()
            .url(operatorAccessUrl)
            .addHeader("Authorization", "Bearer $idToken")
            .addHeader("X-PCOSINA-Schema-Version", BuildConfig.SCHEMA_VERSION)
            .get()
        if (!appCheckToken.isNullOrBlank()) {
            requestBuilder.addHeader("X-Firebase-AppCheck", appCheckToken)
        }
        val request = requestBuilder.build()
        operatorAccessClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            return@withContext when {
                response.isSuccessful -> {
                    val payload = gson.fromJson(responseBody, OperatorAccessPayload::class.java)
                        ?: throw IOException("Operator access verification returned an empty payload")
                    val status = OperatorAccessStatus(
                        allowed = payload.allowed,
                        roles = payload.roles.map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet(),
                        actor = payload.actor,
                        emailVerified = payload.emailVerified,
                        mfaVerified = payload.mfaVerified
                    )
                    if (BuildConfig.DEBUG) {
                        Log.i(
                            OperatorAccessLogTag,
                            "Operator access result allowed=${status.allowed} roles=${status.roles.sorted()} " +
                                "emailVerified=${status.emailVerified} mfaVerified=${status.mfaVerified}"
                        )
                    }
                    status
                }
                response.code == 403 -> {
                    val message = mapOperatorAccessDeniedMessage(parseErrorDetail(responseBody))
                    if (BuildConfig.DEBUG) {
                        Log.i(OperatorAccessLogTag, "Operator access denied: $message")
                    }
                    OperatorAccessStatus(message = message)
                }
                response.code == 401 -> {
                    if (BuildConfig.DEBUG) {
                        Log.w(OperatorAccessLogTag, "Operator access token rejected with HTTP 401")
                    }
                    throw IllegalStateException("Couldn't verify operator access right now.")
                }
                else -> {
                    if (BuildConfig.DEBUG) {
                        Log.w(OperatorAccessLogTag, "Operator access failed with HTTP ${response.code}")
                    }
                    throw IOException("Operator access verification failed with HTTP ${response.code}")
                }
            }
        }
    }

    suspend fun signUp(email: String, password: String): Result<Unit> {
        return try {
            firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            firebaseAuth.currentUser?.sendEmailVerification()?.await()
            firebaseAuth.signOut()
            syncSessionFromFirebase()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(IllegalStateException(mapAuthExceptionToMessage(AuthMessageAction.SignUp, e), e))
        }
    }

    suspend fun login(email: String, password: String): Result<Unit> {
        return try {
            firebaseAuth.signInWithEmailAndPassword(email, password).await()
            val user = firebaseAuth.currentUser
            user?.reload()?.await()
            if (user != null && !user.isEmailVerified) {
                firebaseAuth.signOut()
                syncSessionFromFirebase()
                return Result.failure(IllegalStateException("Please verify your email before logging in."))
            }
            syncSessionFromFirebase()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(IllegalStateException(mapAuthExceptionToMessage(AuthMessageAction.Login, e), e))
        }
    }

    suspend fun loginWithGoogle(idToken: String): Result<Unit> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            firebaseAuth.signInWithCredential(credential).await()
            syncSessionFromFirebase()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(IllegalStateException(mapAuthExceptionToMessage(AuthMessageAction.GoogleSignIn, e), e))
        }
    }

    suspend fun resendVerification(email: String, password: String): Result<Unit> {
        return try {
            firebaseAuth.signInWithEmailAndPassword(email, password).await()
            firebaseAuth.currentUser?.sendEmailVerification()?.await()
            firebaseAuth.signOut()
            syncSessionFromFirebase()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(IllegalStateException(mapAuthExceptionToMessage(AuthMessageAction.ResendVerification, e), e))
        }
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> {
        return try {
            firebaseAuth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(IllegalStateException(mapAuthExceptionToMessage(AuthMessageAction.PasswordReset, e), e))
        }
    }

    suspend fun logout() {
        val firebaseCleared = runCatching {
            firebaseAuth.signOut()
        }.onFailure { error ->
            if (BuildConfig.DEBUG) {
                Log.w(AuthSessionLogTag, "Firebase sign-out failed during logout; continuing local cleanup.", error)
            }
        }.isSuccess

        val googleProviderCleared = runCatching {
            signOutGoogleProvider()
        }.onSuccess {
            if (BuildConfig.DEBUG) {
                Log.i(AuthSessionLogTag, "Google provider session cleared during logout.")
            }
        }.onFailure { error ->
            if (BuildConfig.DEBUG) {
                Log.w(AuthSessionLogTag, "Google provider sign-out failed during logout; continuing local cleanup.", error)
            }
        }.isSuccess

        runCatching {
            context.authDataStore.edit { prefs ->
                prefs[Keys.IS_LOGGED_IN] = false
                prefs.remove(Keys.CURRENT_USER_EMAIL)
                prefs.remove(Keys.CURRENT_USER_UID)
            }
        }.onSuccess {
            if (BuildConfig.DEBUG) {
                Log.i(
                    AuthSessionLogTag,
                    "Logout cleanup completed firebaseCleared=$firebaseCleared googleProviderCleared=$googleProviderCleared"
                )
            }
        }.onFailure { error ->
            if (BuildConfig.DEBUG) {
                Log.e(AuthSessionLogTag, "Local auth session cleanup failed during logout.", error)
            }
        }
    }

    private suspend fun signOutGoogleProvider() {
        val appContext = context.applicationContext
        val googleSignInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(appContext.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        GoogleSignIn.getClient(appContext, googleSignInOptions).signOut().await()
    }

    private suspend fun getOptionalAppCheckToken(): String? {
        val warmToken = runCatching {
            firebaseAppCheck.getAppCheckToken(false).await().token
        }.onFailure { error ->
            if (BuildConfig.DEBUG) {
                Log.w(OperatorAccessLogTag, "Firebase App Check warm token unavailable; continuing with Firebase ID token.", error)
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
        if (warmToken != null) return warmToken
        return runCatching {
            firebaseAppCheck.getAppCheckToken(true).await().token
        }.onFailure { error ->
            if (BuildConfig.DEBUG) {
                Log.w(OperatorAccessLogTag, "Firebase App Check refresh token unavailable; continuing without App Check.", error)
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun parseErrorDetail(body: String): String? = runCatching {
        JSONObject(body).optString("detail").takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun mapOperatorAccessDeniedMessage(detail: String?): String {
        val normalized = detail.orEmpty()
        return when {
            normalized.contains("MFA", ignoreCase = true) ->
                "This operator account needs MFA-verified sign-in before opening operator tools."
            normalized.contains("Verified operator email", ignoreCase = true) ->
                "Verify this operator email before opening operator tools."
            normalized.contains("override", ignoreCase = true) || normalized.contains("blocked", ignoreCase = true) ->
                "Operator access for this account is currently blocked."
            normalized.isNotBlank() && !normalized.contains("Admin role required", ignoreCase = true) ->
                normalized
            else -> "This account can sign in, but it doesn't have operator access."
        }
    }

    private fun normalizeBaseUrl(raw: String): String {
        var url = raw.trim()
        url = url.trim('"', '\'')
        if (url.startsWith(":")) {
            url = url.removePrefix(":")
        }
        if (url.startsWith("//")) {
            url = "https:$url"
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }
        if (!url.endsWith("/")) {
            url += "/"
        }
        return url
    }

    private companion object {
        const val AuthSessionLogTag = "PCOSINA-Auth"
        const val OperatorAccessLogTag = "PCOSINA-OperatorAccess"

        fun maskEmail(email: String?): String {
            val clean = email?.trim().orEmpty()
            if (clean.isBlank() || "@" !in clean) return "(none)"
            val local = clean.substringBefore("@")
            val domain = clean.substringAfter("@")
            val localMask = when {
                local.length <= 2 -> "${local.firstOrNull() ?: '*'}*"
                else -> "${local.take(2)}***${local.takeLast(1)}"
            }
            return "$localMask@$domain"
        }

        fun maskToken(value: String?): String {
            val clean = value?.trim().orEmpty()
            if (clean.isBlank()) return "(none)"
            return if (clean.length <= 8) "***" else "${clean.take(4)}...${clean.takeLast(4)}"
        }
    }
}
