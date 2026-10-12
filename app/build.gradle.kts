plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.raabta.keyboard"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.raabta.keyboard"
        minSdk = 26
        targetSdk = 35
        versionCode = 11
        versionName = "0.8.0"
    }
    // Ship a small ARM64 phone APK while retaining universal/ChromeOS builds.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            isUniversalApk = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
            listOf("https.proxyHost", "https.proxyPort", "http.proxyHost", "http.proxyPort", "http.nonProxyHosts").forEach { name ->
                System.getProperty(name)?.let { value -> it.systemProperty(name, value) }
            }
        }
    }
    lint { warningsAsErrors = true }
}

dependencies {
    implementation("com.google.mlkit:translate:17.0.3")
    // ML Kit declares an old OkHttp; resolve the maintained TLS implementation instead.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}
