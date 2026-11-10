# 半字输入法 · 引擎与技术栈

> 版本：0.5.44（versionCode 140）
> 结论：**没有使用第三方输入法引擎**。拼音、分词、候选排序、联想全部自研（Java）；唯一真正的本地推理引擎是语音 ASR（sherpa-onnx + SenseVoice，C/JNI）。联想是纯统计 n-gram，不是神经网络，也没有引入 Rime / 搜狗输入法内核 / 第三方 IME SDK。

## 一、分层与实现

| 层 | 实现 | 类型 | 说明 |
|---|---|---|---|
| 拼音候选 | `LocalPinyinProvider` | 自研 Java | 静态词典 + 词频打分，查表级 |
| 可选云拼音 | `SogouPinyinProvider` | 可选外部接口 | 只有配置 endpoint 才走云，失败/未配自动回退本地 |
| 分词 / 整句 | `Segmenter` | 自研算法 | DAG + 动态规划，一元语言模型 + bigram 转移 |
| 纠错 | `Fuzzy` | 自研算法 | 模糊音、漏打、多打、邻位换位 |
| 候选排序 | `refreshCandidates` | 自研打分器 | 读音 tier + 个人词库 + 短期高频 + 语境 + N-best |
| 联想 | `AssocStore` | 自研统计模型 | bigram/trigram + 时间衰减 + 混合候选池 |
| 可选 AI 联想 | `AiPredictor` / `AiWords` | 云端 API | DeepSeek，需 Key，默认不联网 |
| 语音识别 | `NativeAsr` | 本地推理引擎 | sherpa-onnx + SenseVoice int8，C JNI |

## 二、拼音引擎

初始化（`ClawImeService.onCreate`）：

```java
localPy = new LocalPinyinProvider(this);
pinyin = new SogouPinyinProvider(sogouUrl, localPy);  // 配了云 URL 才走云
```

- 未配置 endpoint / 请求失败 → **自动回退本地**，所以默认就是纯本地。
- 数据资产（打包在 APK `assets/`）：

| 文件 | 大小 | 内容 |
|---|---|---|
| `pinyin.dict` | 3.87 MB | 17 万+ 拼音键的词典 |
| `wordfreq.txt` | 1.20 MB | 10.6 万词频（jieba 语料派生） |
| `newwords.txt` | 小 | 新词注入（词 + 拼音 + 频次） |
| `syllables.txt` / `syllables_extra.txt` | 小 | 合法音节表（用于切分/纠错） |
| `colloquial.txt` | 小 | 口语词表，首次运行并入个人词库 |

## 三、分词 / 整句（`Segmenter`）

- 拼音长度 ≥ 8 时启用：建 DAG，动态规划求覆盖整串的最大分路径。
- 一元语言模型打分：`FREQ_W × log10(1+词频) − WORD_COST`，多字词额外奖励，整串命中奖励；再用 `AssocStore.link()` 的 bigram 接续做转移加成。
- 关键常量：`WORD_COST = 8.2`（≈log10 语料规模，低了会把词拆成单字）、`RARE_WORD_PENALTY = 0.8`、`UNKNOWN_WORD_PENALTY = 2.5`、`MULTI_CHAR_BONUS = 0.35`。
- `Fuzzy` 纠错代价：模糊音 0.45 / 真打错 1.0 / 邻键 0.85 / 多打 1.2。
- N-best 3 条；实测一次切分约 10ms（日志 `seg ok n=8 words=1 alts=2 us=10265`）。

## 四、候选排序（`ClawImeService.refreshCandidates`）

依次叠加的信号：

1. **读音贴合 tier**：0 = 拼音完全对应；1 = 多打字母的补全；2 = 词库无此读音（个人词/缩写）
2. **个人词库**：`UserDict.countStrong` 强证据词优先（手选非首候选才 markStrong）
3. **短期高频**：24h 内的选择次数加成 0~12（压不过全局词频，不会无脑置顶）
4. **语境 / 跨词补全**：上文接得上的词（+40）、上文末字 + 本次拼音的整词
5. **整句 N-best**：长拼音 DP 结果，可逐词点选
6. **试用池投放**：自学词从第 2 位插入（不占首选，避免位置赢）
7. **AI 列词**（可选云端）：只做异步补位/重排，不阻塞本地候选

## 五、联想算法（v4，当前版本）

### 5.1 数据

- **live**：用户自学习 bigram（词→词）与 trigram（前 2 词→词），存 `assoc.json` / `assoc_tri.json`，每对带最后时间戳 `assoc_ts.json`。
- **种子**：从聊天史挖出的 bigram / trigram，只读，打包为 `assoc_seed.json`（0.50 MB）+ `assoc_seed_tri.json`（1.03 MB）。
- 学习时机：连续上屏两词 → bigram +3；连打三词 → trigram +1；手选非首候选额外加权；删掉刚上屏的词会回滚学习。

### 5.2 打分（v4）

```
候选池 = 有接续证据的词 ∪ 个人高频前 45 词

score = 8  × live_bigram × 0.5^(距今天数/14)
      + 3  × min(种子 bigram, 10)
      + 14 × live_trigram
      + 10 × min(种子 trigram, 4)
      + 3  × log1p(个人词频)
```

