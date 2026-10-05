plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val qaKeystorePath = providers.environmentVariable("ASCEND_KEYSTORE_PATH").orNull
val qaKeystorePassword = providers.environmentVariable("ASCEND_KEYSTORE_PASSWORD").orNull
val qaKeyAlias = providers.environmentVariable("ASCEND_KEY_ALIAS").orNull
val qaKeyPassword = providers.environmentVariable("ASCEND_KEY_PASSWORD").orNull
val qaSigningAvailable = listOf(
    qaKeystorePath,
    qaKeystorePassword,
    qaKeyAlias,
    qaKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "app.ascend.mobile"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.ascend.mobile"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (qaSigningAvailable) {
            create("qa") {
                storeFile = file(requireNotNull(qaKeystorePath))
                storePassword = requireNotNull(qaKeystorePassword)
                keyAlias = requireNotNull(qaKeyAlias)
                keyPassword = requireNotNull(qaKeyPassword)
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            manifestPlaceholders["appLabel"] = "ASCEND Dev"
        }
        release {
            manifestPlaceholders["appLabel"] = "ASCEND"
            if (qaSigningAvailable) {
                signingConfig = signingConfigs.getByName("qa")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets {
        getByName("debug").assets.srcDir("../test-fixtures/front")
        getByName("test").resources.srcDir("../test-fixtures/front")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        warningsAsErrors = false
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.exifinterface)
    implementation(libs.mediapipe.tasks.vision)

    implementation(libs.androidx.room3.runtime)
    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.sqlite)
    ksp(libs.androidx.room3.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit4)

    // Keep the Phase 0 compatibility regression fixtures alongside WP06 tests.
    androidTestImplementation(libs.androidx.room3.runtime)
    androidTestImplementation(libs.sqlcipher.android)
    androidTestImplementation(libs.androidx.sqlite)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    kspAndroidTest(libs.androidx.room3.compiler)
}
