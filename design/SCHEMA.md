# 设计台 · 数据契约定稿 v1（Lead 定，双方共同遵守）

设计台（WebView，assets/design.html，源文件 design/preview.html）与输入法之间只通过
`ClawTheme.apply(json)` / `ClawTheme.effective()` 传递**一个 JSON**。

## 1. 顶层
```json
{ "colors":{...}, "dims":{...}, "fx":{...}, "layouts":{ "num":<PAGE>, "letter":<PAGE>, "symbol":<PAGE> } }
```
- `colors/dims/fx` 沿用现有字段（Theme.java 已解析），**不删不加**已有键。
- `layouts` 为新增；缺失时输入法用内置默认（现有这版外观）。

## 2. PAGE（一页键盘布局）
```json
{
  "pad": 5, "gap": 4, "radius": 12,
  "rows": [
    { "h": 1.0, "cells": [ <CELL>, ... ] },
    ...
  ]
}
```
- `h`：行高倍率（1 = keyH）；`pad`/`gap`/`radius` 可缺省，缺省取 dims。

## 3. CELL
```json
{ "k":"char|space|del|enter|sym|back|num|emoji|gap|label",
  "t":"显示文字",        // char/label 必填；其它可缺省
  "w":1.0,              // flex 权重
  "h":1.0,              // 仅当一格跨多行时用；默认 1（单行内竖向撑满）
  "fs":16,              // 字号 sp，可选
  "color":"#22D3EE",    // 可选，缺省按类型取主题色
  "r":12                // 圆角 dp，可选
}
```
动作语义：
- `char` 上屏 `t`；`space` 空格；`del` 退格；`enter` 回车；`sym` 打开符号页；
  `back` 回字母页；`num` 进数字页；`emoji` 占位（暂上屏 emoji 字符）；`gap` 纯占位不可点；
  `label` 只显示不动作（用于装饰）。
- 数字页左侧竖栏用 `rows[].cells` 里的普通 char 表达即可；若要"一格跨 3 行"，用
  `rows[0].cells=[{k:"gap",...}]` 占位 + `rows` 的 `h` 自行组合（v1 不做 rowspan，保持简单）。

## 4. 动效必须跟随布局
- 涟漪/玻璃/按下动画一律由**视图实际 bounds**驱动（RippleDrawable 天然跟随），
  代码里禁止再出现写死的键宽/键高常量来画动效；圆角按 CELL.r → 缺省 theme.KEYRADIUS。
- 设计台预览里同样以 flex 布局渲染，键宽/键高变化时涟漪预览跟随。

## 5. 图标
- 全 app 图标改为 SVG 源（统一放 `design/icons/*.svg`），构建时转 VectorDrawable
  （v1 先落 AI / 设置 / 回退 / 空格 / 回车 五个，再逐步替换其余）。SVG 用 `currentColor`
  以便按主题色着色。

## 6. pages（二级页：数字 / 符号 / Emoji，与 colors/dims/fx/layouts 同级）

二级页（ALT 页）的单一数据源，**v2 无分页**：每个 tab 是一串**扁平 chars**，渲染每 `gridCols` 个一行、
整页可上下滚动。设计台「二级页」卡片负责编辑，输入法 `AltPages` 读它渲染。
规格背景见 `docs/ALT-PAGES-SPEC.md`。数据优先级：

1. theme JSON 顶层 `pages`（设计台 / 用户覆盖，最高）
2. `assets/alt_pages.json`（构建期默认，由 `tools/gen_alt_pages.py` 产出）
3. 代码内置默认（`AltPages` / 设计台占位，兜底）

```json
{ "colors":{...}, "dims":{...}, "fx":{...}, "layouts":{...},
  "pages": {
    "version": 2,
    "gridRows": 4, "gridCols": 6, "gridRowH": 0.85, "railH": 0.6, "sideCols": 1,
    "num": {
      "label": "1",
      "side": { "chars": ["+","−","×","÷","^","=","%","(",")","[","]","{","}",
                          "@","#","&","*","\"","'","!","?",":",";","_","~"] },
      "digits": [ ["1","2","3"], ["4","5","6"], ["7","8","9"] ],
      "bottom": [ "back", "0", "." ],
      "funcs": [ "del", "space", "calc", "enter" ]
    },
    "sym": { "tabs": [
      { "id":"cjk",  "label":"，中文", "chars": ["，","。","、","…"] },
      { "id":"en",   "label":"EN",    "chars": ["!","\"","#","…"] },
      { "id":"math", "label":"数学",  "chars": ["±","×","÷","…"] },
      { "id":"roman","label":"罗马",  "chars": ["Ⅰ","Ⅱ","Ⅲ","…"] },
      { "id":"jp",   "label":"日文",  "chars": ["あ","い","う","…"] },
      { "id":"lib",  "label":"导入",  "chars": [] }
    ]},
    "emoji": { "tabs": [
      { "id":"recent",  "label":"🕘最近",  "chars": [] },
      { "id":"smile",   "label":"😀表情",  "chars": ["😀","😃","…"] },
      { "id":"gesture", "label":"👍手势",  "chars": [] },
      { "id":"animal",  "label":"🐱动物",  "chars": [] },
      { "id":"food",    "label":"🍔食物",  "chars": [] },
      { "id":"activity","label":"⚽活动",  "chars": [] },
      { "id":"travel",  "label":"🚗旅行",  "chars": [] },
      { "id":"object",  "label":"💡物品",  "chars": [] },
      { "id":"symbol",  "label":"❤符号",  "chars": [] }
    ]}
  }
}
```

