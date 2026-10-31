plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
    
}
android {
    namespace = "com.devvault.core.ui"
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
    api(project(":core:data"))
    api(libs.androidx.appcompat)
    api(libs.androidx.lifecycle.runtime)
    api(libs.androidx.constraintlayout)
    testImplementation(libs.kotlin.test)
}
