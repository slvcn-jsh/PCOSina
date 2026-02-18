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
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.FirebaseNetworkException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.authDataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_prefs")

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
            Result.failure(e)
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
            Result.failure(e)
        }
    }

    suspend fun loginWithGoogle(idToken: String): Result<Unit> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            firebaseAuth.signInWithCredential(credential).await()
            syncSessionFromFirebase()
            Result.success(Unit)
        } catch (e: Exception) {
            val mapped = when (e) {
                is FirebaseAuthInvalidCredentialsException ->
                    IllegalStateException(
                        "Google credential rejected. Check Firebase SHA fingerprints and OAuth client setup."
                    )
                is FirebaseAuthInvalidUserException ->
                    IllegalStateException("Google account is not available for sign-in.")
                is FirebaseNetworkException ->
                    IllegalStateException("Network error during Google sign-in. Please try again.")
                else -> e
            }
            Result.failure(mapped)
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
            Result.failure(e)
        }
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> {
        return try {
            firebaseAuth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
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
