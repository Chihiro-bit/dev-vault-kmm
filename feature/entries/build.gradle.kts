plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
    
}
android {
    namespace = "com.devvault.feature.entries"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += "-opt-in=kotlin.io.encoding.ExperimentalEncodingApi"
    }
    buildFeatures { viewBinding = true }
}
dependencies {
    implementation(project(":core:ui"))
    testImplementation(libs.kotlin.test)
}
