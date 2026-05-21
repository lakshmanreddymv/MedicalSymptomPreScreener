package com.example.medicalsymptomprescreener

import com.example.medicalsymptomprescreener.data.api.GeminiTriageApi
import com.example.medicalsymptomprescreener.domain.model.CareType
import com.example.medicalsymptomprescreener.domain.model.TriageResult
import com.example.medicalsymptomprescreener.domain.model.UrgencyLevel
import com.example.medicalsymptomprescreener.domain.monitor.NetworkMonitor
import com.example.medicalsymptomprescreener.domain.usecase.TriageSymptomUseCase
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Network-failure mode tests for [TriageSymptomUseCase].
 *
 * These tests complement [TriageSymptomUseCaseTest] by covering deeper failure
 * scenarios not addressed there:
 *  - DNS/socket-level errors (UnknownHostException, SocketTimeoutException)
 *  - Gemini 429/500/503 HTTP error strings
 *  - Partial/malformed JSON responses (JsonSyntaxException)
 *  - Layer 1 EMERGENCY safety guaranteed under ALL network conditions
 *  - Fallback result content invariants (disclaimer, care type, timeframe)
 *  - Gemini is NEVER called when offline (no unnecessary retries)
 *
 * CRITICAL CONSTRAINT: Do NOT touch [TriageRuleEngine], [EmergencySymptomMatcher],
 * or Spanish ML Kit. This file mocks [GeminiTriageApi] only.
 *
 * DO NOT TOUCH: 3-layer safety architecture.
 */
class NetworkFailureTest {

    private lateinit var geminiApi: GeminiTriageApi
    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var useCase: TriageSymptomUseCase

    @Before
    fun setUp() {
        geminiApi      = mock()
        networkMonitor = mock()
        whenever(networkMonitor.isConnected).thenReturn(MutableStateFlow(true))
        whenever(networkMonitor.isConnectedNow()).thenReturn(true)
        useCase = TriageSymptomUseCase(geminiApi, networkMonitor)
    }

    // ── DNS / socket-level errors ─────────────────────────────────────────────

    @Test
    fun `UnknownHostException (DNS failure) — returns URGENT fallback`() = runTest {
        doAnswer { throw UnknownHostException("Unable to resolve host") }
            .whenever(geminiApi).triage(any())

        val result = useCase.triage("sore throat").getOrThrow()

        assertEquals(UrgencyLevel.URGENT, result.urgencyLevel)
    }

    @Test
    fun `SocketTimeoutException — returns URGENT fallback not a crash`() = runTest {
        doAnswer { throw SocketTimeoutException("connect timed out") }
            .whenever(geminiApi).triage(any())

        val result = useCase.triage("mild fever").getOrThrow()

        assertEquals(UrgencyLevel.URGENT, result.urgencyLevel)
        assertTrue("Should succeed (Result.success)", useCase.triage("mild fever").isSuccess)
    }

    @Test
    fun `connect timeout on first call — always Result success`() = runTest {
        doAnswer { throw SocketTimeoutException("timeout after 30s") }
            .whenever(geminiApi).triage(any())

        // Medical app must NEVER throw to caller — always wrap in Result.success
        val result = useCase.triage("I have back pain")
        assertTrue("Result must always be success — safety fallback wraps all exceptions", result.isSuccess)
    }

    // ── HTTP error status codes ───────────────────────────────────────────────

    @Test
    fun `Gemini 429 rate-limit error — URGENT fallback returned`() = runTest {
        doAnswer { throw IOException("HTTP 429 Too Many Requests") }
            .whenever(geminiApi).triage(any())

        val result = useCase.triage("headache").getOrThrow()

        assertEquals(UrgencyLevel.URGENT, result.urgencyLevel)
    }

    @Test
    fun `Gemini 500 internal error — URGENT fallback not SELF_CARE`() = runTest {
        doAnswer { throw IOException("HTTP 500 Internal Server Error") }
            .whenever(geminiApi).triage(any())

        val result = useCase.triage("stomach ache").getOrThrow()

        // Safety invariant: on ANY API error, urgency must be URGENT minimum (never SELF_CARE)
        assertTrue(
            "Urgency must be URGENT or EMERGENCY on API error, not NON_URGENT/SELF_CARE",
            result.urgencyLevel.ordinal <= UrgencyLevel.URGENT.ordinal
        )
    }

