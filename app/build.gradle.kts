import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Assinatura de release: vem de variáveis de ambiente (GitHub Actions) ou de keystore.properties local.
val assinatura = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun segredo(nome: String): String? = System.getenv(nome) ?: assinatura.getProperty(nome)

android {
    namespace = "com.roberto.entregas"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.roberto.entregas"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        val arquivo = segredo("KEYSTORE_FILE")
        if (arquivo != null && file(arquivo).exists()) {
            create("release") {
                storeFile = file(arquivo)
                storePassword = segredo("KEYSTORE_PASSWORD")
                keyAlias = segredo("KEY_ALIAS")
                keyPassword = segredo("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Mapa OpenStreetMap (código aberto, sem chave)
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    // QR Code do cafezinho
    implementation("com.google.zxing:core:3.5.3")

    testImplementation("junit:junit:4.13.2")
}
