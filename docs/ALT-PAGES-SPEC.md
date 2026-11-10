# 统一二级页 · 数字 / 符号 / Emoji · 规格 v2（2026-11-09 夜）

> v2 变更（用户追加）：
> 1. **不要分页，要滚动页**——符号页 / Emoji 页网格整页竖向滚动（连续），不画翻页点、不左右翻页
> 2. 数字页左竖栏也是**上下滚动**（连续字符列表，4 格可视），不是「换组」式分页
> 3. 数字页左下角固定「返回」
>
> 用户定的：符号页 5 类全上 + 用户字符库导入；符号页/数字页/26 键页**同高**；emoji 分类；
> 数字页按新体系重新定义；所有东西最终能被设计台编辑（单一数据源）。
> 视觉骨架来自手绘：满铺大圆角格子 + 底部等宽胶囊页签 + 角落异形功能键 + 细分割线。
> 手绘的歪斜是笔迹，不是设计。

## 0. 一句话

数字页 / 符号页 / Emoji 页统一由 **AltPages** 渲染：`4 行 × 0.85 KEYH 的可滚动网格 + 0.6 KEYH 页签栏
= 4 × KEYH`，与 26 键页（3 字母行 + 底部行）**逐像素同高**。网格是滚动页：一眼 4 行，可上滑看更多。

## 1. 几何（全部写死为常量，后续可在设计台覆盖）

```
keyboardHost 内容高 = 4 × KEYH            // 26 键页：3 行字母 + 1 行底行
AltPage 总高        = 4 × KEYH
  ├ gridView: 4 行可见 × (0.85 × KEYH)    // 竖向可滚动，内容行数 ≥4，超出往下滑
  ├ rail: 1 行 × (0.6 × KEYH)             // 页签栏
  └ 上下 padding 由 keyboardHost 统一（不要再加）
grid 列数 = 6（宽度 = 总宽 − 右侧滚动指示条 18dp）
键间距 KEYGAP=4dp；网格外左右各 5dp 内边距（与字母页一致）
滚动：gridView 竖向滚动，默认隐藏滚动条；右侧 18dp 竖条画 4 个小圆点当**滚动位置指示**
      （不是翻页点：随滚动高亮移动，顶=第1点、底=第4点；内容不足一屏时不画）
页签栏：HorizontalScrollView，等宽 tab；tab 宽 = max(64dp, (总宽-10dp)/6)；选中 = SURFACE2 底 +
TEXT 字，未选中 = 透明底 + MUTED 字；圆角 12dp；字号 Theme.FONT TAB
```

- 键面与字母键同玻璃底/圆角；emoji 22sp，符号 20sp，数字 24sp。
- **没有** 26 键页的底部行；返回靠页签栏左端固定的 `返回`。
- 空格 / 退格 / 回车：ALT 页时在**候选栏右侧**（⌄ 撤出键左边）临时出现三个小键
  `[空格][⌫][⏎]`（各 40dp 宽 × CANDH 高），离开 ALT 页隐藏（对应手绘右上角异形键）。
- 网格滚动与点按互斥：手指位移 > 8dp 判为滚动，取消本次按键提交（滚动中不落字）。

## 2. 三种页

### 2.1 数字页（v9 定稿，2026-11-10）

HTML 线框：`design/mockups/num-page.html` v9（已过用户验收）。手机预览：`bash tools/serve_mockups.sh` → `http://127.0.0.1:8899/num-page.html`

**几何**：宽 = 左 1 : 中 3 : 右 1 五等分（**左右纵栏等宽，完全对称**，与数字键同宽）；
高 = 4 行，无第 5 行、无底部页签栏；总高 = 4 × KEYH（与 26 键页同高）。

```
┌────┬─────────────┬──────┐
│ +  │  1    2   3 │  ⌫   │
│ −  │  4    5   6 │ 空格  │
│ ×  │  7    8   9 │ x÷y  │
│ =  │ Back   0   .│  ⏎   │
└────┴─────────────┴──────┘
```

- 左列（1 列宽，**可上下滚动**，露半格暗示可滑）：`+ − × ÷ ^ = % ( ) [ ] { } @ # & * " ' ! ? : ; _ ~`
  点按上屏；长按编辑 prefs `num_side_chars`
- 中间 3×3：`1 2 3 / 4 5 6 / 7 8 9`（大字号 28sp）；第 4 行 `Back / 0 / .`（Back=回 26 键，`.`=小数点）
- 右列（1 列宽 4 格）：`⌫ / 空格 / x÷y（计算）/ ⏎（回车）`；**没有 Emo**（emoji 从符号页进）
- 顶部候选栏保留，最右 ⌄ 收起；数字页**隐藏** topBar 的 mini 键

