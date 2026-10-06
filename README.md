# BiliClassic - 安卓1也要看B站！

<img width="1366" height="768" alt="Android 1" src="https://github.com/user-attachments/assets/884b9db5-3411-491e-98cb-5c5eabc17724" />

一个面向各种旧设备的第三方哔哩哔哩客户端项目，支持Android 1.0+、Windows Mobile 5.0+、Windows Phone 7.0+、Mymobile 0.9.3+和webOS 3.0+。致力于还原2013年前后的经典界面与交互体验，让那些在抽屉里吃灰的老设备能重新看上B站的说~

## 免责声明

本应用为第三方开源项目，与哔哩哔哩（上海宽娱数码科技有限公司）无关。

- 本应用仅用于学习和研究目的，所有视频内容版权归上海宽娱数码科技有限公司及其权利人所有
- 本应用仅提供视频播放功能，所有视频内容来自 bilibili.com 公开接口
- 用户应遵守 bilibili.com 的服务条款，合理使用本应用
- 开发者不对因使用本应用而产生的任何问题承担法律责任
- 本应用仅供个人学习交流使用，请勿用于商业用途

使用本应用即表示您已阅读并同意本免责声明。

## 当前版本

### 0.5.0 (Tiger E) 更新

一次巨大的更新……顺便消灭了legacy版233

## Android 版已实现功能

- 轻量适配 Android 1.0+ 设备
- 完美适配 Android 1.6+ 设备（带IJK V3播放器）
- 扫码登录 / Cookie登录 / 网页登录 / 验证码登录
- 视频搜索（支持AV/BV号快捷跳转）
- 播放历史记录
- 收藏夹管理
- 查看评论区
- 生放送！
- 动态功能
- 离线缓存
- 拉黑用户
- 关注用户
- 回复私信
- 分P下载
- 夜间模式支持
- 番剧播放
- 不明的WebView
- Metro/Classic主题切换
- 更换你喜欢的不明背景图片
- 自选清朝格式转码播放
- 8K30/4K120 HDR DASH音视频播放（仅支持IJK播放器）
- MediaPlayer内核的Ostwind播放器和完整IJK V3播放器
- 视频播放（内置播放器 / MX Player / VLC / MoboPlayer / QQ影音等）
- 弹幕引擎切换（完整版DanmakuFlameMaster / BT-5简易版）
- 弹幕渲染方式自动跟随视频渲染方式（View / SurfaceView / TextureView）
- 发布评论和弹幕
- 内置解码方式选择（系统硬解 / IJK软解 / IJK硬解）
- 检查更新（多分支版本管理）
- 设备信息检测（含各种老设备彩蛋）
- 崩溃日志收集
- 彩蛋！

## 技术路线

- 最低支持API 1 (Android 1.0)，目标API 29
- Java层代码使用Java 7实现，NDK r10e编译
- 二维码生成使用SwetakeQRCode魔改
- 参考bilibili-api-collect实现数据获取
- 独家魔改support-v4库，支持安卓1.0
- WBI 签名算法已迫真适配

## 弹幕引擎

本项目内置两套弹幕引擎，可在设置中自由切换：

| 特性 | 完整版 (DanmakuFlameMaster) | 简易版 (BitmapText-5) |
|------|---------------------------|------------|
| 渲染方式 | `SurfaceView` / `TextureView` / `View`，`Canvas.drawText` 实时绘制 | `SurfaceView` / `TextureView` / `View`，`Canvas.drawBitmap` 预渲染 |
| 弹幕样式 | 描边 / 阴影 / 投影 | 纯色填充 |
| 碰撞检测 | 有（O(n²) 行布局） | 无（8行轮转） |
| 渲染帧率 | 25-60fps | 20fps |
| Bitmap缓存 | ~16MB LRU池 (ARGB_8888) | 2MB去重池 (ARGB_4444) |
| Native依赖 | 有（ndkbitmap.so） | 无 |
| 推荐设备 | armeabi-v7a及以上 | armeabi |

