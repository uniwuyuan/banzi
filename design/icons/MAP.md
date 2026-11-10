# 半字图标系统 · 清单与接入映射

> 图标唯一源：`design/icons/*.svg`（24×24 网格 / 线宽 1.8 / 圆头圆角 / `currentColor`）。
> Android 侧由 `res/drawable/ic_claw_*.xml`（VectorDrawable）承载，颜色一律 `#FFFFFFFF` + 代码 tint。
> 本次任务约束：**不改 Java、不跑构建**，因此下表全部资源「已就绪」，代码侧统一标记「待接入」。

## 0. 交付物

| 类别 | 数量 | 位置 |
|---|---|---|
| SVG 源 | 35（34 个 UI 图标 + 1 个启动图标） | `design/icons/*.svg` |
| VectorDrawable | 34（24dp 视口，启动图标除外） | `res/drawable/ic_claw_*.xml` |
| 本清单 | 1 | `design/icons/MAP.md` |

文件命名映射：SVG 用「小写连字符」，Android 资源用「小写下划线」——
`emoji-face.svg → ic_claw_emoji_face.xml`、`chevron-down.svg → ic_claw_chevron_down.xml`、
`chevron-right.svg → ic_claw_chevron_right.xml`。

## 1. 规范（与 `ui/Glyph.java` 对齐）

| 项 | 约定 |
|---|---|
| 画布 | 24×24 `viewBox`（启动图标 108×108） |
| 描边 | 1.8（`Glyph` 是 1.7，替换后视觉几乎无差） |
| 端点/转角 | `stroke-linecap: round` / `stroke-linejoin: round` |
| 颜色 | SVG 一律 `currentColor`；仅启动图标允许渐变；VD 里固定 `#FFFFFFFF`，由代码 tint |
| 风格 | 细线、几何、克制；不用拟物/emoji 风/粗描边；相似图标同线宽同视觉重量 |
| 尺寸 | VD 宽高 `24dp`、viewport `24`；不使用 `<group>` 缩放，直接用 24 视口坐标 |

## 2. 第一优先级（6 个）——资源已就绪，建议接入点

| 图标 | 资源 | 建议用途 | 接入位置 | 状态 |
|---|---|---|---|---|
| `ai.svg` | `R.drawable.ic_claw_ai` | AI 键 / AI 联想入口（气泡+星） | ClawImeService.java:2294 `keyAlt("AI")`（26 键第三排左）；3945 `aiKeyView.setText("AI")` | 已就绪 · **待接入** |
| `gear.svg` | `R.drawable.ic_claw_gear` | 设置键 / 齿轮 | ClawImeService.java:2034 `pill("⚙")`（工具栏）；2743 `keyAlt("⚙")`（底排设置键） | 已就绪 · **待接入** |
| `backspace.svg` | `R.drawable.ic_claw_backspace` | 退格键 | ClawImeService.java:2310 / 2440 `keyAlt("⌫")` | 已就绪 · **待接入** |
| `enter.svg` | `R.drawable.ic_claw_enter` | 回车 / 换行键 | ClawImeService.java:2477 / 2748 `keyAlt("⏎")` | 已就绪 · **待接入** |
| `space.svg` | `R.drawable.ic_claw_space` | 空格键 | ClawImeService.java:2737 `keyAlt("LOVE")`（底排空格键） | 已就绪 · **待接入** |
| `emoji-face.svg` | `R.drawable.ic_claw_emoji_face` | emoji 键（笑脸） | ClawImeService.java:2446 `keyAlt("🙂")`（数字页/表情入口） | 已就绪 · **待接入** |

**六个的具体建议**

1. **AI 键** → `ic_claw_ai`：气泡+星，表达「AI 联想写进输入流」。用于 26 键第三排左键（`ClawImeService.java:2294`）与 App 键盘卡片右上 `aiKeyView`（3945）。
2. **设置 / 齿轮** → `ic_claw_gear`：底排 `⚙` 键（2743）与工具栏 pill（2034）。
   另有 `ic_claw_settings`（滑杆）专用于「参数调整」语义，两者分工：齿轮=设置键、滑杆=调参入口。
3. **退格** → `ic_claw_backspace`：左尖气泡+×，两处 `keyAlt("⌫")`（2310 / 2440）。
4. **回车** → `ic_claw_enter`：折返箭头（`↵` 语义比 `⏎` 更中性），两处 `keyAlt("⏎")`（2477 / 2748）。
5. **空格** → `ic_claw_space`：U 形键位示意，替换底排写死的 `LOVE` 文案（2737）。
6. **emoji** → `ic_claw_emoji_face`：笑脸，替换数字页占位 `🙂`（2446）；表情面板入口用 `ic_claw_emoji`（笑脸+火花）。

