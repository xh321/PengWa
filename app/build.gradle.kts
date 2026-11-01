import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// 发布签名：口令只从本机的 secrets/keystore.properties 读取（该目录已被 git 忽略）。
// 没有这个文件时发布版保持未签名，其他人 clone 下来仍能正常构建调试版。
// 也可以用 -PkeystoreProperties=<文件路径> 指定别的文件。模板见根目录的 keystore.properties.example。
val keystoreProperties = Properties().apply {
    val path = providers.gradleProperty("keystoreProperties").orNull
        ?: rootProject.file("secrets/keystore.properties").path
    val file = file(path)
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "app.pengwa"
    // AndroidX 最新版要求 compileSdk >= 37；targetSdk 仍保持 36，不影响运行时行为
    compileSdk = 37

    defaultConfig {
        applicationId = "app.pengwa"
        // 29：往自己创建的相册条目写照片不需要存储权限，省掉旧版本的一整套分支
        minSdk = 29
        targetSdk = 36
        val ciRun = providers.environmentVariable("GITHUB_RUN_NUMBER").orNull?.toInt()
        versionCode = providers.gradleProperty("appVersionCode").orNull?.toInt()
            ?: ciRun?.let { 100_000 + it * 100 + (providers.environmentVariable("GITHUB_RUN_ATTEMPT").orNull?.toInt() ?: 1) }
            ?: 2
        versionName = providers.gradleProperty("appVersionName").orNull ?: "0.1.1"
        val revision = providers.environmentVariable("GITHUB_SHA").orNull
            ?.takeIf { it.matches(Regex("[0-9a-fA-F]{40,64}")) } ?: "local"
        buildConfigField("String", "BUILD_REVISION", "\"$revision\"")
    }

    signingConfigs {
        if (keystoreProperties.getProperty("storeFile") != null) {
            create("release") {
                // 相对路径按项目根目录解析，绝对路径原样使用
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                // 留空表示与库口令相同
                keyPassword = keystoreProperties.getProperty("keyPassword")?.takeIf { it.isNotBlank() }
                    ?: keystoreProperties.getProperty("storePassword")
            }
        }
    }

    buildTypes {
        debug {
            val run = providers.environmentVariable("GITHUB_RUN_NUMBER").orNull?.toInt()
            if (run != null) versionNameSuffix = "-nightly.$run"
            // 调试版可与正式版共存
            applicationIdSuffix = ".debug"
        }
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    // 直接分发 APK 时压缩 DEX 和原生库，安装时由系统解压。
    // 保留全部 CPU 架构、资源和功能；DEX 设置不影响 AAB。
    packaging {
        dex.useLegacyPackaging = true
        jniLibs.useLegacyPackaging = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        screenshotTests.create("screenshotTest") {
            engineVersion = libs.versions.screenshot.get()
            targetVariants.add("debug")
            dependencies {
    implementation(libs.coil.compose)
                implementation(libs.androidx.compose.ui.tooling)
                implementation(libs.screenshot.validation.api)
            }
        }
    }
}

dependencies {
    implementation(libs.coil.compose)
    implementation(project(":core:model"))
    implementation(project(":core:protocol"))
    implementation(project(":core:data"))
    implementation(project(":core:bluetooth"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:nfc"))
    implementation(project(":core:media"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // 二维码：ZXing core 同时负责生成和识别；CameraX 只负责取景和送帧
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.kotlin.test.junit)
}
