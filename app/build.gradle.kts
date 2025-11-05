/*
 * SPDX-FileCopyrightText: 2021-2025 Rahul Kumar Patel <whyorean@gmail.com>
 * SPDX-FileCopyrightText: 2022-2025 The Calyx Institute
 * SPDX-FileCopyrightText: 2023 grrfe <grrfe@420blaze.it>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

@file:OptIn(KspExperimental::class)

import com.google.devtools.ksp.KspExperimental
import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.jetbrains.kotlin.compose)
    alias(libs.plugins.jetbrains.kotlin.parcelize)
    alias(libs.plugins.jetbrains.kotlin.serialization)
    alias(libs.plugins.google.ksp)
    alias(libs.plugins.androidx.navigation)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.google.services)
    alias(libs.plugins.rikka.tools.refine.plugin)
    alias(libs.plugins.hilt.android.plugin)
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
        freeCompilerArgs.addAll(
            "-Xannotation-default-target=param-property"
        )
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi",
            "coil3.annotation.ExperimentalCoilApi"
        )
    }
}

android {
    namespace = "com.aurora.store"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.sleke.store"
        minSdk = 26
        targetSdk = 36

        versionCode = 71
        versionName = "4.7.5"

        testInstrumentationRunner = "com.aurora.store.HiltInstrumentationTestRunner"
        testInstrumentationRunnerArguments["disableAnalytics"] = "true"

        buildConfigField("String", "EXODUS_API_KEY", "\"bbe6ebae4ad45a9cbacb17d69739799b8df2c7ae\"")

        missingDimensionStrategy("device", "vanilla")
    }

    packaging {
        resources {
//            excludes += "google/protobuf/descriptor.proto"
            pickFirsts += "**/*.proto"
        }
    }

    signingConfigs {
        if (File("signing.properties").exists()) {
            create("release") {
                val properties = Properties().apply {
                    File("signing.properties").inputStream().use { load(it) }
                }

                keyAlias = properties["KEY_ALIAS"] as String
                keyPassword = properties["KEY_PASSWORD"] as String
                storeFile = file(properties["STORE_FILE"] as String)
                storePassword = properties["KEY_PASSWORD"] as String
            }
        }
        create("aosp") {
            // Generated from the AOSP test key:
            // https://android.googlesource.com/platform/build/+/refs/tags/android-11.0.0_r29/target/product/security/testkey.pk8
            keyAlias = "testkey"
            keyPassword = "testkey"
            storeFile = file("testkey.jks")
            storePassword = "testkey"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }

        register("nightly") {
            isMinifyEnabled = false
            isShrinkResources = false
            isDebuggable = true
            initWith(getByName("release"))
            applicationIdSuffix = ".nightly"
        }

        debug {
//            applicationIdSuffix = ".debug"
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    flavorDimensions += "device"

    productFlavors {
        create("vanilla") {
            isDefault = true
            dimension = "device"
        }

        create("huawei") {
            dimension = "device"
            versionNameSuffix = "-hw"
        }

        // This flavor is only for preloaded devices / users who push the app to system
        create("preload") {
            dimension = "device"
            versionNameSuffix = "-preload"
        }
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
        aidl = true
        compose = true
    }
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_21.toString()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    lint {
        lintConfig = file("lint.xml")
    }

    androidResources {
        generateLocaleConfig = true
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    applicationVariants.configureEach {
        outputs.configureEach {
            (this as? BaseVariantOutputImpl)?.outputFileName = "Store_${versionName}.apk"
        }
    }
}

androidComponents {
    beforeVariants(selector().all()) { variant ->
        val flavour = variant.flavorName
        if ((flavour == "huawei" || flavour == "preload") && variant.buildType == "nightly") {
            variant.enable = false
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    useKsp2 = false // TODO: Drop after getting rid of epoxy
}

configurations.all {
    resolutionStrategy {
        force("com.google.protobuf:protobuf-javalite:4.26.1")
    }
}

dependencies {
    implementation(project(":sleke"))

    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.room:room-paging:2.8.3")
    //Google's Goodies
    implementation(libs.google.android.material)
    api(libs.google.protobuf.javalite)

    //AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.navigation3)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.paging.runtime)

    implementation(libs.androidx.adaptive.core)
    implementation(libs.androidx.adaptive.navigation)
    implementation(libs.androidx.adaptive.layout)
    implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.material3)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)

    // Paging Compose
    implementation("androidx.paging:paging-compose:3.3.6")

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    //Coil
    implementation(libs.coil.kt)
    implementation(libs.coil.compose)
    implementation(libs.coil.network)

    //Shimmer
    implementation(libs.facebook.shimmer)

    //Epoxy
    implementation(libs.airbnb.epoxy.android)
    ksp(libs.airbnb.epoxy.processor)

    //HTTP Clients
    implementation(libs.squareup.okhttp)
    implementation(libs.squareup.okhttp.logging)

    //Lib-SU
    implementation(libs.github.topjohnwu.libsu)

    //GPlayApi
    implementation(libs.auroraoss.gplayapi)

    //Shizuku
    compileOnly(libs.rikka.hidden.stub)
    implementation(libs.rikka.tools.refine.runtime)
    implementation(libs.rikka.shizuku.api)
    implementation(libs.rikka.shizuku.provider)

    implementation(libs.lsposed.hiddenapibypass)

    //Test
    testImplementation(libs.junit)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.google.truth)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.google.truth)
    androidTestImplementation(libs.androidx.espresso.core)

    //Hilt
    ksp(libs.hilt.android.compiler)
    ksp(libs.hilt.androidx.compiler)
    implementation(libs.androidx.hilt.navigation)
    implementation(libs.hilt.android.core)
    implementation(libs.hilt.androidx.work)

    kspAndroidTest(libs.hilt.android.compiler)
    androidTestImplementation(libs.hilt.android.testing)

    //Room
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)

    implementation(libs.process.phoenix)

//    "huaweiImplementation"(libs.huawei.hms.coreservice)

    // LeakCanary
    debugImplementation(libs.squareup.leakcanary.android)
}
