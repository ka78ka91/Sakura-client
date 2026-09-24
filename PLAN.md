# Sakura Client 改造计划（PLAN.md）

> 调研范围：`src/main/java/com/sakura/client/` 全部 90 个源文件 + `src/main/resources/` 配置。
> 基线：commit `bae09fa`，分支 `main`，编译通过（Fabric Loom 1.17.21，Minecraft 1.21.11，Yarn 映射）。

---

## 1. 项目现状总结

### 1.1 规模与结构

| 包 | 内容 | 规模 |
|---|---|---|
| `module` | Module 框架 + 14 个功能模块 | Module 171 行，框架干净 |
| `hud` | HudModule/HudManager/HudRenderer + 9 个 HUD 元素 + 音乐提供者 | MusicPlayerElement 567 行，最大单文件 |
| `gui` | ClickGuiScreen 902 行 + HudEditorScreen + ModuleSettingsPanel 623 行 + 6 个 widget | 玻璃拟态，900x550 本地坐标空间 |
| `setting` | Setting 基类 + 8 个具体类型 | 序列化自成一体，无 Gson 依赖 |
| `mixin` | 13 个 Mixin（含 2 个 Accessor） | 全部面向 1.21.11 Yarn，编译即验证 |
| `render` | RenderUtils 671 行（纯 GUI API）+ Animations + World 投影 | 无 GL 固定管线调用 |
| `rotation` | RotationManager（silent 旋转走 sendMovementPackets 换入换出） | 设计比 LiquidBounce 的包改写更稳 |
| `safety` | FlagDetector（事后）+ Clicker（滚动 CPS 窗口） | SafetyManager（事前）不存在 |

### 1.2 与任务描述的数字出入

- 功能模块实际 **14** 个（描述 15）：Combat **10**（AutoClicker, AutoTotem, AutoWeapon, Criticals, Hitbox, KeepSprint, NoMissCooldown, Reach, TriggerBot, Velocity）、Visuals **4**（Esp, Fullbright, NameTags, Tracers；Esp/NameTags/Tracers 继承抽象的 EntityOverlayModule）。Movement/Player/World/Misc 为空，属实。
- 注册顺序（SakuraClient.onInitializeClient）：模块 → HUD 元素 → HudRenderer → World 投影/overlay → ClickGUI 键位。
- 配置单文件 `config/sakura.json`，媒体桥文件在 `config/sakura/`（smtc-helper.ps1 / album-art.img / media-command.txt）。

### 1.3 代码质量总评

框架层（Setting/Module/HudModule/Animations/RotationManager）质量高：注释解释"为什么"、无 GL 依赖、帧率无关动画工具已存在（`Animations.approach`，e-folds/秒）。问题集中在：**新代码没复用 Animations**（widget 用固定系数）、**HUD 渲染路径每帧重复计算**、**配置系统单薄**（无迁移/备份/多档）、**行为模块默认值与"低调"定位不符**。

---

## 2. 问题清单核实（30 条逐条）

标注：**确认** / **描述有误** / **未复现**。

### 高优先级 bug

| # | 描述 | 结论 | 证据 |
|---|---|---|---|
| 1 | MusicPlayerElement 在 onTick 处理鼠标点击，会丢点击 | **确认** | `onTick()` → `handleInput()` 读 `client.mouse.wasLeftButtonClicked()`。原版 `MinecraftClient` 每帧消费该标志；tick 路径 20Hz，几乎永远读不到 true。修复方向：输入检测移到 render（每帧，HudRenderer→HudManager→element.render 已是帧路径），命令发送保留 |
| 2 | CriticalsModule 切 Mode 不清 Blink 队列 / Timer 时钟 | **确认** | `mode` 是 EnumSetting，运行中切换不触发 onDisable。Blink→其它：`stopBlinking` 仅在 `!isEnabled()`（onTick 头部）或 `tickBlink`（仅 BLINK 分支）调用，切到 JUMP 后 `queued` 永久滞留、连接发不出包（flush 只在 stopBlinking 里）；Timer→其它：`requestedTimerSpeed` 保持 <1.0，`scaleTimerTime` 永走慢速分支，时钟永久变慢。修复：给 `mode` 挂 `onChange`，切出 BLINK/TIMER 时 `stopBlinking(client)` + `releaseTimer()` |
| 3 | MultiChoiceSetting.fromConfig 枚举名不匹配时静默清空 | **确认** | 配置项全部无法匹配枚举时 `restored` 为空集，`set(restored)` 直接清空选择。修复：匹配数为 0 且配置里确实有条目时，保留当前值不调 set |
| 4 | 关 GUI 瞬间按着的绑定键立刻触发 toggle | **确认** | GUI 打开期间每 tick 走 `currentScreen != null` 分支 `KEYS_DOWN_LAST_TICK.clear()`；关 GUI 的同一 tick `pollModuleKeybinds` 见到 down=true、wasDown=false → 误触发。修复：screen 从非空变空的第一个 tick 只记录键态、不触发 |
| 5 | ToggleWidget / DropdownWidget 动画固定系数，帧率相关 | **确认** | ToggleWidget LERP_FACTOR=0.2、DropdownWidget ANIMATION_FACTOR=0.25，均按帧插值无 delta。修复：换成 `Animations.approach(cur, target, speed, deltaSeconds)`（项目已有工具），widget 需要各自的帧 delta 或共享 Clock |

