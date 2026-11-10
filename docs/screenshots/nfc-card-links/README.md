# 碰碰娃 NFC 名片验证截图

使用 Android Studio 模拟器 Android SDK built for arm64，Android 16 / API 36。所有名片均为合成测试数据。

| 文件 | 验证场景 |
| --- | --- |
| android-before.png / android-after.png | 同一测试名片与 NFC 就绪状态，主页由手机互碰改为自动读取实体标签 |
| android-after-dark.png | NFC 名片主页深色模式 |
| guide-before.png / guide-after.png | 首次引导第 3 步，选择 NFC，展示新的标签使用说明 |
| profile-before.png / profile-after.png | 同一张测试名片，核对 Profile 中新增的写入入口 |
| editor-before.png / editor-after.png | 同一编辑器示例滚动至保存按钮，核对 NFC 提示 |
| android-write-confirm.png | 在 NFC 就绪的测试状态下，从「我的名片」点「写入 NFC」，查看公开名片确认 |
| android-online-card.png | 在主页注入模拟标签链接，从本地 HTTPS 服务读取并自动打开名片 |
| web-light.png / web-dark.png | Chromium 手机视口 390 × 844，打开同一张在线名片 |

本地 HTTPS 联调已验证 App 发布、读取、同一链接更新和停用。模拟标签验证了进入 Profile 会暂停读取，返回主页后会自动读取。浏览器也读取了更新后的名片，停用后原链接返回 404。测试 App 使用临时证书信任配置，交付的 APK 不含测试证书或测试入口。

模拟器没有 NFC 硬件。主页 NFC 就绪状态和写入前确认使用测试数据演示，实体标签读写仍待真机验证。
