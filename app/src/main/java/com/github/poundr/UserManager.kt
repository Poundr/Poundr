package com.github.poundr

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.github.poundr.model.Role
import com.github.poundr.network.LoginRestService
import com.github.poundr.network.SettingsRestService
import com.github.poundr.network.model.AuthResponse
import com.github.poundr.network.model.FcmPushRequest
import com.github.poundr.network.model.LoginEmailRequest
import com.github.poundr.network.model.UpdateLocationRequest
import com.google.firebase.installations.FirebaseInstallations
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private const val TAG = "UserManager"

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "userManager")

/* Persistent auth data */
private val EMAIL_KEY = stringPreferencesKey("email")
private val PROFILE_ID_KEY = longPreferencesKey("profile_id")
private val SESSION_ID_KEY = stringPreferencesKey("session_id")
private val AUTH_TOKEN_KEY = stringPreferencesKey("auth_token")

@Singleton
class UserManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val loginRestService: LoginRestService,
    private val settingsRestService: SettingsRestService
) {
    var isReady: Boolean = false
        private set

    private val _loggedIn = MutableStateFlow(false)
    val loggedIn = _loggedIn.asStateFlow()

    var shouldSendFcmToken: Boolean = false
        private set

    /* Auth data */
    var email: String = ""
        private set
    var profileId: Long = 0
        private set
    var sessionId: String = ""
        private set
    var authToken: String = ""
        private set

    /* User data */
    var roles: String = "[]"
        private set
    var role: Role = Role.FREE
        private set

    init {
        runBlocking(Dispatchers.IO) {
            val data = context.dataStore.data.first()
            email = data[EMAIL_KEY] ?: ""
            profileId = data[PROFILE_ID_KEY] ?: 0L
            sessionId = data[SESSION_ID_KEY] ?: ""
            authToken = data[AUTH_TOKEN_KEY] ?: ""
            _loggedIn.value = (email != null) && profileId != 0L && sessionId.isNotEmpty() && authToken.isNotEmpty()
            isReady = true
        }
    }

    suspend fun login(email: String, password: String) {
        withContext(Dispatchers.IO) {
            val firebaseToken = suspendCoroutine { continuation ->
                FirebaseMessaging.getInstance().token.addOnCompleteListener {
                    if (it.isSuccessful) {
                        continuation.resume(it.result)
                    } else {
                        continuation.resume(null)
                    }
                }
            }

            val response = loginRestService.postSessions(
                LoginEmailRequest(
                    email = email,
                    password = password,
                    authToken = null,
                    token = firebaseToken
                )
            )

            shouldSendFcmToken = true

            setAuthResponse(email, response)
        }
    }

    suspend fun pushFcmToken() {
        withContext(Dispatchers.IO) {
            try {
                val installationId = FirebaseInstallations.getInstance().id.await()
                val firebaseToken = FirebaseMessaging.getInstance().token.await()

                loginRestService.postGcmPushTokens(
                    FcmPushRequest(
                        vendorProvidedIdentifier = installationId,
                        token = firebaseToken
                    )
                )

                shouldSendFcmToken = false
            } catch (e: Exception) {
                Log.e(TAG, "Failed to push FCM token", e)
            }
        }
    }

    suspend fun putLocation(geohash: String) {
        withContext(Dispatchers.IO) {
            settingsRestService.putMeLocation(UpdateLocationRequest(geohash))
        }
    }

    private suspend fun setAuthResponse(email: String, response: AuthResponse) {
        if (
            response.profileId == null || response.profileId == 0L ||
            response.sessionId.isNullOrEmpty() ||
            response.authToken.isNullOrEmpty()
        ) {
            Log.d(TAG, "setAuthResponse: Invalid response")
            return
        }

        setEmail(email)
        setProfileId(response.profileId)
        setSessionId(response.sessionId)
        setAuthToken(response.authToken)

        _loggedIn.value = true
    }

    suspend fun setEmail(email: String) = withContext(Dispatchers.IO) {
        this@UserManager.email = email
        context.dataStore.edit { settings ->
            settings[EMAIL_KEY] = email
        }
    }

    suspend fun setProfileId(profileId: Long) = withContext(Dispatchers.IO) {
        this@UserManager.profileId = profileId
        context.dataStore.edit { settings ->
            settings[PROFILE_ID_KEY] = profileId
        }
    }

    suspend fun setSessionId(sessionId: String) = withContext(Dispatchers.IO) {
        this@UserManager.sessionId = sessionId
        context.dataStore.edit { settings ->
            settings[SESSION_ID_KEY] = sessionId
        }
    }

    suspend fun setAuthToken(authToken: String) = withContext(Dispatchers.IO) {
        this@UserManager.authToken = authToken
        context.dataStore.edit { settings ->
            settings[AUTH_TOKEN_KEY] = authToken
        }
    }

    suspend fun refreshToken() {
        val firebaseToken = suspendCoroutine { continuation ->
            FirebaseMessaging.getInstance().token.addOnCompleteListener {
                if (it.isSuccessful) {
                    continuation.resume(it.result)
                } else {
                    continuation.resume(null)
                }
            }
        }

        val response = loginRestService.postSessions(
            LoginEmailRequest(
                email = email,
                password = null,
                authToken = authToken,
                token = firebaseToken
            )
        )
        setAuthResponse(email, response)
    }
}