plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
}

android {
    namespace = "com.devvault.android"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.devvault.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "android.test.InstrumentationTestRunner"
        // 旧版 runner 默认用 DexFile 扫描整个 APK 查找用例，在新系统上会超时，直接指定用例类
        testInstrumentationRunnerArguments["class"] = "com.devvault.android.LayoutSmokeTest"
    }
    buildTypes {
        debug {
            // Isolated package for UI verification; never shares the user's vault.
            if (providers.gradleProperty("uiPreview").isPresent) applicationIdSuffix = ".uitest"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += "-opt-in=kotlin.io.encoding.ExperimentalEncodingApi"
    }
    buildFeatures { viewBinding = true }
    // LayoutSmokeTest 使用平台自带的 android.test，无需额外 androidx.test 依赖
    useLibrary("android.test.base")
    useLibrary("android.test.runner")
}

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":feature:entries"))
    implementation(project(":feature:todos"))
    implementation(project(":feature:settings"))
    implementation(libs.androidx.recyclerview)
}
