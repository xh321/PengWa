# 自动显示默认钱包提示

设备：Pixel 7 配置模拟器，Android 16 / API 36，1080 × 2400。日期：2026-11-01。

所有截图来自模拟器上运行的 Debug 应用 `UiPreviewActivity`，使用固定名片“小棉花”、测试 UUID，注入 NFC 与默认钱包状态，不读取真实联系人或修改系统钱包。修改前预览与 main 相同，修改后使用本分支预览。

| 截图 | 页面 / 状态 |
| --- | --- |
| before-light.png / before-dark.png | 修改前出示页：非默认钱包，只有页面内设置入口 |
| after-light.png / after-dark.png | 修改后同一场景：自动显示应用内“设为默认钱包”提示 |
| after-dismissed.png | 返回键取消后：提示消失，页面内设置入口保留 |
| after-default.png | 已是默认钱包：不显示提示 |
| after-reader.png | 读取模式：不显示提示 |

复现：安装 Debug APK，启动 `adb shell am start -n app.pengwa.debug/app.pengwa.ui.UiPreviewActivity --es screen home --ei sample 0`。`sample=1` 验证已是默认钱包，`sample=2` 验证读取模式；切换场景前 force-stop 应用。通过 `adb shell cmd uimode night yes/no` 切换深浅主题。在提示上按返回键验证取消后不重弹。

本地 `testDebugUnitTest`、`assembleDebug`、`lintDebug` 通过，已目视核对提示及深浅主题。模拟器没有 NFC 硬件，预览只验证固定状态 UI：真实默认钱包判断、点击设置后系统请求和返回、旋转后不重弹、扫码返回、离开页面重新进入、NFC 开关及 Android 14 及以下入口仍待 NFC 真机验证，不能将完整设备行为验证标记为完成。
