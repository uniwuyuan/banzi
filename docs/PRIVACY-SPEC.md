# 隐匿模式 · 规格 v1（2026-11-09 夜）

> 用户已定的交互（原文）：
> 「不按APP名单，开启默认L1，L2需在“设置”键浮窗中选中“隐匿模式”停留3秒，退出则是停留一秒，
> 否则只退回L1，从L1再次进行才可退回标准模式，状态切换不要有气泡提示，状态提示则做到
> “收起键盘”按键上，暗紫呼吸动画，退出仅限手动，顺便把设置页浮窗里的“收起键盘”按键删掉，
> 只保留26键盘的收起键盘按键」

## 1. 三级状态

| 级别 | 名称 | 语义 |
|---|---|---|
| 0 | 标准 | 现状，不解释 |
| 1 | L1 轻隐匿 | **只关「记」，不关「用」**：一切落盘停止；已有数据仍可读、候选照常、云端功能照常（但不得把本次输入写进任何历史） |
| 2 | L2 严隐匿 | **L1 + 关「读」+ 关「云」**：个人历史数据不参与候选（个人词库/前缀召回/联想/最近/剪贴板/常用语/记忆库），所有云端请求停用（AI 候选、AI 面板、语音云识别、云端 OCR、微信回复、AutoBrain、搜狗云拼音） |

持久化：`claw_ime` prefs `privacy_level` = 0/1/2，默认 0；**退出仅手动**，收起键盘/重启都不改级别。

## 2. 开关交互（只此一处：⚙ 长按浮层）

浮层菜单项顺序：`剪贴板 / 常用语 / 隐匿模式 / BOOST 性能模式`。
**删掉**原来的「收起键盘」项（`runSettingsItem` idx 2），键盘上的 ⌄ 撤出键保留。

「隐匿模式」项按住不松：
- 停留 **3 秒**（≥3000ms）→ 升一级：`0 → 1`、`1 → 2`（2 封顶）
- 停留 **1 秒**（≥1000ms 且 <3000ms）→ 降一级：`2 → 1`、`1 → 0`
- 不到 1 秒松手 → 不变（等同点按，关菜单）
- 全程 **无气泡、无 toast、无文字**；松手后菜单关闭
- 项文字本身显示当前状态：`隐匿模式 L1` / `隐匿模式 L2` / `隐匿模式`

实现要点：`showSettingsMenu` 里给该项传入长按/停留计时；浮层滑动选择逻辑
（`updateActionCursor`/`actionIndex`）保持不变，只是在「选中项已是隐匿模式」时启动计时器，
滑走到别项或松手时取消。

## 3. 状态提示：⌄ 撤出键暗紫呼吸

- 顶部候选栏最右的 ⌄ 键（`onExitKey` 那个）在 level≥1 时叠加**暗紫呼吸**：
  - 底色/光晕在 `#2A1B3D`（暗紫）与 `#4B2A6B` 之间缓慢呼吸，周期 ≈2.4s，正弦，无边线
  - L2 比 L1 更明显：L1 峰值 alpha ≈0.55，L2 ≈0.9、周期略快（≈1.8s）
- 新增 `ui/PrivacyBreathDrawable.java`（ValueAnimator 驱动，`start()`/`stop()`/`setLevel()`），
  键盘重建时按当前级别挂到 exit 键背景（叠加在原键背景上：用 FrameLayout 或 drawable 组合均可，
  简单做法：exit 键 setBackground(new PrivacyBreathDrawable(...))，级别 0 时恢复 Theme.press 键面）
- 换级别立即生效，不需要重建键盘。

## 4. 落盘闸门（L1/L2 都生效）

新增 `core/Privacy.java`：

```java
public final class Privacy {
    public static int LEVEL = 0;              // 0/1/2，由 ClawImeService 初始化时读 prefs
    public static boolean on() { return LEVEL >= 1; }
    public static boolean strict() { return LEVEL >= 2; }
    public static void setLevel(Context c, int level) { LEVEL = level; prefs保存; }
}
```

在这些「写」入口加 `if (Privacy.on()) return;`（读函数不动）：

