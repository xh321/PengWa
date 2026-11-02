# 交换申请与相遇成功动效验证

## 改动

- 交换申请由 Material `AlertDialog` 改为品牌弹层：两张卡片循环靠近、蓝牙连接标识、呼吸光环与星光；保留不可忽略弹层、明确“同意交换 / 拒绝”和原有控制器回调。
- 相遇成功由全局 `Snackbar` 改为非模态庆祝卡片：卡片汇聚、成功标识与一次性星光动画；保留震动，新事件通过 `collectLatest` 替换旧反馈。
- 成功卡片可手动关闭，并在 3.6 秒后自动消散；底层页面不锁死。系统动画时长比例为 0 时显示静态终态。
- 使用 Compose 矢量绘制，没有新增 Lottie、视频或图片播放器依赖。

## 运行截图

所有截图均来自运行中的 Debug 应用，使用虚构名字和 ID，不含真实用户信息。

设备：`sdk_gphone64_x86_64` 模拟器，Android 16；默认 1080×2400、420dpi。窄屏场景改为 840×1920、1.5 倍字体；横屏场景改为 1920×840。

| 场景 | 变更前 | 变更后 |
| --- | --- | --- |
| 交换申请 · 浅色 | [before-request-light.png](before-request-light.png) | [after-request-light.png](after-request-light.png) |
| 交换申请 · 深色 | 未单独留存 | [after-request-dark.png](after-request-dark.png) |
| 交换申请 · 窄屏大字体 | 未单独留存 | [after-request-large-text.png](after-request-large-text.png) |
| 相遇成功 · 浅色 | [before-meeting-light.png](before-meeting-light.png) | [after-meeting-light.png](after-meeting-light.png) |
| 相遇成功 · 深色 | 未单独留存 | [after-meeting-dark.png](after-meeting-dark.png) |
| 相遇成功 · 窄屏大字体 | 未单独留存 | [after-meeting-large-text.png](after-meeting-large-text.png) |
| 相遇成功 · 横屏可滚动 | 不适用，原 Snackbar 无此卡片布局 | [after-meeting-landscape.png](after-meeting-landscape.png) |
| 相遇成功 · 系统关闭动画 | 不适用，原 Snackbar 无定制动效 | [after-meeting-reduced-motion.png](after-meeting-reduced-motion.png) |

### 真实事件流与操作

| 验证 | 截图 | 结果 |
| --- | --- | --- |
| `MeetingRecorder.events → PengWaRoot` | [after-meeting-live-light.png](after-meeting-live-light.png) | 成功卡片在真实根组件中显示 |
| 成功反馈自动消散 | [after-meeting-dismissed.png](after-meeting-dismissed.png) | 约 3.6 秒后回到原页面，页面可操作 |
| 真实 incoming request 点击“拒绝” | [after-request-declined.png](after-request-declined.png) | 弹层消失，返回原页面 |

真实事件验证使用调试构建中的临时触发器，完成后已移除；最终代码只保留以下不写库的固定预览。这里的验证不代替两台设备的 NFC/BLE 完整交换验收。

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
  -n app.pengwa.debug/app.pengwa.ui.UiPreviewActivity --es screen meeting
```

深色：`adb shell cmd uimode night yes`；恢复：`adb shell cmd uimode night no`。
窄屏大字体：`adb shell wm size 840x1920` 和 `adb shell settings put system font_scale 1.5`；验证后执行 `adb shell wm size reset` 并恢复 `font_scale 1.0`。

## 自动检查

```bash
bash gradlew --no-daemon --continue \
  :app:assembleDebug testDebugUnitTest :core:model:test :core:protocol:test
bash gradlew :app:compileScreenshotTestKotlin
```

`RequestScreenshots` 与 `ConnectionSuccessScreenshots` 均使用浅色、深色、窄屏大字体参数。静态 PNG 只展示动画的一帧，不断言动画时序。
