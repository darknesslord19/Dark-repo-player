import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val cloudstreamVersion: String by project

android {
    namespace = "com.streambox.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.streambox.app"
        minSdk = 24
        targetSdk = 35
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "0.1.${System.getenv("GITHUB_RUN_NUMBER") ?: "0"}"
    }

    // Her derlemede aynı anahtar (AOSP testkey) kullanılır: yeni APK eskisinin üstüne kurulur
    signingConfigs {
        create("shared") {
            storeFile = rootProject.file("keystore/streambox.p12")
            storeType = "pkcs12"
            storePassword = "streambox"
            keyAlias = "streambox"
            keyPassword = "streambox"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("shared")
        }
        getByName("release") {
            // Plugin'ler uygulamanın sınıflarına isimle eriştiği için küçültme KAPALI olmalı
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/*.kotlin_module",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/versions/9/previous-compilation-data.bin"
            )
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    // --- CloudStream API: .cs3 plugin'lerin çağırdığı sınıfların gerçek hali ---
    implementation("com.github.recloudstream.cloudstream:library:$cloudstreamVersion")

    // Plugin'lerin uygulamada hazır bulmayı beklediği kütüphaneler
    implementation("com.github.Blatzar:NiceHttp:0.4.11")
    implementation("org.jsoup:jsoup:1.18.3")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.1")
    implementation("org.mozilla:rhino:1.7.14")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-dnsoverhttps:4.12.0")

    // Oynatıcı
    val media3 = "1.8.0"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-exoplayer-dash:$media3")
    implementation("androidx.media3:media3-datasource-okhttp:$media3")
    implementation("androidx.media3:media3-ui:$media3")

    // Arayüz
    implementation(platform("androidx.compose:compose-bom:2025.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("io.coil-kt:coil-compose:2.7.0")
}

// Nuvio eklentilerinin istediği crypto-js kütüphanesini derlemeden önce indir
val fetchJsLibs by tasks.registering {
    val out = file("src/main/assets/js/crypto-js.min.js")
    outputs.file(out)
    doLast {
        if (!out.exists() || out.length() < 1000) {
            out.parentFile.mkdirs()
            URI("https://cdnjs.cloudflare.com/ajax/libs/crypto-js/4.2.0/crypto-js.min.js")
                .toURL().openStream().use { i -> out.outputStream().use { i.copyTo(it) } }
        }
    }
}
tasks.named("preBuild") { dependsOn(fetchJsLibs) }
