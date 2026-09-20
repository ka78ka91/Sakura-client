# Sakura Client

一个面向 **Minecraft 1.21.11 (Fabric)** 的客户端增强模组：战斗辅助、视觉增强、信息 HUD 与玻璃拟态界面。

代码大量参考并移植自 [LiquidBounce](https://github.com/CCBlueX/LiquidBounce)（GPL-3.0），并按"**能被服务器接受、能被玩家理解**"的原则做了取舍：宁可少一个模式，也不做一个名不副实的功能。每个模式都自带风险标注（`safe` / `risky` / `outdated`），运行时还有全局"被纠正即暂停"的安全阀。

> ⚠️ **免责声明**：本项目仅供单机、自建服务器或已获许可的环境使用。在禁止第三方客户端的公开服务器上使用违反其规则与 Mojang 服务条款，可能导致封禁。请自行承担使用风险。

---

## 📦 环境要求

| 项目 | 版本 |
| --- | --- |
| Minecraft | 1.21.11 |
| Fabric Loader | ≥ 0.19.5 |
| Fabric API | 0.141.6+1.21.11 |
| Java | **21**（运行与编译都需要） |
| 构建工具 | Gradle 9.7.1（仓库已带 wrapper） |

**安装**：把 `build/libs/sakura-client-1.0.0.jar` 放进 `.minecraft/mods/`，并确保已安装 Fabric Loader 与 Fabric API。

---

## 🆕 最新更新

### 1. 真实音乐对接（Windows 系统媒体会话）

音乐 HUD 不再是假数据 —— 它现在读取 **Windows SMTC（系统媒体传输控制）**，也就是音量面板/媒体键背后的那套接口。

* **任何注册了媒体会话的播放器都能识别**：Spotify、网易云音乐、QQ 音乐、foobar2000、AIMP、MusicBee、VLC、PotPlayer、Chrome / Edge / Firefox 网页播放……
* **显示真实信息**：标题、歌手、专辑、播放状态、当前进度 / 总时长，以及**真实专辑封面**。
* **封面取色**：从封面中提取一个鲜艳主色，自动作为面板、进度条、控件和发光的高亮色（可在设置里关闭）。
* **进度条实时插值**：桥接每 0.5 秒轮询一次，进度条在两次轮询之间自行外推，所以是平滑滑行而不是每半秒跳一格。
* **悬停控件**：鼠标移上去出现 ⏮ ⏯ ⏭，点击直接控制播放器（播放/暂停、上一首、下一首）。
* **优雅降级**：非 Windows 或桥接启动失败时自动使用 Demo 占位音轨；当前没有播放内容时显示"Nothing playing"提示，而不是空白。

**实现架构**

```
MusicPlayerElement  (HUD 绘制 / 动画 / 交互)
   ├── MusicProvider          统一数据接口（可插拔）
   ├── SmtcMusicProvider      Windows 实现：进程桥 + JSON 解析
   │      └── smtc-helper.ps1  (随 jar 发布，自动释放到 config/sakura/)
   ├── MockMusicProvider      降级用占位数据
   └── AlbumArt               封面解码 / 缩放 / 取色 / 纹理上传
```

* 桥接脚本用 **Windows PowerShell 5.1**（系统自带）运行，因为只有它能直接投影 WinRT API；PowerShell 7 不行，这一点在代码里已按 `powershell.exe` 绝对路径处理。
* 桥接进程隐藏启动、随游戏退出而结束，且**在后台线程创建**，启动等待不会卡住游戏。
* 数据流是单向管道（脚本 stdout 每 0.5 秒一行 JSON），控制指令通过命令文件回传，避免双向管道的复杂性。
* 封面在后台线程解码、盒式平均缩放到 256×256，再由渲染线程上传；纹理复用同一个 ID，旧图由原版 API 负责释放，不会泄漏。

**故障排查**

| 现象 | 原因 / 处理 |
| --- | --- |
| 一直显示 "Nothing playing" | 播放器没有注册媒体会话（老版本播放器常见），或确实没在播放 |
| 显示 "Media session unavailable" | 桥接进程未能启动：查看日志 `sakura/music`；脚本位于 `config/sakura/smtc-helper.ps1`，可手动运行排查 |
| 有歌名但没有封面 | 该会话未提供缩略图（例如某些网页播放器） |
| 想手动改桥接 | 直接编辑 `config/sakura/smtc-helper.ps1`，下次启动会覆盖为 jar 内版本 |

### 2. 视觉全面升级

**新增渲染基础设施**

* `render/Animations.java` —— 缓动曲线（cubic / quint / back / elastic / smoothstep）、**帧率无关**的指数平滑 `approach(current, target, speed, dt)`，以及墙钟帧计时器 `Clock`。所有动画在 60Hz 和 240Hz 下手感一致，掉帧时也不会瞬移到终点。
* `RenderUtils` 新增视觉原语：
  * `drawGradientRect` / `drawRoundedGradient` —— 渐变与圆角渐变（按圆方程分带，角落误差 < 1px）
  * `drawSoftShadow` / `drawGlow` —— 无模糊 API 下的多层同心软阴影与彩色发光
  * `drawGlassPanel` —— **统一玻璃材质**：阴影 + 渐变glass + 顶部高光 + 内描边 + 外描边，全客户端一个质感
  * `drawAccentWash` —— 顶部的强调色晕染（会向下淡出，不像描边）
  * `drawProgressBar` / `drawRoundedTexture` / `drawMarqueeText` —— 圆角进度条、**圆角图片**（逐行裁剪跟随圆弧）、跑马灯文字
  * 颜色工具：`mix` / `withAlpha` / `multiplyAlpha` / `brighten` / `darken` / `scaleRgb`

**音乐 HUD 重做**

玻璃面板（含呼吸式封面发光）、圆角封面、封面取色、均衡器跳动徽章（播放中）/ 暂停徽章、进度条发光拖动头、时间显示、"标题过长自动来回滚动"、悬停淡入的三键控件、切换歌曲时强调色平滑过渡、整体淡入。

**HUD 元素视觉升级**

`TargetHUD`（血条动画 + 受伤闪烁 + 出现/消失淡入淡出）、`Keystrokes`（按键按压动画）、`FPS` / `Ping·TPS` / `Coordinates`（数值平滑不抖动）、`ArmorHUD`（耐久条动画 + 绿→红渐变）、`ModuleList`（模块开关时淡入滑出）、`PotionHUD`（剩余时间条），全部换成统一玻璃面板。

---

## 🎯 功能一览

### 战斗（Combat）

| 模块 | 说明 | 风险 |
| --- | --- | --- |
| **Reach** | 加长实体/方块交互距离（在原版数值上叠加，不替换） | risky |
| **Criticals** | 强制暴击：`Jump`（真跳斩，安全）/ `Packet`（7 种 Y 轴微调子模式）/ `NoGround` / `Blink` / `Timer` / `None` | 分级标注 |
| **Velocity** | 防击退：`JumpReset`（起跳吃掉击退，不伪造任何包） | safe |
| **TriggerBot** | 准星指向敌人自动攻击，支持**静音旋转**（服务端收到瞄准角度，相机不动） | risky |
| **AutoClicker** | 自动连点，CPS 区间随机；走原版攻击入口，仍受攻击冷却与距离限制 | risky |
| **AutoWeapon** | 自动切最合适武器（盾牌→斧、锤击重击、其余按每秒伤害评分） | safe |
| **AutoTotem** | 低血自动把图腾换到副手（`Switch` / `PickUp` 两种点击方式） | safe |
| **KeepSprint** | 命中后保留水平速度（可设百分比与概率） | risky |
| **Hitbox** | 扩大其他实体判定框（默认 +0.1） | risky |
| **NoMissCooldown** | 空挥不重置攻击冷却 | risky |

### 视觉（Visuals）

**ESP**（实体框 / 四角括号两种样式，支持填充与按类别配色）、**Tracers**（射线，起点可选屏幕中心/底部）、**NameTags**（头顶名字 + 血量 + 距离 + 背景板）、**Fullbright**（亮度拉满，恢复原值）。三者共享目标筛选（玩家/怪物/动物/其他、距离、忽略隐身）与配色设置。

### HUD / 界面

* **ClickGUI**：900×550 玻璃窗口、侧边栏分类、模块搜索、参数面板（滑块/开关/下拉/多选/概率/颜色选择/按键绑定）、Config 页
* **HUD 编辑器**：拖拽定位、实时预览
* **通知系统**：模块开关提示
* **HUD 元素**：模块列表、按键精灵（含 CPS）、坐标、FPS、Ping/TPS、TargetHUD、装备耐久、药水效果、**音乐播放器**

### 安全 / 反检测

`FlagDetector` —— 当服务器下发位置纠正包（说明客户端的位置/速度声明被拒绝）时，所有操纵移动、速度、旋转的模块会自动静默一段时间；可被各模块的 `Pause On Flag` 开关引用。

### 配置

所有状态保存在 `config/sakura.json`：模块开关、按键绑定、每个模块的参数、HUD 元素位置、界面主题色与缩放。文件损坏时自动回退默认值，不会导致游戏无法启动。

---

## 🔨 从源码构建

```bash
# 1) 需要 JDK 21
java -version        # 应为 21.x

# 2) 构建（wrapper 已配置国内镜像，首次会下载 Gradle 与 Minecraft 依赖）
./gradlew build      # Windows: .\gradlew.bat build

# 产物
build/libs/sakura-client-1.0.0.jar          # 成品模组（已 remap）
build/libs/sakura-client-1.0.0-sources.jar  # 源码包
```

**国内网络提示**：仓库的 `gradle/wrapper/gradle-wrapper.properties` 指向腾讯云 Gradle 镜像；Loom 依赖走 `maven.fabricmc.net`、`repo1.maven.org` 与 Mojang 官方分发仓库。若直连 GitHub 受限，可用任意 GitHub 反代拉取本仓库源码后本地构建。

**开发环境要求**：`fabric-loom` 1.17.21 需要 **Gradle 9.5+**（其插件 API 版本为 9.5.0，Gradle 8.x 会报 variant 不兼容），因此请不要用旧版 Gradle 构建。

---

## 📁 目录结构

```
src/main/java/com/sakura/client/
├── SakuraClient.java        入口：注册模块、HUD 元素、按键、配置
├── config/                  sakura.json 读写
├── gui/                     ClickGUI、HUD 编辑器、参数面板、控件
├── hud/                     音乐接口与实现、HUD 管理、TPS 追踪
│   └── element/             9 个 HUD 元素
├── mixin/                   13 个混入（距离、暴击、击退、旋转、位置纠正…）
├── module/                  模块框架与 15 个功能模块
├── notification/            提示气泡
├── render/                  绘制工具、动画、世界投影
├── rotation/                旋转管理（含静音旋转）
├── safety/                  安全阀与连点计数
└── setting/                 参数类型体系

src/main/resources/assets/sakura/
├── smtc-helper.ps1          Windows 媒体桥（随 jar 发布）
└── lang/en_us.json
```

---

## 🙏 致谢与许可

* 本项目以 **GPL-3.0** 发布，与上游保持一致。
* 大量模块移植/改写自 **[LiquidBounce](https://github.com/CCBlueX/LiquidBounce)**（GPL-3.0），代码注释中逐处标注了来源与差异。
* 作者：**Idontkonw** ｜ 仓库：<https://github.com/ka78ka91/Sakura-client>