- **证据门槛**：一个「有接续证据」的候选都没有时返回空 → 保持「空候选 + 律动」的原行为，不在每次上屏后硬凑常用词。
- **开关**：`assoc_v4`（默认 true）；关掉回退 v3（时间衰减 + 证据内常用度），再关回退 v2/旧打分。

### 5.3 离线回放评测

数据：真实上屏记录 15,763 条（4,723 词），按时间切 train/test，脚本 `tools/eval_assoc.py`。

| 协议 | 模型 | hit@1 | hit@3 | hit@5 | MRR | 生僻词 |
|---|---|---|---|---|---|---|
| 75/25 | 旧 predict | 2.84% | 5.86% | 7.21% | 0.0442 | 25.2% |
| 75/25 | v3 | 3.77% | 6.55% | 8.25% | 0.0536 | 10.7% |
| 75/25 | **v4** | **4.04%** | 7.48% | **9.69%** | **0.0603** | 10.0% |
| 90/10 | **v4** | **3.77%** | 7.92% | **9.80%** | **0.0604** | 8.9% |

> 联想没有本地神经语言模型、没有 embedding/transformer 推理；增益来自「候选池覆盖 + 证据门槛」，不是更复杂的学习器。详见 `docs/ASSOC-V4.md`。

## 六、语音（唯一的本地推理引擎）

- `NativeAsr`：sherpa-onnx + SenseVoice int8 模型，经 `libclawasr.so`（C JNI）调用。
- 模型不打包进 APK：首次使用时从本机 ASR 服务（127.0.0.1:18794）拉到 App 私有目录（约 239 MB），之后完全离线。
- 兜底：`AsrClient` 可配置 OpenAI 兼容的 ASR endpoint；`AsrText` 做后处理。

## 七、Android 技术栈

### 7.1 语言与构建

| 项 | 用的什么 |
|---|---|
| 应用层语言 | **Java 8**（源码 `-source 8 -target 8`；无 Kotlin 源码） |
| Kotlin 依赖 | 仅第三方液态玻璃库（`kotlin-stdlib-2.0.21.jar` + `libnativegauss.so`），我们自己的代码不含 Kotlin |
| 原生层 | **C**（JNI：ASR 桥接），clang 编译 |
| 构建链 | `aapt2` 编译资源 → `javac` → `d8` 转 dex → `jar` 打包 → 自研 `zipalign` → `apksigner` 签名 |
| 最低/目标版本 | minSdk 26（Android 8.0）/ targetSdk 33 |
| 构建产物 | debug APK 约 14.5 MB（含 4.9 MB 词库、1.5 MB 联想种子；ASR 模型运行期下载） |

### 7.2 运行时与系统组件

| 能力 | Android 组件 |
|---|---|
| 输入法本体 | `InputMethodService`（`ClawImeService`） |
| 界面 | 原生 `View` / `Canvas` / `Drawable` 自绘（**无 Compose、无 Flutter**） |
| 设计台 | `WebView` 加载 `assets/design.html`，`@JavascriptInterface` 桥 `window.ClawTheme` |
| 记忆库 | `SQLiteOpenHelper`（`MemoryDb`，`claw_memory.db`） |
| 配置 | `SharedPreferences` + `org.json`（主题、词库、开关） |
| 读屏取词 | `AccessibilityService`（`ScreenReaderService`） |
| 通知/媒体律动 | `NotificationListenerService` + `MediaProjection` 前台服务 |
| 网络 | `HttpURLConnection`（云端 AI）+ 本机 `ServerSocket` HTTP 桥（127.0.0.1） |
| 语音采集 | `AudioRecord` / `MediaRecorder` |
| 后台调度 | `Handler` / `Thread` / `ExecutorService`（无 WorkManager） |

### 7.3 APK 内原生库

| 库 | 架构 | 大小 | 用途 |
|---|---|---|---|
| `libonnxruntime.so` | arm64-v8a | 20.68 MB | ASR 推理运行时 |
| `libsherpa-onnx-c-api.so` | arm64-v8a | 4.20 MB | sherpa-onnx C API |
| `libclawasr.so` | arm64-v8a | 0.01 MB | 我们自己的 JNI 胶水（C） |
| `libnativegauss.so` | arm64-v8a / armeabi-v7a | 0.27 / 0.16 MB | 液态玻璃库的原生高斯模糊 |

### 7.4 没有用的东西

- ❌ Rime / Fcitx / Gboard / 搜狗输入法内核 / 任何第三方 IME SDK
- ❌ 本地神经语言模型、embedding、transformer 推理
- ❌ Jetpack Compose、Flutter
- ❌ Room、Retrofit、OkHttp（网络用系统 `HttpURLConnection`，DB 用原生 SQLite）

## 八、相关文档

- 联想模型与负结果：`docs/ASSOC-V3.md`、`docs/ASSOC-V4.md`
- 前缀召回研究（个人短语记忆）：`docs/PHRASE-MEMORY-RESEARCH.md`
- 评测工具：`tools/eval_assoc.py`、`tools/eval_prefix_recall.py`
