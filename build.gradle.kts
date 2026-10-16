/*
 * SPDX-FileCopyrightText: 2021-2025 Aurora OSS
 * SPDX-FileCopyrightText: 2022-2025 The Calyx Institute
 * SPDX-FileCopyrightText: 2023 grrfe <grrfe@420blaze.it>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

buildscript {
    dependencies {
        // https://developer.android.com/build/releases/agp-9-0-0-release-notes#runtime-dependency-on-kotlin-gradle-plugin-upgrade
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
    }
}

plugins {
    base
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.jetbrains.kotlin.compose) apply false
    alias(libs.plugins.jetbrains.kotlin.parcelize) apply false
    alias(libs.plugins.jetbrains.kotlin.serialization) apply false
    alias(libs.plugins.google.ksp) apply false
    alias(libs.plugins.androidx.navigation) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.hilt.android.plugin) apply false
    alias(libs.plugins.google.firebase.crashlytics) apply false
}

val testSlekeArchitecture by tasks.registering(Exec::class) {
    group = "verification"
    description = "Tests the SLEKE architecture checker against passing and violating fixtures."
    workingDir(rootDir)
    environment("PYTHONDONTWRITEBYTECODE", "1")
    commandLine(
        "python3",
        "-m",
        "unittest",
        "discover",
        "-s",
        "scripts/tests",
        "-p",
        "test_sleke_architecture.py"
    )
    inputs.file("scripts/check_sleke_architecture.py")
    inputs.files(fileTree("scripts/tests") { include("test_sleke_architecture.py") })
}

val checkSlekeArchitecture by tasks.registering(Exec::class) {
    group = "verification"
    description = "Checks SLEKE source and dependency boundaries across every source set."
    dependsOn(testSlekeArchitecture)
    workingDir(rootDir)
    environment("PYTHONDONTWRITEBYTECODE", "1")
    commandLine("python3", "scripts/check_sleke_architecture.py", "--root", rootDir.absolutePath)
    inputs.file("scripts/check_sleke_architecture.py")
    inputs.files(fileTree("sleke/src") { include("**/*.kt") })
    inputs.files(fileTree("sleke") { include("build.gradle*") })
}

project(":sleke") {
    val checkArchitecture by tasks.registering {
        group = "verification"
        description = "Runs the SLEKE boundary checker and its regression fixtures."
        dependsOn(rootProject.tasks.named("checkSlekeArchitecture"))
    }
    tasks.matching { it.name == "check" }.configureEach {
        dependsOn(checkArchitecture)
    }
}

tasks.named("check") {
    dependsOn(":app:check", ":sleke:check", checkSlekeArchitecture)
}
