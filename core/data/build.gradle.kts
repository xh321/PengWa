plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "app.pengwa.core.data"
    defaultConfig { testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
}

dependencies {
    implementation(libs.androidx.datastore.preferences)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlin.test.junit)
    api(project(":core:model"))
    api(libs.kotlinx.coroutines.core)
}