## 3. 全量映射表

| # | 图标 | SVG | VectorDrawable | 用途 | 建议接入点 | 状态 |
|---|---|---|---|---|---|---|
| 1 | `ai` | `design/icons/ai.svg` | `res/drawable/ic_claw_ai.xml` | AI 键 / AI 联想入口（气泡+星） | ClawImeService.java:2294 `keyAlt("AI")`（26 键第三排左）；3945 `aiKeyView.setText("AI")` | 已就绪 · **待接入** |
| 2 | `settings` | `design/icons/settings.svg` | `res/drawable/ic_claw_settings.xml` | 设置/参数入口（滑杆） | 与 `gear` 分工：滑杆=「设置/参数」类入口（如 App 侧外观参数、设计台） | 已就绪 · 待接入 |
| 3 | `backspace` | `design/icons/backspace.svg` | `res/drawable/ic_claw_backspace.xml` | 退格键 | ClawImeService.java:2310 / 2440 `keyAlt("⌫")` | 已就绪 · **待接入** |
| 4 | `enter` | `design/icons/enter.svg` | `res/drawable/ic_claw_enter.xml` | 回车 / 换行键 | ClawImeService.java:2477 / 2748 `keyAlt("⏎")` | 已就绪 · **待接入** |
| 5 | `space` | `design/icons/space.svg` | `res/drawable/ic_claw_space.xml` | 空格键 | ClawImeService.java:2737 `keyAlt("LOVE")`（底排空格键） | 已就绪 · **待接入** |
| 6 | `emoji-face` | `design/icons/emoji-face.svg` | `res/drawable/ic_claw_emoji_face.xml` | emoji 键（笑脸） | ClawImeService.java:2446 `keyAlt("🙂")`（数字页/表情入口） | 已就绪 · **待接入** |
| 7 | `shift` | `design/icons/shift.svg` | `res/drawable/ic_claw_shift.xml` | 大写 / Shift | 字母页第三排左（现为 AI 键位，需产品决策） | 已就绪 · 待接入 |
| 8 | `symbols` | `design/icons/symbols.svg` | `res/drawable/ic_claw_symbols.xml` | 符号页入口 | 底排/工具栏「，⋀」符号入口 | 已就绪 · 待接入 |
| 9 | `numbers` | `design/icons/numbers.svg` | `res/drawable/ic_claw_numbers.xml` | 数字页入口（#） | CN 键上滑 / 底排「123」 | 已就绪 · 待接入 |
| 10 | `language` | `design/icons/language.svg` | `res/drawable/ic_claw_language.xml` | 中英切换 / 翻译 | ClawImeService.java:2080 `switchMode(MODE_EN/MODE_CN)` 的 CN/EN 键 | 已就绪 · 待接入 |
| 11 | `mic` | `design/icons/mic.svg` | `res/drawable/ic_claw_mic.xml` | 语音输入 | 空格键长按说话、工具栏语音键 | 已就绪 · 待接入 |
| 12 | `emoji` | `design/icons/emoji.svg` | `res/drawable/ic_claw_emoji.xml` | 表情面板入口（笑脸+火花） | 表情页（待做）入口 | 已就绪 · 待接入 |
| 13 | `clipboard` | `design/icons/clipboard.svg` | `res/drawable/ic_claw_clipboard.xml` | 剪贴板 | 剪贴板面板入口（工具栏/长按） | 已就绪 · 待接入 |
| 14 | `search` | `design/icons/search.svg` | `res/drawable/ic_claw_search.xml` | 搜索 | 词库/记忆搜索、设置页搜索 | 已就绪 · 待接入 |
| 15 | `close` | `design/icons/close.svg` | `res/drawable/ic_claw_close.xml` | 关闭 / 收起键盘 | 键盘收起、对话框关闭 | 已就绪 · 待接入 |
| 16 | `chevron-down` | `design/icons/chevron-down.svg` | `res/drawable/ic_claw_chevron_down.xml` | 展开 / 收起 | 候选栏收起箭头（现为「⌄」字符） | 已就绪 · 待接入 |
| 17 | `chevron-right` | `design/icons/chevron-right.svg` | `res/drawable/ic_claw_chevron_right.xml` | 进入二级页 | 设置页一级入口右侧箭头（现为「›」字符） | 已就绪 · 待接入 |
| 18 | `plus` | `design/icons/plus.svg` | `res/drawable/ic_claw_plus.xml` | 新增 | 词库/记忆/短句新增 | 已就绪 · 待接入 |
| 19 | `check` | `design/icons/check.svg` | `res/drawable/ic_claw_check.xml` | 确认 / 已选 | 预设确认、多选项 | 已就绪 · 待接入 |
| 20 | `trash` | `design/icons/trash.svg` | `res/drawable/ic_claw_trash.xml` | 删除 | 词库/记忆/短句删除 | 已就绪 · 待接入 |
| 21 | `copy` | `design/icons/copy.svg` | `res/drawable/ic_claw_copy.xml` | 复制 | 读屏取词后的复制操作 | 已就绪 · 待接入 |
| 22 | `paste` | `design/icons/paste.svg` | `res/drawable/ic_claw_paste.xml` | 粘贴 | 剪贴板粘贴 | 已就绪 · 待接入 |
| 23 | `history` | `design/icons/history.svg` | `res/drawable/ic_claw_history.xml` | 历史 / 上次 | 候选/最近输入/历史记录（语义为「回退式箭头」） | 已就绪 · 待接入 |
| 24 | `memory` | `design/icons/memory.svg` | `res/drawable/ic_claw_memory.xml` | 记忆 / 知识图谱 | MainActivity 底部导航「记忆」Tab（现 Glyph.ORB） | 已就绪 · 待接入 |
| 25 | `keyboard` | `design/icons/keyboard.svg` | `res/drawable/ic_claw_keyboard.xml` | 键盘 | MainActivity 底部导航「键盘」Tab（现 Glyph.KEYBOARD） | 已就绪 · 待接入 |
| 26 | `spark` | `design/icons/spark.svg` | `res/drawable/ic_claw_spark.xml` | AI / 智能 | MainActivity 导航「AI」Tab（现 Glyph.SPARK）、AI 联想标记 | 已就绪 · 待接入 |
| 27 | `gear` | `design/icons/gear.svg` | `res/drawable/ic_claw_gear.xml` | 设置键 / 齿轮 | ClawImeService.java:2034 `pill("⚙")`（工具栏）；2743 `keyAlt("⚙")`（底排设置键） | 已就绪 · **待接入** |
| 28 | `wave` | `design/icons/wave.svg` | `res/drawable/ic_claw_wave.xml` | 语音 / 声波 | 语音识别状态、律动（Rhythm）入口 | 已就绪 · 待接入 |
| 29 | `globe` | `design/icons/globe.svg` | `res/drawable/ic_claw_globe.xml` | 语言 / 网络 | 语言切换、联网状态 | 已就绪 · 待接入 |
| 30 | `layout` | `design/icons/layout.svg` | `res/drawable/ic_claw_layout.xml` | 布局自定义 | 设计台「布局」二级页入口 | 已就绪 · 待接入 |
| 31 | `palette` | `design/icons/palette.svg` | `res/drawable/ic_claw_palette.xml` | 外观 / 配色 | 设计台「设计语言」入口、App 侧「外观与背景」 | 已就绪 · 待接入 |
| 32 | `ruler` | `design/icons/ruler.svg` | `res/drawable/ic_claw_ruler.xml` | 几何 / 尺寸 | 设计台「几何」二级页入口（键高/键宽/键距） | 已就绪 · 待接入 |
| 33 | `motion` | `design/icons/motion.svg` | `res/drawable/ic_claw_motion.xml` | 动效 / 动画 | 设计台「动效」入口（涟漪/glass/动画/彩蛋） | 已就绪 · 待接入 |
| 34 | `magic` | `design/icons/magic.svg` | `res/drawable/ic_claw_magic.xml` | 魔法 / 一键优化 | AI 一键优化、词库整理 | 已就绪 · 待接入 |
| 35 | `launcher` | `design/icons/launcher.svg` | `res/drawable/ic_claw_launcher.xml` | App 启动图标（108×108 自适应前景） | 见「4. 启动图标替换」 | 已就绪 · 待接入 |

