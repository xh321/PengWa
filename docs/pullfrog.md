# Pullfrog 接入

工作流位于 [`.github/workflows/pullfrog.yml`](../.github/workflows/pullfrog.yml)。
使用官方 Action 的固定版本（v0.1.110），并准备与项目 CI 一致的 Java 21、Android SDK 和 Gradle 环境。

## 启用

1. 由仓库所有者或有安装权限的管理员[安装 Pullfrog GitHub App](https://github.com/apps/pullfrog/installations/new)，授权 `xh321/PengWa`。
2. 在 [Pullfrog 仓库控制台](https://pullfrog.com/console/xh321?repo=PengWa) 完成仓库 onboarding，选择模型并配置对应凭据或订阅。凭据存入 Pullfrog 的加密存储，本工作流不读取本地登录信息，也不需要将 API Key 写入仓库。
3. 将本工作流合入默认分支 `main`。控制台若提示创建工作流，使用本仓库已有文件，保留 Android 环境准备步骤。
4. 在控制台按需启用 PR review、响应 review、CI 修复等自动化。GitHub 事件由 App 接收并派发 `workflow_dispatch`，无需在 YAML 中额外添加 `pull_request` 或 `issue_comment` 触发器。

工作流的 GitHub token 仅有 `contents: read` 和 `id-token: write`；Pullfrog 使用 OIDC 获取短期 GitHub App installation token。`push: restricted` 允许推送功能分支，阻止直接推送默认分支、删除分支或推送 tag。实际 GitHub 权限仍由 App 授权决定。

## 验证

完成上述配置后，可从 Actions → Pullfrog → Run workflow 手动运行，`prompt` 填写：

```text
请读取 AGENTS.md 和项目构建配置，用中文概述项目结构及验证命令。不要修改文件或发布评论。
```

确认日志中环境初始化、模型认证与 agent 执行成功。随后可在测试 Issue / PR 中提及 `@pullfrog`，验证 App 的事件派发；自动 review 则用新建测试 PR 验证。

开发任务应遵守根目录 `AGENTS.md`，验证命令以当前 CI 为准。UI 变更必须附 Android 真机或模拟器运行截图；无法运行或截图时应报告阻碍，不能标记 UI 验证完成。本次接入仅修改工作流与文档，UI 截图不适用。

## 未启用时排查

- Actions 中找不到工作流：确认文件已进入默认分支，且仓库允许 GitHub Actions。
- App 无法派发：确认安装覆盖本仓库，且控制台 onboarding 已完成。
- 模型认证失败：检查控制台中的模型和凭据配置。
- 修改权限或执行异常：检查 App 安装权限和 Pullfrog 控制台设置，勿通过给工作流添加长期 PAT 解决。

参考：[官方入门](https://pullfrog.com/start)、[官方 Action 及使用说明](https://github.com/pullfrog/pullfrog)。
