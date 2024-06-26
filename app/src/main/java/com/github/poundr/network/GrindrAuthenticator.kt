package com.github.poundr.network

import android.util.Log
import com.github.poundr.UserManager
import com.github.poundr.utils.Jwt
import dagger.Lazy
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

private const val TAG = "GrindrAuthenticator"

class GrindrAuthenticator @Inject constructor(
    private val lazyUserManager: Lazy<UserManager>,
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        val userManager = lazyUserManager.get()

        // Get the server date
        val serverDate = parseHttpDate(response.header("Date")) ?: Date()

        // Check if the token is expired
        var forceRefresh = false
        if (!Jwt.isExpired(userManager.sessionId, serverDate)) {
            val exp = Jwt.getExp(userManager.sessionId)
            Log.w(TAG, "Server responded with 401 but token is not expired! (exp: $exp, now: ${serverDate.time / 1000})")
            forceRefresh = true
        }

        // Avoid multiple threads refreshing the token
        synchronized(this) {
            // Check if the token is still expired
            if (forceRefresh || Jwt.isExpired(userManager.sessionId, serverDate)) {
                // Refresh the token
                Log.d(TAG, "Refreshing token (forceRefresh: $forceRefresh)")
                runBlocking { userManager.refreshToken() }
                forceRefresh = false
            }
        }

        // Put the new tokens in the headers, in the same order as the original request
        val headers = Headers.Builder()
            .apply {
                response.request.headers.forEach { (name, value) ->
                    if (name == "Authorization") {
                        add(name, "Grindr3 ${userManager.sessionId}")
                    } else {
                        add(name, value)
                    }
                }
            }
            .build()

        // Return the new request
        return response.request.newBuilder()
            .headers(headers)
            .build()
    }

    private fun parseHttpDate(date: String?): Date? {
        if (date == null) return null
        val format = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
        return try {
            format.parse(date)
        } catch (e: Exception) {
            null
        }
    }
}