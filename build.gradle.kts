import com.android.build.api.dsl.LibraryExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

// Shared library defaults; modules declare only their namespace and dependencies.
subprojects {
    pluginManager.withPlugin("com.android.library") {
        extensions.configure<LibraryExtension> {
            compileSdk = 37
            defaultConfig { minSdk = 29 }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
        }
    }
    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
        extensions.configure<KotlinJvmProjectExtension> {
            compilerOptions.jvmTarget = JvmTarget.JVM_17
        }
    }
}
