plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("org.jetbrains.kotlin.plugin.compose")
}

val ciSigningKeystore = providers.environmentVariable("DUE_SIGNING_KEYSTORE").orNull
val ciSigningStorePassword = providers.environmentVariable("DUE_SIGNING_STORE_PASSWORD").orNull
val ciSigningKeyAlias = providers.environmentVariable("DUE_SIGNING_KEY_ALIAS").orNull
val ciSigningKeyPassword = providers.environmentVariable("DUE_SIGNING_KEY_PASSWORD").orNull
val hasCiSigning = listOf(
    ciSigningKeystore,
    ciSigningStorePassword,
    ciSigningKeyAlias,
    ciSigningKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "dev.sharno.due"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.sharno.due"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "1.0.2"

        // minSdk 26 natively supports vector drawables. Avoiding generated PNGs
        // also keeps release APKs reproducible across Android build environments.
        vectorDrawables {
            generatedDensities?.clear()
        }
    }

    signingConfigs {
        if (hasCiSigning) {
            create("ciTester") {
                storeFile = file(ciSigningKeystore!!)
                storePassword = ciSigningStorePassword
                keyAlias = ciSigningKeyAlias
                keyPassword = ciSigningKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // AGP otherwise embeds the checkout path and revision in the APK.
            vcsInfo {
                include = false
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }

        if (hasCiSigning) {
            create("signed") {
                initWith(getByName("release"))
                signingConfig = signingConfigs.getByName("ciTester")
                matchingFallbacks += listOf("release")
            }
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
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.01.00"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    kapt("androidx.room:room-compiler:2.6.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

kapt {
    correctErrorTypes = true
    arguments {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.incremental", "true")
    }
}
