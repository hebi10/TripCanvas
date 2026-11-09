plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
}

val releaseSigningNames = listOf(
    "ANDROID_RELEASE_KEYSTORE_PATH", "ANDROID_RELEASE_STORE_PASSWORD",
    "ANDROID_RELEASE_KEY_ALIAS", "ANDROID_RELEASE_KEY_PASSWORD", "ANDROID_RELEASE_CERT_SHA256"
)
val releaseSigningEnvironment = releaseSigningNames.associateWith { providers.environmentVariable(it).orNull }
val hasReleaseSigning = releaseSigningEnvironment.values.all { !it.isNullOrBlank() }
val requestedVersionCode = providers.gradleProperty("versionCode")
    .orElse(providers.environmentVariable("ANDROID_VERSION_CODE")).orElse("1").get()
val releaseVersionCode = requestedVersionCode.toIntOrNull()
    ?.takeIf { it in 1..2100000000 }
    ?: throw GradleException("versionCode / ANDROID_VERSION_CODE must be an integer between 1 and 2100000000")

android {
    namespace = "com.hebi.tripcanvas"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.hebi.tripcanvas"
        minSdk = 26
        targetSdk = 36
        versionCode = releaseVersionCode
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("existingRelease") {
                storeFile = file(releaseSigningEnvironment.getValue("ANDROID_RELEASE_KEYSTORE_PATH")!!)
                storePassword = releaseSigningEnvironment.getValue("ANDROID_RELEASE_STORE_PASSWORD")
                keyAlias = releaseSigningEnvironment.getValue("ANDROID_RELEASE_KEY_ALIAS")
                keyPassword = releaseSigningEnvironment.getValue("ANDROID_RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("existingRelease")
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

val verifyReleaseSigning by tasks.registering(Exec::class) {
    doFirst {
        val missing = releaseSigningNames.filter { releaseSigningEnvironment[it].isNullOrBlank() }
        if (missing.isNotEmpty()) {
            throw GradleException("Release signing requires the provisioned app signer: ${missing.joinToString()}")
        }
        commandLine(
            "python3", rootProject.file("scripts/release_signing.py").absolutePath,
            "verify-keystore", "--keystore", releaseSigningEnvironment.getValue("ANDROID_RELEASE_KEYSTORE_PATH")!!
        )
    }
}
tasks.configureEach {
    if (name == "preReleaseBuild") dependsOn(verifyReleaseSigning)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation("org.maplibre.gl:android-sdk-opengl:13.7.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    ksp(libs.androidx.room.compiler)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    androidTestImplementation("androidx.test:core:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
}
