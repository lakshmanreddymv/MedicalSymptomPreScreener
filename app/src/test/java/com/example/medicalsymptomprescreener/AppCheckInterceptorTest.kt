package com.example.medicalsymptomprescreener

import com.example.medicalsymptomprescreener.data.api.AppCheckInterceptor
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.argumentCaptor

/**
 * Unit tests for [AppCheckInterceptor].
 *
 * Verifies that:
 * 1. A valid token is appended as the "X-Firebase-AppCheck" header.
 * 2. An empty or null token is handled gracefully, allowing the request to proceed without the header.
 * 3. Any exception thrown during token retrieval is captured safely and the request proceeds normally.
 *
 * S: Single Responsibility — tests the token injection logic of [AppCheckInterceptor] in isolation.
 */
class AppCheckInterceptorTest {

    /**
     * Verifies that when a valid token is provided, it is successfully added to the request headers.
     */
    @Test
    fun testAppCheckToken_injectedSuccessfully() {
        val interceptor = AppCheckInterceptor { "valid-app-check-token" }
        val chain = mock(Interceptor.Chain::class.java)
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent")
            .build()
        `when`(chain.request()).thenReturn(request)
        
        val response = mock(Response::class.java)
        val requestCaptor = argumentCaptor<Request>()
        `when`(chain.proceed(requestCaptor.capture())).thenReturn(response)

        interceptor.intercept(chain)

        val capturedRequest = requestCaptor.firstValue
        assertEquals("valid-app-check-token", capturedRequest.header("X-Firebase-AppCheck"))
    }

    /**
     * Verifies that when the token is null, the interceptor degrades gracefully and does not add the header.
     */
    @Test
    fun testAppCheckToken_nullDegradesGracefully() {
        val interceptor = AppCheckInterceptor { null }
        val chain = mock(Interceptor.Chain::class.java)
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent")
            .build()
        `when`(chain.request()).thenReturn(request)
        
        val response = mock(Response::class.java)
        val requestCaptor = argumentCaptor<Request>()
        `when`(chain.proceed(requestCaptor.capture())).thenReturn(response)

        interceptor.intercept(chain)

        val capturedRequest = requestCaptor.firstValue
        assertNull(capturedRequest.header("X-Firebase-AppCheck"))
    }

    /**
     * Verifies that when an exception is thrown during token provider invocation, it is caught
     * and the request still proceeds without crashing the app.
     */
    @Test
    fun testAppCheckToken_exceptionDegradesGracefully() {
        val interceptor = AppCheckInterceptor { throw RuntimeException("App Check provider is unavailable") }
        val chain = mock(Interceptor.Chain::class.java)
        val request = Request.Builder()
            .url("https://genericalsymptom.com")
            .build()
        `when`(chain.request()).thenReturn(request)
        
        val response = mock(Response::class.java)
        val requestCaptor = argumentCaptor<Request>()
        `when`(chain.proceed(requestCaptor.capture())).thenReturn(response)

        interceptor.intercept(chain)

        val capturedRequest = requestCaptor.firstValue
        assertNull(capturedRequest.header("X-Firebase-AppCheck"))
    }
}
