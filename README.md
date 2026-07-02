# BiliClassic - 安卓2也要看B站！

<img width="1366" height="767" alt="Android 2" src="https://github.com/user-attachments/assets/52277284-e776-496c-886d-12351b15cc00" />

一个面向 Android 1.6+ 设备的 Bilibili 客户端，致力于还原 2013 年前后的经典界面与交互体验。
让那些被遗忘在抽屉里的老设备重新获得观看 Bilibili 视频的能力。

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

**0.3.4 (稳定版)** —— 无播放器，纯净浏览体验

**0.4.0-beta2 (尝鲜版)** —— 内置播放器，功能更完整（开发中）

---

## 已实现功能

- 轻量适配 Android 1.6+ 设备
- 完美适配 Android 2.3+ 设备（带内置播放器）
- 扫码登录 / Cookie 登录 / 手动输入 Cookie
- 视频搜索（支持 AV / BV 号快捷跳转）
- 播放历史记录
- 收藏夹管理
- 离线缓存
- 分P下载（带封面缓存）
- 视频播放（内置播放器 / MX Player / VLC / MoboPlayer / QQ影音等）
- 弹幕开关
- 发布评论和弹幕
- 内置解码方式选择（系统硬解 / IJK 软解 / IJK 硬解）
- 检查更新（多分支版本管理）
- 设备信息检测（含各种老设备彩蛋）
- 崩溃日志收集
- 彩蛋！

---

## 技术路线

- 最低支持 API 4 (Android 1.6)，目标 API 17
- 纯 Java 6 实现，兼容古早 Dalvik 虚拟机
- 参考 Bilibili 官方 API 文档实现数据获取
- WBI 签名算法已适配，登录、下载、播放一条龙
- 二维码生成使用 SwetakeQRCode 魔改（纯 Java 实现，兼容 Android 1.6）
- 二维码绘制支持硬件加速检测（Canvas / EGL 迫真方法 / setPixels 兼容）

---

## 兼容性说明

本项目的目标设备是 2011 年前后的 Android 机型，包括但不限于：

### 配置要求

#### 最低配置
- 系统: Android 1.6 (Donut) 及以上
- 处理器: ARMv5TE
- 内存: 128MB
以及其他搭载 ARMv5TE / ARMv6 / ARMv7-A / ARMv8-A / x86 / MIPS 处理器、运行 Android 1.6+ 系统的设备。

#### 推荐配置
- 系统: Android 2.3 (Gingerbread) 及以上
- 处理器: ARMv7-A 及以上
- 内存: 512MB

### 各平台最低推荐处理器 (ARMv7-A 及以上)

手机平台
- 高通 MSM7227A — 1GHz Cortex-A5 单核
- 高通骁龙 S1 (QSD8250/QSD8650) — 1GHz Scorpion 单核
- 三星蜂鸟 (S5PC100) — 667MHz/800MHz Cortex-A8 单核
- 德州仪器 OMAP 3430 — 600MHz Cortex-A8 单核
- 英伟达 Tegra 2 (T20/T25) — 1GHz Cortex-A9 双核
- 联发科 MT6575 — 1GHz Cortex-A9 单核
- 英特尔 Atom Z2460 — 1.6GHz Saltwell 单核 (x86)
- 意法爱立信 U8500 — 1GHz Cortex-A9 双核
- 海思 K3V2 — 1.2GHz Cortex-A9 四核
- 展讯 SC6820 — 1GHz Cortex-A5 单核

平板/盒子/其他平台
- 瑞芯微 RK2918 — 1GHz Cortex-A8 单核
- 晶晨 AML8726-M — 800MHz Cortex-A9 双核
- MIPS 1074Kc — 1GHz MIPS32 双核
- 飞思卡尔 i.MX51 — 800MHz Cortex-A8 单核
- 全志 A10 — 1GHz Cortex-A8 单核

只要你的设备不是古老的纯armeabi架构，我想……呃，大约都能跑的比较流畅吧hhh

---

## 本项目是如何出现的？

开发者本人是个怀旧狂，非常喜欢大约2014年左右的B站旧界面，于是他在使用 Android 2.3 / Android 4.0 手机的时候，就真的很想让这些手机看上B站，然而当时能找到的最低适配的B站，也就是隔壁的哔哩终端（hhh）只能支持安卓4.0.4+。就这样，在等了许久后，连隔壁iOS 5都看上了（！），安卓2还是没有任何能在线看B站的方法，开发者就终于决定自己动手了！现在嘛，这个梦想终于大约已经实现了23333

---

## 分支说明

| 分支 | 说明 |
|------|------|
| `master` | 0.4.x 开发主线（含内置播放器） |
| `0.3.x` | 0.3.x 稳定分支（无播放器） |

0.3.x 分支将仅进行维护性更新，未来的新功能将在 master 分支开发。

---

## 致谢

本项目的网络请求、WBI 签名等模块参考并引用了以下开源项目，在此表示感谢：

- 哔哩终端 (BiliClient)
- BiliBili TV 1.6.6-repair
- WearBili
- WristBili
- DanmakuFlameMaster
- IJKPlayer

感谢所有愿意在 2026 年还在折腾老设备的群友们，你们的反馈让这个项目越来越好的说~

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

- 官网: http://www.biliclassic.cn
- GitHub: https://github.com/AktuelleKamera/BiliClassic
- 反馈 Issue: https://github.com/AktuelleKamera/BiliClassic/issues
