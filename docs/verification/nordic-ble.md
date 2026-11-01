# Nordic BLE 客户端验证

基线：main 661e145。保留原生扫描、广播和 GATT 服务端；客户端使用 Nordic Android BLE Library 2.11.0。业务层的串行设备队列、预览连接复用、同意前小申请、PWC3 分块、通知窗口和 ACK 保持原协议。

## 本地构建

```
bash ./gradlew :app:assembleDebug testDebugUnitTest :core:model:test :core:protocol:test
bash ./gradlew :core:bluetooth:assembleDebugAndroidTest
```

均通过。

## 两端实际蓝牙传输

设备：Android Emulator PengWaIcon（emulator-5554）与 PengWaSignal（emulator-5556），sdk_gphone64_x86_64，Android 16 / API 36；共享 emulator netsimd 虚拟蓝牙环境。使用固定测试 UUID 和合成卡片，无真实身份或联系人。

两台设备都安装 `core/bluetooth/build/outputs/apk/androidTest/debug/bluetooth-debug-androidTest.apk`，然后在两个终端同时运行：

```
adb -s emulator-5554 shell am instrument -w -r -e role server -e scenario accept -e class app.pengwa.ble.BlePeerTest app.pengwa.core.bluetooth.test/androidx.test.runner.AndroidJUnitRunner
```

```
adb -s emulator-5556 shell am instrument -w -r -e role client -e scenario accept -e class app.pengwa.ble.BlePeerTest app.pengwa.core.bluetooth.test/androidx.test.runner.AndroidJUnitRunner
```

`scenario` 可同时改为 `decline`、`cancel`。不提供 role 时此测试按要求跳过，不能把普通无设备运行算作双机验证。

| 场景 | 客户端 | 服务端 | 核对 |
|---|---|---|---|
| accept | 通过 | 通过 | 预览连接复用、同意后双方完整卡片、多个通知窗口、ACK 后才完成 |
| decline | 通过 | 通过 | 客户端收到拒绝、双方均没有完整名片回调 |
| cancel | 通过 | 通过 | 等待决定时停止扫描并取消连接、无完整名片、服务端处理断开 |

成功场景传输约 24 KB/方向（10 个长文本块和 PNG 头像），两端逐字段比较完整卡片。最终含 PNG 的运行协商 MTU 515，测试完成约 2–3 秒；此数值仅为模拟器运行记录，不代表真机速度。

尚未覆盖不同厂商真机、Android 10–15、真实射频干扰与系统后台限流。现有纯 Kotlin 协议测试继续覆盖 MTU 23 分块等边界，但不能替代上述真机验证。

UI 截图：不适用。此 PR 没有修改界面、布局或文案。
