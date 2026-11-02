plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

dependencies {
    api(project(":core:model"))
    // Flow and BitMatrix are part of this module's public API.
    api(libs.kotlinx.coroutines.core)
    api(libs.zxing.core)
    testImplementation(libs.kotlin.test.junit)
}
