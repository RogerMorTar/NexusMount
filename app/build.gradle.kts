plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.nexusmount.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.nexusmount.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 48
        versionName = "4.5.48"
    }


    signingConfigs {
        create("release") {
            storeFile = file("keystore/nexusmount-debug.jks")
            storePassword = "android"
            keyAlias = "nexusmount"
            keyPassword = "android"
        }
        getByName("debug") {
            storeFile = file("keystore/nexusmount-debug.jks")
            storePassword = "android"
            keyAlias = "nexusmount"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.6")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.6")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    // SMB client for real network shares
    implementation("com.hierynomus:smbj:0.13.0")
    implementation("org.slf4j:slf4j-android:1.7.36")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("androidx.drawerlayout:drawerlayout:1.2.0")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
}
