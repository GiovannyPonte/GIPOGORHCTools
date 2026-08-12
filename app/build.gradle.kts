plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Keep Room's generated schemas versioned so migrations can be reviewed and verified.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        // Make annotation placement explicit before Kotlin adopts this behavior by default.
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }
}

android {
    namespace = "com.gipogo.rhctools"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {
        applicationId = "com.gio.gipogo.rhctools"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()

        versionCode = 20
        versionName = "17.0.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    bundle {
        language {
            // Runtime locale switching needs every translation in the installed bundle.
            enableSplit = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // Android, lifecycle, and navigation foundations.
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.androidx.navigation.compose)

    // Compose versions are aligned by a single BOM.
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Local preferences and encrypted Room persistence.
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.appcompat)
    implementation(libs.google.play.app.update)
    implementation(libs.google.play.app.update.ktx)

    implementation(libs.sqlcipher)
    implementation(libs.androidx.sqlite)
    implementation(libs.androidx.security.crypto)

    // Java API backports required on supported Android versions below API 26.
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    // JVM and device-side verification.
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:core-ktx:1.7.0")
}