说明：`launcher.svg` 只有 SVG 源（108×108），没有对应的 24dp VectorDrawable，见下一节。

## 4. 启动图标（launcher）替换

`design/icons/launcher.svg`：108×108 视口，**字形（半）落在 72×72 安全区（18..90）内**，
描边用青→紫渐变（`#22D3EE → #C084FC`），右上角紫色火花（`#C084FC → #A78BFA`）。
这是自适应图标**前景层**；背景层建议纯色 `#0B1220`（与键盘底一致）。

现状：`res/mipmap-anydpi-v26/ic_launcher.xml` 用
`<foreground android:drawable="@mipmap/ic_launcher_fg"/>`，实体是 5 档 PNG（`res/mipmap-*/ic_launcher_fg.png`）。

**方案 A（最快，改 PNG，不动代码/清单）**
把 `launcher.svg` 从中心 72×72 区域导出为带透明通道的 PNG，按密度覆盖：

| 密度 | 目录 | 前景 PNG 像素（108dp × 倍率） |
|---|---|---|
| mdpi | `res/mipmap-mdpi/` | 108×108 |
| hdpi | `res/mipmap-hdpi/` | 162×162 |
| xhdpi | `res/mipmap-xhdpi/` | 216×216 |
| xxhdpi | `res/mipmap-xxhdpi/` | 324×324 |
| xxxhdpi | `res/mipmap-xxxhdpi/` | 432×432 |