弹幕宿主与视频渲染方式绑定（选好视频渲染方式后自动跟随，避免跨层组合黑屏）：

| 视频渲染方式 | 可选弹幕宿主（默认加粗） | 说明 |
|------|------|------|
| TextureView | **View** / TextureView | Surface弹幕会把TextureView视频一起干死，故不提供 |
| SurfaceView | **SurfaceView** / View | 硬件overlay，Android 2等清朝设备最推荐；Texture组合未测试，暂不提供 |

BT-5弹幕引擎为本项目原创，为armeabi设备设计。通过预渲染Bitmap、同文案去重、低帧率轮询和后台线程，尝试将弹幕渲染对视频解码的干扰降到最低。然而我还是推荐你用DanmakuFlameMaster（

### (BT-5-IS快速坦克)
<img width="847" height="514" alt="07n9mbv7f7541" src="https://github.com/user-attachments/assets/d820526d-bdb6-4a41-84b4-c5fe05e3b5c3" />

## 播放器

本项目内置两套播放器，可在设置中自由切换：

| 特性 | Ostwind（东风） | IJK（内置播放器） |
|------|-------------------|-------------------|
| 播放内核 | 系统MediaPlayer / ffmpeg | IjkMediaPlayer（ffmpeg/IJK硬解） |
| 最低系统 | Android 1.0（API 1） | Android 1.6（API 4+） |
| 解码方式 | 软解 / 硬解可切换 | 软解 / 硬解可切换 |
| 弹幕 | DanmakuManager（完整 / 简易双引擎） | DanmakuManager（完整 / 简易双引擎） |
| 播放进度上报 | ✓ | ✓ |
| 渲染 | SurfaceView + SURFACE_TYPE_PUSH_BUFFERS | TextureView / SurfaceView |
| 控制栏 | 风味控制栏（比例循环 / 横滑快进 / 双击暂停） | 完整控制器（手势缩放 / 清晰度 / 弹幕发送） |
| 包体积 | 仅含ffmpeg相关so | 含 ijkffmpeg/ijkplayer.so |
| 推荐设备 | Android 2.3以下 | Android 1.6+ |

**Ostwind 播放器**是本项目为Android 1.6及以下的设备专门设计的在线播放器。基于系统MediaPlayer和MoboPlayer，通过本地HTTP代理携带B站防盗链请求头；支持清朝B站风味控制栏、横滑快进、双击暂停、画面比例循环、弹幕与播放历史上报等功能。

**IJK 播放器**基于ffmpeg/ffplay，支持软解与硬解切换，在Android 1.6+设备上提供更完整的播放体验，包括手势缩放、清晰度切换、弹幕发送等完整控制器功能。当然，本项目也对 IJK 播放器进行了魔改，否则是不会能在Android 1.6+运行的（

### (Ostwind 自行防空炮)
<img width="610" height="458" alt="xrpaiYN" src="https://github.com/user-attachments/assets/02f6c6f1-0919-46a5-a66e-948d2c397501" />

## 兼容性说明

本项目的目标设备是2011年前后的移动设备，包括但不限于：

### 配置要求

#### webOS 版推荐配置
- 系统: webOS 3.0 及以上
- 处理器: ARM-v7A
- 设备: HP TouchPad

#### Windows Mobile 版推荐配置
- 系统: Windows Mobile 6.1 及以上
- 处理器: ARM-v7A
- 物理内存: 256 MB

#### Mymobile 版推荐配置
- 系统: Mymobile 0.9.3.7 及以上
- 处理器: ARM11 667MHz（三星S3C6410）
- 物理内存: 256 MB

（其实Mymobile好像也就魅族M8在用的说……）

#### Android 版最低配置
- 系统: Android 1.0 及以上
- 处理器: ARMv5TE
- 物理内存: 64 MB

以及其他搭载 ARMv5TE / ARMv6 / ARMv7-A / ARMv8-A / x86 / MIPS 处理器、运行 Android 1.5+ 系统的设备。

