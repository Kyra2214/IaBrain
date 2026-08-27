plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-parcelize")
}

android {
    namespace = "com.aibrain.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.aibrain.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    // Fase 12.9 — permite rodar testes unitários locais que usam classes do
    // framework Android (org.json.JSONObject), retornando valores padrão em
    // vez de estourar "not mocked".
    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // Núcleo Android + Kotlin (base leve, sem dependências pesadas)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Custom Tabs (Fase 5 - abrir IA no navegador integrado)
    implementation("androidx.browser:browser:1.8.0")

    // Listagem (Fase 6 - tela inicial com o conjunto de IAs)
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.cardview:cardview:1.0.0")

    // Ciclo de vida / MVVM
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.4")
    // Armazenamento seguro da API key usando Android Keystore.
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Coroutines (leitura assíncrona do JSON)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Fase 12.9 — Testes unitários (lógica de filtro/ordenação e do AI Brain)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    // Fase 12.9 — org.json real em testes: o `org.json` embutido no Android SDK é
    // um stub que estoura em testes locais; a dependência abaixo fornece a
    // implementação completa para os testes de curadoria (Fase 18.8).
    testImplementation("org.json:json:20240303")
}
