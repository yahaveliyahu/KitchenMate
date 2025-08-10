import java.util.Properties
import java.io.FileInputStream


plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.services)
    id("org.jetbrains.kotlin.kapt")
}

// Reading the local.properties file
val localProperties = Properties().apply {
    load(FileInputStream(rootProject.file("local.properties")))
}
val spoonacularApiKey = localProperties["spoonacularApiKey"] as String



android {
    namespace = "dev.yahaveliyahu.kitchenmate"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.yahaveliyahu.kitchenmate"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "SPOONACULAR_API_KEY", "\"$spoonacularApiKey\"")

    }

    buildFeatures {
        buildConfig = true
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

}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-appcheck-ktx")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    kapt("com.github.bumptech.glide:compiler:4.16.0")
    implementation("org.atteo:evo-inflector:1.3")
    implementation ("com.google.firebase:firebase-storage:20.3.0")
    implementation ("com.google.mlkit:text-recognition:16.0.0")
    implementation("com.google.android.material:material:1.11.0")

    // OkHttp
    implementation ("com.squareup.okhttp3:okhttp:4.12.0")

    // CameraX
    implementation ("androidx.camera:camera-camera2:1.3.0")
    implementation ("androidx.camera:camera-lifecycle:1.3.0")
    implementation ("androidx.camera:camera-view:1.3.0")
// ML Kit barcode
    implementation ("com.google.mlkit:barcode-scanning:17.2.0")
// ML Kit
    implementation ("com.google.mlkit:image-labeling:17.0.7")

// ZXing
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
// tensorflow-lite
    implementation("org.tensorflow:tensorflow-lite:2.13.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.3")

// tess-two
    implementation("com.rmtheis:tess-two:9.1.0")




    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