同时把 `ic_launcher_bg.png` 换成纯色 `#0B1220`（或保留现有背景继续用）。
旧系统（API < 26）读 `ic_launcher.png`，也要一并替换成「背景+前景」合成图。

**方案 B（矢量，需改两处 XML，仍不动 Java）**
1. 新建 `res/drawable/ic_launcher_fg.xml`（`<vector>`，`android:width/height="108dp"`，
   `viewportWidth/Height="108"`，path 用 `launcher.svg` 的 d 原样搬入，白色 `#FFFFFFFF`，背景交给底色）。
2. 把 `res/mipmap-anydpi-v26/ic_launcher.xml` 与 `ic_launcher_round.xml` 的
   `<foreground .../>` 改成 `@drawable/ic_launcher_fg`；背景可新建 `@drawable/ic_launcher_bg`（`<shape>` 纯色）。

风险提示：`res/drawable/` 目录本次新建，`build.sh` 的 `aapt2 compile --dir res` 会自动收进去；
但方案 B 改了 `mipmap-anydpi-v26`，属于资源结构变更，建议由 Lead 排一次构建验证。

## 5. Java 侧引用建议（本次不改，供接入方照做）

```java
// 1) 纯图标控件（ImageView / ImageButton / 自定义 View）
Drawable d = ContextCompat.getDrawable(ctx, R.drawable.ic_claw_gear);
d.setTint(Theme.MUTED);                 // VD 内是白色，tint 上主题色
imageView.setImageDrawable(d);

// 2) 现在是 keyAlt("⌫") 这类「带文字的 Button」：清空文字，把图标塞进 compound drawable
Button b = keyAlt("");                   // 或不传文案直接 new
Drawable g = ContextCompat.getDrawable(ctx, R.drawable.ic_claw_backspace);
g.setTint(Theme.CYAN);
b.setCompoundDrawablesRelativeWithIntrinsicBounds(null, g, null, null);

// 3) 自绘（Glyph 的替代路径）：drawable 直接 draw 到 canvas
Drawable k = ContextCompat.getDrawable(ctx, R.drawable.ic_claw_keyboard);
k.setBounds(0, 0, size, size);
k.setTint(Theme.TEXT);
k.draw(canvas);
```

- **推荐**：纯图标用 `ImageView#setImageDrawable`；键位按钮用 `CompoundDrawables`（居中、跟随按钮尺寸、无需额外布局）。
- `Drawable#setTint` 自 API 21 起可用，minSdk 26 不需要 appcompat。
- 统一走一个工具方法（如 `Ui.icon(ctx, R.drawable.ic_claw_x, color)`）避免每处重复 tint 代码。
- 底部导航现在用的是 `ui/Glyph.java` 自绘路径（`Glyph.KEYBOARD/SPARK/ORB/SETTINGS`）。
  过渡期可以不动；彻底替换时给 `IconView` 加一个 `setDrawable(...)` 分支，映射：
  `KEYBOARD→ic_claw_keyboard`、`SPARK→ic_claw_spark`、`ORB→ic_claw_memory`、`SETTINGS→ic_claw_settings`。
- 数字页/符号页按钮同样可以复用这套图标（`backspace`/`enter`/`space`/`symbols`）。

## 6. 校验记录

| 检查 | 方式 | 结果 |
|---|---|---|
| SVG 可解析 / 有 viewBox / 有用途注释 | Python `xml.etree` 解析（本机无 `xmllint`，等价解析级校验） | 35/35 通过 |
| SVG 着色规范 | 24 网格图标必须出现 `currentColor`、禁止硬编码 `#RRGGBB` | 34/34 通过 |
| 启动图标渐变例外 | 只允许 `stop-color` 出现 hex | 通过 |
| VD 根节点/尺寸/viewport | `<vector>` + `24dp` + viewport 24 | 34/34 通过 |
| VD pathData 非空 | 逐 path 检查 | 34/34 通过 |
| SVG ↔ VD 路径一致 | 按「描边/填充」两组逐条比对 d 字符串 | 34/34 完全一致 |
| 视觉复核 | 用 `cairosvg` 渲染 35 图对照表（768×610） | 通过（空格/启动图标已按观感修过一轮） |

> 对照图在 `/tmp/icons_sheet3.png`（临时文件，非交付物）；需要的话可以让构建侧产出一张 `design/icons/preview.png`。