### 中优先级

| # | 描述 | 结论 | 证据 |
|---|---|---|---|
| 6 | ModuleList / PotionHud / PingTps 每帧重复计算 | **确认** | ModuleList：`syncRows()` 在 resolveX→getWidth、resolveY→getHeight、render、render 内的 getWidth 各调一次（每帧 ≥4 次，每次遍历全部模块 + 插入排序）。PotionHud：`refresh()`（复制全部状态效果 + 排序）同样每帧 ≥5 次。PingTps：`pingOf()`（玩家列表 HashMap 查询 + 文本拼接）每帧 3~4 次。修复：见阶段 4 |
| 7 | ClickGuiScreen.layout 每帧 new 大量对象 | **确认** | `layout()` 在 render 每帧调用，且 mouseClicked/mouseDragged/mouseReleased/mouseScrolled 各调一次；每次重建全部 Rect/Row/GroupLabel（7 分类 + 3 实用页 ≈ 10 行 + 2 组标签 + 2 按钮）。修复：可变布局对象池 + 脏标记 |
| 8 | ConfigManager.load 损坏后不自动修复 | **确认** | catch 后 `config = new SakuraConfig()` 但**不 save**，坏文件留在磁盘，下次启动再解析再失败。修复：失败时把默认配置写回 |
| 9 | AlbumArt 的 NativeImageBackedTexture 签名要和 Yarn 核对 | **未复现** | 构造 `new NativeImageBackedTexture(Supplier<String>, NativeImage)` 与 `setImage(NativeImage)` 编译通过，即与当前 1.21.11 Yarn 映射一致。**无需改动** |
| 10 | HudEditorScreen.close 总是跳回主菜单 | **描述有误** | `close()` 实际 `setScreen(new ClickGuiScreen())` —— 回 ClickGUI 而非主菜单；行为本身合理（编辑器从 ClickGUI 打开）。真正的瑕疵是 Javadoc 写 "Always returns to the main menu"，注释与代码矛盾。处理：阶段 7 修正注释 |
| 11 | Reach / Hitbox 没实现 Tagged，风险标签错误 | **确认** | 两模块都没有 `EnumSetting<Tagged>`，`Module.getRisk()` 只扫 EnumSetting → 返回 SAFE。Reach 的注释自己写着 "Detectable"。修复：两模块覆写 `getRisk()` 返回 RISKY（无模式枚举可挂，覆写比造一个假枚举干净） |
| 12 | Reach 默认 +1.0，低调应 +0.1 | **确认** | `entityReach` 默认 1.0（vanilla 实测 5.0 → 6.0）。改为 0.1 |
| 13 | Hitbox 默认开启，低调应关 | **描述有误** | 构造器用三参 super → `enabledByDefault = false`，**已经默认关闭**（可能基于旧版认知）。阶段 2 只需补 getRisk()=RISKY；默认值无需改 |
| 14 | AutoTotem 秒换图腾 | **确认** | `switchDelay` 默认 0.0ms，wantsTotem 成立即当 tick 换。修复：加 `ChanceSetting`（默认 <100%）+ 利用现有 switchDelay |
| 15 | TriggerBot 没有反应延迟 | **确认** | 目标进准星后仅受 CPS 间隔约束，下个 tick 就可攻击。修复：加 `NumberSetting` 反应延迟 100~250ms（默认 150ms 左右），目标切换时重新计时 |
| 16 | AutoClicker 均匀随机，间隔规律 | **确认** | `delay() = 1000 / cps.randomInt()`，均匀分布。阶段 6 换对数正态（Clicker/Clicker 层） |

