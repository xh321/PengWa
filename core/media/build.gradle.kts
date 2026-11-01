plugins {
    alias(libs.plugins.android.library)
}

android { namespace = "app.pengwa.core.media" }

dependencies {
    implementation(project(":core:model"))
    implementation(libs.androidx.core.ktx)
}
