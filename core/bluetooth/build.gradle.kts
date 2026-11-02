plugins {
    alias(libs.plugins.android.library)
}

android { namespace = "app.pengwa.core.bluetooth" }

dependencies {
    api(project(":core:protocol"))
    implementation(libs.androidx.core.ktx)
}