### 低优先级

| # | 描述 | 结论 | 证据 |
|---|---|---|---|
| 17 | RenderUtils 缩进错位 | **确认** | L156：`drawRect` 方法签名、开括号与第一个 `if` 挤在一行；L720：`float t = ...;		float dx = ...;` 两条语句挤一行。其余 799 行缩进正常 |
| 18 | 死代码 | **确认** | grep 全仓库：`VelocityModule.consumeKnockback()`、`VelocityModule.getLimitTicks()`、`Setting.onExternalChange()` 均只有定义无调用。连带：`knockbackReceived` 字段唯一读者是 consumeKnockback（死）、`limitTicks` 唯一读者是 getLimitTicks（死）。删除时需连带清理赋值点与 reset() |
| 19 | HudManager.getAt 注释反了 | **确认（轻微）** | 注释 "later (visually higher) elements win" 中 "visually higher" 有歧义（易读成屏幕 Y 更小）。实际语义正确：后注册 = 后绘制 = z 序更高、覆盖在上。行为无 bug，仅改注释措辞 |
| 20 | RotationManager.tick 顺序导致瞄准晚一帧 | **确认** | `onEndClientTick` 中 `RotationManager.tick`（L192）在 `ModuleManager.tick`（L195）**之前**：模块本 tick `request()` 的角度要等下一 tick 才被推进/应用。对调两行即可 |

### 可定制性缺口

| # | 描述 | 结论 |
|---|---|---|
| 21 | HUD 只有位置能自定义 | **确认**。每个 HUD 元素自带 GLASS_* 常量；字号走原版 textRenderer；圆角全局一个值；缩放仅 MusicPlayer 有 Scale |
| 22 | 全局只有 4 个自定义项 | **确认**。accentColor / descriptions / hudCornerRadius / guiScale（另有 moduleSettingsPanel 布局项，非外观） |
| 23 | ClickGUI 配色硬编码 | **确认**。ClickGuiScreen 顶部 22 个常量 + HudEditorScreen 6 个 + 各 widget 各自若干 |
| 24 | 没有主题系统 | **确认** |
| 25 | 没有字体系统 | **确认**。`RenderUtils.font()` 直接返回 `MinecraftClient.getInstance().textRenderer` |

### 默认值 / 配置系统

| # | 描述 | 结论 |
|---|---|---|
| 26 | 首次启动默认开 3 个 HUD | **确认**。Keystrokes / ModuleList / MusicPlayer 均 `enabledByDefault=true`，其余 6 个 false |
| 27 | 配置散在 config/ 下 | **确认**。sakura.json 在 config/ 根，媒体三件套在 config/sakura/ |
| 28 | 没有多配置档 | **确认** |
| 29 | 没有自动备份 | **确认**。save() 直接覆盖写 |
| 30 | GUI 不记忆上次页面 | **确认**。`selected` 硬编码 `UtilityPage.SETTINGS`，close 时也不写 |

### 新发现问题（调研补充）

| # | 描述 | 处理 |
|---|---|---|
| N1 | `SakuraConfig` 无 schema 版本号，未来格式变更无法程序化迁移 | 阶段 3 加 `schemaVersion` 字段（int，默认当前版本），迁移逻辑按版本走 |
| N2 | `Module.getRisk()` 只扫 `EnumSetting`，未来 MultiChoice 里的 Tagged 选择不参与评级 | 记录在案；当前所有 MultiChoice 选项均 SAFE，不修，避免过度设计 |
| N3 | `ClickGuiScreen.reloadConfig/resetConfig` 里 `ConfigManager.load()` 读取的是同一个静态 config 对象引用，Gson 反序列化会**替换**对象，之后 `ModuleManager.applyPersistedState` 用的是新对象——正确；但 `SakuraClient.persistModuleSettings` 若在同一 tick 后运行会把旧引用的 settings 写回新对象 | 阶段 3 重构 ConfigManager 时让 `load()` 保留字段级更新（Gson 读到临时对象后逐字段拷回），消除引用替换隐患 |
| N4 | `Module.resetSettings()` 只是 `restoreSettings()` 的别名，两者都存在 | 阶段 7 一并检查调用点；若无人用 `resetSettings` 则删（grep 确认后再定） |
| N5 | `HudModule.resolveX/resolveY` 每帧调 `getWidth()/getHeight()`（约束 17 已预判） | 阶段 4 缓存方案必须覆盖这里：HudManager.render 每帧对每个元素先算一次宽高缓存 |

