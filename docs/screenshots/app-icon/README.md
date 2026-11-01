# 应用图标截图与验证

截图日期：2026-11-01。设备：Android Emulator `PengWaIcon`，Pixel 7 硬件配置，1080 × 2400、420 dpi，Google APIs x86_64 系统镜像。Android 16 / API 36，构建 `BE2A.250530.026.F3`，Pixel Launcher 默认圆形遮罩。

前后均安装并启动了真实 Debug 应用 `app.pengwa.debug`。修改前 APK 来自 `9f6ddc3` 的 [CI 构建](https://github.com/xh321/PengWa/actions/runs/41310133037)，修改后 APK 由本分支构建。两者签名不同，因此在专用空白模拟器上卸载旧包后安装新包，重新将快捷方式放到相同位置。模拟器没有登录账号、导入联系人或真实用户数据，截图仅展示系统桌面与应用信息。

| 页面 / 状态 | 变更前 | 变更后 |
| --- | --- | --- |
| 桌面，关闭主题图标，浅色模式 | [launcher-before.png](launcher-before.png) | [launcher-after.png](launcher-after.png) |
| 系统应用信息，浅色模式 | [app-info-before.png](app-info-before.png) | [app-info-after.png](app-info-after.png) |
| 桌面，开启主题图标，浅色模式 | [themed-light-before.png](themed-light-before.png) | [themed-light-after.png](themed-light-after.png) |
| 桌面，开启主题图标，深色模式 | [themed-dark-before.png](themed-dark-before.png) | [themed-dark-after.png](themed-dark-after.png) |

桌面左侧中部为手动固定的碰碰娃图标；底部带圆环的图标为 Pixel Launcher 自动推荐，会随使用记录改变。对比以固定图标为准。旧版未声明应用图标，显示默认 Android 占位图；新版显示莓红彩色图标，开启主题图标后显示由系统着色的名片轮廓。

## 复现

1. 创建 Pixel 7 配置的 Android 16 / API 36 Google APIs 模拟器，安装 Debug APK。
2. 启动 `adb shell am start -n app.pengwa.debug/app.pengwa.MainActivity`，确认可以正常进入应用，再返回桌面。
3. 打开应用抽屉，将「碰碰娃」拖到桌面左侧中部，保持相同壁纸、位置和网格（4 × 5）。
4. 在「Wallpaper & style → Home screen」关闭 `Themed icons`，切换浅色模式，截图彩色图标。
5. 使用 `adb shell am start -a android.settings.APPLICATION_DETAILS_SETTINGS -d package:app.pengwa.debug` 打开系统应用信息，截图应用名称与图标。
6. 返回壁纸设置，开启 `Themed icons`。分别执行 `adb shell cmd uimode night no` 和 `adb shell cmd uimode night yes`，返回桌面，等待界面稳定后截图。
7. 使用 `adb exec-out screencap -p` 保存原始 PNG。前后截图均来自运行中的 Android 系统，没有用设计稿合成或替换图标。

## 结果与范围

- `:app:assembleDebug`、`:app:testDebugUnitTest` 通过，164 项测试全部通过。
- `:app:lintDebug` 通过。
- 新包安装及启动成功；桌面和应用信息均显示新图标，圆形遮罩没有裁掉名片或闪光；浅色与深色主题图标均可辨认。
- 本轮设备截图覆盖 Android 16 Pixel Launcher；其他厂商桌面、其他遮罩以及 Android 10–15 未做设备验证。
