# Aptabase 使用统计

统计默认关闭，用户在「主页 → 设置 → 分享使用统计」主动打开后才会初始化 SDK、发送后续事件。
未配置或配置无效的构建不初始化 SDK，也不发送事件；此时开关不可开启，并显示说明。
关闭后丢弃所有新事件，不补报关闭期间的行为。SDK 已经提交到线程池或发出的请求可能完成，
官方 Kotlin SDK 0.1.0 没有取消/清空接口，也没有磁盘离线队列；断网事件可能丢失。
这些统计用于发现功能使用趋势，不能视作可靠的业务账本或精确人数。

## 构建配置

SDK 固定为 `com.github.aptabase:aptabase-kotlin:0.1.0`，仅该模块允许从 JitPack 下载。
App Key 是内嵌在客户端的项目标识，不是管理凭证。不要填写个人访问令牌或管理密钥。

官方云端的 Key 自带区域 `A-EU-...` 或 `A-US-...`，不填 host：

```sh
APTABASE_APP_KEY=A-EU-yourappkey bash ./gradlew :app:assembleDebug
```

自托管使用 `A-SH-...` Key，并提供 HTTPS 根地址（不含账号、路径、查询参数或 fragment）：

```sh
APTABASE_APP_KEY=A-SH-yourappkey APTABASE_HOST=https://stats.example.org \
  bash ./gradlew :app:assembleRelease
```

也支持 Gradle 属性 `-PaptabaseAppKey=... -PaptabaseHost=...`，优先于同名环境配置。
开发者可将属性写入自己的 `~/.gradle/gradle.properties`；不要提交真实部署配置到仓库。
GitHub Actions 的构建步骤读取仓库 Variables `APTABASE_APP_KEY`、`APTABASE_HOST`，未设置时正常构建为不上报版本。
Debug 使用 SDK 的 `asDebug`，Release 使用 `asRelease`，在 Aptabase 中按环境查看，Nightly 属于 Debug。
改变配置后需要重新构建 APK。

## 公开事件清单

| 事件 | 属性 | 触发位置与口径 |
|---|---|---|
| `app_started` | 无 | MainActivity 新建且没有恢复状态；不含后台服务启动，旋转恢复不计 |
| `screen_view` | `screen=home/nearby/contacts/settings` | 主页面进入、从详情返回，以及设置面板打开；Compose 重组不重复发送，Activity 重建可能重计 |
| `peer_recognized` | `source=nfc/qr` | NFC 或二维码识别有效对方身份后进入发现流程；排除自己及当前发现会话内的重复识别；重试也可能计数 |
| `ble_exchange_started` | `direction=outgoing` | 附近列表申请或识别对方后的 BLE 申请实际发起时 |
| `ble_exchange_finished` | `direction=outgoing`, `result=success/failure` | BLE 发起端收到终态回调；不含识别后尚未发起 BLE 的发现超时 |
| `ble_card_received` | 无 | 接收端收到完整卡片完成回调；不是去重后的集邮册新增人数 |
| `card_created` | 无 | 首次显示名通过校验并持久化后；不是进入首次设置页 |
| `card_edit_started` | 无 | 用户打开卡片编辑器 |
| `card_editor_preview_opened` | 无 | 编辑器中主动打开卡片预览 |
| `card_edit_finished` | `result=success/cancelled` | 成功保存草稿 / 放弃编辑；不上传字段内容 |
| `card_viewed` | `card=own/contact` | 主动打开自己的卡片详情 / 集邮册某张卡片；不含对象标识 |
| `qr_scan_opened` | 无 | 从扩列页打开扫码页；不代表相机权限已授予 |
| `qr_scan_finished` | `result=peer/self/invalid/cancelled` | 扫到有效对方 / 自己 / 非法内容 / 主动关闭；不上传码内容 |
| `photo_capture_started` | 无 | 点击集邮册拍照入口；可能随后请求相机权限，不等同于系统相机已启动 |
| `photo_capture_finished` | `result=success/failure/cancelled` | 照片成功保存 / 失败 / 用户取消；不上传照片或文件路径 |
| `preference_requested` | `setting=background_broadcast/ongoing_notification`, `value=on/off` | 用户操作设置开关时；记录意图，权限拒绝或系统蓝牙未开可能导致实际功能未生效 |
| `exchange_mode_selected` | `mode=nfc_display/nfc_read/qr_scan` | 用户主动切换 NFC 展示 / 读取模式或点击扫码入口；不记录页面恢复、硬件启停或默认模式 |
| `ble_incoming_prompted` | 无 | 真实交换申请进入待处理队列；同一待处理请求不重复计数 |
| `ble_incoming_decided` | `decision=accept/decline/expired`, `mechanism=manual/nfc_auto/cooldown/capacity/timeout` | 手动同意或拒绝、NFC 短时自动授权、拒绝冷却、队列容量拒绝、待处理超时；只发送有效组合，模拟申请不计 |
| `peer_discovery_retried` | `source=nfc/qr` | 存在有效的上次识别目标且没有进行中交换时，用户发起重试；重试也会产生新识别事件 |
| `peer_discovery_timed_out` | `source=nfc/qr` | 识别后的 30 秒发现窗口到期且目标仍未完成发现流程；BLE 已发起后转入独立传输超时，不计入此事件 |