| 类 | 方法 |
|---|---|
| `core/UserDict` | `learn(...)` 全部重载、`markStrong`、保存 |
| `core/AssocStore` | `learn`、`learnTri`、`unlearn`、证据写入 |
| `core/ChoiceLog` | `record(...)` |
| `core/CandLog` | `record(...)` |
| `core/Recent` | 写入 |
| `core/TrialPool` | `learn/record` |
| `core/AiWords`、`core/AiHeat`、`core/AiDomain` | 写入 |
| `read/ClipStore` | `start/push/captureNow/remove/写入`（L1 起就不记剪贴板；手动「剪贴板」页的删除仍可用） |
| `core/PhraseStore`、`core/EnWords`、`core/WordPool` | 写入 |
| `memory/MemoryStore`、`read/MemIn` | 写入（记忆库/微信锚点） |

注意：**读入口一律不加门**（L1 还能用历史；L2 的「读」门在服务层统一关）。

## 5. L2 额外：读门 + 云门（ClawImeService）

读门（L2 时）：
- `refreshCandidates`：不取 `userDict` 前缀/个人词、不取「最近」、不取常用语；
- 联想候选（`buildAssoc`/`ctxCandidates`）直接返回空；
- 剪贴板页、常用语页入口置灰或提示不可用（不弹 toast：菜单里文字变暗）；
- 记忆库/微信回复入口停用。

云门（L2 时）：以下调用前 `if (Privacy.strict()) { …本地兜底或直接返回; }`
- `GptHttp` / `DeepSeekLlmClient` / `AiPredictor` / `SogouPinyinProvider` / `AsrClient` /
  `OcrClient` / `VisionClient` / `AutoBrain` / `SayMind` / `GptBridge` / `TaskRunner`
- 微信回复、AI 面板、识图、云端语音：直接不可用（入口文字变暗/本地提示，不用 toast）。

## 6. 验收

- 长按 ⚙ 浮层：没有「收起键盘」项；「隐匿模式」在；
- 在「隐匿模式」上停 3s → ⌄ 键开始暗紫呼吸；再停 3s → 呼吸更重；停 1s → 回到 L1；再 1s → 停止；
- 全程无 toast/气泡；
- L1 下打字：`/data/data/com.claw.ime/files/user_dict.json`、`choice_log.jsonl`、`cand_seen.json`
  mtime/大小不变（对比前后）；
- L2 下：候选里不出现个人词库词/联想/最近词；断开网络后 AI 面板/识图不再发请求；
- 状态切完退出 App 重进、重启手机，级别保持。

## 7. 实现清单（PRIV-CORE / task-4，2026-11-09 夜）

> 验证：`python3 tools/verify_privacy.py`（14 项静态检查，当前 14/14 通过）。
> Java 语法：`javalang` 解析全部改动文件 18/18 通过（未跑构建机器人）。

### 7.1 新增

| 文件 | 内容 |
|---|---|
| `src/com/claw/ime/core/Privacy.java` | `LEVEL`（默认 `STANDARD=0`）、`on()`（≥L1）、`strict()`（≥L2）、`init(Context)` / `read(Context)` 读 prefs、`setLevel(Context,int)` 写 `claw_ime` / `privacy_level`、`up()` / `down()`（3s/1s 用）、`label()`（浮层项文字）、`clamp()` |

### 7.2 落盘闸门（`if (Privacy.on()) return;  // privacy: L1+ 不落盘`）

