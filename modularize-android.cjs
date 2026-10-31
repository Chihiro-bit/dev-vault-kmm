const fs=require('fs'), path=require('path');
function write(p,s){fs.mkdirSync(path.dirname(p),{recursive:true});fs.writeFileSync(p,s)}
function move(from,to){const a=path.resolve(from),b=path.resolve(to);if(!a.startsWith(__dirname+path.sep)||!b.startsWith(__dirname+path.sep))throw Error('outside workspace');fs.mkdirSync(path.dirname(to),{recursive:true});fs.renameSync(from,to)}
const src='app/src/main/kotlin/com/devvault';
for(const [module,names] of [['model',['model','util']],['security',['crypto','platform','totp']],['data',['data','sync','AppStore.kt']]])for(const name of names)move(`${src}/${name}`,`core/${module}/src/main/kotlin/com/devvault/${name}`);
for(const name of ['NativeUi','NativeForm','VaultActivity','Constants','DvIcon']){
 const dest=`core/ui/src/main/kotlin/com/devvault/android/${name}.kt`;move(`${src}/android/${name}.kt`,dest);
 write(dest,fs.readFileSync(dest,'utf8').replace('package com.devvault.android','package com.devvault.android\n\nimport com.devvault.core.ui.R').replace('com.devvault.android.databinding','com.devvault.core.ui.databinding'));
}
for(const [module,file] of [['entries','EntryScreens'],['todos','TodoScreens'],['settings','SettingsScreens']]){
 const dest=`feature/${module}/src/main/kotlin/com/devvault/android/${file}.kt`;move(`${src}/android/${file}.kt`,dest);
 write(dest,fs.readFileSync(dest,'utf8').replace('package com.devvault.android','package com.devvault.android\n\nimport com.devvault.core.ui.R').replace('com.devvault.android.databinding',`com.devvault.feature.${module}.databinding`));
}
move('app/src/main/res/layout/card_entry.xml','feature/entries/src/main/res/layout/card_entry.xml');
move('app/src/main/res/layout/page_form.xml','core/ui/src/main/res/layout/page_form.xml');
for(const dir of ['values','values-night'])for(const file of fs.readdirSync(`app/src/main/res/${dir}`))move(`app/src/main/res/${dir}/${file}`,`core/ui/src/main/res/${dir}/${file}`);
move('app/src/test/kotlin/com/devvault/CryptoCompatTest.kt','core/security/src/test/kotlin/com/devvault/CryptoCompatTest.kt');
move('app/src/test/kotlin/com/devvault/sync/SniFreeHttpTest.kt','core/data/src/test/kotlin/com/devvault/sync/SniFreeHttpTest.kt');
const modules={
 'core:model':['api(libs.kotlinx.serialization.json)','api(libs.kotlinx.datetime)'],
 'core:security':['api(libs.kotlinx.serialization.json)','implementation(libs.androidx.appcompat)','implementation(libs.androidx.biometric)','implementation(libs.kotlinx.coroutines.android)'],
 'core:data':['api(project(":core:model"))','api(project(":core:security"))','api(libs.kotlinx.coroutines.android)','implementation(libs.ktor.client.core)','implementation(libs.ktor.client.okhttp)','implementation(libs.androidx.sqlite.bundled)'],
 'core:ui':['api(project(":core:data"))','api(libs.androidx.appcompat)','api(libs.androidx.lifecycle.runtime)','implementation(libs.androidx.constraintlayout)'],
 'feature:entries':['implementation(project(":core:ui"))'],
 'feature:todos':['implementation(project(":core:ui"))'],
 'feature:settings':['implementation(project(":core:ui"))'],
};
for(const [module,deps] of Object.entries(modules)){
 const dir=module.replaceAll(':','/');
 write(`${dir}/build.gradle.kts`,`plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
    ${['core:model','core:security','core:data'].includes(module)?'alias(libs.plugins.kotlinSerialization)':''}
}
android {
    namespace = "com.devvault.${module.replaceAll(':','.')}"
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
    ${deps.join('\n    ')}
    testImplementation(libs.kotlin.test)
}
`);
 write(`${dir}/src/main/AndroidManifest.xml`,'<manifest />\n');
}
let app=fs.readFileSync('app/build.gradle.kts','utf8');
app=app.slice(0,app.indexOf('dependencies {'))+`dependencies {
    implementation(project(":core:ui"))
    implementation(project(":feature:entries"))
    implementation(project(":feature:todos"))
    implementation(project(":feature:settings"))
    implementation(libs.androidx.recyclerview)
}
`;
write('app/build.gradle.kts',app.replace('    alias(libs.plugins.kotlinSerialization)\n',''));
write('settings.gradle.kts',fs.readFileSync('settings.gradle.kts','utf8')+'\n'+Object.keys(modules).map(m=>`include(":${m}")`).join('\n')+'\n');
write('gradle/libs.versions.toml',fs.readFileSync('gradle/libs.versions.toml','utf8')+'\nandroidLibrary = { id = "com.android.library", version.ref = "agp" }\n');
write('build.gradle.kts',fs.readFileSync('build.gradle.kts','utf8').replace('plugins {','plugins {\n    alias(libs.plugins.androidLibrary) apply false'));
const main='app/src/main/kotlin/com/devvault/android/MainActivity.kt';
write(main,fs.readFileSync(main,'utf8').replace('package com.devvault.android','package com.devvault.android\n\nimport com.devvault.core.ui.R'));