**计算键行为（用户验收口径）**
- 按 `=`：照常上屏 `=`；若**光标前连续算式**合法 → 候选栏立刻出两条：
  1. 完整算式 `191÷71%×75^2=1513204.2254`
  2. 结果 `1513204.2254`
  点哪条：把输入框里「算式=」整段**替换**成该条
- 按 `x÷y`：不出字符，直接做同样的计算/候选（手动触发）
- 算式不合法：只留 `=`，不出候选
- 语法：`+ - × ÷ * / ( ) %`（后缀百分比，12%=0.12）、`^` 乘方、小数、负号、隐式乘法 `2(3+4)`；
  求值器 `core/Calc.java`（Lead 提供），结果四舍五入到 ≤10 位小数并去掉浮点尾巴
- 范围：只取**光标前连续**的算式字符（不碰整行文字）

### 2.2 符号页

`sym`：6 列滚动网格，页签栏：
`[返回] [，中文] [EN] [数学] [罗马] [日文] [导入]`

每类是一串连续字符（不是分页数组）：
- 中文（cjk）：全角标点，≥48 个（`，。、；：？！""''（）【】《》…—～·￥$€£%‰@#&*+-=/\\|<>^_` 等）
- EN（en）：ASCII 标点 `!"#$%&'()*+,-./:;<=>?@[\]^_\`{|}~` 等 ≥48 个
- 数学（math）：`± × ÷ √ ∞ ≈ ≠ ≤ ≥ ∑ ∏ ∫ π Δ ∇ ∂ ∈ ∉ ∅ ∩ ∪ ⊂ ⊃ ∠ ⊥ ° ′ ″ µ Ω` 等
- 罗马（roman）：`Ⅰ Ⅱ Ⅲ Ⅳ Ⅴ Ⅵ Ⅶ Ⅷ Ⅸ Ⅹ Ⅺ Ⅻ ⅰ ⅱ ⅲ ⅳ ⅴ ⅵ ⅶ ⅷ ⅸ ⅹ`
- 日文（jp）：平假名 + 片假名 + `ー ・ 〜 々`（≥48 个，滚动看全部）
- 导入（lib）：读 `files/char_lib.txt`（一行一串字符，空白/换行都当分隔，去重）；**长按「导入」页签**
  → 弹编辑框（多行文本）→ 保存 `files/char_lib.txt`；没有文件时该页显示提示
  「长按导入页签，粘贴你的字符」。滚动页内显示全部字符。
- 滚动到底不加额外行；内容不足一屏自然留白。
- 切页签 = 换字符列表并回到顶部。

### 2.3 Emoji 页

`emoji`：6 列滚动网格；系统 emoji 字体直接渲染；页签栏：

`[返回] [🕘最近] [😀表情] [👍手势] [🐱动物] [🍔食物] [⚽活动] [🚗旅行] [💡物品] [❤符号]`

- 数据：Unicode 官方 `emoji-test.txt`（15.1）筛「fully-qualified 且单码点」按官方分组生成 →
  `assets/alt_pages.json`；分组映射：Smileys/People→表情/手势、Animals→动物、Food→食物、
  Activities→活动、Travel→旅行、Objects→物品、Symbols→符号；Flags 不收。
- 「最近」：`files/emoji_recent.txt` 最近 48 个（新的前插、去重）；`Privacy.on()` 时不写（仍可读）。
- 点击 = 直接上屏（`ic.commitText`），不学词、不写候选日志；滚动照旧。

## 3. 交互 / 入口

| 操作 | 结果 |
|---|---|
| 逗号键上滑 / 长按（现有） | 打开符号页（记住上次 tab） |
| CN/EN 键上滑（现有进数字页） | 打开数字页 |
| 数字页页签 `[🙂]` / 符号页页签 `[🙂]` | 打开 emoji 页 |
| 页签 `[返回]` | 回字母页（`lastLetterMode`） |
| 网格上下滑 | 滚动当前列表（不换页、不落字） |
| 页签点击 | 切换分类/页面类型，滚动回顶部 |

ALT 页打开时**候选栏保留**；顶部候选照常刷新。

## 4. 数据（单一数据源）

- 默认数据：`assets/alt_pages.json`（生成器 `tools/gen_alt_pages.py`：emoji 段从 emoji-test.txt 生成，
  符号/数字段手写常量）
- 用户/设计台覆盖：theme JSON 顶层 `"pages"` 键（与 colors/dims/fx/layouts 同级）；`Theme.java` 增加
  `public static String PAGES`（raw JSON），`applyJson` 解析、`effectiveJson` 回写；`AltPages` 读
  `Theme.PAGES`，为 null/解析失败 → 读 assets 兜底。
- 设计台（`design/preview.html` → `assets/design.html`）新增「二级页」分区：编辑 pages（分类列表 +
  每条字符列表 + 网格可视行数/行高/列数），实时预览 + 保存进 theme JSON。

