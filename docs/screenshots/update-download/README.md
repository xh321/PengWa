# 国内下载线路与断点续传验证

默认线路为“自动”，优先使用国内加速服务，失败后回退 GitHub。可手动选择加速或直连，并记住选择。
新增当前线路、下载量和速度提示，下载失败后可直接重试，相同摘要的部分文件支持续传。

## 设备、数据和范围

- Android Emulator，AVD `PengWaNavigation` 独立只读实例，`sdk_gphone64_x86_64`，Android 16 / API 36，端口 5562。
- 常规 1080 × 2400 / 420 dpi / 字体 1.0；窄屏 840 × 1680 / 字体 1.5 / 深色。
- 变更前 APK 来自自更新初版提交 `7872540`，变更后为本次源码构建。全部图片来自运行中的 APK，通过 `adb exec-out screencap -p` 获取。
- `update` 固定状态展示生产 `AppUpdateContent`，不触发下载或安装，不代表完成了对应网络流程。
- `update-live` 使用生产 `AppUpdateSection` 与真实 ViewModel，可实际切换线路、保存偏好和检查远端清单。其余设置开关仅为测试占位。
- 没有真实用户姓名、ID 或联系人数据。固定下载量、速度及更新说明均为合成测试数据。

## 同场景前后截图（固定测试数据）

| 页面 / 状态 | 变更前 | 变更后 |
| --- | --- | --- |
| 发现新版 · 浅色 | ![新版信息前](available-before.png) | ![新版信息后，默认自动](available-after.png) |
| 发现新版 · 深色 | ![深色新版前](available-before-dark.png) | ![深色新版后](available-after-dark.png) |
| 下载 46% · 浅色 | ![下载前](downloading-before.png) | ![下载后，线路、下载量和速度](downloading-after.png) |
| 下载 46% · 深色 | ![深色下载前](downloading-before-dark.png) | ![深色下载后](downloading-after-dark.png) |

## 关键状态

| 状态 | 浅色 | 深色 |
| --- | --- | --- |
| 自动模式回退到 GitHub（固定下载状态） | ![自动回退](auto-fallback.png) | ![深色自动回退](auto-fallback-dark.png) |
| 中断后可重试下载（固定状态） | ![重试下载](download-retry.png) | ![深色重试](download-retry-dark.png) |
| 校验后待安装（固定状态，线路选择禁用） | ![待安装](ready-after.png) | ![深色待安装](ready-after-dark.png) |
| 手动选择 GitHub（固定状态） | ![直连选择](direct-selected.png) | ![深色直连选择](direct-selected-dark.png) |

| 窄屏大字体（已滚动至更新区域） | 截图 |
| --- | --- |
| 新版信息与下载按钮 | ![窄屏新版](available-narrow-dark.png) |
| 自动回退与下载进度 | ![窄屏回退](auto-fallback-narrow-dark.png) |
| 下载中断及重试按钮 | ![窄屏重试](download-retry-narrow-dark.png) |

窄屏使用纵向滚动，线路选项和操作按钮可换行，截图可能只包含滚动至末尾时的可见部分。

## 真实设备行为验证

1. 在 `update-live` 首次打开，界面默认选择“自动”。
2. 切换“GitHub 直连”，强制停止并重新启动 Activity；确认 SharedPreferences 保存 `GitHub`，重新打开仍选直连。
3. 切回“自动”并点检查更新，实际向加速线路请求清单；当前清单不存在，回退 GitHub，显示“当前线路：GitHub 直连”及缺少清单的提示。
4. 确认偏好已保存为 `Auto`。测试后保持默认自动。

| 默认自动 | 手动直连 |
| --- | --- |
| ![真实默认自动](live-auto-default-dark.png) | ![真实手动直连](live-direct-selected-dark.png) |

| 实际检查失败后回退，浅色 | 实际检查失败后回退，深色 |
| --- | --- |
| ![真实自动回退](live-auto-fallback.png) | ![真实深色自动回退](live-auto-fallback-dark.png) |

## 自动测试与公开资源探测

已通过 `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug`：应用单元测试共 31 项，含新增 16 项：

- 默认自动、加速优先及直连绕过代理；代理配置、仓库 URL、重定向 HTTPS 主机限制（5 项）。
- 续传 `206`、忽略 Range 的 `200`、加速失败回退、断流保存进度后重新创建下载器续传、`416` 重新开始、错误范围拒绝、摘要错误丢弃后回退、超大响应拒绝、更新变更清理旧部分、完整缓存重新计算摘要及协程取消传播（11 项）。

续传测试用确定性的 HTTP 连接替身和临时文件，验证最终字节一致性、请求偏移和缓存保留，不依赖公开网络。
清单生成脚本原有 2 项测试也通过。

2026-11-02 在当前开发环境对公开 Nightly `pengwa-nightly-111-1.apk` 请求 `Range: bytes=0-65535`：

- GitHub 直连和 `gh-proxy.com` 都返回 `206`，`Content-Range: bytes 0-65535/18781062`。
- 两份 65536 字节响应内容逐字节一致。
- 这是服务协议可用性探测，不是国内运营商吞吐测试，不能推断国内用户能达到相同速度。

## 复现

```bash
adb -s emulator-5562 shell am force-stop app.pengwa.debug
adb -s emulator-5562 shell am start -W \
  -n app.pengwa.debug/app.pengwa.ui.UiPreviewActivity --es screen update --ei sample 7
```

`sample`：2 发现新版、3 加速下载、7 自动回退直连下载、8 中断重试、9 手动直连。
真实行为将 `--es screen update` 改为 `--es screen update-live`，不需要 `sample`。
深色用 `cmd uimode night yes`；窄屏用 `wm size 840x1680` 和 `settings put system font_scale 1.5`，随后滚动至更新区。
结束后恢复 `wm size reset`、字体 `1.0`、浅色 `no`。

## 尚未验证

- 中国不同地区、运营商及弱网环境的实际吞吐量、时延与可用性；第三方服务可能限速、失效或缓存滞后。
- 公开 Release 仍缺少更新清单，未完成真实 APK 下载中断后的 Android 设备续传、进程被系统终止后的设备级续传或跨线路完整下载。
- 完整安装授权、系统安装确认/取消与覆盖安装后的数据保留验证仍待两版同签名 APK 发布后完成；本次没有修改或上传任何签名密钥。
