# AI 评测脚本

离线、可复现的输入法算法评测（不依赖 App，只需要 `choice_log.jsonl` 数据）。

| 脚本 | 作用 |
|---|---|
| `eval_assoc.py` | 联想（下一词预测）held-out 回放：按时间切分训练/测试，输出 hit@1/3/5、MRR、生僻词占比 |
| `eval_prefix_recall.py` | 前缀召回研究：测试词中同拼音历史的比例、可节省按键数 |

用法：
```bash
python3 eval_assoc.py 路径/choice_log.jsonl
python3 eval_prefix_recall.py 路径/choice_log.jsonl
```

数据格式（每行一条上屏记录）：
```json
{"ts": 1700000000, "py": "nihao", "shown": ["你好","拟好"], "pick": 0}
```

评测协议与结论见 `../docs/ASSOC-V4.md`、`../docs/PHRASE-MEMORY-RESEARCH.md`。
