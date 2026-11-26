import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Clés d'API lues depuis local.properties (jamais commitées)
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun secret(name: String, default: String = ""): String = "\"${localProps.getProperty(name, default)}\""

android {
    namespace = "com.karaokedrive"
    // 37.1 : version minimale exigée par Compose 1.13
    compileSdk {
        version = release(37) { minorApiLevel = 1 }
    }

    defaultConfig {
        applicationId = "com.karaokedrive"
        minSdk = 29
        // API 36 : cible exigée par Google Play depuis août 2026
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-proto"

        buildConfigField("String", "ACR_HOST", secret("acr.host"))
        buildConfigField("String", "ACR_ACCESS_KEY", secret("acr.accessKey"))
        buildConfigField("String", "ACR_ACCESS_SECRET", secret("acr.accessSecret"))
        // Traduction des paroles par IA (format OpenAI) : Gemini par défaut, clé dans local.properties
        buildConfigField("String", "LLM_BASE_URL", secret("llm.baseUrl", "https://generativelanguage.googleapis.com/v1beta/openai/"))
        buildConfigField("String", "LLM_MODEL", secret("llm.model", "gemini-3.8-flash"))
        buildConfigField("String", "LLM_API_KEY", secret("llm.apiKey"))

        // Téléphones actuels uniquement : retire ~45 Mo de bibliothèques natives (x86, ARM 32 bits)
        ndk { abiFilters += "arm64-v8a" }
    }

    buildTypes {
        release {
            // Code et ressources inutilisés retirés, code optimisé (R8)
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Prototype perso : signée avec la clé de debug pour s'installer par-dessus la version de test.
            // ⚠️ À remplacer par une vraie clé de publication avant toute diffusion (Play Console).
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.11.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.1")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("com.squareup.okhttp3:okhttp:5.5.1")
    implementation("androidx.car.app:app:1.7.0")
    // Traduction des paroles anglaises, sur l'appareil
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("com.google.mlkit:language-id:17.0.6")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260814")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation("com.squareup.okhttp3:mockwebserver3:5.5.1")
}
