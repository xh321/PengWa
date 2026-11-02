# Aptabase 使用统计

首次引导的最后一步在完成按钮上方显示独立一行的「分享使用统计」复选项，默认勾选，可以取消。
点击旁边的「说明」查看采集字段；在点击完成引导前不保存选择，也不初始化 SDK 或发送事件。
点击完成时先持久化选择，再保存引导完成状态，两者成功后进入主页；保存失败则保留引导并显示重试提示。
返回上一步或切换教学方式不会重置当前勾选状态。重新从设置打开扩列图解不显示此选项，也不改统计选择。
「主页 → 设置 → 分享使用统计」保留随时关闭入口。既有用户的设置不自动改动，未设置的既有用户仍为关闭。
仅在服务配置有效、统计开关开启且引导完成时才会初始化 SDK、发送后续事件。
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
| `screen_view` | `screen=home/nearby/contacts/settings` | 主页面进入，以及设置面板打开；Compose 重组不重复发送，Activity 重建可能重计 |
| `peer_recognized` | `source=nfc/qr` | NFC 或二维码识别有效对方身份后进入发现流程；排除自己及当前发现会话内的重复识别；重试也可能计数 |
| `ble_exchange_started` | `direction=outgoing` | 附近列表申请或识别对方后的 BLE 申请实际发起时 |
| `ble_exchange_finished` | `direction=outgoing`, `result=success/failure` | BLE 发起端收到终态回调；不含识别后尚未发起 BLE 的发现超时 |
| `ble_card_received` | 无 | 接收端收到完整卡片完成回调；不是去重后的集邮册新增人数 |
| `card_created` | 无 | 首次显示名或引导卡片成功保存后；不代表引导同意前会发送事件 |
| `card_edit_started` | 无 | 用户从自己的卡片详情打开编辑器 |
| `card_editor_preview_opened` | 无 | 编辑器中主动打开卡片预览 |
| `card_edit_finished` | `result=success/cancelled` | 非引导编辑成功保存 / 确认放弃；校验失败不记成功 |
| `card_viewed` | `card=own/contact` | 主动打开自己的卡片详情 / 集邮册某张卡片；不含对象标识 |
| `qr_scan_opened` | 无 | 点击首页扫码入口且原来不在扫描；不代表相机权限已授予 |
| `qr_scan_finished` | `result=peer/self/invalid/cancelled` | 扫到有效对方 / 自己 / 非法内容 / 主动关闭；不上传码内容，peer 不代表完成交换 |
| `preference_requested` | `setting=background_broadcast/ongoing_notification`, `value=on/off` | 用户操作开关时；记录意图，权限拒绝或系统蓝牙未开可能导致功能未生效 |
| `ble_incoming_prompted` | 无 | 真实交换申请进入待处理队列；同一待处理请求不重复计数 |
| `ble_incoming_decided` | `decision=accept/decline/expired`, `mechanism=manual/nfc_auto/cooldown/capacity/timeout` | 手动同意或拒绝、NFC 短时自动授权、冷却/容量拒绝、待处理超时；只发送有效组合，模拟申请不计 |
| `peer_discovery_retried` | `source=nfc/qr` | 存在有效的上次识别目标且没有进行中交换时，用户发起重试；重试也会产生新识别事件 |
| `peer_discovery_timed_out` | `source=nfc/qr` | 识别后的 30 秒发现窗口到期且目标仍未完成发现流程；BLE 已发起后转入独立传输超时，不计入此事件 |

### 分析建议与限制

- `card_edit_started → card_edit_finished` 观察编辑完成/放弃趋势；`card_editor_preview_opened` 观察预览偏好。
- 对比 `card_viewed` 的 `own/contact`，观察自我展示与集邮册回访的相对频率。
- `qr_scan_opened → qr_scan_finished → peer_recognized → ble_exchange_started → ble_exchange_finished` 观察扫码识别与传输各阶段事件量；按识别来源、申请决定机制分析使用倾向。
- `preference_requested` 观察后台发现与通知的操作意图。
- main 已采用 NFC 自动协调，不再提供手动展示/读取模式选择，故不添加已失去触发入口的模式偏好事件。
- 这些是匿名事件量，不是去重人数；只覆盖开启统计的人群，存在选择偏差。未增加安装 ID、用户画像或跨会话关联 ID。
- 中途退出、进程死亡、引导未完成或不可靠网络会使漏斗不闭合，事件比率不能视作精确转化率。首次卡片保存可能早于引导完成，不补报被门禁丢弃的 `card_created`。
- `ble_incoming_decided` 记录成功消费 app 队列后的手动处理意图、自动授权提交或 app 待处理超时，不代表底层传输完成。

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

单元测试覆盖配置拒绝、未开启时不采集、只采集开启后的事件、关闭后丢弃、SDK 异常隔离和属性白名单。
运行中的设置页前后截图与复现步骤见 [截图记录](screenshots/analytics/README.md)。
已配置仓库变量 `APTABASE_APP_KEY`（官方 US 区域），一次带 `isDebug=true` 的 `integration_test` 接收接口请求返回 HTTP 200。
此请求仅验证 Key 与接收端可用性，不等同于已核查控制台图表。带配置的应用也已执行引导选择与重启测试。
还需要在目标网络及控制台核查：开启 → 进入页面和完成交换 → Aptabase Debug 面板出现上述事件；
关闭 → 再操作不新增事件；离线与恢复不影响交换功能。引导中的勾选和取消勾选均已在模拟器完成、重启并读取 DataStore 确认保存结果。Aptabase 控制台事件展示和双机交换尚未验证。

参考：[官方 Kotlin SDK](https://github.com/aptabase/aptabase-kotlin/tree/0.1.0)、
[SDK 源码](https://github.com/aptabase/aptabase-kotlin/blob/0.1.0/aptabase/src/main/java/com/aptabase/Aptabase.kt)。