| 类 | 行号（2026-11-09 23:10） | 方法 |
|---|---|---|
| `core/UserDict` | 99, 188, 194, 261, 339, 659 | `markStrong`、`learn(py,word)`、`learn(py,word,weight)`、`addAiWords`、`addShorthand`、`saveNow`（保存） |
| | 118, 148 | 私有 `saveStrong` / `saveDemoted`（证据落盘） |
| `core/AssocStore` | 120, 145, 151, 157, 178 | `learnTri`、`learn`、`learnWeak`、`unlearn`、`bumpPair`（证据写入） |
| | 704, 724, 367 | 私有 `saveNow` / `saveTri` / `saveTs` |
| `core/ChoiceLog` | 38 | `record` |
| `core/CandLog` | 58, 226 | `seen`、私有 `saveNow` |
| `core/Recent` | 60, 107 | `bump`、`flush` |
| `core/TrialPool` | 161, 208, 238, 290, 305, 333, 556, 793 | `add`、`expose(…,app)`、`settle`、`rejected`、`good`、`bad`、`promote`、私有 `save` |
| `core/AiWords` | 102, 117, 143 | `put`、`putItems`、`save` |
| `core/AiHeat` | 102, 111, 124 | `put`、`markDrop`、`save` |
| `core/AiDomain` | 359 | 私有 `write`（AI 研究结论落盘） |
| `core/PhraseStore` | 93 | `saveText`（`add`/`remove` 也走这里） |
| `core/EnWords` | 57, 109 | `bump`、私有 `save` |
| `core/WordPool` | 108（`persist = !Privacy.on()`） | 隐私模式不写 `wordpool.txt`/`charpy.txt` 缓存，已有缓存仍可读、没有则纯内存解析 |
| `read/ClipStore` | 57, 62, 80, 88 | 监听回调（连系统剪贴板都不读）、首个快照、`captureNow`、`push` |
| `read/MemIn` | 73 | 私有 `write`（记忆胶囊/微信锚点） |
| `memory/MemoryStore` | 32 | `add` |
| `memory/MemoryDb`（补漏） | 197, 243, 310, 321, 383, 461, 584 | `addLog`、`upsertFact`、`updateFactValue`、`setWeight`、`rememberTarget`、`upsertTone`、`addAlias` |
| `memory/WxDb`（补漏） | 93, 113, 122 | `insert`、`markProcessed`、`markProcessedAll` |

**读入口一律没加门**：`forPinyin/forPrefix/prefixMatches/recs`、`next/predict/predictV3/predictV4/link`、
`top/sampleByPy/boost`、`feed*/stats/reportText`、`get/entries/heat/dropped`、
`last/recent/snapshot/readAll`、`list/entries/text`、`all/byIdentity/recentLogs/facts/subjects`、
`lastCapsule`、`sample/pinyinOf`、`seen/unprocessedCount` 等（校验脚本逐条断言）。L2 的读门在服务层统一关（PRIV-UI）。

### 7.3 两处判断说明

1. **ClipStore 的删除**：§4 表里写了 `remove`，但括注又要求「手动删除仍可用」。按括注处理：
   录制口（`start`/`captureNow`/`push`/监听回调）全部挡掉，**删除/清空保留并能落盘**（只清理不记录，`save()`
   此时只有 remove/clear 能触发）。
2. **MemoryDb / WxDb 是补漏**：§4 表只列了 `MemoryStore`/`MemIn`，但真正持续写「记忆库/微信锚点」的是
   `MemoryDb`（log/fact/tone 加密落盘）和 `WxDb`（微信消息内容）。为满足 L1「一切落盘停止」，两者写方法一并挡掉。
   删除/清理类方法（`deleteFact`/`clearAll`/`clearIdentity` 等）不挡。

### 7.4 留给 PRIV-UI（task-5）

- **`ClawImeService.onCreate()` 第一行（`super.onCreate()` 之后）调 `Privacy.init(this)`**，
  必须早于 `ClipStore.start(this)` 和各学习库构造，否则重启后级别读不回来（默认 0）。
- 浮层：`showSettingsMenu` 菜单项 `[剪贴板][常用语][隐匿模式 Lx][BOOST]`，删 `runSettingsItem` 的收起键盘项；
  在「隐匿模式」项上起计时，≥3000ms `Privacy.up(this)`、≥1000ms `Privacy.down(this)`、<1000ms 不变；
  项文字用 `Privacy.label()`；全程无 toast；滑走/松手取消计时。
- `ui/PrivacyBreathDrawable.java` + ⌄ 键挂背景（level 0 恢复 Theme.press）。
- L2 读门/云门按 §5；级别变化即时生效（`Privacy.LEVEL` 是 volatile 静态，闸门每次现读，无需重建键盘）。
- 级别回 0 后剪贴板录制自动恢复（监听仍在，只是回调在 L1+ 返回）；`ClipStore.start` 幂等。


## 8. PRIV-UI 实现清单（task-5，2026-11-09 夜）

> 验证：`python3 tools/verify_privacy_ui.py`（38 项）＋ `python3 tools/verify_privacy.py`（14 项）全通过；
> `javalang` 解析改动文件 19/19 通过（未跑构建机器人）。

