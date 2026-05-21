# Changelog

## [1.2.0.0] - 2026-05-20

### Added
- Firebase App Check integration to protect the Gemini 2.5 Flash REST API endpoint from unauthorized API consumption.
- Register `PlayIntegrityAppCheckProviderFactory` for cryptographically verified production builds.
- Configure `DebugAppCheckProviderFactory` strictly for debug build environments.
- Introduce `AppCheckInterceptor` with high testability via decoupled constructor token provider lambda.
- Graceful error degradation and robust exception handling (logging warnings via `System.err` to comply with zero `android.util.Log` inside `data`/`domain` package rules).
- 3 new JUnit unit tests in `AppCheckInterceptorTest` covering successful token injection, null/empty token gracefully passing requests, and error/exception tolerance.
- Custom `@Named("gemini")` OkHttpClient inside `AppModule.kt` to target App Check token attestation exclusively to Gemini API calls, keeping Google Places API unaffected.
- Fallback manifest placeholder mapping `manifestPlaceholders["mapsapikey"] = "PLACEHOLDER"` inside `app/build.gradle.kts` defaultConfig to fix unit test manifest merging issues without affecting production environment values.

## [1.1.0.0] - 2026-05-02

### Added
- Gemini API response caching — LRU cache (5 entries, 5-min TTL) in GeminiTriageApiImpl,
  keyed on symptoms.take(200).hashCode(). Reduces API quota usage during demos and prevents
  redundant TTS "Call 911" triggers on repeated identical submissions.
  Layer 1 (EmergencySymptomMatcher) always bypasses cache — only the Gemini call is cached.
- GeminiCacheTest — 6 tests covering hit, miss, distinct keys, LRU eviction, TTL expiry,
  200-char key boundary
- SessionExpiredCard in TriageScreen — replaces blank screen on process death with an explicit
  "Session Expired — Start New Assessment" card
- GitHub Actions CI workflow — unit tests + debug APK on every push/PR to main,
  Node.js 24 compatibility, correct testDebugUnitTest report path
- KDoc on every class, interface, function, and important property across all 43 source files
  - SAFETY CRITICAL annotations on EmergencySymptomMatcher and TriageRuleEngine
  - UDF pattern block on all ViewModels
  - SOLID principles (S + D) on all classes
  - Per-group medical rationale in EmergencySymptomMatcher keyword sets
  - Mount Sinai 2026 fix fully documented in TriageRuleEngine

### Changed
- Gemini model updated from gemini-2.0-flash to gemini-2.5-flash
- README rewritten to production standard with Mermaid architecture diagrams,
  engineering decisions table, bugs-fixed table, real-world use cases
- Total unit tests: 73 (was 67), 0 failures

---

## [1.0.0.0] - 2026-04-28

### Added
- Three-layer defense-in-depth safety architecture
  - Layer 1: EmergencySymptomMatcher — offline keyword + synonym detection on full untruncated string, 7 emergency groups, 50+ synonyms, natural language variants ("my heart feels weird")
  - Layer 1b: TemporalDetector — sudden onset + amber term → URGENT minimum floor
  - Layer 2: TriageRuleEngine — post-AI validation, hedging language detection, anatomical risk term check (Mount Sinai 2026 "paradoxical safety explanation" fix)
- Gemini 2.5 Flash triage integration — advisory only, temperature 0.1, responseMimeType JSON, single-turn (no instruction drift)
- NetworkMonitor — NET_CAPABILITY_VALIDATED check before every Gemini call, graceful offline fallback
- SharedTriageViewModel — NavGraph-scoped, process-death safe, no serialization
- SpeechRecognitionManager — @ActivityRetainedScoped, all 9 error codes mapped, partial results for live transcript
- TextToSpeechManager — auto-trigger on EMERGENCY ("Call 911 now"), opt-in for other urgency levels
- Google Maps + Places API v1 integration — new endpoint (searchNearby), urgency-based type mapping
- GuidanceScreen for TELEHEALTH/HOME_CARE — skips Places API call entirely
- RuralFallbackCard — 911 dial button + Google Maps deep link when no facilities found
- Room persistence — SymptomEntity, SymptomDao, swipe-to-delete HistoryScreen
- RECORD_AUDIO permission gate in InputScreen (Accompanist) — text input always visible
- DisclaimerCard — visible on every screen, safety disclaimer always present
- 67 unit tests: EmergencySymptomMatcher (25), TriageRuleEngine (16), UrgencyLevelOrdering (6), TriageSymptomUseCase (12), FacilitiesFailureMode (7)
- UrgencyLevelOrderingTest — safety-critical enum invariant guard
- All 8 failure modes verified: Gemini down, Maps fail, mic denied, offline, emergency keywords, anatomical escalation, process death, TELEHEALTH routing
- Clean Architecture: domain layer has zero Android dependencies
- Hilt dependency injection with two Retrofit instances (Gemini + Places)
