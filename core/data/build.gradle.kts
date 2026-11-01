plugins {
    alias(libs.plugins.ksp)
    alias(libs.plugins.android.library)
}

android {
    namespace = "app.pengwa.core.data"
    defaultConfig { testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }


dependencies {
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlin.test.junit)
    api(project(":core:model"))
    api(libs.kotlinx.coroutines.core)
}
