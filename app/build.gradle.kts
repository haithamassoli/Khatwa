import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.haithamassoli.khatwa"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.haithamassoli.khatwa"
        minSdk = 31
        targetSdk = 37
        versionCode = 2
        versionName = "1.1.0"

        val localProps = Properties().apply { rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) } }
        val mapsKey = localProps.getProperty("MAPS_API_KEY", "")
        manifestPlaceholders["MAPS_API_KEY"] = mapsKey
        // Proxy mode (see server/): REST calls go through the Worker, which holds the key, so none is compiled into
        // BuildConfig. MAPS_API_KEY then only feeds the manifest (Maps SDK tiles) and should be Android-restricted.
        val proxyUrl = localProps.getProperty("KHATWA_PROXY_URL", "").trim().trimEnd('/')
        buildConfigField("String", "KHATWA_PROXY_URL", "\"$proxyUrl\"")
        buildConfigField("String", "MAPS_API_KEY", "\"${if (proxyUrl.isEmpty()) mapsKey else ""}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val signing = Properties().apply { rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) } }
    signingConfigs {
        if (signing.getProperty("RELEASE_STORE_FILE") != null) create("release") {
            storeFile = file(signing.getProperty("RELEASE_STORE_FILE"))
            storePassword = signing.getProperty("RELEASE_STORE_PASSWORD")
            keyAlias = signing.getProperty("RELEASE_KEY_ALIAS")
            keyPassword = signing.getProperty("RELEASE_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            optimization {
                enable = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.maps.compose)
    implementation(libs.play.services.location)
    implementation(libs.kotlinx.coroutines.play.services)
    testImplementation(libs.org.json) // android.jar's org.json is stubbed in JVM unit tests
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}