---

## 3. 改造计划

顺序即用户给定：阶段 2 → 7，每阶段独立编译、commit、push，可独立回滚。

### 阶段 2：修 bug + 默认值调整

**Goal**：修 5 个高优先级 bug，默认配置低调化，风险标签如实。

**TodoList**：
1. CriticalsModule：`mode.onChange` 里切出 BLINK 时 `stopBlinking`、切出 TIMER 时 `releaseTimer`（新增私有 `onModeChanged(Mode old, Mode now)`，onDisable 复用同一清理）
2. MusicPlayerElement：输入检测从 `onTick()` 挪到 `render()` 头部（帧路径）；`onTick` 只保留 provider 的 tick() 与 pressedTicks 递减；`wasLeftButtonClicked` 的消费移到帧路径后，确认不会与原版 attack 抢（点击落在 widget 上时无需吞事件，仅发送媒体命令，不冲突）
3. MultiChoiceSetting.fromConfig：`restored` 为空且 `list` 非空时直接 return（保留现值）
4. SakuraClient：`pollModuleKeybinds` 增加 `screenWasOpenLastTick` 状态，关屏首 tick 只更新 KEYS_DOWN_LAST_TICK 不触发
5. ToggleWidget / DropdownWidget：内部持 `Animations.Clock`（或接收 delta—— widget.draw 无 delta 参数，最小侵入方案是 widget 自持 Clock），`animation += (target-animation)*0.2` → `Animations.approach(animation, target, 12.0f, delta)`；Dropdown 同理（speed ~14）
6. Keystrokes / ModuleList / MusicPlayer 构造器 `enabledByDefault` → false
7. ReachModule：默认 entityReach 1.0 → 0.1；覆写 `getRisk()` 返回 `Risk.RISKY`
8. HitboxModule：覆写 `getRisk()` 返回 `Risk.RISKY`（默认已关，无需改）
9. AutoTotemModule：加 `ChanceSetting chance`（默认 60%），tickEquip 在换之前 `chance.roll()`；描述注明"低于 100% 换图腾不再每次命中阈值就立刻换"
10. TriggerBotModule：加 `NumberSetting reactionDelay`（默认 150ms，min 0 max 500 step 10）；`found != this.target` 时记录 `targetSince=now`，`now - targetSince < reactionDelay` 不打
11. AutoClickerModule.delay()：本阶段先做**轻量人类化**（在对数正态引入前的过渡：以 1000/cps 为均值加 ±20% 抖动 + 5% 概率 2 倍间隔）；阶段 6 换成正式对数正态并接入 SafetyManager
12. 编译 → commit `[阶段 2] 修复高优先级bug，默认配置低调化` → push

**涉及文件**：CriticalsModule、MusicPlayerElement、MultiChoiceSetting、SakuraClient、ToggleWidget、DropdownWidget、KeystrokesElement、ModuleListElement、ReachModule、HitboxModule、AutoTotemModule、TriggerBotModule、AutoClickerModule（13 个）

**验证**：编译；grep 确认无 onTick 内鼠标读取残留；人工核对每处清理路径（模式切换矩阵 BLINK/TIMER × 切出方向）。

**风险**：ToggleWidget 动画自持 Clock 在 GUI 打开首帧 delta 偏大——Clock 已 cap 100ms，可接受。Criticals 清理需注意 stopBlinking 会 flush，切模式瞬间包集中发出是预期行为（与 LB 一致）。

---

### 阶段 3：配置系统重构

**Goal**：版本文件夹隔离 + 多配置档 + 健壮迁移 + 自动备份 + GUI 页面记忆。

