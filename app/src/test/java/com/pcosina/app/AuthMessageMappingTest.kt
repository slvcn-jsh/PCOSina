package com.pcosina.app

import com.pcosina.app.data.repository.AuthFailureKind
import com.pcosina.app.data.repository.AuthMessageAction
import com.pcosina.app.data.repository.mapAuthFailureKindToMessage
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthMessageMappingTest {

    @Test
    fun login_mapsInvalidCredentials_toFriendlyCopy() {
        val message = mapAuthFailureKindToMessage(
            AuthMessageAction.Login,
            AuthFailureKind.InvalidCredentials
        )

        assertEquals("Incorrect email or password. Try again.", message)
    }

    @Test
    fun signUp_mapsUserCollision_toFriendlyCopy() {
        val message = mapAuthFailureKindToMessage(
            AuthMessageAction.SignUp,
            AuthFailureKind.UserCollision
        )

        assertEquals("This email is already in use. Try logging in instead.", message)
    }

    @Test
    fun passwordReset_mapsNetworkFailure_toFriendlyCopy() {
        val message = mapAuthFailureKindToMessage(
            AuthMessageAction.PasswordReset,
            AuthFailureKind.Network
        )

        assertEquals("Can't reach the internet right now. Check your connection and try again.", message)
    }

    @Test
    fun resendVerification_mapsTooManyRequests_toFriendlyCopy() {
        val message = mapAuthFailureKindToMessage(
            AuthMessageAction.ResendVerification,
            AuthFailureKind.TooManyRequests
        )

        assertEquals("Too many attempts right now. Please wait a bit and try again.", message)
    }
}
