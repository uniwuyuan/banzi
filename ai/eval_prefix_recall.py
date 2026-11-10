#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""前缀召回（短语记忆）潜力评估：可复现，不依赖真机。

数据：<data-dir>/choice_log.jsonl（或 <data-dir>/files/choice_log.jsonl）
思路：
  1. 按时间排序，前 85% 当「用户历史」，后 15% 当测试；
  2. 历史里每个 (拼音 → 词) 记次数与最后时间；
  3. 测试时对每个记录尝试用拼音前缀（1..len-1）召回，按 次数×时间衰减 排序；
  4. 统计 recall@1/3、覆盖率、以及「前缀命中可省键数」。

用法：python3 tools/eval_prefix_recall.py --data-dir /tmp/assoc_eval
"""
import argparse, collections, json, os, sys

HL = 14.0


def load_stream(path):
    recs = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            try:
                d = json.loads(line)
            except Exception:
                continue
            shown = d.get("shown") or []
            pick = d.get("pick", -1)
            if not isinstance(pick, int) or pick < 0 or pick >= len(shown):
                continue
            w = shown[pick]
            if not w or len(w) > 8:
                continue
            recs.append((int(d.get("ts", 0)), (d.get("py") or "").lower(), w))
    recs.sort(key=lambda x: x[0])
    return recs


def find(data_dir, name):
    for p in (os.path.join(data_dir, name), os.path.join(data_dir, "files", name)):
        if os.path.exists(p):
            return p
    return None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--data-dir", default="/tmp/assoc_eval")
    ap.add_argument("--split", type=float, default=0.85)
    args = ap.parse_args()
    path = find(args.data_dir, "choice_log.jsonl")
    if not path:
        print("缺少 choice_log.jsonl", file=sys.stderr)
        return 2
    recs = load_stream(path)
    split = int(len(recs) * args.split)
    train, test = recs[:split], recs[split:]

    idx = collections.defaultdict(lambda: collections.defaultdict(lambda: [0, 0]))
    for ts, py, w in train:
        if not py:
            continue
        e = idx[py][w]
        e[0] += 1
        e[1] = ts

    pref = collections.defaultdict(dict)
    for py, words in idx.items():
        for L in range(1, len(py)):
            for w, (c, ts) in words.items():
                cur = pref[py[:L]].get(w)
                if cur is None or c > cur[0] or (c == cur[0] and ts > cur[1]):
                    pref[py[:L]][w] = [c, ts]

    def rank(prefix, now):
        d = pref.get(prefix)
        if not d:
            return []
        scored = [(w, c * (0.5 ** (max(0.0, (now - ts) / 86400.0) / HL)))
                  for w, (c, ts) in d.items()]
        return [w for w, _ in sorted(scored, key=lambda x: -x[1])]

    n = len(test)
    rep = [r for r in test if r[1]]
    same_py = sum(1 for ts, py, w in rep if w in idx.get(py, {}))
    print("上屏记录 %d（历史 %d / 测试 %d），有拼音 %d" % (len(recs), split, n, len(rep)))
    print("测试词「同拼音历史打过」占比: %.1f%%（prefix 召回的天花板）" % (100.0 * same_py / max(1, len(rep))))

    for L in (1, 2, 3, 4, 5, 6):
        cov = r1 = r3 = tot = 0
        for ts, py, w in rep:
            if len(py) <= L:
                continue
            tot += 1
            r = rank(py[:L], ts)
            if r:
                cov += 1
                if w == r[0]:
                    r1 += 1
                if w in r[:3]:
                    r3 += 1
        if tot:
            print("前缀长度 %d: 覆盖率 %5.1f%% (n=%d)  recall@1 %5.1f%%  recall@3 %5.1f%%"
                  % (L, 100.0 * cov / tot, tot, 100.0 * r1 / max(1, cov), 100.0 * r3 / max(1, cov)))

    # 省键估算：首次出现在 top3 的前缀长度
    recover = save = 0
    dist = collections.Counter()
    for ts, py, w in rep:
        for L in range(1, len(py)):
            r = rank(py[:L], ts)
            if w in r[:3]:
                recover += 1
                save += len(py) - L
                dist[L] += 1
                break
    print("前缀召回可省键记录: %d/%d (%.1f%%)，平均省 %.2f 字母（全体平均 %.2f）"
          % (recover, len(rep), 100.0 * recover / max(1, len(rep)),
             save / max(1, recover), save / max(1, len(rep))))
    print("最小命中前缀长度分布:", dict(sorted(dist.items())))
    return 0


if __name__ == "__main__":
    sys.exit(main())
