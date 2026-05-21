package com.example.medicalsymptomprescreener

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class for the Medical Symptom Pre-Screener.
 *
 * Annotated with [@HiltAndroidApp] to trigger Hilt's code generation and initialize
 * the application-level dependency injection component. This class must be declared
 * in [AndroidManifest.xml] via `android:name=".MedicalSymptomPreScreenerApp"`.
 *
 * This class also initializes Firebase and registers the appropriate App Check provider
 * factory dynamically depending on whether it is a debug or release build.
 *
 * S: Single Responsibility — initializes Hilt DI and Firebase App Check at application startup.
 */
@HiltAndroidApp
class MedicalSymptomPreScreenerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        
        // Initialize Firebase
        FirebaseApp.initializeApp(this)
        
        // Configure Firebase App Check provider factory
        val appCheck = FirebaseAppCheck.getInstance()
        if (BuildConfig.DEBUG) {
            appCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
        } else {
            appCheck.installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance()
            )
        }
    }
}
