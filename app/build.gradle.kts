plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.secrets)
    alias(libs.plugins.google.services)
    id("jacoco")
}

android {
    namespace = "com.example.medicalsymptomprescreener"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.medicalsymptomprescreener"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "1.2.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["mapsapikey"] = "PLACEHOLDER"
    }

    buildTypes {
        debug {
            enableUnitTestCoverage = true
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
            isIncludeAndroidResources = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

secrets {
    propertiesFileName = "local.properties"
    defaultPropertiesFileName = "local.defaults.properties"
}

kotlin { jvmToolchain(11) }

// ── JaCoCo — 90% line coverage threshold (medical-grade app) ────────────────

jacoco { toolVersion = "0.8.12" }

val jacocoExcludes = listOf(
    "**/R.class", "**/R\$*.class", "**/BuildConfig.*", "**/Manifest*.*",
    "**/*Hilt*.*", "**/*_Factory*.*", "**/*_MembersInjector*.*",
    "**/Dagger*Component*.*", "**/di/**",
    "**/*Screen*.*", "**/*Activity*.*", "**/*Theme*.*",
    "**/*Color*.*", "**/*Type*.*", "**/*Card*.*",
    "**/*Banner*.*", "**/*Button*.*",
    "**/model/**", "**/*_Impl*.*"
)

tasks.register<JacocoReport>("jacocoTestReport") {
    group = "verification"
    description = "JaCoCo unit-test coverage report — debug build."
    dependsOn("testDebugUnitTest")
    reports { xml.required.set(true); html.required.set(true) }
    val classesDir = layout.buildDirectory.dir("tmp/kotlin-classes/debug")
    classDirectories.setFrom(fileTree(classesDir) { exclude(jacocoExcludes) })
    sourceDirectories.setFrom(files("src/main/java"))
    executionData.setFrom(fileTree(layout.buildDirectory) {
        include("outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec")
    })
}

tasks.register<JacocoCoverageVerification>("jacocoCoverageVerification") {
    group = "verification"
    description = "Fail if line coverage < 90% — medical-grade standard."
    dependsOn("jacocoTestReport")
    violationRules {
        rule {
            limit { counter = "LINE"; value = "COVEREDRATIO"; minimum = "0.90".toBigDecimal() }
        }
    }
    val classesDir = layout.buildDirectory.dir("tmp/kotlin-classes/debug")
    classDirectories.setFrom(fileTree(classesDir) { exclude(jacocoExcludes) })
    sourceDirectories.setFrom(files("src/main/java"))
    executionData.setFrom(fileTree(layout.buildDirectory) {
        include("outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec")
    })
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Retrofit + OkHttp + Gson
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.gson)

    // Maps
    implementation(libs.maps.compose)
    implementation(libs.play.services.maps)
    implementation(libs.play.services.location)

    // Accompanist
    implementation(libs.accompanist.permissions)

    // ML Kit Translation (on-device Spanish ↔ English)
    implementation(libs.mlkit.translate)

    // DataStore Preferences (language preference persistence)
    implementation(libs.datastore.preferences)

    // Coil
    implementation(libs.coil.compose)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Firebase App Check
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.appcheck.playintegrity)
    implementation(libs.firebase.appcheck.debug)
}
