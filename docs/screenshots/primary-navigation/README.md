# 扩列主导航入口

将碰一碰／二维码／扫一扫所在的默认页面从“主页”改为导航正中的“扩列”。导航顺序为“附近娃娃／扩列／集邮册”。扩列入口使用常驻主题主色底、放大的碰一碰图标和加粗标签，选中时额外显示圆点；两侧保留常规导航样式。横屏短窗口的侧栏使用相同主次关系。默认打开的页面与交换逻辑沿用现有实现。

## 运行环境与测试数据

- Android Emulator，AVD `PengWaNavigation`，Pixel 6 配置，`sdk_gphone64_x86_64`，Android 16 / API 36。
- 常规：1080 × 2400、420 dpi、字体 1.0 倍；浅色与深色主题。
- 窄屏：840 × 1680、420 dpi（320 × 640dp）、字体 1.5 倍；浅色。
- 横屏短窗口：1680 × 840、420 dpi（640 × 320dp）、字体 1.0 倍；浅色。
- 全部图片来自运行中的 Debug APK，由 `adb exec-out screencap -p` 获取。使用原有 `UiPreviewActivity` 固定测试场景，没有真实用户姓名、ID 或联系人。
- 扩列场景为 `home` sample 1（合成身份“小棉花”，已是默认钱包）；附近场景为 `bluetooth` sample 0（未授权），集邮册为 `contacts` sample 0（空列表）。前后截图使用同一工作区基线、相同数据与设备配置。

## 前后截图（可用于 PR 描述）

| 页面／状态 | 变更前 | 变更后 |
|---|---|---|
| 扩列，浅色，选中 | ![变更前](before-home-light.png) | ![变更后](after-home-light.png) |
| 附近娃娃，扩列未选中 | ![变更前](before-nearby-light.png) | ![变更后](after-nearby-light.png) |
| 集邮册，扩列未选中 | ![变更前](before-contacts-light.png) | ![变更后](after-contacts-light.png) |
| 扩列，深色 | ![变更前](before-home-dark.png) | ![变更后](after-home-dark.png) |
| 扩列，320dp／1.5 倍字体 | ![变更前](before-narrow.png) | ![变更后](after-narrow.png) |
| 扩列，横屏侧栏 | ![变更前](before-landscape.png) | ![变更后](after-landscape.png) |

## 复现步骤

1. 执行 `bash ./gradlew assembleDebug lintDebug`，安装 `app/build/outputs/apk/debug/app-debug.apk`。
2. 打开合成扩列场景：

   ```sh
   adb -s emulator-5560 shell am force-stop app.pengwa.debug
   adb -s emulator-5560 shell am start -n app.pengwa.debug/app.pengwa.ui.UiPreviewActivity --es screen home --ei sample 1
   ```

3. 分别使用 `--es screen bluetooth --ei sample 0`、`--es screen contacts --ei sample 0` 打开两侧页面。确认扩列在正中、始终有主题主色底；仅选中扩列时标签前有圆点。
4. 用 `adb -s emulator-5560 shell cmd uimode night yes` 切换深色，`no` 恢复浅色。
5. 窄屏验证：`adb -s emulator-5560 shell wm size 840x1680`，`adb -s emulator-5560 shell settings put system font_scale 1.5`，确认三个入口及标签可见。
6. 字体恢复 `1.0`，使用 `wm size 1680x840` 验证横屏侧栏。完成后执行 `wm size reset`、`font_scale 1.0` 和 `cmd uimode night no` 恢复配置。
7. 用 `adb -s emulator-5560 exec-out screencap -p > screenshot.png` 截图。

## 验证结果与边界

- 原工作区的 Debug 构建、Android lint 与 `git diff --check` 通过。
- PR 独立分支基于最新 main（`a9ac122`）构建；全模块 `lintDebug` 被已有蓝牙模块的两处 `WrongConstant` 错误阻挡，位置均在未修改的 `core/bluetooth/.../BleExchangeManager.kt:902`。PR 未修改该模块。
- 已核对上述运行中应用的前后截图：导航顺序、扩列常驻强调、选中圆点、浅深主题、窄屏大字体和横屏侧栏。
- 主入口使用 Tab 角色和 selected 语义，整个导航项可点击，最小高度 80dp。未进行 TalkBack 语音实测。
- 固定预览场景的导航回调为空，仅用于验证界面与各页面选中状态；真实 NFC、蓝牙交换与扫码硬件行为未在本次复测。
