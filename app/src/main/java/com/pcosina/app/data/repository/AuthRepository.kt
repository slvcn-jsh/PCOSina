package com.pcosina.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pcosina.app.data.model.Session
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.net.UnknownHostException

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
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()

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
        try {
            firebaseAuth.signOut()
            context.authDataStore.edit { prefs ->
                prefs[Keys.IS_LOGGED_IN] = false
                prefs.remove(Keys.CURRENT_USER_EMAIL)
                prefs.remove(Keys.CURRENT_USER_UID)
            }
        } catch (e: Exception) {
            // Log or handle the logout failure if necessary
        }
    }
}
