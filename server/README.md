# 在线名片与 NFC 标签

Android 主页的「写入 NFC」先确认公开当前名片，再把一条标准 NDEF URI 记录写入实体标签。记录内容形如 `https://cards.example.com/c/<cardId>`。浏览器打开这个地址即可查看名片，App 的「读取 NFC」也用同一地址定位卡片。扫码和附近娃娃仍用于蓝牙交换和集邮。

目前每个 Android 安装只有一张自己的名片。首次发布生成随机公开 ID 和独立的写入凭据，凭据仅保存在 App 私有存储中，服务端只存其摘要。编辑本机名片后会同步到原链接，失败时在下一次编辑或重新启动 App 后重试。可从「写入 NFC」停用链接。清除 App 数据或卸载会丢失管理凭据，服务端仍保留已经发布的名片。

## 启动服务

使用 Python 3.9+ 和可持久化目录。数据库会自动创建。

```sh
mkdir -p card-data
python3 server/cards.py --db card-data/cards.sqlite3
```

将实际名片域名的 HTTPS 流量转发至 `127.0.0.1:8080`。服务提供 `/c/<cardId>`、`/api/cards/<cardId>` 和 `/card.css`。代理应转发 Authorization 请求头，并对公开写接口应用请求速率和存储配额限制。服务也可用 `--cert` 和 `--key` 启动本地 TLS 测试，客户端需要信任对应证书。

## 构建 Android 调试包

```sh
sh gradlew :app:assembleDebug -PcardOrigin=https://cards.example.com
```

将示例域名替换为已启动的 HTTPS 服务域名。也可以通过 `PENGWA_CARD_ORIGIN` 传入同一配置。App 校验 NFC 链接的 HTTPS 来源及路径，构建时请保持域名稳定。标签上已有旧数据时，写入会覆盖原内容。

## 验证

```sh
python3 -m unittest discover -s server
sh gradlew :app:testDebugUnitTest :core:model:test :core:protocol:test
```

实体标签验收用支持 NDEF 的可写标签。发布并写入测试名片，移开手机后分别通过 App 和手机系统读取。修改显示名并检查原链接，随后停用并确认原标签打开的是失效页。再分别检查只读标签、容量不足、断网和取消操作。
