import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

android {
    val localProps = Properties().apply { rootProject.file("local.properties").inputStream().use { load(it) } }
    namespace = "com.example.jagadiaapp"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.jagadiaapp"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "ORS_API_KEY", "\"${localProps.getProperty("ORS_API_KEY", "")}\"")
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures { buildConfig = true }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    implementation("org.maplibre.gl:android-sdk:13.7.1")
}