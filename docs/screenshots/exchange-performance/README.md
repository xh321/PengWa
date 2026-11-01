# 交换性能协议 V3：二维码载荷版本更新

设备：Android Emulator sdk_gphone64_x86_64，emulator-5556；Android 16（API 36）。
截图来自安装 APK 后运行中的 UiPreviewActivity，使用内置测试身份，无真实用户信息。

步骤：安装 V2 / V3 APK，设置系统浅色或深色，启动：
`adb shell am start -n app.pengwa.debug/app.pengwa.ui.UiPreviewActivity --es screen home --ei sample 0`

| 状态 | V2 | V3 |
|---|---|---|
| 主页二维码／浅色 | ![](qr-before-light.png) | ![](qr-after-light.png) |
| 主页二维码／深色 | ![](qr-before-dark.png) | ![](qr-after-dark.png) |

二维码像素随 D3 载荷改变，布局没有调整。截图仅验证运行中的显示，不证明 NFC / BLE 识别、确认或真实传输性能；双真机验证待补。
