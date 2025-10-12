plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.hilt.android.plugin)
    alias(libs.plugins.google.ksp)
}

android {
    namespace = "com.sleke.library"
    compileSdk = 36

    defaultConfig {
        minSdk = 26

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
    }
}

dependencies {
    api(libs.timber)
    api("androidx.datastore:datastore-preferences:1.1.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")

    // Firebase
    api(platform(libs.firebase.bom))
    api(libs.firebase.auth)
    api(libs.firebase.firestore)
    api(libs.firebase.ui.firestore)
    api(libs.firebase.ui.auth)
    api(libs.firebase.core)

    // Paging
    api("androidx.paging:paging-runtime-ktx:3.3.0")

    // Lifecycle
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Room annotations for data models
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.compose.ui)

    ksp(libs.hilt.android.compiler)
    ksp(libs.hilt.androidx.compiler)
    implementation(libs.hilt.android.core)
    implementation(libs.hilt.androidx.work)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.bundles.ktor)
}