# 主页二维码与扫码入口

- 移除二维码外层卡片的白色背景、描边和额外内边距。二维码固定黑白，保留四模块识别静区；放大二维码使用相同绘制方式。
- 删除底部通栏“扫一扫”，改为右上角的取景框式扫码图标（Phosphor Scan）；使用 Material `IconButton` 的 48dp 触控区域和“扫一扫”无障碍名称。
- 移除主页直接编辑入口。点击眼睛图标“查看我的卡片”，在名片预览内点击“编辑”；保存后返回预览。

截图来自运行中的 Debug APK，使用 `adb exec-out screencap -p` 获取。变更前为 `409ffb6`，变更后为本分支。测试设备：Android Emulator `emulator-5556`，`sdk_gphone64_x86_64`，Android 16 / API 36，1080 × 2400，420dpi，字体 1.0。预览使用固定合成身份“小棉花”与 `00000000`，相机流程使用既有测试身份 `TestCard`；没有真实用户资料。

| 页面／状态 | 变更前 | 变更后 |
|---|---|---|
| 主页，浅色 | ![](before-home-light.png) | ![](after-home-light.png) |
| 主页，深色 | ![](before-home-dark.png) | ![](after-home-dark.png) |
| 名片预览内编辑入口，浅色 | ![](before-my-card-light.png) | ![](after-my-card-light.png) |
| 名片预览内编辑入口，深色 | ![](before-my-card-dark.png) | ![](after-my-card-dark.png) |
| 放大二维码，浅色 | ![](before-expanded-light.png) | ![](after-expanded-light.png) |
| 放大二维码，深色 | ![](before-expanded-dark.png) | ![](after-expanded-dark.png) |

补充状态：[相机扫码](after-scanner-light.png)、[375dp 手机](after-home-phone-375.png)、[320dp／2 倍字体](after-home-large-text.png)、[640 × 320dp 横屏](after-home-landscape.png)。适配截图均为浅色、420dpi，分辨率依次为 984 × 1968、840 × 1680、1680 × 840。

## 复现与验证

1. `bash ./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest :core:protocol:test`，安装 `app/build/outputs/apk/debug/app-debug.apk`。
2. 启动 `adb shell am start -n app.pengwa.debug/app.pengwa.ui.UiPreviewActivity --es screen home --ei sample 1`，使用 NFC 就绪且已设置钱包的固定场景。将 screen 改为 `my-card` 查看预览内编辑入口；点击主页二维码查看放大状态。
3. 使用 `adb shell cmd uimode night yes`／`no` 切换深浅主题；同一场景分别截图。
4. 正常启动应用，使用测试身份完成引导。点击右上角“扫一扫”，允许相机权限后应进入取景界面；点击“关闭”或系统返回，应恢复二维码。
5. 点击眼睛图标“查看我的卡片”，点击“编辑”，保存后应回到预览；点击“返回”回到主页。主页不再提供直接编辑按钮。
6. 点击二维码放大，再关闭；核对二维码保持黑白。使用 ZXing 从运行截图解码，核对主页及放大状态在深浅主题下均可识别。
7. 用 `wm size` 切换上述尺寸、`settings put system font_scale 2.0` 验证大字体；结束后恢复尺寸及字体。

构建、Lint、应用单元测试、二维码协议测试及 `git diff --check` 通过。模拟器验证扫码进入／关闭／系统返回、名片预览／编辑／保存／返回、二维码放大／关闭。真实双机相机识别、蓝牙交换、NFC 硬件及 TalkBack 未复测。
