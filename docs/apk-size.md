# APK 体积优化验证

2026-11-02，以 main `7fe4e0a` 为基线，同一份源码、依赖、AGP 9.5.0 与 JDK 21 对比。

| 构建 | 修改前（字节） | 修改后（字节） | MiB 前 → 后 | 减少 |
|---|---:|---:|---:|---:|
| debug | 44,442,873 | 18,031,745 | 42.38 → 17.20 | 59.43% |
| release | 4,271,812 | 2,345,448 | 4.07 → 2.24 | 45.09% |

Release 为未签名 APK，Debug 使用本机调试签名。Nightly 使用相同 Debug 打包配置；实际大小随提交和签名略有差异。

## 配置与取舍

`app/build.gradle.kts` 中启用 `packaging.dex.useLegacyPackaging = true` 和 `packaging.jniLibs.useLegacyPackaging = true`，压缩 DEX 和原生库，避免未压缩原生库带来的 APK 页对齐填充。保留全部 CPU 架构、语言资源、业务功能、Debug 调试能力，以及 Release 的 R8 和资源裁剪。

安装时系统需要解压代码和原生库，安装后占用可能增加；本表仅代表 APK 文件体积，不代表内存、启动速度或安装空间改善。DEX 设置不影响 AAB 生成的 APK，本次未测 AAB。参见 [DEX 打包 API](https://developer.android.com/reference/tools/gradle-api/9.5/com/android/build/api/dsl/DexPackaging)、[DEX 默认不压缩的取舍](https://developer.android.com/build/releases/agp-4-2-0-release-notes) 和 [原生库压缩说明](https://developer.android.com/guide/practices/page-sizes)。

## 验证

```sh
./gradlew :app:assembleDebug :app:assembleRelease testDebugUnitTest :core:model:test :core:protocol:test
apksigner verify --verbose app/build/outputs/apk/debug/app-debug.apk
zipalign -c -P 16 4 app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5556 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5556 shell am force-stop app.pengwa.debug
adb -s emulator-5556 shell am start -W -n app.pengwa.debug/app.pengwa.MainActivity
```

- Debug/Release 构建成功，205 项现有单元测试通过，0 失败。
- 两种 APK 的文件列表均与基线一致，DEX、原生库和其他文件解压后逐字节一致；只有 Manifest 因系统解压配置发生变化（`extractNativeLibs` 从 false 变为 true）。所有 DEX 和 `.so` 均已压缩。
- Debug 签名和 ZIP 对齐检查通过。ZIP 对齐检查不等于 16 KB 页大小设备运行验证。
- Android 16（API 36）、`sdk_gphone64_x86_64` 模拟器 `emulator-5556`：覆盖安装成功，主 Activity 冷启动成功，进程存活，检查启动后的 AndroidRuntime 日志无崩溃。
- 未测安装空间、启动性能差异或 16 KB 页大小设备；未重测真机 NFC、BLE、相机。Release 未签名 APK 未安装。

UI 截图：不适用；仅修改构建打包方式，没有 UI、图标、文案或资源内容变更。
