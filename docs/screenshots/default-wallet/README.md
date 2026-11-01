# 默认钱包引导截图与验证

设备：iQOO Z9 Turbo+ / V2417A，Android 16（SDK 36），PD2417_A_16.2.11.2.W10。
截图日期：2026-11-01。测试数据：小棉花，UUID `00000000-0000-0000-0000-000000000001`。

所有图片均来自手机上运行的应用。布局截图使用仓库已有的 Debug `UiPreviewActivity`，注入固定状态，不访问真实联系人或改变系统钱包。

| 图片 | 页面与状态 |
| --- | --- |
| `home-before.png` | 修改前（7381513）的出示页，无钱包引导 |
| `home-after.png` | 修改后的出示页，本应用尚非默认钱包，显示兼容提示和“设为默认钱包” |
| `home-default.png` | 修改后的出示页，本应用已是默认钱包，隐藏提示 |
| `home-reader.png` | 读取页，不显示出示端的钱包提示 |

截图复现：安装 Debug 包，启动 `app.pengwa.ui.UiPreviewActivity`，传入 `screen=home`，`sample=0/1/2` 分别查看提示、已是默认钱包、读取三种状态。该 Activity 只存在于 Debug 构建中。

独立于固定状态截图，还在真实 `MainActivity` 中验证了系统确认后的返回流程：

1. 安装同源码、独立包名的测试版 `app.pengwa.wallettest`，保留原 Nightly 和数据。
2. 测试版未持有钱包角色时，前台 NFC 日志为 `walletDefault=false`、`setPreferredService accepted=true`。
3. 用户通过系统界面将测试版设为默认钱包后，`cmd role get-role-holders --user 0 android.app.role.WALLET` 返回测试版包名。
4. 返回应用，日志变为 `walletDefault=true`、`privateAidDefault=true`，前台出示恢复；界面层级中兼容提示和设置按钮消失。

取消系统请求、在系统设置切回其他钱包后再返回，以及 Android 14 及以下的旧设置入口，尚未在本轮真机操作中覆盖。对应页面状态已包含在预览中；更多厂商仍需验证。本次 UI 验证未重复双机名片交换，也不代表所有厂商的钱包弹窗都能被抑制。
