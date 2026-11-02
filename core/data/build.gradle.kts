plugins {
    alias(libs.plugins.android.library)
}

android { namespace = "app.pengwa.core.data" }

dependencies {
    api(project(":core:model"))
    api(libs.kotlinx.coroutines.core)
}
