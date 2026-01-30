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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.security.MessageDigest

val Context.authDataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_prefs")

class AuthRepository(private val context: Context) {

    private object Keys {
        val IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        val CURRENT_USER_EMAIL = stringPreferencesKey("current_user_email")
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
                currentUserEmail = preferences[Keys.CURRENT_USER_EMAIL]
            )
        }

    suspend fun signUp(email: String, password: String): Result<Unit> {
        val passwordHashKey = stringPreferencesKey("pwd_hash_$email")
        
        return try {
            context.authDataStore.edit { prefs ->
                if (prefs.contains(passwordHashKey)) {
                    throw Exception("User already exists")
                }
                prefs[passwordHashKey] = hashPassword(password)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun login(email: String, password: String): Result<Unit> {
        val passwordHashKey = stringPreferencesKey("pwd_hash_$email")
        
        return try {
            val preferences = context.authDataStore.data.first()
            val storedHash = preferences[passwordHashKey]
            
            if (storedHash == null) {
                Result.failure(Exception("User does not exist"))
            } else if (storedHash == hashPassword(password)) {
                context.authDataStore.edit { prefs ->
                    prefs[Keys.IS_LOGGED_IN] = true
                    prefs[Keys.CURRENT_USER_EMAIL] = email
                }
                Result.success(Unit)
            } else {
                Result.failure(Exception("Invalid password"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun logout() {
        try {
            context.authDataStore.edit { prefs ->
                prefs[Keys.IS_LOGGED_IN] = false
                prefs.remove(Keys.CURRENT_USER_EMAIL)
            }
        } catch (e: Exception) {
            // Log or handle the logout failure if necessary
        }
    }

    private fun hashPassword(password: String): String {
        val bytes = password.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }
}
