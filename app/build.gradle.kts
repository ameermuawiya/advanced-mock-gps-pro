plugins {
    id("com.android.application")
}

android {
    namespace = "com.muawiya.fakegps"

    compileSdk = 36

    defaultConfig {
        applicationId = "com.muawiya.fakegps"
        minSdk = 26
        targetSdk = 36

        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isCrunchPngs = false

            proguardFiles(
                getDefaultProguardFile(
                    "proguard-android-optimize.txt"
                ),
                "proguard-rules.pro"
            )


            signingConfig = signingConfigs.getByName("debug")
        }

        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {

    // Android Core
    // implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.appcompat:appcompat:1.8.0-alpha01")

    // Material Design
    implementation("com.google.android.material:material:1.14.0")

    // UI
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.viewpager2:viewpager2:1.1.0")

    // Preferences
    implementation("androidx.preference:preference:1.2.1")

    // Activity
    implementation("androidx.activity:activity:1.13.0")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0-rc01")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.11.0-rc01")
    implementation("androidx.lifecycle:lifecycle-livedata:2.11.0-rc01")
    implementation("androidx.lifecycle:lifecycle-common-java8:2.11.0-rc01")

    // Room
    implementation("androidx.room:room-runtime:2.8.4")
    annotationProcessor("androidx.room:room-compiler:2.7.0")
    
    implementation("com.google.code.gson:gson:2.14.0")

    // WorkManager
    implementation("androidx.work:work-runtime:2.11.2")

    // Retrofit
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-moshi:3.0.0")

    // Moshi
    implementation("com.squareup.moshi:moshi-kotlin:1.15.2")

    // OkHttp
    implementation("com.squareup.okhttp3:okhttp:5.4.0")
    implementation("com.squareup.okhttp3:logging-interceptor:5.4.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // DataStore
    implementation(
        "androidx.datastore:datastore-preferences:1.3.0-alpha09"
    )

    // Google Maps
    implementation("com.google.android.gms:play-services-maps:20.0.0")

    // Location
    implementation(
        "com.google.android.gms:play-services-location:21.3.0"
    )

    // CameraX
    implementation("androidx.camera:camera-core:1.7.0-alpha01")
    implementation("androidx.camera:camera-camera2:1.7.0-alpha01")
    implementation("androidx.camera:camera-lifecycle:1.7.0-alpha01")
    implementation("androidx.camera:camera-view:1.7.0-alpha01")

    // Tests
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")

    androidTestImplementation(
        "androidx.test.ext:junit:1.3.0"
    )

    androidTestImplementation(
        "androidx.test.espresso:espresso-core:3.7.0"
    )

    androidTestImplementation(
        "androidx.test:runner:1.7.0"
    )
}