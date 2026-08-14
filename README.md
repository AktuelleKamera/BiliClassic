# BiliClassic for Windows Mobile
## 这是一个非常早期的测试版！

一个面向 Windows Mobile 6.5 设备的 Bilibili 客户端，让被遗忘在抽屉里的 Windows Mobile 设备重新获得观看 Bilibili 视频的能力。


---

## 免责声明

本应用为第三方开源项目，与哔哩哔哩（上海宽娱数码科技有限公司）无关。

- 本应用仅用于学习和研究目的，所有视频内容版权归上海宽娱数码科技有限公司及其权利人所有
- 本应用仅提供视频播放功能，所有视频内容来自 bilibili.com 公开接口
- 用户应遵守 bilibili.com 的服务条款，合理使用本应用
- 开发者不对因使用本应用而产生的任何问题承担法律责任
- 本应用仅供个人学习交流使用，请勿用于商业用途

使用本应用即表示您已阅读并同意本免责声明。

---

## 当前版本

**0.1.1 (尝鲜版)** —— 功能不完善版本，仅供尝鲜体验

---

## 已实现功能 (0.1.1)

- 暂时仅支持 HTC HD2 等QSD8250设备播放
- 基于 .NET Compact Framework 开发
- 观看视频
- 搜索视频
- 检查更新
- 扫码登录

---

## 计划中功能

- 历史记录

---

## 技术路线

- 基于 .NET Compact Framework 3.5 / VB.NET 开发
- 使用 Visual Studio 2008 智能设备项目构建
- 通过 Bilibili WBI 签名接口获取视频地址，播放 H.264 视频
- 生成 CAB 安装包部署到 Windows Mobile 设备

---

## 兼容性说明

本项目的推荐设备是 HTC HD2。

### 配置
- 系统: Windows Mobile 6.5+
- 处理器: QSD8250 ARMv7单核
- 物理内存: 256MB以上

---

## 本项目是如何出现的？

开发者买了一台HTC HD2后发现留在Windows Mobile 6.5只能吃灰，那为什么不顺便做个原生客户端呢？于是就有了这个版本。目前这个界面只能说没有设计，主要是测试功能，并且基本只能看视频的说……

---

## 分支说明

| 分支 | 说明 |
|------|------|
| `wm` | Windows Mobile 版开发主线 |

---

## 致谢

- github —— 让我可以传上来。
- Visual Studio 2008 —— 去死吧，Gradle 和 IDE！
- 所有还在折腾 Windows Mobile 的玩家们……
---

## 许可证

本项目使用 GPLv3 许可证开源。

GPLv3 保证您有以下自由：
- 自由使用：您可以自由地运行本软件，用于任何目的
- 自由修改：您可以修改源代码，以适应您的需求
- 自由分发：您可以复制、分发本软件
- 自由改进：您可以将改进后的代码贡献回社区

详细的许可证文本请参阅项目根目录下的 LICENSE 文件。

---

## 下载与反馈

- GitHub: https://github.com/AktuelleKamera/BiliClassic/tree/wm (Windows Mobile 分支)
- 反馈 Issue: https://github.com/AktuelleKamera/BiliClassic/issues