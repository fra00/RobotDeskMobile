import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) load(FileInputStream(file))
}

android {
    namespace = "com.example.mydeskrobot"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.mydeskrobot"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "LLM_API_KEY",
            "\"${localProperties.getProperty("LLM_API_KEY", "")}\"",
        )
        buildConfigField(
            "String",
            "LLM_BASE_URL",
            "\"${localProperties.getProperty("LLM_BASE_URL", "http://10.0.2.2:1234/v1/")}\"",
        )
        buildConfigField(
            "String",
            "LLM_MODEL",
            "\"${localProperties.getProperty("LLM_MODEL", "")}\"",
        )
        buildConfigField(
            "String",
            "LLM_VISION_MODEL",
            "\"${localProperties.getProperty("LLM_VISION_MODEL", "")}\"",
        )
        buildConfigField(
            "String",
            "WEATHER_API_KEY",
            "\"${localProperties.getProperty("WEATHER_API_KEY", "")}\"",
        )
        buildConfigField(
            "String",
            "SEARX_BASE_URL",
            "\"${localProperties.getProperty("SEARX_BASE_URL", "https://searx.be")}\"",
        )
        buildConfigField(
            "String",
            "EMBEDDING_MODEL_DIR",
            "\"${localProperties.getProperty("embedding.model.dir", "")}\"",
        )
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    androidResources {
        noCompress += "onnx"
    }
    packaging {
        resources {
            pickFirsts += "META-INF/AL2.0"
            pickFirsts += "META-INF/LGPL2.1"
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.moshi)
    implementation(libs.okhttp)
    implementation(libs.jsoup)
    implementation(libs.moshi.kotlin)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.vosk.android) {
        exclude(group = "net.java.dev.jna", module = "jna")
    }
    implementation(libs.onnxruntime.android)
    implementation(libs.djl.tokenizers) {
        exclude(group = "net.java.dev.jna", module = "jna")
    }
    // vosk (AAR) and DJL (JAR) both transitively depend on JNA — pin Android artifact once.
    implementation("net.java.dev.jna:jna:5.14.0@aar")
    testImplementation(libs.onnxruntime)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}