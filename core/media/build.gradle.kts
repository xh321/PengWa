plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "app.pengwa.core.media"
    defaultConfig { testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
}

dependencies {
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation(libs.kotlin.test.junit)
    api(libs.coil.core)
    implementation(project(":core:model"))
    implementation(libs.androidx.core.ktx)
}