### 分析建议与限制

- `card_edit_started → card_edit_finished` 可观察编辑完成与放弃趋势；比较 `card_editor_preview_opened` 的使用频率。
- 对比 `card_viewed` 的 `own/contact` 分类，可观察自我展示与集邮册回访的相对使用频率。
- `qr_scan_opened → qr_scan_finished → peer_recognized → ble_exchange_started → ble_exchange_finished` 可观察扫码识别与传输各阶段的事件量。
- `photo_capture_started → photo_capture_finished` 可观察拍照入口的使用与完成趋势；设置意图事件用于评估后台发现和通知功能偏好。
- `exchange_mode_selected` 比较主动选择的交换方式；默认展示模式没有操作事件，不能从这些事件推断所有人的默认使用方式。`ble_incoming_decided` 的固定机制可区分手动意图、自动策略与待处理超时，不表示底层传输已经完成。
- 这些是匿名事件量，不是去重用户数。只覆盖主动开启统计的人群，存在选择偏差；不应据此推断所有用户偏好。
- 未增加安装 ID、用户画像或跨会话关联 ID；中途退出、进程死亡和不可靠网络会使漏斗不闭合，事件比率不能当作精确转化率。

`ble_exchange_finished` 的成功与失败只统计底层 BLE 交换；NFC 与二维码负责识别，并非独立完成交换。
不为每次交换上传关联 ID，因此不能跨设备拼接漏斗；不能把接收端事件与发起端成功事件相加当作交换次数。
当前不上传错误原文、错误码或耗时，避免把协议日志中可能出现的身份信息带出；后续可增加固定分类错误码。
实验室模拟申请不触发真实申请处理及卡片接收埋点；预览入口只渲染测试状态，不触发任何统计。

## 上报字段与隐私

业务代码只能发送 `AnalyticsEvent` 中预定义的事件和分类值，接口不接受任意属性。
不上传名片 GUID、姓名、联系方式、照片、二维码载荷、蓝牙地址、真实错误信息或诊断日志。
SDK 自动包含：`isDebug`、`osName`、`osVersion`、`locale`、`appVersion`、`appBuildNumber`、`sdkVersion`、`deviceModel`，
以及事件时间和内存中的随机会话 ID（SDK 当前约一小时不活跃后更换，进程重启后重新生成）。
没有使用名片 GUID 或安装持久 ID 作为统计身份。服务端仍会在网络层收到 IP，运营者应自行明确访问日志与数据保留策略。

## 验证

```sh
bash ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

单元测试覆盖配置拒绝、默认不采集、只采集开启后的事件、关闭后丢弃、SDK 异常隔离和属性白名单。
运行中的设置页前后截图、生产路径 smoke 及未验证项见 [行为埋点扩展验证](screenshots/analytics/README.md#行为埋点扩展统计范围说明更新)。
取得真实 App Key 后，还需要在目标网络上验证：开启 → 进入页面和完成交换 → Aptabase Debug 面板出现上述事件；
关闭 → 再操作不新增事件；重启后保留开关；离线与恢复不影响交换功能。真实云端收件和双机交换尚未验证。

参考：[官方 Kotlin SDK](https://github.com/aptabase/aptabase-kotlin/tree/0.1.0)、
[SDK 源码](https://github.com/aptabase/aptabase-kotlin/blob/0.1.0/aptabase/src/main/java/com/aptabase/Aptabase.kt)。
