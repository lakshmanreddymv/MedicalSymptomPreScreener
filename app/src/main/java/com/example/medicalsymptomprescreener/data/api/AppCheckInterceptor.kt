package com.example.medicalsymptomprescreener.data.api

import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp [Interceptor] that appends the Firebase App Check cryptographic attestation token
 * under the "X-Firebase-AppCheck" header.
 *
 * Designed to be highly testable and decoupled from the static Android GMS task and Firebase
 * runtime environments by taking a lambda [tokenProvider] in its constructor.
 *
 * S: Single Responsibility — appends the App Check token to HTTP requests.
 */
class AppCheckInterceptor(
    private val tokenProvider: () -> String?
) : Interceptor {

    /**
     * Intercepts the HTTP request chain and appends the App Check token if available.
     *
     * @param chain The OkHttp interceptor chain.
     * @return The HTTP response.
     */
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        
        val token = try {
            tokenProvider()
        } catch (e: Exception) {
            // Invariant: No android.util.Log in data/ or domain/ layers.
            System.err.println("Firebase App Check token provider threw an exception: ${e.message}")
            null
        }
        
        val builder = originalRequest.newBuilder()
        
        if (!token.isNullOrEmpty()) {
            builder.addHeader("X-Firebase-AppCheck", token)
        }
        
        return chain.proceed(builder.build())
    }
}
