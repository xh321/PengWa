# 发布与签名

## 构建签名版 APK

```
gradlew assembleRelease
```

产物：`app/build/outputs/apk/release/app-release.apk`。

签名口令从 `secrets/keystore.properties` 读取（模板见根目录 `keystore.properties.example`）。
`secrets/` 整个目录已被 git 忽略；没有这个文件时，发布版保持未签名（`app-release-unsigned.apk`），
其他人 clone 下来仍能构建调试版。也可以用 `-PkeystoreProperties=<文件路径>` 指定别的配置文件。

校验签名：

```
apksigner verify --verbose --print-certs app-release.apk
```

## 密钥信息

- 密钥库：`secrets/pengwa-release.jks`，别名 `pengwa`（RSA，有效期约 27 年）。
- **密钥库和口令只在本机，丢了就无法再更新已发布的应用**。请离线备份至少两份，口令存进密码管理器。
- 发布版包名 `app.pengwa`；调试版包名 `app.pengwa.debug`，两者可以共存。
- 目前只使用 APK 签名方案 v2（`minSdk 29`，不需要 v1）。

## 证书指纹（SHA-256，公开信息）

| 用途 | 指纹 |
|---|---|
| 发布版（`app.pengwa`） | `6C:60:59:20:B3:84:80:BB:84:41:E7:23:20:59:CE:CF:A8:4D:B4:51:D5:D4:8D:27:0E:B9:D9:1A:D7:B5:6F:24` |
| 调试版（`app.pengwa.debug`，**仅本机**） | `76:9D:70:5D:C9:1B:9E:89:DF:FE:69:88:D6:F6:32:A0:78:5D:30:66:F5:B6:E2:64:62:57:58:7E:E5:7D:9E:3F` |

调试版指纹来自本机的 `~/.android/debug.keystore`，换一台开发机就会不同。
如果以后改用 Google Play 的"应用签名托管"，发布版指纹要改成 Play 后台"应用完整性"里显示的那一个。

## App Links 的 `assetlinks.json` 模板（C 阶段用）

放在网站的 `https://<你的域名>/.well-known/assetlinks.json`。要求：公开可访问、`https`、
不能有重定向、`Content-Type: application/json`。

```json
[
  {
    "relation": ["delegate_permission/common.handle_all_urls"],
    "target": {
      "namespace": "android_app",
      "package_name": "app.pengwa",
      "sha256_cert_fingerprints": [
        "6C:60:59:20:B3:84:80:BB:84:41:E7:23:20:59:CE:CF:A8:4D:B4:51:D5:D4:8D:27:0E:B9:D9:1A:D7:B5:6F:24"
      ]
    }
  },
  {
    "relation": ["delegate_permission/common.handle_all_urls"],
    "target": {
      "namespace": "android_app",
      "package_name": "app.pengwa.debug",
      "sha256_cert_fingerprints": [
        "76:9D:70:5D:C9:1B:9E:89:DF:FE:69:88:D6:F6:32:A0:78:5D:30:66:F5:B6:E2:64:62:57:58:7E:E5:7D:9E:3F"
      ]
    }
  }
]
```

上线正式版前，建议删掉调试版那一段，避免别人用调试密钥冒充。

## 应用内自更新（GitHub APK 分发）

入口：主页右上角设置 → 应用更新 → 检查更新。用户查看版本与下载大小后下载，
应用校验通过再打开系统安装器；第一次需允许“安装未知应用”，授权回来后再次点“安装更新”。
安装器中的确认或取消由用户决定。覆盖安装保留本地卡片、集邮册和照片。

- 正式版仅检查 `https://github.com/xh321/PengWa/releases/latest/download/update.json`。
- Debug/Nightly 仅检查 `https://github.com/xh321/PengWa/releases/download/nightly/update.json`。
- 不在启动或后台自动联网；只有手动检查、下载时连接更新线路，不发送名片或联系人数据。
- 更新线路首次默认“自动”，优先使用第三方 `gh-proxy.com` 加速；失败时回退 GitHub 直连。可手动选择“国内加速”或“GitHub 直连”，选择会保存。
- 只接受更大的 `versionCode`，并检查设备 Android API 是否满足 `minSdk`。
- 仅通过 HTTPS 从选定线路下载本仓库 Release APK，限制重定向来源和文件大小；下载到应用私有缓存，
  先核对大小与 SHA-256，再检查 APK 包名、版本、最低系统版本及当前签名完全一致。
  系统安装器仍负责最终验证。当前实现保守拒绝证书轮换，轮换需单独设计兼容流程。
- 下载由 Activity 的 ViewModel 管理，关闭设置或旋转不丢失状态；进程结束后需重新检查下载，
  没有后台下载服务。部分下载按 APK 的 SHA-256 隔离保存在私有缓存；网络中断或进程重启后重新检查相同版本，可续传已保存的部分。若服务器不支持续传，则从头下载。网络错误、缺少元数据或签名不符均提供明确提示，可切换线路重试。
- 用户取消系统安装后，已下载文件仍可再次点安装；不要把打开安装器视为安装成功。

### 固定 Nightly 签名（启用覆盖更新的前提）

CI 支持仓库 Actions Secret：`PENGWA_NIGHTLY_KEYSTORE_BASE64`。
其内容是固定的 Android 调试 keystore 的 Base64；须使用标准调试参数：
别名 `androiddebugkey`，store/key 密码均为 `android`。
CI 会在构建前恢复到 `$HOME/.android/debug.keystore`。
**调试密钥与正式发布密钥必须分开，禁止将正式发布 keystore 放入此 Secret。**

