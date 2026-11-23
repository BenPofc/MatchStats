plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("androidx.room")
}

android {
    namespace = "br.com.matchstats"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "br.com.matchstats"
        minSdk = 26
        targetSdk = 36
        versionCode = 10
        versionName = "0.10.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.foundation:foundation:1.12.0")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.navigation:navigation-compose:2.10.2")

    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.room:room-runtime:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.12.0")

    constraints {
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2") {
            because("Alinha as corrotinas transitivas do AndroidX.")
        }
        implementation("androidx.compose.material:material-ripple:1.12.0") {
            because("Mantém o ripple transitivo do Material 3 alinhado ao Compose Foundation.")
        }
    }
}
