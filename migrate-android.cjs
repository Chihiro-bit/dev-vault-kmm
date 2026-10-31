const fs = require('fs');
const path = require('path');
const root = __dirname;
function write(p, s) { fs.mkdirSync(path.dirname(p), {recursive:true}); fs.writeFileSync(p,s); }
fs.cpSync('androidApp/src', 'app/src', {recursive:true});
const common = 'shared/src/commonMain/kotlin/com/devvault';
for (const name of fs.readdirSync(common)) {
  if (['ui','platform'].includes(name)) continue;
  fs.cpSync(`${common}/${name}`, `app/src/main/kotlin/com/devvault/${name}`, {recursive:true});
}
for (const name of ['platform/Platform','platform/DeviceAuth','sync/SniFreeHttp']) {
  write(`app/src/main/kotlin/com/devvault/${name}.kt`, fs.readFileSync(`shared/src/androidMain/kotlin/com/devvault/${name}.android.kt`,'utf8').replace(/\bactual /g,''));
}
const http = 'app/src/main/kotlin/com/devvault/sync/CloudHttp.kt';
write(http, fs.readFileSync(http,'utf8').replace(/internal expect object SniFreeHttp \{[\s\S]*?\n\}/,''));
fs.cpSync('shared/src/commonTest/kotlin','app/src/test/kotlin',{recursive:true});
fs.cpSync('shared/src/androidUnitTest/kotlin','app/src/test/kotlin',{recursive:true});
const icons = fs.readFileSync(`${common}/ui/theme/Icons.kt`,'utf8');
write('app/src/main/kotlin/com/devvault/android/DvIcon.kt', 'package com.devvault.android\n\n' + icons.slice(icons.indexOf('enum class DvIcon'),icons.indexOf('private val cache')));
const constants = fs.readFileSync(`${common}/ui/Constants.kt`,'utf8').replace('package com.devvault.ui','package com.devvault.android').replace('import androidx.compose.ui.graphics.Color','import android.graphics.Color').replace('import com.devvault.ui.theme.DvIcon','').replace(/Color\((0x[\dA-F]+)\)/g,'$1.toInt()').replace(/: Color/g,': Int');
write('app/src/main/kotlin/com/devvault/android/Constants.kt', constants);
const theme = fs.readFileSync(`${common}/ui/theme/Theme.kt`,'utf8');
for (const [name,folder] of [['LightColors','values'],['DarkColors','values-night']]) {
  const block = theme.slice(theme.indexOf(`val ${name}`)).split(')\n')[0];
  const colors = [...block.matchAll(/(\w+) = Color\(0x([\dA-F]+)\)/g)].map(m=>`    <color name="${m[1].replace(/[A-Z]/g,s=>'_'+s.toLowerCase())}">#${m[2]}</color>`).join('\n');
  write(`app/src/main/res/${folder}/colors.xml`,`<resources>\n${colors}\n</resources>\n`);
}
write('app/build.gradle.kts', `plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.kotlinSerialization)
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
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.androidx.sqlite.bundled)
    implementation(libs.androidx.biometric)
    testImplementation(libs.kotlin.test)
}
`);
write('build.gradle.kts',`plugins {
    alias(libs.plugins.kotlinAndroid) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.kotlinSerialization) apply false
}
`);
write('settings.gradle.kts',fs.readFileSync('settings.gradle.kts','utf8').replace('DevVaultMobile','DevVaultAndroid').replace('include(":shared")\ninclude(":androidApp")','include(":app")').replace('include(":shared")\r\ninclude(":androidApp")','include(":app")'));
write('gradle/libs.versions.toml', fs.readFileSync('gradle/libs.versions.toml','utf8').split(/\r?\n/).filter(l=>! /compose|Multiplatform|androidLibrary|darwin|androidx-activity/.test(l)).join('\n').replace('org.jetbrains.kotlin:kotlin-test"','org.jetbrains.kotlin:kotlin-test-junit"'));
write('gradle.properties',fs.readFileSync('gradle.properties','utf8').split(/\r?\n/).filter(l=>! /kotlin.mpp|kotlin.native|iOS/.test(l)).join('\n'));
write('app/src/main/res/values/themes.xml',`<resources>
    <style name="Theme.DevVault" parent="Theme.AppCompat.DayNight.NoActionBar">
        <item name="colorAccent">@color/primary</item>
        <item name="android:fontFamily">sans</item>
        <item name="android:colorBackground">@color/bg</item>
        <item name="android:textColorPrimary">@color/text</item>
        <item name="android:textColorSecondary">@color/text2</item>
        <item name="android:windowBackground">@color/bg</item>
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:navigationBarColor">@color/panel</item>
        <item name="android:windowLightStatusBar">true</item>
    </style>
</resources>`);