可用固定开发机的调试 keystore，或专门生成一份并离线备份：

```bash
keytool -genkeypair -keystore pengwa-nightly.jks -alias androiddebugkey \
  -storepass android -keypass android -keyalg RSA -keysize 2048 \
  -validity 10000 -dname 'CN=PengWa Nightly,O=PengWa,C=CN'
base64 -w0 pengwa-nightly.jks | gh secret set PENGWA_NIGHTLY_KEYSTORE_BASE64 --repo xh321/PengWa
```

以上配置需由维护者执行，本次代码变更没有上传或更改任何签名密钥。
未配置 Secret 时保留原来的临时调试签名构建能力；跨次签名不同将被应用内校验阻止覆盖安装。
旧临时签名版无法无损切换到不同的新签名，不能靠提高版本号修复，**不要让用户直接卸载旧版**。
Nightly CI 的版本号按 `100000 + GITHUB_RUN_NUMBER × 100 + GITHUB_RUN_ATTEMPT` 递增，
版本名加 `-nightly.<run>`；正式版发布时显式传入版本参数，避免受 CI 环境影响。

### 正式版发布更新

1. 使用原有正式密钥构建，`appVersionCode` 必须大于所有已发布正式版本：

   ```bash
   bash ./gradlew :app:assembleRelease -PappVersionCode=3 -PappVersionName=0.1.2
   ```

2. 用实际 APK 与 Gradle 产物信息生成更新清单（标签和文件名应与本次 Release 一致）：

   ```bash
   python3 scripts/create-update-manifest.py \
     --apk app/build/outputs/apk/release/app-release.apk \
     --metadata app/build/outputs/apk/release/output-metadata.json \
     --apk-url https://github.com/xh321/PengWa/releases/download/v0.1.2/pengwa-v0.1.2.apk \
     --notes '本版本的更新说明' \
     --output update.json
   ```

3. 用 `apksigner verify --verbose --print-certs` 确认签名，把该 APK（发布名为
   `pengwa-v0.1.2.apk`）和 `update.json` 一起上传至同一 GitHub Release，并设为 latest。
   不要上传未签名 APK，不要将 Nightly 标为 latest。

Nightly CI 会自动生成并上传同样的清单。先上传新 APK，再替换清单，再清理旧 APK；
恰逢发布窗口读取旧清单遇到已删除文件时会失败，重新检查即可。
首个包含此功能的版本仍需用户手动安装，从下一次同签名的新版本开始才能在应用内覆盖更新。

UI 截图、复现步骤及端到端验证限制见 [自更新验证记录](screenshots/self-update/README.md)。
Android 安装授权接口依据：[PackageManager 官方文档](https://developer.android.com/reference/android/content/pm/PackageManager#canRequestPackageInstalls())。


### 国内下载线路与断点续传

- 默认 `自动`：检查更新及 APK 下载均先尝试 `https://gh-proxy.com/<完整 GitHub Release URL>`，失败后回退 GitHub；手动选直连时不访问加速服务。加速线路只转发本仓库的公开更新文件 URL，不传输本地身份、联系人或登录令牌。
- 加速源返回“不比已安装版本新”时，自动模式会向 GitHub 确认，避免把滞后的缓存当成最新；原站也不可用时给出错误。加速源存在缓存延迟，用户也可直接选择 GitHub 重新检查。
- 每条线路最多尝试两次；自动模式中，加速源首次失败且未取得更多数据时立即回退，不先空等第二次。连接超时为 5 秒、读取停滞超时为 15 秒；这些是每次连接/读取的限制，并非整项操作的总时限（DNS、多地址尝试、重定向可能增加等待）。
- 部分安装包以预期摘要作为缓存键，使用 `Range: bytes=<已下载大小>-` 续传；核验 `Content-Range` 的起点、终点和总大小。返回完整 `200` 响应时清空旧部分再写入，`416` 时重新下载，错误范围或摘要不符时丢弃不可信部分。
- 自动切线、手动切线和重新启动进程可复用相同摘要的部分文件；不同更新的旧部分会清理。最终文件仍须通过完整 SHA-256 及当前应用签名等检查，才会打开系统安装器。
- 界面显示当前线路、已下载大小和速度。失败后保留版本信息，支持直接重试下载；重新检查后如版本未变也可续传。用户清理缓存或系统回收缓存后则需要重新下载。
- 维护者可在构建时用 `-PupdateProxyBaseUrl=https://your-proxy.example/` 替换为自有 GitHub 反向代理。只支持无路径、端口、认证信息、查询参数的 HTTPS origin；需兼容完整 URL 转发及 Range，重定向仅允许配置的代理主机和 GitHub 资产主机。不是任意 CDN/对象存储直链配置。

公开服务可用性、限流和各运营商下的速度会变化。本次未测量中国各运营商线路的实际吞吐量，不保证该加速源在所有网络下更快。后续可替换为维护者自有的反向代理。

依据：[GH-Proxy 服务说明](https://gh-proxy.com/)、[HTTP Range 规范（RFC 9110）](https://www.rfc-editor.org/rfc/rfc9110.html#name-range-requests)。
界面前后截图及线路、续传验证见 [国内下载体验验证](screenshots/update-download/README.md)。