    @Test
    fun `Gemini 503 service unavailable — fallback recommendedCareType is URGENT_CARE`() = runTest {
        doAnswer { throw IOException("HTTP 503 Service Unavailable") }
            .whenever(geminiApi).triage(any())

        val result = useCase.triage("back pain").getOrThrow()

        assertEquals(CareType.URGENT_CARE, result.recommendedCareType)
    }

    // ── Malformed / partial JSON responses ────────────────────────────────────

    @Test
    fun `JsonSyntaxException from partial Gemini response — URGENT fallback`() = runTest {
        doAnswer { throw JsonSyntaxException("Unexpected end of stream") }
            .whenever(geminiApi).triage(any())

        val result = useCase.triage("nausea").getOrThrow()

        assertEquals(UrgencyLevel.URGENT, result.urgencyLevel)
    }

    @Test
    fun `RuntimeException with JSON parse context — URGENT fallback`() = runTest {
        doAnswer { throw RuntimeException("Failed to deserialize urgencyLevel: null") }
            .whenever(geminiApi).triage(any())

        val result = useCase.triage("dizziness").getOrThrow()

        assertEquals(UrgencyLevel.URGENT, result.urgencyLevel)
    }

    // ── Fallback result content invariants ───────────────────────────────────

    @Test
    fun `network failure fallback always contains disclaimer`() = runTest {
        doAnswer { throw IOException("Connection reset") }.whenever(geminiApi).triage(any())

        val result = useCase.triage("rash on arm").getOrThrow()

        assertNotNull(result.disclaimer)
        assertTrue(
            "Disclaimer must mention professional/doctor/diagnosis",
            result.disclaimer.contains("diagnosis", ignoreCase = true) ||
            result.disclaimer.contains("professional", ignoreCase = true) ||
            result.disclaimer.contains("healthcare", ignoreCase = true)
        )
    }

    @Test
    fun `network failure fallback reasoning contains 911 guidance`() = runTest {
        doAnswer { throw IOException("Network error") }.whenever(geminiApi).triage(any())

        val result = useCase.triage("shortness of breath").getOrThrow()

        assertTrue(
            "Fallback reasoning must include 911 guidance for safety",
            result.reasoning.contains("911", ignoreCase = true)
        )
    }

    @Test
    fun `network failure fallback timeframe is not null or blank`() = runTest {
        doAnswer { throw IOException("Timeout") }.whenever(geminiApi).triage(any())

        val result = useCase.triage("persistent cough").getOrThrow()

        assertNotNull(result.timeframe)
        assertTrue("Timeframe must not be blank", result.timeframe.isNotBlank())
    }

    @Test
    fun `offline fallback reasoning contains 911 guidance`() = runTest {
        whenever(networkMonitor.isConnectedNow()).thenReturn(false)

        val result = useCase.triage("severe abdominal pain").getOrThrow()

        assertTrue(
            "Offline fallback must include 911 guidance for safety",
            result.reasoning.contains("911", ignoreCase = true)
        )
    }

    @Test
    fun `offline fallback result has non-null followUpQuestions`() = runTest {
        whenever(networkMonitor.isConnectedNow()).thenReturn(false)

        val result = useCase.triage("mild headache").getOrThrow()

        // followUpQuestions may be empty but must not be null
        assertNotNull(result.followUpQuestions)
    }

    // ── Gemini never called when offline ─────────────────────────────────────

    @Test
    fun `offline — Gemini API is never invoked for non-emergency symptoms`() = runTest {
        whenever(networkMonitor.isConnectedNow()).thenReturn(false)

        useCase.triage("mild nausea")

        verify(geminiApi, never()).triage(any())
    }

    @Test
    fun `offline — Gemini API is never invoked even for high-severity non-emergency symptoms`() = runTest {
        whenever(networkMonitor.isConnectedNow()).thenReturn(false)

        useCase.triage("I have been vomiting for 2 hours")

        verify(geminiApi, never()).triage(any())
    }

    // ── EMERGENCY safety guaranteed under all network states ─────────────────

    @Test
    fun `EMERGENCY keyword offline — Layer 1 fires before network check`() = runTest {
        whenever(networkMonitor.isConnectedNow()).thenReturn(false)

        val result = useCase.triage("I have chest pain and cannot breathe").getOrThrow()

        // Layer 1 fires FIRST — no network check needed for emergency keywords
        assertEquals(UrgencyLevel.EMERGENCY, result.urgencyLevel)
        verify(geminiApi, never()).triage(any())
    }

