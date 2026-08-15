plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.chayu.volumecontrol"
    compileSdk = 36

    val releaseKeystoreFile = rootProject.file("release.jks")
    val releasePasswordFile = rootProject.file(".release-signing-password")
    val releaseSigningAvailable = releaseKeystoreFile.exists() && releasePasswordFile.exists()

    signingConfigs {
        if (releaseSigningAvailable) {
            create("release") {
                val releasePassword = releasePasswordFile.readText().trim()
                storeFile = releaseKeystoreFile
                storePassword = releasePassword
                keyAlias = "utb"
                keyPassword = releasePassword
            }
        }
    }

    buildTypes {
        getByName("release") {
            if (releaseSigningAvailable) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.chayu.volumecontrol"
        minSdk = 24
        targetSdk = 36
        versionCode = 15
        versionName = "0.0.15"
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom-alpha:2025.07.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.12.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3:1.5.0-alpha01")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