## 5. JSON schema（assets/alt_pages.json，v2 无分页）

```json
{
  "version": 2,
  "gridRows": 4, "gridCols": 6, "gridRowH": 0.85, "railH": 0.6, "sideCols": 1,
  "num": {
    "label": "1",
    "side": { "chars": ["+","-","×","÷","=","%","(",")","[","]","{","}","@","#","&","*","\"","'","!","?",":",";","_","~"] },
    "digits": [["1","2","3"],["4","5","6"],["7","8","9"],[".","0",","]],
    "funcs": [ {"k":"del","rows":2}, {"k":"space"}, {"k":"enter"} ]
  },
  "sym": { "tabs": [
      { "id":"cjk",  "label":"，中文", "chars": ["，","。","、", "..."] },
      { "id":"en",   "label":"EN",    "chars": ["!","\"","#", "..."] },
      { "id":"math", "label":"数学",  "chars": ["±","×","÷", "..."] },
      { "id":"roman","label":"罗马",  "chars": ["Ⅰ","Ⅱ","Ⅲ", "..."] },
      { "id":"jp",   "label":"日文",  "chars": ["あ","い","う", "..."] },
      { "id":"lib",  "label":"导入",  "chars": [] }
  ]},
  "emoji": { "tabs": [
      { "id":"recent","label":"🕘最近","chars":[] },
      { "id":"smile", "label":"😀表情","chars":["😀","😃", "..."] },
      { "id":"gesture","label":"👍手势","chars":[] },
      { "id":"animal","label":"🐱动物","chars":[] },
      { "id":"food",  "label":"🍔食物","chars":[] },
      { "id":"activity","label":"⚽活动","chars":[] },
      { "id":"travel","label":"🚗旅行","chars":[] },
      { "id":"object","label":"💡物品","chars":[] },
      { "id":"symbol","label":"❤符号","chars":[] }
  ]}
}
```

- 所有 tab 的 `chars` 是**扁平字符列表**（不分页），渲染时每 6 个一行。
- num 的 `side.chars` 与 `grid` 独立；`side` 一列 4 格可视、整体可滚动。
- `gridRows/gridCols/gridRowH/railH` 可选，缺省用常量。

## 6. 代码边界

- 新文件 `src/com/claw/ime/ui/AltPages.java`：
  ```java
  public final class AltPages {
      public interface Host {
          void altCommit(String text);
          void altOpen(String pageId);     // "num" / "sym" / "emoji" / "letter"
          void altBackspace(); void altEnter(); void altSpace();
          void altEditCharLib();           // 长按「导入」页签 / 长按数字页左栏
      }
      public AltPages(Context c, Host host);
      public View build(String pageId);
      public String currentPos();          // "sym:cjk" 记住上次位置
  }
  ```
- `ClawImeService` 改动（只允许这些区域）：
  1. 字段 `String altPage = null;`（"num"/"sym"/"emoji"）+ `AltPages altPages;`
  2. `buildKeyboard()`：`if (altPage != null) { keyboardHost.addView(altPages.build(altPage)); 同步 alt 小键; return; }`（放在 MODE_AGENT 后）
  3. 移除 `buildSymbolPage`/`buildNumberPage` 的调用；方法体保留 fallback 但不再入口
  4. 逗号键上滑/长按、CN/EN 上滑：改设 `altPage`；`onFinishInputView` 不重置
  5. 候选栏右侧加 `[空格][⌫][⏎]` mini 键（默认 GONE，ALT 页 VISIBLE）
  6. Host 实现：`altCommit` → `flushPinyinThenCommit(text)`；`altOpen` → 切 altPage/buildKeyboard；
     `altBackspace`→onBackspace；`altEnter`→onEnter；`altSpace`→commitSymbol(" ")；`altEditCharLib`→对话框
- `Theme.java`：PAGES 字段（见 §4）。
- 构建/安装由 Lead 统一；teammate 不触发 Termux 构建机器人。

## 7. 验收清单

1. 编译通过（BUILD_OK）
2. 四页截图同高：26 键 / 数字 / 符号 / emoji，键盘外框高度逐像素相同
3. 符号页：页签可滑可点；网格**整页上下滚动**，滚到底不换页；6 列 × 4 行可见；无翻页点，只有滚动位置小点
4. 数字页：左竖栏上下滚动（4 格可视）；主网格滚动；左下角固定「返回」可用；打出 `123.45+6=5.3`
5. emoji：点按直接上屏；最近记录；分类切换回顶部；L1 隐匿下最近不写
6. 导入页长按可编辑并持久化
7. 设计台改 pages → 输入法实时生效
8. `tools/verify_alt_pages.py` 全 PASS