    @Test
    fun `EMERGENCY keyword + Gemini 503 — EMERGENCY still returned`() = runTest {
        // Network is up but Gemini fails — Layer 1 must have already returned EMERGENCY
        // before the API call. Verify the result is still EMERGENCY.
        doAnswer { throw IOException("503 unavailable") }.whenever(geminiApi).triage(any())

        val result = useCase.triage("having a stroke, face drooping, arm weak").getOrThrow()

        // Layer 1 matches emergency keywords → returns before Gemini is even called
        assertEquals(UrgencyLevel.EMERGENCY, result.urgencyLevel)
    }

    @Test
    fun `EMERGENCY keyword + DNS failure — EMERGENCY returned, Gemini not called`() = runTest {
        doAnswer { throw UnknownHostException("DNS failure") }.whenever(geminiApi).triage(any())

        val result = useCase.triage("I think I am having a heart attack").getOrThrow()

        assertEquals(UrgencyLevel.EMERGENCY, result.urgencyLevel)
        // Layer 1 returned before reaching network — Gemini must not have been invoked
        verify(geminiApi, never()).triage(any())
    }

    @Test
    fun `EMERGENCY keyword when offline — recommendedCareType is EMERGENCY_ROOM`() = runTest {
        whenever(networkMonitor.isConnectedNow()).thenReturn(false)

        val result = useCase.triage("severe chest pain").getOrThrow()

        assertEquals(
            "Emergency keywords must route to ER regardless of connectivity",
            CareType.EMERGENCY_ROOM,
            result.recommendedCareType
        )
    }

    // ── Symptom truncation does not affect safety ─────────────────────────────

    @Test
    fun `emergency keyword beyond 1000 chars returned EMERGENCY even with offline network`() = runTest {
        whenever(networkMonitor.isConnectedNow()).thenReturn(false)
        // Layer 1 runs on FULL untruncated string
        val padding = "I feel generally unwell. ".repeat(50) // > 1000 chars
        val symptoms = padding + "I have chest pain"
        assertTrue("Setup: string must exceed 1000 chars", symptoms.length > 1000)

        val result = useCase.triage(symptoms).getOrThrow()

        // Layer 1 catches it on the full string even when offline
        assertEquals(UrgencyLevel.EMERGENCY, result.urgencyLevel)
    }

    @Test
    fun `non-emergency symptoms exactly at 1000 chars — offline returns URGENT fallback`() = runTest {
        whenever(networkMonitor.isConnectedNow()).thenReturn(false)
        val symptoms = "mild headache ".repeat(72).take(1000)
        assertEquals(1000, symptoms.length)

        val result = useCase.triage(symptoms).getOrThrow()

        // Non-emergency + offline → URGENT safety fallback
        assertEquals(UrgencyLevel.URGENT, result.urgencyLevel)
    }

    // ── Repeated failure calls — no state leakage ────────────────────────────

    @Test
    fun `three consecutive network failures — each returns independent URGENT fallback`() = runTest {
        doAnswer { throw IOException("Network error") }.whenever(geminiApi).triage(any())

        val r1 = useCase.triage("headache").getOrThrow()
        val r2 = useCase.triage("back pain").getOrThrow()
        val r3 = useCase.triage("sore throat").getOrThrow()

        assertEquals(UrgencyLevel.URGENT, r1.urgencyLevel)
        assertEquals(UrgencyLevel.URGENT, r2.urgencyLevel)
        assertEquals(UrgencyLevel.URGENT, r3.urgencyLevel)
    }

    @Test
    fun `network recovers after failure — Gemini is called again on next triage`() = runTest {
        // First call: network down
        whenever(networkMonitor.isConnectedNow()).thenReturn(false)
        val offlineResult = useCase.triage("mild cold").getOrThrow()
        assertEquals(UrgencyLevel.URGENT, offlineResult.urgencyLevel)

        // Network recovers — second call reaches Gemini
        val geminiResult = fakeTriageResult(UrgencyLevel.NON_URGENT)
        whenever(networkMonitor.isConnectedNow()).thenReturn(true)
        whenever(geminiApi.triage(any())).thenReturn(geminiResult)

        val onlineResult = useCase.triage("mild cold").getOrThrow()
        assertEquals(UrgencyLevel.NON_URGENT, onlineResult.urgencyLevel)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun fakeTriageResult(urgency: UrgencyLevel) = TriageResult(
        urgencyLevel = urgency,
        reasoning = "Test reasoning",
        recommendedCareType = CareType.PRIMARY_CARE,
        timeframe = "soon",
        disclaimer = "Not a diagnosis. Consult a healthcare professional.",
        followUpQuestions = emptyList()
    )
}
