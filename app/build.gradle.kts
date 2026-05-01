plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.aitunes.app"
    compileSdk = 36

    // Si sigues viendo aviso 16 KB en libc++_shared: SDK Manager → instala NDK r27+ y pon aquí esa versión.
    ndkVersion = "26.1.10909125"

    defaultConfig {
        applicationId = "com.aitunes.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // Con -PemuAbiOnly=true solo se empaqueta x86_64: APK mucho mas pequeno (instalacion en emulador).
            if (project.findProperty("emuAbiOnly") == "true") {
                abiFilters += "x86_64"
            } else {
                abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
            }
        }

        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DGGML_NEON=ON"
                )
                cppFlags += listOf("-O3", "-fPIC")
            }
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Inferencia GGUF nativa (Maven): la coordenada solicitada no está publicada en Central.
    // Cuando dispongas de AAR/JNI, descomenta o sustituye por tu artefacto:
    // implementation("io.github.paddlepaddle:llama-android:0.1.0")

    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

// Gradle 8.9 + Windows: "Cannot access output property 'resultsDir'" / fallo MD5 en reports de tests instrumentados.
afterEvaluate {
    tasks.named("connectedDebugAndroidTest").configure {
        doNotTrackState(
            "Salida de connectedAndroidTest no siempre legible para el tracker incremental (AV/OneDrive/rutas)."
        )
    }
}