| 区域 | 文件 / 位置 | 内容 |
|---|---|---|
| 级别读回 | `ClawImeService.onCreate`（`Privacy.init(this)`，在 `ClipStore.start` 之前）；`MainActivity` / `SettingsActivity` onCreate 也 init | 重启/主进程都从 prefs 恢复 |
| ⚙ 浮层 | `showSettingsMenu` / `runSettingsItem` / `choose` 计时（`privacyDwellAt`、`applyPrivacyDwell`） | 菜单 `[剪贴板][常用语][隐匿模式 Lx][BOOST]`；删「收起键盘」；≥3000ms `Privacy.up`、≥1000ms `Privacy.down`、<1000ms 不变；无 toast |
| 菜单 L2 变暗 | `menuItemEnabled` + `updateActionSelection` | L2 剪贴板/常用语两项文字+图标压暗、选中也不执行 |
| ⌄ 呼吸 | 新增 `ui/PrivacyBreathDrawable.java`；`exitKeyBtn` / `exitKeyBaseBg` / `applyPrivacyKey` | L1 2.4s α≤0.55、L2 1.8s α≤0.9；level 0 恢复原键面；`onPrivacyLevelChanged` 即时生效不重建键盘 |
| L2 读门 | `refreshCandidates`（关页）、`openPage`、`buildCountedPage`（个人词/前缀/A3/试用池）、`candScore`（最近/联想/英文/个人词）、`ctxCandidates`、`buildAssoc`、`crossTails`、`personalExact`、`aiListFor`、`heatOf`、`openTargetPicker`、`buildClipPage`/`buildPhrasePage` 兜底 | 候选不出现个人词库/前缀/最近/联想/剪贴板/常用语；记忆库入口变暗 |
| L2 云门（入口） | `onOcr`、`applyCapture`、`onAi`、`onWxReply`、`onWxCloud`、`onWxPreset`、`startVoice`、`warmAsr`、`warmAsrDecode`、`switchMode(MODE_AGENT)`、AI 键（点击/长按/右划）、`showAgentMenu`、`applyAiKeyLook`、`SayMind.settle`/`SayFirst.onAttach` 调用点 | AI 面板/识图/云语音入口变暗停用 |
| L2 云门（客户端中央闸） | `DeepSeekLlmClient.chat`、`AsrClient.ping/waitReady/transcribe`、`AiPredictor.ready/cached/wordsForPinyin/listWords/nextWords/rerank`、`SogouPinyinProvider.suggest`、`OcrClient.post`、`VisionClient.ask`、`GptHttp.handle`、`GptBridge.onReceive`、`AutoBrain.chat`、`SayFirst.force/onAttach`、`AiDomain.maybeThink` | 任意调用路径都拦得住，不只键盘入口 |
| 追加 L1 落盘 | `read/Dbg.log`、`auto/Probe`、`auto/TaskRunner.report`、`read/GptBridge.write`、`auto/SayMind.recordSaid/settle` | claw.log / probe-*.txt / auto-report.txt / bridge 回执 / 话术历史在 L1+ 停写 |
| ALT 修复 | `onCreateInputView` 的 `altMiniBar` LayoutParams | 由 `matchWrap()`（MATCH_PARENT）改 `WRAP_CONTENT`，⌄ 撤出键不再被挤掉 |

**真机验收步骤（PRIV-UI）**
1. 长按 ⚙：菜单无「收起键盘」，有「隐匿模式」（当前级别文字）。
2. 长按 ⚙ 拖到「隐匿模式」停 3s 松手 → ⌄ 键暗紫呼吸（轻）；再停 3s → 呼吸更重；停 1s → 回 L1；再 1s → 呼吸停止。全程无 toast。
3. L1 下打字：`files/user_dict.json`、`choice_log.jsonl`、`cand_seen.json` 的 mtime/大小不变（`ls -l` 前后对比）。
4. L2 下：候选里不出现个人词库词/联想/最近词；⚙ 菜单里剪贴板/常用语变暗；AI 键压暗，点击/长按无反应；在微信里点「微信回复」无反应；断网后 AI 面板/识图不再发请求。
5. 切到 L2 后重启 App / 重启手机，再长按 ⚙ → 项文字仍是「隐匿模式 L2」，⌄ 呼吸仍在。
