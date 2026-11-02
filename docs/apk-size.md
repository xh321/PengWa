# APK 体积优化验证

2026-11-02，同一工作区、相同依赖和构建工具（AGP 9.5.0、JDK 21）构建前后对比。

| 构建 | 修改前（字节） | 修改后（字节） | 修改前 → 修改后（MiB） | 减少 |
|---|---:|---:|---:|---:|
| debug | 41,908,022 | 17,132,742 | 39.97 → 16.34 | 59.12% |
| release | 4,072,746 | 2,467,114 | 3.88 → 2.35 | 39.42% |

Release 为未签名 APK；Debug 使用本机调试签名。CI/Nightly 使用相同 Debug 打包配置，具体体积随提交和签名略有变化。

## 变更与取舍

`app/build.gradle.kts` 设置 `packaging.dex.useLegacyPackaging = true`，将 APK 中的 DEX 改为 ZIP 压缩。保留 Release 的 R8 代码优化、资源裁剪，以及 Debug 调试能力、全部语言资源和 CPU 架构。没有删减业务功能或改变界面。

项目 minSdk 为 29，AGP 默认不压缩 DEX；此次调整针对直接分发的 APK，不影响 AAB 生成的 APK。安装时系统需要解压，安装后占用可能增加；这里测量的是 APK 文件体积，不代表安装空间、内存或启动性能改善。参见 [Android 官方 DEX 打包说明](https://developer.android.com/reference/tools/gradle-api/9.5/com/android/build/api/dsl/DexPackaging) 与 [默认不压缩 DEX 的取舍](https://developer.android.com/build/releases/agp-4-2-0-release-notes)。

## 验证

```sh
./gradlew :app:assembleDebug :app:assembleRelease testDebugUnitTest :core:model:test :core:protocol:test
apksigner verify --verbose app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5556 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5556 shell am start -W -n app.pengwa.debug/app.pengwa.MainActivity
```

- 构建成功，188 项现有单元测试通过，0 失败。
- ZIP 检查：两种 APK 的全部 DEX 均已压缩；Release 解压后的每个文件与修改前逐字节一致，文件列表一致。
- Debug 签名验证通过。
- Android 16（API 36），`sdk_gphone64_x86_64` 模拟器 `emulator-5556`：覆盖安装成功，主 Activity 冷启动成功，进程存活，检查启动后的 AndroidRuntime 日志无崩溃。
- 未测量安装后占用、启动性能差异；未重测真机 NFC、BLE、相机。Release 未签名包未直接安装。

UI 截图：不适用。本次仅调整 APK 压缩方式，没有界面、文案或资源内容变更。
