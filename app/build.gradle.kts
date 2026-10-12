plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.ksp)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.translens.browser"
    minSdk = 24
    targetSdk = 36
    versionCode = 2
    versionName = "2.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    // Release signing comes from the environment when available; otherwise release builds
    // are signed with the local debug key so they can still be installed for testing.
    System.getenv("KEYSTORE_PATH")?.let { path ->
      create("release") {
        storeFile = file(path)
        storePassword = System.getenv("STORE_PASSWORD")
        keyAlias = System.getenv("KEY_ALIAS") ?: "upload"
        keyPassword = System.getenv("KEY_PASSWORD")
      }
    }
  }

  buildTypes {
    debug {
      // 64-bit phones + the x86_64 emulator
      ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }
    release {
      // 64-bit ARM phones only; keeps native ML libraries out of unused ABIs
      ndk { abiFilters += listOf("arm64-v8a") }
      // R8 strips unused code (icons, Compose debug pieces) — roughly halves the APK
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
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
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.camera.camera2)
  implementation(libs.androidx.camera.core)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.webkit)
  implementation(libs.kotlinx.coroutines.android)
  // On-device OCR (Latin, Chinese, Japanese, Korean, Devanagari)
  implementation(libs.mlkit.text.recognition)
  implementation(libs.mlkit.text.recognition.chinese)
  implementation(libs.mlkit.text.recognition.japanese)
  implementation(libs.mlkit.text.recognition.korean)
  implementation(libs.mlkit.text.recognition.devanagari)
  // On-device translation + language detection (offline fallback)
  implementation(libs.mlkit.translate)
  implementation(libs.mlkit.language.id)
  ksp(libs.androidx.room.compiler)

  testImplementation(libs.junit)
  testImplementation(libs.androidx.test.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.espresso.core)
  debugImplementation(libs.androidx.compose.ui.tooling)
}
