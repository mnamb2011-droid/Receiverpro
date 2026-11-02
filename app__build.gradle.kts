import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties()
if (keystorePropsFile.exists()) {
    keystoreProps.load(FileInputStream(keystorePropsFile))
}

android {
    namespace = "com.smartreceiverpro.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.smartreceiverpro.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 100
        versionName = "1.0.0-PRO"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            if (keystorePropsFile.exists()) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile", "smart_receiver_pro_release.jks"))
                storePassword = keystoreProps.getProperty("storePassword", "")
                keyAlias = keystoreProps.getProperty("keyAlias", "smart_receiver_pro_key")
                keyPassword = keystoreProps.getProperty("keyPassword", "")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-DEBUG"
            isDebuggable = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
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
    bundle {
        language {
            enableSplit = false // Keep Arabic & English bundled for instant in-app switching
        }
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:security"))
    implementation(project(":core:database"))
    implementation(project(":core:network"))
    implementation(project(":core:design"))
    implementation(project(":core:media"))
    implementation(project(":core:discovery"))

    implementation(project(":receiver-engine:api"))
    implementation(project(":receiver-engine:protocol"))
    implementation(project(":receiver-engine:connection"))
    implementation(project(":receiver-engine:capabilities"))

    implementation(project(":receiver-plugins:senator"))
    implementation(project(":receiver-plugins:gmscreen"))
    implementation(project(":receiver-plugins:generic-upnp"))

    implementation(project(":feature:dashboard"))
    implementation(project(":feature:devices"))
    implementation(project(":feature:remote"))
    implementation(project(":feature:channels"))
    implementation(project(":feature:live-tv"))
    implementation(project(":feature:epg"))
    implementation(project(":feature:timers"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:satfinder"))
    implementation(project(":feature:media-sharing"))
    implementation(project(":feature:voice-control"))
    implementation(project(":feature:backups"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
