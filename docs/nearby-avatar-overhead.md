# 附近头像的开销与验证

基于附近列表去重修复 #36。头像使用独立可选 GATT 特征，先发布姓名和身份，再在同一次连接中读取小图，不读取完整名片。头像是公开预览，无需交换同意；原头像和完整卡片仍需同意。

## 预算与行为

| 项目 | 实现 / 上限 |
|---|---|
| 广播 | 不增加广播及 scan response 字节 |
| 首次头像 | 一个普通 GATT 读取，包最多 512 B；包含 17 B 版本和身份摘要，图像最多 495 B |
| 无头像 | 返回 17 B 空图片包；结果也缓存 |
| 小 MTU / 旧同协议应用 | MTU < 513 或缺少可选特征时跳过图片，保留文字头像 |
| 延时预算 | 可选读取最多 1.5 秒；正常姓名预览先回调；已排队的目标交换优先 |
| 重复发现 | 同一扫描会话按身份最多缓存 32 项；换地址复用；停止扫描清空 |
| 图片 | sRGB、正方形中心裁切；48 px 起，逐级降低质量及尺寸；去掉 ICC/EXIF 等元数据 |
| 发送端 | 原头像变化时在后台压缩一次；GATT 回调只读不可变字节 |
| 展示 | 复用已有 Coil 管线和缓存；无头像 / 异常 / 解码失败保持 48 dp 文字头像 |
| 新生产依赖 | 无；蓝牙模块的 media 依赖只用于 Android 测试 |

32 项 Base64 字符正文按 UTF-16 上界计算约 41.3 KiB（32 × 660 × 2 B），不含对象和键。单张 48 × 48 ARGB 位图为 9 KiB；20 张同时展示的位图像素约 180 KiB。Coil 的键、缓存、渲染资源和 JVM 对象另计，不能将以上数字等同于进程 RSS。

## Android 上的编码 / 解码测量

设备：Android Emulator `PengWaSignal`，`sdk_gphone64_x86_64`，Android 16 / API 36。`NearbyAvatarImagesTest` 用固定生成的虚构头像、横图、透明图和噪声图，预热 3 次后测量 30 次；每次均重新编码或解码，不把缓存命中计入编解码耗时。P95 使用排序后的第 29 项。

| 输入 | 原图 B | 缩略图 B | 整包 B | 编码 P50 / P95 ms | 解码 P50 / P95 ms | 解码位图 B |
|---|---:|---:|---:|---:|---:|---:|
| 生成的脸 | 1,925 | 342 | 359 | 0.360 / 0.384 | 0.051 / 0.074 | 9,216 |
| 横图 | 2,141 | 260 | 277 | 0.383 / 0.450 | 0.059 / 0.085 | 9,216 |
| 透明图 | 4,167 | 344 | 361 | 0.828 / 0.905 | 0.032 / 0.065 | 9,216 |
| 固定噪声 | 8,870 | 360 | 377 | 0.607 / 0.641 | 0.035 / 0.082 | 9,216 |

这些样本均保留 48 × 48 像素。实测 Android 编码器附带了 456 B ICC 配置，已显式剥离后再判断字节预算。容器按 [WebP 官方规范](https://developers.google.com/speed/webp/docs/riff_container) 处理；Android 30 及以上使用 [WEBP_LOSSY](https://developer.android.com/reference/android/graphics/Bitmap.CompressFormat#WEBP_LOSSY)，Android 29 使用 WEBP 有损分支。以上是模拟器结果，不代表手机处理器或实际照片的通用耗时。

## 双模拟器 GATT 验证

`NearbyAvatarPeerTest` 在 `PengWaIcon` / `emulator-5554` 与 `PengWaSignal` / `emulator-5556` 同时运行服务端和客户端，经模拟蓝牙扫描、连接、MTU 协商和真实 GATT API 读取。每个场景读取两次名字，服务端头像提供器被调用恰好 1 次；未发交换申请、未传完整名片。

| 场景 | 首次包大小 | 名字回调后到头像回调 | 第二次头像回调 | 服务端头像读取数 |
|---|---:|---:|---:|---:|
| 实际编码的纯色测试头像 | 93 B | 16 ms | 1 ms，身份缓存命中 | 1 |
| 无头像 | 17 B | 17 ms | 0 ms，身份缓存命中 | 1 |

两端共 4 次测试通过。Android 可能在两个短生命周期 GATT 客户端之间保留物理链路，因此不以服务端连接事件数作为重复传输次数。传输时间由本次虚拟无线环境决定，不是实体手机的吞吐或功耗测量。头像命中缓存后通常由下一次合并发布显示，页面还有既有的最多 300 ms 发布间隔；表中不包含这一 UI 间隔。

复现：

```sh
bash ./gradlew :core:media:assembleDebugAndroidTest :core:bluetooth:assembleDebugAndroidTest
adb -s emulator-5556 install -r core/media/build/outputs/apk/androidTest/debug/media-debug-androidTest.apk
adb -s emulator-5556 shell am instrument -w -e class app.pengwa.identity.NearbyAvatarImagesTest app.pengwa.core.media.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5556 logcat -d -s NearbyAvatarBench:I
```

分别在两台 BLE 设备安装 `core/bluetooth/build/outputs/apk/androidTest/debug/bluetooth-debug-androidTest.apk`，先启动服务端，再启动客户端：

```sh
adb -s emulator-5554 shell am instrument -w -e class app.pengwa.ble.NearbyAvatarPeerTest -e role server -e scenario avatar app.pengwa.core.bluetooth.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5556 shell am instrument -w -e class app.pengwa.ble.NearbyAvatarPeerTest -e role client -e scenario avatar app.pengwa.core.bluetooth.test/androidx.test.runner.AndroidJUnitRunner
```

两条命令需在不同终端并行执行。将双方 `scenario` 改为 `empty` 验证无头像缓存；客户端日志标签为 `NearbyAvatarPeer`。

## 检查与未验证项

- 协议模块 164 项、应用 12 项单元测试通过；Android 图片测试 3 项、两端 GATT 测试 4 次通过；Debug 构建和 lint 通过。
- 实际界面的浅色、深色、等待同意、无头像、损坏图片回退、换地址合并和窄屏大字截图见 [截图记录](screenshots/nearby-avatar/README.md)。
- 头像按会话缓存，扫描中对方改头像不会立即轮询更新；重新打开扫描后读取新图。48 px 小图在高密度屏幕上会放大，清晰度低于完整名片头像，这是压低蓝牙开销的取舍。
- 小 MTU、旧版缺少特征、读取超时按代码路径回退；本次没有实体手机、小 MTU对端或 Android 10 真机实测。电池、无线干扰、20 人同时在线、真实低端机耗时和进程 RSS 尚未测量。