#### Android 版推荐配置
- 系统: Android 2.2 (Froyo) 及以上
- 处理器: ARMv7-A 及以上
- 物理内存: 512 MB

### 各平台最低推荐处理器 (建议 ARMv7-A 及以上)

手机平台
- 高通 MSM7227A — 1GHz Cortex-A5单核
- 高通骁龙 S1 (QSD8250/QSD8650) — 1GHz Scorpion单核
- 三星蜂鸟 (S5PC110) — 1GHz Cortex-A8单核
- 德州仪器 OMAP 3430 — 600MHz Cortex-A8单核
- 英伟达 Tegra 2 (T20/T25) — 1GHz Cortex-A9双核
- 联发科 MT6575 — 1GHz Cortex-A9单核
- 英特尔 Atom Z2460 — 1.6GHz Saltwell单核 (x86)
- 意法爱立信 U8500 — 1GHz Cortex-A9双核
- 海思 K3V2 — 1.2GHz Cortex-A9四核
- 展讯 SC6820 — 1GHz Cortex-A5单核

平板/盒子/其他平台
- 瑞芯微 RK2816 — 600Mhz ARM11单核
- 晶晨 AML8726-M — 800MHz Cortex-A9双核
- MIPS 1074Kc — 1GHz MIPS32 双核
- 飞思卡尔 i.MX51 — 800MHz Cortex-A8单核
- 全志 A10 — 1GHz Cortex-A8单核

（瑞芯微RK28系列较为特殊，是自带了解码单元的armeabi，比其他ARM11手机流畅）
只要你的设备不是古老的纯armeabi架构，我想……呃，大约都能跑的比较流畅吧hhh
如果你是自带硬解单元的ARM11甚至ARM9，那就真可能看了（

## 本项目是如何出现的？

开发者本人是个怀旧狂，非常喜欢大约2014年左右的B站旧界面，于是他在使用Android 2.3 / Android 4.0手机的时候，就真的很想让这些手机看上B站，然而当时能找到的最低适配的B站，也就是隔壁的哔哩终端（hhh）只能支持安卓4.0.4+。就这样，在等了许久后，连隔壁iOS 5都看上了（！），安卓2还是没有任何能在线看B站的方法，开发者就终于决定自己动手了！现在嘛，这个梦想终于大约已经实现了23333

## 分支说明

| 分支 | 说明 |
|------|------|
| `android` | 0.5.x 开发主线（完整版） |
| `0.3.x` | 0.3.x 旧版分支（无播放器） |
| `0.4.x` | 0.4.x 旧版分支（无Ostwind） |
| `mymobile` | Mymobile 版本 |
| `webos` | webOS 3.0 版本 |
| `wm` | Windows Mobile 版本 |
| `wp` | Windows Phone 版本 |

0.3.x/0.4.x分支将仅进行维护性更新，未来的新功能都将在其他分支开发。

## 致谢

本项目的网络请求、WBI签名等模块参考并引用了以下开源项目，在此表示感谢：

- 哔哩终端 (BiliClient)
- BiliBili TV 1.6.6-repair
- WearBili
- WristBili
- DanmakuFlameMaster
- IJKPlayer
- WolfSSL

感谢所有愿意在2026年还在折腾老设备的群友们，你们的反馈让这个项目越来越好的说~

## 许可证

本项目使用GPLv3许可证开源。

GPLv3 保证您有以下自由：
- 自由使用：您可以自由地运行本软件，用于任何目的
- 自由修改：您可以修改源代码，以适应您的需求
- 自由分发：您可以复制、分发本软件
- 自由改进：您可以将改进后的代码贡献回社区

详细的许可证文本请参阅项目根目录下的LICENSE文件。

## 下载与反馈

- 官网: http://www.biliclassic.cn
- GitHub: https://github.com/AktuelleKamera/BiliClassic
- 反馈 Issue: https://github.com/AktuelleKamera/BiliClassic/issues