**TodoList**：
1. 新建 `config/ConfigPaths.java`：`FabricLoader.getInstance().getGameDir()` 逐级向上找 `versions/<ver>/`（比较 `versions` 目录下子目录名）；找不到（开发环境 run/ 或版本隔离关闭）回退 `config/sakura/`。API：`getBaseDir()` / `getConfigFile()` / `getProfilesDir()` / `getBackupDir()` / `getMediaDir()` / `getFontsDir()` / `getLogsDir()`，首次访问时 `Files.createDirectories`
2. ConfigManager：路径改走 ConfigPaths；`load()` 先读 `sakura.json`，不存在则找 `config/sakura.json`（旧位置）→ 存在则**复制**（不移动，防中途失败）到新位置再读；找不到旧文件静默用默认。损坏时自动修复（阶段 4 的条款提前并入本阶段实现，避免 load 语义两次改）——解析失败：备份坏文件为 `backup/sakura.json.corrupt-<ts>` 后写回默认
3. SakuraConfig 加 `schemaVersion`（N1）；加 `lastGuiPage`（String，存 Category.name() 或 UtilityPage.name()）、`activeProfile`（String，默认 "default"）
4. 多配置档：`profiles/<name>.json` 每档一份完整 SakuraConfig；当前档名存 `settings.json`（baseDir 下，独立于档）；`ConfigManager.load/save/reset` 路由到当前档；新增 `switchProfile(name)`：先 save 当前档，再 load 目标档（重放 applyPersistedState + loadPositions + 通知 GUI 刷新）
5. 自动备份：save() 前把旧文件复制到 `backup/sakura-<yyyyMMdd-HHmmss>.json`，按修改时间保留最近 10 份，超出删除
6. GUI 记忆：ClickGuiScreen 构造读 `config.lastGuiPage`，`close()` 写；序列化用 `Category`/`UtilityPage` 的 `name()`，认不出回 SETTINGS
7. 媒体迁移：`SmtcMusicProvider.create()` 的目录改 `ConfigPaths.getMediaDir()`；启动时若 `config/sakura/` 下有 smtc-helper.ps1 / album-art.img / media-command.txt 且新位置没有，静默复制
8. 多档切换 GUI 入口：Config 页加 profiles 区（列出 profiles/*.json，点击切换，当前档高亮）
9. 编译 → commit → push

**涉及文件**：ConfigPaths（新）、ConfigManager、SakuraConfig、SmtcMusicProvider、ClickGuiScreen、SakuraClient（restorePersistedState 里 applyPersistedState 时序在 profile 切换后重放）

**验证**：编译；删除 run/config 下 sakura.json 验证默认；手工放坏 json 验证备份与修复；profiles 目录放第二档验证切换。

**风险**：版本目录探测在版本隔离关闭时会误判（versions/ 下多版本）——策略：只在 `versions/` 下**恰好一个**子目录时采用，否则回退，保守不猜。Setting.set() 的 globalChangeListener 在档切换 applySettings 时由 `loadingSettings` 抑制（现有机制复用）。

---

### 阶段 4：性能优化

**Goal**：消除 HUD/ClickGUI 每帧重复计算与分配。

**TodoList**：
1. **【已决策，用户追加 1】Rect 不池化，保持 record 原样**。理由：`Rect` 是公开嵌套类型，record 的值语义（自动 equals/hashCode）是其契约一部分，改成普通类需要全仓走查且违背"不改公开 API"的精神；每帧 ~30 个 32 字节短命 record 属年轻代廉价分配，收益可忽略。池化只作用于 ClickGuiScreen **内部私有**的 Row / GroupLabel / Button：由 private record 转为私有可变类 + 实例池，改动完全封在 ClickGuiScreen.java 单文件内。drawSection / button / moduleRow 里的 `new Rect` 保留（数量小、不可变、生命周期被每帧 clear 的容器约束，无 aliasing 风险）
2. ModuleListElement：`syncRows()` 只在 render 内每帧首调一次；`getWidth()/getHeight()` 改读 `cachedWidth/cachedHeight` 字段（render 末尾回写；首次调用未缓存时 lazy sync 一次）。resolveX/resolveY 在渲染前由 HudManager 调用的场景读到的就是上一帧缓存，位置拖动反馈一帧内到位，无感知
3. PotionHudElement：同方案，`refresh()` 每帧一次，宽高读缓存
4. PingTpsElement：`pingOf` 结果每帧缓存一次（`cachedPing` 字段，render 开头取一次），pingText/pingTextWidth/render 内复用
5. ClickGuiScreen.layout 加 `layoutDirty` 标记：render 每帧仍 layout（窗口尺寸可变、页面切换需重算），但**输入处理器**（mouseClicked/mouseDragged/mouseReleased/mouseScrolled）不再无条件 layout()，仅布局已脏时重算——这是"每帧 new 几十个"的主要放大器
6. Row / GroupLabel / Button 池化落地（见第 1 项）：rows / groupLabels / activeButtons 列表逐帧复用实例，字段重写；Button 持有的 action lambda 每帧仍新建（行为所需），Rect 字段照常分配
7. 编译 → commit → push

**涉及文件**：ModuleListElement、PotionHudElement、PingTpsElement、ClickGuiScreen、（HudManager 仅注释更新）

**验证**：编译；代码走查确认每帧 syncRows/refresh/pingOf 各 ≤1 次；GUI 手感（拖动滑条、点击）无回归。

**风险**：Row/GroupLabel/Button 池化引入"本帧数据被下帧覆盖"类 bug——池只在 render 与其后的输入处理之间存活，输入读的是本帧池内容（render 先于输入），时序安全；comment 里写明该时序假设。Rect 保持 record 不动，公开 API 零变化。

---

### 阶段 5：好看化（主题 + 字体 + HUD 独立配置）

**Goal**：Theme 集中配色、FontManager 自定义字体、HUD 独立外观、ClickGUI 读主题。

**TodoList**：
1. 新建 `render/Theme.java`：静态注册表 + 当前主题；字段：accent、windowBg、windowBorder、sidebarBg、sidebarDivider、rowSelected、rowHover、rowText、rowIcon、sectionBg、sectionOutline、headerDivider、text、textDim、textFaint、buttonBg、buttonBgHover、scrollbar、glassTop/glassBottom/glassBorder/glassShadow（HUD 共用玻璃材质）、radius、hudRadius。内置 5 套：Sakura（默认，即现配色）/ Midnight / Mono / Sunset / Forest
2. SakuraConfig 加 `theme`（String）；ConfigManager 存取；ClickGuiScreen Settings 页加主题下拉，切换即 `Theme.set(name)` 立即生效
3. ClickGuiScreen / HudEditorScreen / 各 widget 的硬编码颜色常量改为读 Theme（常量删除，引用点替换——纯机械替换，逐文件过）
4. HUD 元素的 GLASS_* 常量收编到 Theme（drawGlass 各元素私有方法改调 Theme 值）；保留各元素特有色（FPS 温度色、Ping 温度色、Potion 效果色）——这些是数据可视化色，不属于主题
5. 新建 `render/FontManager.java`：持有独立 TextRenderer；`RenderUtils.font()` 改为 `FontManager.font()`（内部：启用自定义且加载成功→自定义，否则原版 fallback，不抛异常）；字体文件放 `ConfigPaths.getFontsDir()/`；SakuraConfig 加 `customFont`（boolean）。**字体 API 属高风险项**：1.21.11 注册自定义 TextRenderer 的路径（FontManager/TextHandler/TrueTypeLoader）若与预期不符，**停下来问用户**，不猜
6. **HUD 独立配置基座**：HudModule 加可选外观 Setting（color / scale / 透明度 / 圆角），默认值 = "继承主题"（color 用 0 表示继承），不改任何现有公开方法签名
7. **阶段 5a（流程节点，用户追加 2）**：HudModule 基座 + FpsElement 样板（结构最简单）→ 编译 → commit `[阶段 5a] HUD独立配置：FpsElement 样板` → push → 输出 5a 报告，**停下等用户进游戏确认效果**
8. 收到用户「推广」指令后才进入 **阶段 5b**：按样板推广到其余 8 个 HUD 元素，每元素预设 2~3 档（跟随主题 / 纯白 / 强调色）+ 手动微调 → 编译 → commit `[阶段 5b] HUD独立配置推广到其余元素` → push → 输出阶段 5 完整报告

**涉及文件**：Theme（新）、FontManager（新）、RenderUtils、SakuraConfig、ConfigManager、ClickGuiScreen、HudEditorScreen、6 个 widget、9 个 HUD 元素、HudModule

**验证**：编译；切换 5 套主题目视核对 ClickGUI/HUD/通知无残留旧色；字体开关往返不崩；FpsElement 样板确认后推广。

**风险**：① 字体 API 不确定性（详见 TodoList 5，红线：不猜就问）；② 主题替换涉及 ~40 个引用点，机械替换可能漏——以 grep `0x` 残留清单为准逐一归位（数据可视化色白名单除外）；③ widget 绘制在本地坐标空间，主题色直接替换不影响几何。

---

### 阶段 6：安全层

**Goal**：事前规避层（预算 + 人类化分布 + 反应延迟），FlagDetector 保持底层不变。

**TodoList**：
1. 新建 `safety/SafetyManager.java`：`canAct(module, cost)` / `recordAction(module)` / `tick()`；每模块每秒操作预算（默认 20 点，Attack=1，InventoryClick=2），超预算拒绝；注册表 keyed by Module 实例；tick() 在主循环推进窗口。FlagDetector 保留原样，SafetyManager 是**叠加层**
2. Clicker 人类化：新增 `safety/HumanizedDistribution.java`（或并入 Clicker）：对数正态（mu 由目标 CPS 反推，sigma≈0.25）替代均匀；5% 概率插 2~3 倍长间隔；结果 clamp 在 RangeSetting min/max；**不改 RangeSetting 本身**
3. AutoClicker.delay() 接入 2 的分布（替换阶段 2 的过渡实现）
4. RotationManager：反应延迟（目标出现后 100~250ms 才开始转动，模块 request 传入时 RotationManager 记录 firstRequestAt）+ 锁定微抖（到达后 ±1~2° 正态抖动，仅 silent 模式发抖动角度，相机不动）；抖动幅度走常量，不新增用户设置
5. AutoTotem / TriggerBot 接入 SafetyManager（换图腾 clickSlot 记 2 点、TriggerBot 攻击记 1 点），超预算当 tick 放弃
6. **分布测试证明**：新增 `test` 源集？——项目无 test 目录且约束禁动 build.gradle，无法加 JUnit 依赖。替代：写一个 `main()` 的独立验证类 `tools/DistributionCheck.java`（不进 mixins，不注册，仅开发者手动跑或临时 main），1000 次采样输出均值/方差/分位数，结果贴进阶段报告后**该类保留在 tools 包**（无害、可复跑）
7. 编译 → commit → push

**涉及文件**：SafetyManager（新）、HumanizedDistribution（新，或并入 Clicker）、DistributionCheck（新，tools 包）、AutoClickerModule、AutoTotemModule、TriggerBotModule、RotationManager、SakuraClient（tick 挂 SafetyManager.tick）

**验证**：编译；DistributionCheck 输出（均值 ≈ 目标间隔、变异系数 ≈ sigma、5% 长尾可见）；手动游玩确认 Clicker 不抢输入。

**风险**：RotationManager 反应延迟与 TriggerBot 已有的 reactionDelay（阶段 2）语义重叠——分工：TriggerBot 的 delay 管"何时攻击"，RotationManager 的管"何时开始转头"，两层独立可配，注释写清。预算耗尽可能让 AutoTotem 在关键时刻换不上——默认预算给足（20 点/秒 > 任何模块极限用量），预算仅约束异常爆发。

---

### 阶段 7：收尾

**Goal**：死代码清零、注释纠偏、tick 顺序归位、最终报告。

**TodoList**：
1. 删 `VelocityModule.consumeKnockback()` / `getLimitTicks()`（连带字段 `knockbackReceived` 及其在 onVelocityPacket 的赋值、`limitTicks` 及其在 onTick/reset 的读写）——删除前再 grep 一次确认零引用
2. 删 `Setting.onExternalChange()`
3. 删 `ClickGuiScreen` 三个测试方法：`getAppliedScale()` / `isScaleLimited()` / `getPageTitle()`（已确认全仓库零调用；内部用的是字段 `scaleLimited`，方法删后字段保留）
4. 修 RenderUtils L156 / L720 两处语句挤行
5. 修 `HudManager.getAt` 注释措辞（"later in draw order, i.e. rendered on top, wins"）
6. 修 `HudEditorScreen.close` Javadoc（"main menu" → ClickGuiScreen）
7. `RotationManager.tick` 移到 `ModuleManager.tick` 之后（SakuraClient.onEndClientTick 两行对调）
8. grep 全仓库 `0x[0-9A-Fa-f]{6,8}` 复核阶段 5 无漏网硬编码色（数据可视化色白名单外）
9. 输出最终报告（全部改动文件 / 新增 / 删除 / 每项最终状态 / commit 列表 / 后续方向）
10. commit → push

**涉及文件**：VelocityModule、Setting、ClickGuiScreen、RenderUtils、HudManager、HudEditorScreen、SakuraClient

**风险**：删字段连带面（knockbackReceived/limitTicks）需编译器兜底确认；RotationManager 顺序对调后 TriggerBot 首击角度由本 tick 请求决定，实测确认不引入"打早了没瞄准"——逻辑上更正确（请求当 tick 即推进）。

---

## 4. 不改的文件清单 + 理由

| 文件/类 | 理由 |
|---|---|
| `build.gradle` / `gradle.properties` / `settings.gradle` / `gradlew*` | 约束 5 明令禁止 |
| `fabric.mod.json` / `sakura.mixins.json` | 约束 6；13 个 Mixin 目标方法均已随编译与 1.21.11 Yarn 对齐（MinecraftClientAttackMixin 注释还记录了 resetTicksSince 的字节码验证），无新增 Mixin 计划 |
| 全部 13 个 mixin 类 | 同上；阶段 6 若需 RotationManager 钩子，仅改 RotationManager 自身（beginPacket/endPacket 钩子已存在） |
| `module/Category` / `ModuleManager` / `RenderModule` | 框架稳定，无需求触碰 |
| `module/impl/{Esp,Tracers,NameTags,EntityOverlay,Fullbright,KeepSprint,NoMissCooldown,AutoWeapon}Module` | 与问题清单无涉；阶段 5 主题化只覆盖 ClickGUI + HUD 元素 + widgets，世界 overlay 的实体色属数据可视化色保留 |
| `setting/{NumberSetting,BooleanSetting,ColorSetting,ChanceSetting,SettingType,Risk,Tagged,EnumSetting}` | 健康且稳定；RangeSetting 阶段 6 明令不动 |
| `safety/FlagDetector` / `safety/Clicker` | FlagDetector 是阶段 6 保留的底层；Clicker 仅在阶段 6 增方法（滚动窗口逻辑不动） |
| `rotation/{Rotation,RotationMode,RotationSettings}` / `ClientPlayerEntityRotationMixin` | silent 旋转的包换入换出机制设计正确，阶段 6 只在 RotationManager 内加延迟与抖动 |
| `render/{WorldProjection,WorldOverlayRenderer}` | 每帧矩阵捕获链路正常 |
| `hud/{MusicProvider,MockMusicProvider,AlbumArt,TickRateTracker}` | AlbumArt 签名已核对（#9 未复现）；TickRateTracker 无问题 |
| `assets/sakura/smtc-helper.ps1` / `lang/en_us.json` | 资源文件，阶段 3 只搬运行时副本不改源 |
| `notification/NotificationManager` | 逻辑简单；阶段 5 主题化时若其内部有色常量则一并替换（列入阶段 5 走查清单），无独立改动 |
| `LICENSE` / `README.md` / `.gitignore` | 无涉 |

---

## 5. 风险汇总 + 应对

| 风险 | 等级 | 应对 |
|---|---|---|
| 字体 API 与 1.21.11 Yarn 预期不符 | **高** | 红线：不猜。先做最小 spike（注册一个 TTF 试渲染），不通即停下问用户；fallback（原版字体）路径先行保证功能不回退 |
| 版本目录探测误判（版本隔离关闭） | 中 | 保守策略：versions/ 下恰好一个子目录才采用，否则回退 config/；迁移只复制不移动 |
| 主题替换漏改/错改 | 中 | 走查以 grep 残留清单为准；数据可视化色（FPS/Ping 温度、Potion 效果色、实体类别色）白名单化 |
| 池化对象跨帧污染 | 中 | 明确时序契约（render 填池 → 输入读池）并注释；池条目每帧 reset |
| Criticals 清理路径引入新回归 | 中 | 模式切换矩阵逐一走查（6 模式 × 切出），onDisable 与 onChange 共用同一清理函数 |
| 删死代码连带字段漏删 | 低 | 编译器兜底 + 删前 grep |
| ConfigManager.load 引用替换隐患（N3） | 低 | 阶段 3 改为字段级拷回 |
| gradle daemon 缓存导致"假编译通过" | 低 | 每阶段 commit 前跑完整 `gradlew build`（含 remapJar），不只 compileJava |

---

## 6. 里程碑

| 里程碑 | 阶段 | 验收标准 |
|---|---|---|
| M1 低个性化基线 | 2 | 5 个高优 bug 修复；默认关 3 HUD；Reach +0.1；风险标签如实 |
| M2 配置体系 | 3 | versions/<ver>/sakura/ 结构落地；旧配置自动迁移；损坏自愈；多档切换可用；GUI 记忆页面 |
| M3 性能 | 4 | HUD 每帧重复计算清零；ClickGUI 输入路径零分配 |
| M4 可定制 | 5（5a → 用户确认 → 5b） | 5 套主题即换即生效；字体可换可回退；FpsElement 样板（5a）经用户确认后推广到全部 HUD（5b），每元素 2~3 预设 + 微调 |
| M5 安全层 | 6 | SafetyManager 预算生效；对数正态分布有 1000 采样数据支撑；Rotation 反应延迟 + 微抖 |
| M6 收尾 | 7 | 死代码清零；tick 顺序归位；最终报告 + 全部推送 |

每阶段结束：编译通过 → `git add` 仅本阶段文件 → `commit "[阶段 N] ..."` → `git push origin main` → 阶段报告，等确认。
