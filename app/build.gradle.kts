plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
}



android {
    namespace = "com.group_43.sevns"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.group_43.sevns"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")

    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("org.osmdroid:osmdroid-android:6.1.18")

    implementation(platform("com.google.firebase:firebase-bom:34.6.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-firestore:24.8.1")
    implementation("com.google.firebase:firebase-database:20.3.0")
    implementation(libs.firebase.auth)
    implementation(libs.annotation)
    implementation(libs.lifecycle.livedata.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")

    implementation("org.osmdroid:osmdroid-android:6.1.18")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

}
