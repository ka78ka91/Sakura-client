# Sakura Client

一个面向 **Minecraft 1.21.11 (Fabric)** 的客户端增强模组：战斗辅助、视觉增强、信息 HUD 与玻璃拟态界面。

代码大量参考并移植自 [LiquidBounce](https://github.com/CCBlueX/LiquidBounce)（GPL-3.0），并按"**能被服务器接受、能被玩家理解**"的原则做了取舍：宁可少一个模式，也不做一个名不副实的功能。每个模式都自带风险标注（`safe` / `risky` / `outdated`），运行时还有全局"被纠正即暂停"的安全阀与逐模块的行为预算。

**所有模块默认关闭**，HUD 元素同样默认关闭——新装好之后界面是空的，直到你自己打开想看的东西。

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

### 1. 战斗核心：KillAura 与 Aimbot

两者共用同一条流水线，区别只在于**是否替你按键**：

| | KillAura | Aimbot |
| --- | --- | --- |
| 自动攻击 | 是 | **否**，只帮你瞄准 |
| 用途 | 自动战斗 | 辅助瞄准 |

流水线按职责拆成四段，每段可单独调：**目标筛选**（`TargetTracker`，支持按距离 / 生命 / 角度排序，复用视觉模块的实体分类）→ **瞄准点**（`PointTracker`）→ **平滑转向**（`RotationManager`，带反应延迟与微抖）→ **攻击**（`Clicker` 的对数正态间隔 + 原版攻击入口）。

两者都接入既有的三层安全设施：间隔来自对数正态分布、每模块每秒动作预算、收到位置纠正后自动停手。

### 2. 与 TargetHUD 联动

新增 `TargetProvider` 接口与注册表：战斗模块在构造时登记自己，**TargetHUD 优先显示 aura 或 aimbot 正在打的目标**（名字、血量、距离），没有任何模块提供目标时才回落到准星。这样面板显示的是你实际在打的人，而不是你碰巧在看的方块。

### 3. 安全层：事前规避 + 事后熔断

| 层 | 职责 |
| --- | --- |
| `FlagDetector`（已有） | **事后**：服务器下发位置纠正包即静默相关模块 |
| `SafetyManager`（新） | **事前**：每模块每秒动作预算，超了就这一 tick 不做 |
| `HumanizedDistribution`（新） | 点击间隔改为**对数正态分布**，并有 5% 概率插入一次明显更长的迟疑 |

人类化不是"加个随机数"：均匀随机本身就是机器特征（每个值等概率、从不出现长停顿）。实测 1000 次采样的均值、方差与直方图由 `DistributionCheck` 输出，可直接运行验证：

```bash
java -cp build/classes/java/main com.sakura.client.safety.DistributionCheck
```

### 4. 界面：平面材质与性能

玻璃材质从"阴影 + 20 带渐变 + 高光 + 内描边 + 边框"五层压到**一次填充**（宽面板再加一条细线）。满 HUD 一帧的填充次数实测从 **4878 降到 881（-81.9%）**。

`drawRoundedRect` 也重写为左右带算法（原实现每行画三块互相重叠的填充）。参数签名未变，因此所有 HUD 元素自动受益。

### 5. 自定义字体（运行时生成，不入库字体文件）

把任意 `.ttf` / `.otf` 放进 `config/sakura/font/`，重启后客户端会在 `resourcepacks/sakura_font/` 生成一个标准资源包，在**选项 > 资源包**里启用即可。

字体文件本身不随本仓库分发——这是刻意的：MiSans 等字体授权允许免费使用，但明确禁止再分发字体软件本身。生成式方案让换字体只需替换一个文件，同时不违反任何许可。生成的资源包会把原版 unifont 作为回落放在第二位，所以只有拉丁字形的字体不会让中文变成方块。

### 6. 真实音乐对接（Windows 系统媒体会话）

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

### 7. 视觉基础设施与动画

**帧率无关的动画**

* `render/Animations.java` —— 缓动曲线（cubic / quint / back / elastic / smoothstep）、指数平滑 `approach(current, target, speed, dt)`，以及墙钟帧计时器 `Clock`。所有动画在 60Hz 和 240Hz 下手感一致，掉帧时也不会瞬移到终点。
* `RenderUtils` 提供的视觉原语：
  * `drawGlassPanel` —— **统一平面材质**：一次填充加一条细线。早期版本是五层（阴影 + 20 带渐变 + 高光 + 内描边 + 边框），满 HUD 一帧要近 5000 次填充；扁平化后实测 881 次，代价与时序见上文"界面：平面材质与性能"
  * `drawRoundedRect` / `drawBorder` —— 圆角矩形与描边，按左右带算法逐行合并同类角点（像素精确）；`drawGradientRect` / `drawRoundedGradient` 仍保留，供封面等需要渐变的地方使用
  * `drawSoftShadow` / `drawGlow` —— 无模糊 API 下的多层同心软阴影与彩色发光（HUD 面板已不用，仅封面光晕等保留）
  * `drawProgressBar` / `drawRoundedTexture` / `drawMarqueeText` —— 圆角进度条、**圆角图片**（逐行裁剪跟随圆弧）、跑马灯文字
  * 颜色工具：`mix` / `withAlpha` / `multiplyAlpha` / `brighten` / `darken` / `scaleRgb`

**音乐 HUD**

圆角封面、封面取色、均衡器跳动徽章（播放中）/ 暂停徽章、进度条拖动头、时间显示、"标题过长自动来回滚动"、悬停淡入的三键控件、切换歌曲时强调色平滑过渡、整体淡入。

**HUD 元素动画**

`TargetHUD`（血条动画 + 受伤闪烁 + 出现/消失淡入淡出）、`Keystrokes`（按键按压动画）、`FPS` / `Ping·TPS` / `Coordinates`（数值平滑不抖动）、`ArmorHUD`（耐久条动画 + 绿→红渐变）、`ModuleList`（模块开关时淡入滑出）、`PotionHUD`（剩余时间条）。

---

## 🎯 功能一览

共 **37 个可切换模块**：25 个功能模块 + 12 个 HUD 元素。全部默认关闭。

### 战斗（Combat）

| 模块 | 说明 | 风险 |
| --- | --- | --- |
| **KillAura** | 进入范围自动攻击。四段流水线（目标筛选 → 瞄准点 → 平滑转向 → 攻击）各自可调；三种目标优先级；`Fail Rate` 按概率故意打偏 | risky |
| **Aimbot** | **只瞄准不攻击**，由你自己按键。可限水平/垂直轴；`Mouse Blend` 在你动鼠标时让位，不和你抢镜头 | risky |
| **TriggerBot** | 准星指向敌人自动攻击，支持**静音旋转**（服务端收到瞄准角度，相机不动） | risky |
| **AutoClicker** | 自动连点，CPS 区间随机；走原版攻击入口，仍受攻击冷却与距离限制 | risky |
| **Reach** | 加长实体/方块交互距离（在原版数值上叠加，不替换） | risky |
| **Criticals** | 强制暴击：`Jump`（真跳斩，安全）/ `Packet`（7 种 Y 轴微调子模式）/ `NoGround` / `Blink` / `Timer` / `None` | 分级标注 |
| **Velocity** | 防击退：`JumpReset`（起跳吃掉击退，不伪造任何包） | safe |
| **AutoWeapon** | 自动切最合适武器（盾牌→斧、锤击重击、其余按每秒伤害评分） | safe |
| **AutoTotem** | 低血自动把图腾换到副手（`Switch` / `PickUp` 两种点击方式） | safe |
| **KeepSprint** | 命中后保留水平速度（可设百分比与概率） | risky |
| **Hitbox** | 扩大其他实体判定框（默认 +0.1） | risky |
| **NoMissCooldown** | 空挥不重置攻击冷却 | risky |

### 移动（Movement）

| 模块 | 说明 | 风险 |
| --- | --- | --- |
| **Sprint** | 前进时保持疾跑。按原版条件逐条判定（潜行/滑翔/骑乘/饥饿/失明/使用物品时都不强制），不伪造输入 | safe |
| **NoSlow** | 吃东西、举盾、拉弓时保持速度。分三类开关，可只开其中之一 | risky |
| **SafeWalk** | 走向方块边缘时自动把手速降到原版潜行倍率，不会走出去。只在着地且移动时判定 | risky |

> `InventoryMove`（开背包时能移动）**未实现**：它需要在客户端核心 tick 路径上按序号做注入，失败模式是"开背包时移动异常"这类直接影响游玩的行为，本机环境无法验证，因此没有交付。

### 玩家（Player）

| 模块 | 说明 | 风险 |
| --- | --- | --- |
| **AutoRespawn** | 死亡后按设定延迟自动重生（走原版重生请求，延迟避免"永远瞬时"这个特征） | safe |
| **AutoTool** | 挖掘时自动换到最合适的工具，挖完换回。按物品自身对该方块报告的挖掘速度选，平手时保留你的选择 | safe |
| **FastPlace** | 缩短放置方块之间的等待（原版 4 tick → 可设 0） | risky |

### 杂项（Misc）

| 模块 | 说明 | 风险 |
| --- | --- | --- |
| **AntiAFK** | 每隔 20~45 秒随机轻微转一次视角，避免静止挂机被踢。间隔与方向都随机（固定周期比不动更容易被识别） | risky |
| **AutoReconnect** | 被服务器断开后自动重连（走原版多人菜单的连接入口），可设延迟与尝试次数；自己退回标题屏时不重连 | safe |
| **NameProtect** | 隐藏自己名字，仅覆盖**客户端自己绘制**的文字（HUD、头顶名字标签）。聊天、tab 列表、原版界面仍显示真名 | safe |

### 视觉（Visuals）

**ESP**（实体框 / 四角括号两种样式，支持填充与按类别配色）、**Tracers**（射线，起点可选屏幕中心/底部）、**NameTags**（头顶名字 + 血量 + 距离 + 背景板）、**Fullbright**（亮度拉满，恢复原值）。三者共享目标筛选（玩家/怪物/动物/其他、距离、忽略隐身）与配色设置。

### HUD / 界面

* **ClickGUI**：900×550 窗口、侧边栏分类、模块搜索、参数面板（滑块/开关/下拉/多选/概率/颜色选择/按键绑定）、Config 页
* **HUD 编辑器**：拖拽定位、实时预览
* **通知系统**：模块开关提示
* **12 个 HUD 元素**：模块列表、按键精灵（含 CPS）、坐标、FPS、Ping/TPS、TargetHUD、装备耐久、药水效果、音乐播放器、**Watermark**（客户端名 + 版本 + 可选 FPS）、**ServerInfo**（地址 / 延迟 / TPS）、**Session**（本次会话时长）
* **TargetHUD 与战斗模块联动**：优先显示 KillAura / Aimbot 正在打的目标，无模块提供时才回落到准星

### 安全 / 反检测

| 组件 | 作用 |
| --- | --- |
| `FlagDetector` | **事后熔断**：服务器下发位置纠正包（说明客户端声明被拒绝）时，操纵移动/速度/旋转的模块自动静默一段时间；各模块的 `Pause On Flag` 开关引用它 |
| `SafetyManager` | **事前限额**：每个模块每秒最多做多少次操作（默认 20，与 tick 率一致）。超限只是"这一 tick 不做"——不抛异常、不关模块、不改设置，失败方向永远是更安静而不是更快 |
| `HumanizedDistribution` | 点击间隔用对数正态分布而非均匀随机，并有 5% 概率插入一次明显更长的迟疑 |
| `RotationManager` | 反应延迟（100~250ms，可被模块自己的设置覆盖）与 1~2° 微抖，避免转向总是精确到同一角度 |

### 配置

所有状态保存在 `config/sakura/`：`sakura.json`（模块开关、按键绑定、每个模块的参数、HUD 元素位置、界面主题色与缩放）、`profiles/`（多配置档）、`backup/`（自动备份）、`font/`（放自定义字体）。文件损坏时自动回退默认值，不会导致游戏无法启动。

---

## 📥 下载发行版

不打算自己编译的话，直接去 **[Releases](https://github.com/ka78ka91/Sakura-client/releases)** 下载：

| 文件 | 用途 |
| --- | --- |
| `sakura-client-<版本>.jar` | **成品模组**，放进 `.minecraft/mods/` |
| `sakura-client-<版本>-sources.jar` | 源码包，只有想看代码时才需要 |

安装前提：Fabric Loader ≥ 0.19.5 与 Fabric API 0.141.6+1.21.11，Java 21。

> 只下 `sakura-client-<版本>.jar` 那一个。名字最长的那个才是模组本体，`-sources` 与 `-dev` 后缀的放进 mods 目录不会加载。

### 发版流程（维护者）

推送一个 `v*` 形式的标签即可，CI 会自动构建并把产物挂到 release 上：

```bash
git tag v1.0.0
git push origin v1.0.0
```

`.github/workflows/release.yml` 会用标签里的版本号覆盖 `gradle.properties` 的 `mod_version`，所以产物名与标签一致，不需要手动改版本号再提交。带连字符的标签（如 `v1.1.0-beta`）会自动标为预发布版。

另有 `.github/workflows/build.yml`：每次推送到任意分支与每个 PR 都会编译一次，用于在打标签之前就发现编译不过的提交。

> CI 上用 `GRADLE_OPTS` 覆盖了 `org.gradle.java.home`，因为仓库里的 `gradle.properties` 指向的是作者本机的 JDK 路径。这样同一个文件既能本地用，也能在 runner 上跑。

---

## 🔨 从源码构建

```bash
# 1) 需要 JDK 21
java -version        # 应为 21.x

# 2) 构建（wrapper 已配置国内镜像，首次会下载 Gradle 与 Minecraft 依赖）
./gradlew build      # Windows: .\gradlew.bat build

# 产物
build/libs/sakura-client-1.0.0.jar          # 成品模组（已 remap），放进 mods/
build/libs/sakura-client-1.0.0-sources.jar  # 源码包
```

**本机 JDK 路径**：`gradle.properties` 里有一行 `org.gradle.java.home`，指向作者本机的 JDK。如果你的路径不同，要么改这一行，要么删掉它让 Gradle 用 `JAVA_HOME`（这一行是你本机配置，是否提交由你决定）。

**国内网络提示**：仓库的 `gradle/wrapper/gradle-wrapper.properties` 指向腾讯云 Gradle 镜像；Loom 依赖走 `maven.fabricmc.net`、`repo1.maven.org` 与 Mojang 官方分发仓库。若直连 GitHub 受限，可用任意 GitHub 反代拉取本仓库源码后本地构建。

**开发环境要求**：`fabric-loom` 1.17.21 需要 **Gradle 9.5+**（其插件 API 版本为 9.5.0，Gradle 8.x 会报 variant 不兼容），因此请不要用旧版 Gradle 构建。

---

## 📁 目录结构

```
src/main/java/com/sakura/client/
├── SakuraClient.java        入口：注册模块、HUD 元素、按键、配置、字体包引导
├── RegistryAudit.java       一次性启动自检（-Dsakura.debug.registry=true 开启）
├── config/                  config/sakura/ 读写：主配置、多配置档、备份
├── gui/                     ClickGUI、HUD 编辑器、参数面板、控件
├── hud/                     音乐接口与实现、HUD 管理、TPS 追踪
│   └── element/             12 个 HUD 元素
├── mixin/                   26 个混入（距离、暴击、击退、旋转、位置纠正、慢速、
│                            边缘保护、鼠标位移、物品冷却…）
├── module/                  模块框架与 25 个功能模块
│   └── impl/                含战斗流水线：TargetTracker / PointTracker /
│                            TargetProvider(s)
├── notification/            提示气泡
├── render/                  绘制工具、动画、世界投影、字体包生成（FontPack）、
│                            文本替换钩子（DisplayText）
├── rotation/                旋转管理（静音旋转、反应延迟、微抖）
├── safety/                  行为预算（SafetyManager）、人类化分布
│                            （HumanizedDistribution）、分布验证
│                            （DistributionCheck）、熔断（FlagDetector）
└── setting/                 参数类型体系

src/main/resources/assets/sakura/
├── smtc-helper.ps1          Windows 媒体桥（随 jar 发布）
└── lang/en_us.json
```

---

## 🧪 自检与调试开关

| 开关 | 作用 |
| --- | --- |
| `-Dsakura.debug.registry=true` | 启动时打印一次注册表审计：每个分类的模块数、每个模块的默认状态与全部参数。用于确认模块真的注册成功、参数默认值符合预期 |
| `java -cp build/classes/java/main com.sakura.client.safety.DistributionCheck` | 采样 1000 次并输出均值、方差、标准差、极值与直方图，并与同区间均匀分布对照，用于验证人类化分布确实生效 |

> 这两项都是诊断脚手架，等模块集稳定后会移除。

---

## 🙏 致谢与许可

* 本项目以 **GPL-3.0** 发布，与上游保持一致。
* 大量模块移植/改写自 **[LiquidBounce](https://github.com/CCBlueX/LiquidBounce)**（GPL-3.0），代码注释中逐处标注了来源与差异。
* 作者：**Idontkonw** ｜ 仓库：<https://github.com/ka78ka91/Sakura-client>
