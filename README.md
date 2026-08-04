<p align="center">
  <img src="docs/assets/icon.svg" width="104" height="104" alt="Codex Pocket icon">
</p>

<h1 align="center">Codex Pocket</h1>

<p align="center">
  在 Android 手机上，安全、流畅地远程使用 Mac 上的 Codex。
</p>

<p align="center">
  <a href="https://github.com/renshaojie233/codex-pocket/actions/workflows/ci.yml"><img src="https://github.com/renshaojie233/codex-pocket/actions/workflows/ci.yml/badge.svg" alt="CI"></a>
  <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white" alt="Android 8.0+">
  <img src="https://img.shields.io/badge/macOS-required-111111?logo=apple" alt="macOS required">
  <img src="https://img.shields.io/badge/network-Tailscale-242424?logo=tailscale" alt="Tailscale">
</p>

Codex Pocket 是一个非官方的个人项目。手机通过 Tailscale 私有网络连接 Mac
上的轻量 Bridge；Bridge 与 ChatGPT 桌面端共享同一个 Codex App Server，
因此任务、消息、运行状态和引导消息可以双向同步。手机不保存 OpenAI 登录凭据。

```mermaid
flowchart LR
    Phone[Android · Codex Pocket] -->|Tailscale| Bridge[Mac · Private Bridge]
    Bridge --> Server[Codex App Server]
    Desktop[ChatGPT Desktop] --> Server
```

## 功能

- 查看、新建、归档 Codex 任务，并自动打开到最新一轮消息。
- 手机缓存最近消息，打开即显示；后台只同步最新一批，缓存超限自动清理。
- 实时同步回复、思考状态、工具进度，以及运行中的引导消息。
- 支持 Markdown、图片、视频、全屏缩放和媒体保存。
- 选择模型、思考强度、Default / Plan、Goal 与 Fast 模式。
- 设置只读、工作区或完全访问权限；初始默认是完全访问。
- 管理自动化任务、查看账户用量，并接收完成通知与震动。

## 快速开始

### 1. 准备环境

- Mac 已安装并登录 [ChatGPT 桌面端](https://openai.com/chatgpt/desktop/)。
- Mac 和 Android 手机登录同一个 Tailscale 账户。
- Mac 已安装 Node.js 20 或更高版本。
- 构建 APK 需要 JDK 17 和 Android SDK 35。

### 2. 启动 Mac Bridge

```bash
git clone https://github.com/renshaojie233/codex-pocket.git
cd codex-pocket
npm --prefix bridge install
./bridge/scripts/install-launch-agent.sh
```

脚本会自动适配当前用户名、Node 路径和仓库位置，并让 Bridge 在 macOS 登录后
自动启动。首次启动会生成：

```text
bridge/data/config.json
```

其中包含 Mac 的 Tailscale 地址、端口和随机配对令牌。该文件已被 Git 忽略，
不要发送给其他人。

### 3. 构建并安装 APK

```bash
./scripts/build-android.sh
```

APK 会生成到：

```text
outputs/codex-pocket-0.13.0.apk
```

也可以在手机浏览器打开下面的私有下载页：

```text
http://<Mac 的 Tailscale IPv4>:8787/download
```

安装后，在首次连接页面填写：

```text
地址：ws://<Mac 的 Tailscale IPv4>:8787/ws
令牌：bridge/data/config.json 中的 token
```

以后只要 Mac 已登录系统、Tailscale 在线，就可以直接从手机连接。

## 常用命令

```bash
# 查看 Bridge 状态
curl http://<Mac 的 Tailscale IPv4>:8787/health

# 运行 Bridge 测试
npm --prefix bridge test

# 停止开机自启（配置会保留为 .disabled 备份）
./bridge/scripts/uninstall-launch-agent.sh
```

## 安全说明

- Bridge 默认只监听 Mac 的 Tailscale IPv4，并要求随机 Bearer Token。
- `ws://` 流量由 Tailscale 的 WireGuard 隧道加密；不要把端口转发到公网。
- `bridge/data/`、`android/local.properties`、`outputs/` 和本机专属图标均不会提交。
- 手机消息缓存位于应用私有缓存目录，最多 20 个任务或 12 MB；清理缓存不会影响 Mac 历史记录。
- “完全访问”会移除 Codex 本地沙箱限制，只应在可信任务中使用。

## 项目结构

```text
android/   Kotlin + Jetpack Compose 客户端
bridge/    Node.js WebSocket Bridge
scripts/   APK 构建脚本
```

> Codex、ChatGPT 及相关商标归 OpenAI 所有。本项目与 OpenAI 无隶属或背书关系。
