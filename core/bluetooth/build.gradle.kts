plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "app.pengwa.core.bluetooth"
    defaultConfig { testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
}

dependencies {
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation(libs.kotlin.test.junit)
    androidTestImplementation(project(":core:media"))
    implementation(libs.nordic.ble)
    api(project(":core:protocol"))
    implementation(libs.androidx.core.ktx)
}