约束：
- **几何**：可视高度 `gridRows × gridRowH × KEYH + railH × KEYH = 4 × KEYH`（默认 4×0.85 + 0.6），
  与 26 键页逐像素等高；`gridRows/gridCols/gridRowH/railH` 可缺省（默认 4/6/0.85/0.6）。
  内容超过可视行数时**整页滚动**，不新增行高。
- **扁平 chars**：所有 tab 的 `chars` 是连续字符串数组（不分页），渲染每 `gridCols` 个一行；
  `""` = 空位（不可点占位）；多码点 emoji 由生成器过滤，保证一格一个。
- **num（v9 定稿，2026-11-10，线框 `design/mockups/num-page.html`）**：`label` + `side.chars` + `digits` + `bottom` + `funcs`，无 `tabs`。
  - **几何**：宽 = 左 1 : 中 3 : 右 1 **五等分**（左右纵栏等宽、与数字键同宽，完全对称）；
    高 = **4 行，无第 5 行、无底部页签栏**，总高 = `4 × KEYH`（与 26 键页等高）。
  - `side.chars`：左竖栏一列，**可上下滚动**（露半格暗示可滑），默认 25 个，含 `^`：
    `+ − × ÷ ^ = % ( ) [ ] { } @ # & * " ' ! ? : ; _ ~`；点按上屏，长按编辑；对应旧 prefs `num_side_chars`。
  - `digits`：**3 行 × 3 列**数字九宫格（默认 `1..9`）；`bottom`：第 4 行 3 格（默认 `Back / 0 / .`，
    `"back"` = 回 26 键字母页）。
  - `funcs`：右列 **4 格、顺序可调**；取值 `del | space | emo | calc | enter | empty`
    （`calc` = x÷y 计算，`enter` = ⏎）；默认 `["del","space","calc","enter"]`；
    **右列没有 Emo 入口**（emoji 从符号页进）。
  - 兼容（读取时宽松，写回时统一）：
    - `digits` 也接受**扁平 9 个**（`["1",...,"9"]`）→ 按行折叠成 3×3；旧 `num.grid` → 拍平前 9 格填 `digits`；
    - `bottom` 里 `"Back"` / `"back"` 等价（统一存小写 `"back"`）；
    - `funcs` 也接受对象形式 `{"k":"del","t":"⌫"}`（ime-layout 默认数据即此格式）→ 只取 `k`；
      设计台保存时统一写字符串数组（`t` 这类显示文案由输入法按 `k` 取内置文案）。
    - 旧 `side.groups` → 拍平进 `side.chars`；旧 `num.grid` 不再写回，保存时只写 v9 字段。
- **sym / emoji**：`tabs[]`，每个 tab `{ id, label, chars[] }`；`tabs` 顺序 = 页签栏顺序；
  `label` 就是页签文字；页签栏同样以固定 `[返回]` 开头；切 tab = 换列表并回到顶部。
- 符号固定组 id：`cjk/en/math/roman/jp/lib`；emoji 组 id：
  `recent/smile/gesture/animal/food/activity/travel/object/symbol`。
  `lib` 运行时用 `files/char_lib.txt` 覆盖，`recent` 用 `files/emoji_recent.txt` 覆盖
  （设计台里编辑的是兜底占位）。
- 设计台保存走同一个 `ClawTheme.apply(JSON)`，无需额外桥方法。

## 7. 设计台预览保真（2026-11-10）

设计台里所有预览（首页/几何页的内嵌预览、二级页内嵌预览、右下角「实机预览」浮层、布局画布）**只读当前
theme JSON 的 dims**，不再使用写死 CSS 值；改滑杆即时重画（无需保存）。对照读数条固定显示
`键高 / 键距 / 圆角 / 候选高` 四项。

| 预览元素 | 取值来源 |
|---|---|
| 键高、行高 | `dims.keyH`（二级页数字页 4 行 = 4 × keyH；符号/emoji 行高 = `gridRowH × keyH`） |
| 键距 | `dims.keyGap`（26 键页、数字页 1:3:1 三栏、符号/emoji 网格、候选条内边距全部同一来源） |
| 键圆角 | `dims.keyRadius`（二级页 CELL 的 `r` 仍可覆盖） |
| 候选条 | `dims.candH` 高、`dims.candRadius` 圆角、`dims.fontCand` 字号 |
| 字号 | 26 键页 = `dims.fontKey`；二级页按 `fontKey` 推导：数字 `1.5×`、符号 `1.25×`、emoji `1.35×`、功能键 `0.875×`，小字 = `fontSmall`；页签 = `fontTab` |

注意：`layouts[].pad / gap / radius` 仍保留在 JSON 契约里（兼容旧数据），但**设计台预览与布局画布不再渲染
页内覆盖**——一律以 Theme dims 为准（避免「改键距但预览不变」）；输入法侧二级页也必须读 `Theme.KEYGAP`
（0.5.56 起已修）。

## 8. 视觉/动效补充
- 二级页键面与字母键同材质（玻璃底 + RIPPLE），字号：数字 24sp / 符号 20sp / emoji 22sp。
- 翻页点：网格右侧 18dp 竖条，4 个 6dp 圆点，当前页 CYAN 实心。
- 页签栏：横向等宽胶囊，选中 SURFACE2 底 + TEXT 字，未选中透明 + MUTED 字，圆角 12dp。
