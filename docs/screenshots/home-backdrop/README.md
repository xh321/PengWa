# 主页面背景装饰

主页面交换区新增两张轻轻相碰的淡莓粉色名片插画，搭配爱心和中心闪光，呼应交换名片。装饰位于二维码卡片下方的右下角留白，不参与布局、点击或无障碍朗读。二维码继续使用现有主题的不透明卡片背景。深色主题降低插画透明度；交换区高度不足 440dp 时隐藏，避免短窗口里装饰与说明争抢空间。没有新增动画。

资源：[`home_friend_cards.webp`](../../../app/src/main/res/drawable-nodpi/home_friend_cards.webp)，768 × 512，保留透明通道，约 30 KB。使用内置 imagegen 生成，再缩放、转为 WebP 用于 Android 打包。生成原图保留在本机 imagegen 输出目录。

## 生成提示词

```text
Use case: stylized-concept. Asset type: subtle decorative illustration for the lower corner of PengWa, an Android app for exchanging friend cards. Primary request: two friend cards gently bumping together, expressing a friendly first meeting. A pair of small horizontal rounded paper business cards tilted inward toward one another; their inner upper corners touch at the center with a tiny four-point sparkle. Each card has one simple circular avatar silhouette and two short abstract embossed lines, no readable text. One card dusty blush pink, the other warm ivory with muted berry outlines. Two tiny floating hearts above the contact point and only a few delicate sparkles. Refined flat paper-cut illustration with subtle paper grain, clean rounded edges, warm friendly understated mood, low saturation. Compact cohesive wide composition, 3:2 canvas, generous truly transparent margins. Must clearly read as exactly two business cards, not smartphones or stamps. No flowers, leaves, postage stamps, postal marks, frame, background rectangle, scenery, characters, letters, numbers, logos, QR codes, heavy shadows or glow. Intended to remain clear at 200dp wide and low opacity behind an Android UI. Transparent background.
```

## 运行截图（PR 描述附录）

Android Emulator，`emulator-5570`，`sdk_gphone64_x86_64`，Android 16 / API 36，420 dpi。全部来自运行中的 Debug APK，通过 `adb exec-out screencap -p` 获取，使用 `UiPreviewActivity` 的固定合成身份“小棉花 / 00000000”，无真实用户信息。变更前基于 main `19fa94b`，变更后为该基线加上本次背景装饰。

| 页面／状态 | 变更前 | 变更后 |
| --- | --- | --- |
| 主页面，二维码，1080 × 2400，浅色 | ![](before-light.png) | ![](after-light.png) |
| 主页面，二维码，1080 × 2400，深色 | ![](before-dark.png) | ![](after-dark.png) |
| 主页面，二维码，984 × 1968（375dp），浅色 | ![](before-small.png) | ![](after-small.png) |
| 主页面，二维码，840 × 1680（320dp），2 倍字体，浅色 | ![](before-large-text.png) | ![](after-large-text.png) |
| 主页面，二维码，1680 × 840，横屏，浅色 | ![](before-landscape.png) | ![](after-landscape.png) |

## 复现

1. 执行 `bash ./gradlew :app:assembleDebug :app:lintDebug`，安装 `app/build/outputs/apk/debug/app-debug.apk`。
2. 执行 `adb -s emulator-5570 shell am start -n app.pengwa.debug/app.pengwa.ui.UiPreviewActivity --es screen home --ei sample 1`。此入口复用生产页面的 `FaceToFacePageContent` 与装饰组件，不启动交换硬件。
3. 使用 `shell cmd uimode night yes/no` 切换主题；每次等待页面重绘再截图。
4. 使用 `shell wm size 984x1968` 验证小手机；`shell wm size 840x1680` 和 `shell settings put system font_scale 2.0` 验证大字；恢复字体后用 `shell wm size 1680x840` 验证横屏。短窗口隐藏插画，控件继续沿用原有滚动布局。
5. 使用 `adb -s emulator-5570 exec-out screencap -p > screenshot.png` 保存图片。结束后执行 `shell wm size reset`、`shell settings put system font_scale 1.0`、`shell cmd uimode night no` 恢复。

## 验证边界

构建和 Android lint 通过。上述运行截图用于核对装饰位置、主题与布局；没有修改交换逻辑，未进行双机 NFC／蓝牙或真实扫码交换验证。UI/UX Pro Max 的 Compose 搜索没有匹配背景装饰条目，采用技能内置移动端规则复核留白、无障碍与主题表现。
