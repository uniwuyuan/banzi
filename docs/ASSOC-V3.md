# 联想算法 v3（2026-11-09 夜）

> 目标：同样只给 5 个联想词，**更常猜中 + 更少生僻噪声**。
> 已实现：`AssocStore.predictV3()`（默认启用），服务端两处调用点切到 v3；构建 0.5.43 / versionCode 139。

## 一、改了什么

旧打分 `predict()`：`8×live bigram + 3×种子 bigram + 14×live trigram + 5×种子 trigram`，无时间概念，候选永远从历史累计里出。

v3 公式：

```
score(候选) =
    6.0  × live_bigram_count × 0.5^(距今天数/14)      // 旧习惯会退场
  + 3.0  × min(种子 bigram, 10)                        // 聊天史种子打底
  + 14.0 × live_trigram_count                          // 自己打的三词接续最值钱
  + 10.0 × min(种子 trigram, 4)
  + 6.0  × log1p(全局词频)                             // 只加在「已有接续证据」的候选上
```

最后过滤：同词、纯标点、超长；取前 5。

关键点：**通用词频只用来给有证据的候选排序，不凭词频硬塞新词**——所以不会退化成「的/我/你」乱凑，同时把只出现过一次的怪词挤下去。

## 二、离线回放评测

数据：你备份里的真实上屏记录 `choice_log.jsonl`（15,763 条，4,723 个不同词），按时间排序后模拟 App 学习（bigram +3 / trigram +1 / 记每对最后时间），在切分点之后逐位置预测。

| 切分 | 打分 | n | hit@1 | hit@3 | hit@5 | MRR | 生僻词占比 |
|---|---|---|---|---|---|---|---|
| 75% | 旧 predict() | 3344 | 2.84% | 5.86% | 7.21% | 0.0442 | 25.2% |
| 75% | **v3** | 3344 | **3.77%** | **6.55%** | **8.25%** | **0.0536** | **10.7%** |
| 90% | 旧 predict() | 1326 | 2.79% | 5.96% | 7.32% | 0.0443 | 23.7% |
| 90% | **v3** | 1326 | **3.39%** | **6.71%** | **8.52%** | **0.0527** | **9.8%** |

- hit@1：+33% / +21%（两个切分点）
- hit@5：+14% / +16%
- MRR：+21% / +19%
- 生僻词（训练集出现 ≤2 次）占比：25% → **10% 左右**

绝对命中率低是正常的：下一词预测本身就被上下文和实时话题支配，单靠 bigram/trigram 只能到这个量级；但相对提升在两个切分点一致，不是过拟合单次结果。

## 三、顺手修掉的 bug

`ctxCandidates()`（拼音态「上文接得上」的候选）把参数传反了：

```java
// 旧：把 prevWord 当成 prev1，等于预测「上上个词后面接什么」
assocStore.predict(prevWord == null ? "" : prevWord, lastWord, 20);
// 新：lastWord 才是上一词
assocStore.predictV3(lastWord, prevWord, 20);
```

## 四、代码位置

- `src/com/claw/ime/core/AssocStore.java`
  - `predictV3(prev1, prev2, max)`：新打分
  - `globalFreq()`：全局词频缓存（live + 种子），`bumpPair`/`loadSeed` 时置 `freqDirty`
- `src/com/claw/ime/ClawImeService.java`
  - `buildAssoc()`：默认 `predictV3`（开关 `assoc_v3`，默认 true；设 false 回退旧打分）
  - `ctxCandidates()`：参数顺序修正 + 切到 v3

## 五、复现

```bash
# 数据准备（私有，不随仓库分发）：
#   choice_log.jsonl  ← 备份包 files/choice_log.jsonl
#   assoc_seed.json / assoc_seed_tri.json ← 从 APK assets 解出
python3 tools/eval_assoc.py --data-dir /tmp/assoc_eval
```

## 六、局限与下一步

1. 评测数据只有「上屏词流」，没有 App/对象/时间段；下一步可把 `curApp`、记忆对象作为稀疏特征加进打分。
2. 拼音态联想（候选里混入的「下一词」）现在也用 v3，但还没有单独评测集。
3. 前缀召回（ROADMAP ①-1）未做：输入 1 个字母就召回记忆短语。
4. 线上反馈闭环：联想被选/被忽略应回写权重（`ChoiceLog` 已记录候选列表，可加 `assoc` 来源统计）。
