#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""联想 v3 离线回放评测（可复现，不依赖真机）。

数据（不随仓库分发，含个人输入记录）：
  <data-dir>/choice_log.jsonl   每行 {"ts":秒,"shown":[候选...],"pick":下标,...}
  <data-dir>/assoc_seed.json    {前词: {后词: 次数}}          （来自 APK assets）
  <data-dir>/assoc_seed_tri.json {前2词\\u0001前1词: {后词: 次数}}（来自 APK assets）

做法：
  1. 按 ts 排序，把「实际上屏词」拼成词序列；
  2. 在 split 处按时间切开（默认 85% 训练 / 15% 测试）；
  3. 训练集模拟 App 学习：bigram +3、trigram +1、记录每对最后出现时间；
  4. 测试集逐位置预测，统计 hit@1/3/5、MRR、生僻词占比（训练集出现 ≤2 次）。

用法：
  python3 tools/eval_assoc.py --data-dir /tmp/assoc_eval
"""
import argparse, collections, json, math, os, sys

HL_DAYS = 14.0


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
            recs.append((int(d.get("ts", 0)), w))
    recs.sort(key=lambda x: x[0])
    return recs


def build(stream, split):
    big = collections.defaultdict(collections.Counter)
    tri = collections.defaultdict(collections.Counter)
    pair_ts = {}
    uni = collections.Counter()
    for i in range(split):
        uni[stream[i][1]] += 1
        if i == 0:
            continue
        a, b = stream[i - 1][1], stream[i][1]
        if a == b:
            continue
        big[a][b] += 3
        pair_ts[(a, b)] = stream[i][0]
        if i >= 2:
            tri[stream[i - 2][1] + "\u0001" + a][b] += 1
    return big, tri, pair_ts, uni


def predict(prev1, prev2, now, cfg, big, tri, pair_ts, uni, seed, seedtri):
    score = collections.defaultdict(float)
    evidence = set()
    for w, c in (big.get(prev1) or {}).items():
        tv = pair_ts.get((prev1, w), now)
        days = max(0.0, (now - tv) / 86400.0)
        decay = 0.5 ** (days / cfg["hl"])
        score[w] += cfg["w_big"] * c * decay
        evidence.add(w)
    for w, c in (seed.get(prev1) or {}).items():
        score[w] += cfg["w_seed"] * min(c, 10)
        evidence.add(w)
    if prev2:
        for w, c in (tri.get(prev2 + "\u0001" + prev1) or {}).items():
            score[w] += cfg["w_live_tri"] * c
            evidence.add(w)
        for w, c in (seedtri.get(prev2 + "\u0001" + prev1) or {}).items():
            score[w] += cfg["w_seed_tri"] * min(c, 4)
            evidence.add(w)
    if cfg["uni_w"] > 0:
        pool = evidence if cfg["uni_evidence"] else set(uni.keys())
        for w in pool:
            score[w] += cfg["uni_w"] * math.log1p(uni.get(w, 0))
    order = [w for w, s in sorted(score.items(), key=lambda kv: -kv[1]) if w != prev1]
    return order[:5]


def predict_v4(prev1, prev2, now, cfg, big, tri, pair_ts, uni, seed, seedtri, sess=None):
    score = collections.defaultdict(float)
    for w, c in (big.get(prev1) or {}).items():
        tv = pair_ts.get((prev1, w), now)
        days = max(0.0, (now - tv) / 86400.0)
        score[w] += cfg["w_big"] * c * (0.5 ** (days / cfg["hl"]))
    for w, c in (seed.get(prev1) or {}).items():
        score[w] += cfg["w_seed"] * min(c, 10)
    if prev2:
        for w, c in (tri.get(prev2 + "\u0001" + prev1) or {}).items():
            score[w] += cfg["w_live_tri"] * c
        for w, c in (seedtri.get(prev2 + "\u0001" + prev1) or {}).items():
            score[w] += cfg["w_seed_tri"] * min(c, 4)
    if not score and cfg["need_evidence"]:
        return []
    for w, c in uni.most_common(cfg["top_freq"]):
        if w == prev1:
            continue
        score[w] += cfg["uni_w"] * math.log1p(c)
    if sess and cfg.get("sess_w", 0) > 0:
        for w, c in sess.items():
            if w != prev1:
                score[w] += cfg["sess_w"] * math.log1p(c)
    if cfg.get("len2", 0) > 0:
        for w in list(score.keys()):
            if len(w) == 2:
                score[w] += cfg["len2"]
    order = [w for w, s in sorted(score.items(), key=lambda kv: -kv[1]) if w != prev1]
    return order[:5]


def evaluate(stream, split, cfg, seed, seedtri, v4=False):
    big, tri, pair_ts, uni = build(stream, split)
    hits = {1: 0, 3: 0, 5: 0}
    mrr = 0.0
    n = 0
    rare = 0
    total = 0
    for gi in range(split, len(stream)):
        prev1 = stream[gi - 1][1]
        prev2 = stream[gi - 2][1] if gi >= 2 else ""
        gold = stream[gi][1]
        now = stream[gi][0]
        sess = None
        if v4 and cfg.get("sess_win", 0) > 0:
            sess = collections.Counter(stream[max(0, gi - cfg["sess_win"]):gi])
        r = (predict_v4(prev1, prev2, now, cfg, big, tri, pair_ts, uni, seed, seedtri, sess)
             if v4 else predict(prev1, prev2, now, cfg, big, tri, pair_ts, uni, seed, seedtri))
        if not r:
            continue
        n += 1
        if gold in r[:1]:
            hits[1] += 1
        if gold in r[:3]:
            hits[3] += 1
        if gold in r[:5]:
            hits[5] += 1
        for k, w in enumerate(r, 1):
            if w == gold:
                mrr += 1.0 / k
                break
        for w in r:
            total += 1
            if uni.get(w, 0) <= 2:
                rare += 1
    return n, hits, mrr, rare, total


CFG_OLD = dict(w_big=8, w_seed=3, w_seed_tri=5, w_live_tri=14, hl=1e9,
               uni_w=0.0, uni_evidence=False)
CFG_V3 = dict(w_big=6, w_seed=3, w_seed_tri=10, w_live_tri=14, hl=HL_DAYS,
              uni_w=6.0, uni_evidence=True)
# v4：混合候选池（证据 ∪ 个人高频 topN）+ 证据门槛；高频词用 log1p(freq) 补位
CFG_V4 = dict(w_big=8, w_seed=3, w_seed_tri=10, w_live_tri=14, hl=HL_DAYS,
              uni_w=3.0, uni_evidence=False, top_freq=45, need_evidence=True,
              sess_w=1.5, sess_win=200, len2=1.0)


def fmt(name, n, hits, mrr, rare, total):
    print("%-22s n=%5d  hit@1=%6.2f%%  hit@3=%6.2f%%  hit@5=%6.2f%%  MRR=%.4f  生僻=%5.1f%%"
          % (name, n, 100.0 * hits[1] / n, 100.0 * hits[3] / n, 100.0 * hits[5] / n,
             mrr / n, 100.0 * rare / max(1, total)))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--data-dir", default="/tmp/assoc_eval")
    ap.add_argument("--split", type=float, default=0.85)
    args = ap.parse_args()
    d = args.data_dir
    # 备份包里是 files/choice_log.jsonl；种子文件单独放。两种布局都认。
    def find(name):
        for p in (os.path.join(d, name), os.path.join(d, "files", name)):
            if os.path.exists(p):
                return p
        return None
    need = ["choice_log.jsonl", "assoc_seed.json", "assoc_seed_tri.json"]
    for f in need:
        if not find(f):
            print("缺少数据文件：%s/%s（或 %s/files/%s）" % (d, f, d, f), file=sys.stderr)
            return 2
    stream = load_stream(find("choice_log.jsonl"))
    seed = json.load(open(find("assoc_seed.json"), encoding="utf-8"))
    seedtri = json.load(open(find("assoc_seed_tri.json"), encoding="utf-8"))
    print("上屏记录 %d 条，去重词 %d，split=%.2f"
          % (len(stream), len({w for _, w in stream}), args.split))
    for split in (int(len(stream) * 0.75), int(len(stream) * args.split if args.split != 0.85 else len(stream) * 0.9)):
        fmt("旧打分 predict()", *evaluate(stream, split, CFG_OLD, seed, seedtri))
        fmt("v3 predictV3()", *evaluate(stream, split, CFG_V3, seed, seedtri))
        fmt("v4 predictV4()", *evaluate(stream, split, CFG_V4, seed, seedtri, v4=True))
        print()
    return 0


if __name__ == "__main__":
    sys.exit(main())
