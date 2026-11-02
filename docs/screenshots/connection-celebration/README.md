# 交换申请与对方卡片反馈验证

## 改动

- 交换申请使用品牌弹层：两张卡片循环靠近、蓝牙连接标识、呼吸光环与星光；保留不可忽略弹层、明确“同意交换 / 拒绝”和原有控制器回调。
- 完整卡片交换成功后，只展示一次对方卡片，包含头像、名字和全部卡片内容；不再串行展示庆祝卡片。
- 对方卡片在 3.6 秒后自动消失，也可提前手动关闭。手动关闭不是必要步骤，新增必要操作为 0。
- 长卡片可滚动；连续相遇会替换旧卡片并重新计时。识别或连接失败时不展示不完整名片。
- 蓝牙连接状态仍在二维码下方展示，进行中使用卡片靠近动效；权限与错误状态保留文字和恢复操作。

## 运行截图

所有截图均来自运行中的 Debug 应用，使用虚构名字和 ID，不含真实用户信息。

设备：`sdk_gphone64_x86_64` 模拟器，Android 16；默认 1080×2400、420dpi。窄屏场景改为 840×1920、1.5 倍字体。

| 场景 | 截图 |
| --- | --- |
| 交换申请 · 浅色 | [after-request-light.png](after-request-light.png) |
| 交换申请 · 深色 | [after-request-dark.png](after-request-dark.png) |
| 交换申请 · 窄屏大字体 | [after-request-large-text.png](after-request-large-text.png) |
| 对方卡片 · 浅色 | [after-peer-card-light.png](after-peer-card-light.png) |
| 对方卡片 · 深色 | [after-peer-card-dark.png](after-peer-card-dark.png) |
| 对方卡片 · 窄屏大字体 | [首屏](after-peer-card-large-text.png) / [滚动后](after-peer-card-large-text-scrolled.png) |
| 连接状态动效 | [after-inline-connection-status.png](after-inline-connection-status.png) |

### 真实事件流与操作

| 验证 | 截图 | 结果 |
| --- | --- | --- |
| `MeetingRecorder.events → PengWaRoot` | [after-peer-card-live-light.png](after-peer-card-live-light.png) | 真实根组件自动弹出对方卡片 |
| 无任何后续操作 | [after-peer-card-auto-dismissed.png](after-peer-card-auto-dismissed.png) | 约 3.6 秒后卡片自动消失，直接回到原页面，没有第二段庆祝反馈 |
| 真实 incoming request 点击“拒绝” | [after-request-declined.png](after-request-declined.png) | 弹层消失，返回原页面 |

真实事件验证使用实验室现有“5 秒后模拟收到一个交换申请”，最终代码未增加临时触发器；完整卡片视觉使用固定预览。这里不代替两台设备的 NFC/BLE 完整交换验收。

## 固定预览复现

```bash
ADB="$HOME/.local/share/android-sdk/platform-tools/adb"
bash gradlew :app:assembleDebug
"$ADB" -s emulator-5554 install -r -d app/build/outputs/apk/debug/app-debug.apk

# 每次切换 screen 前 force-stop，确保预览 Activity 使用新 extra 冷启动。
"$ADB" -s emulator-5554 shell am force-stop app.pengwa.debug
"$ADB" -s emulator-5554 shell am start -W \
  -n app.pengwa.debug/app.pengwa.ui.UiPreviewActivity --es screen request

"$ADB" -s emulator-5554 shell am force-stop app.pengwa.debug
"$ADB" -s emulator-5554 shell am start -W \
  -n app.pengwa.debug/app.pengwa.ui.UiPreviewActivity --es screen peer-card
```

深色：`adb shell cmd uimode night yes`；恢复：`adb shell cmd uimode night no`。
窄屏大字体：`adb shell wm size 840x1920` 和 `adb shell settings put system font_scale 1.5`；验证后执行 `adb shell wm size reset` 并恢复 `font_scale 1.0`。

## 自动检查

```bash
bash gradlew --no-daemon --continue \
  :app:assembleDebug testDebugUnitTest :core:model:test :core:protocol:test
bash gradlew :app:compileScreenshotTestKotlin
```

`RequestScreenshots` 与 `PeerCardScreenshots` 均使用浅色、深色、窄屏大字体参数。静态 PNG 只展示动画的一帧，不断言动画或自动消失时序；自动消失通过上述真实事件流验证。
