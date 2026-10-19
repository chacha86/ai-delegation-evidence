"""두 스냅샷 비교. 사용: python snapdiff.py <before.json> <after.json>  → 다른 단계 목록, 마지막 줄 'diff N/M'"""
import json, sys

a = json.load(open(sys.argv[1], encoding="utf8"))
b = json.load(open(sys.argv[2], encoding="utf8"))
if len(a) != len(b):
    print(f"단계 수 다름 {len(a)} vs {len(b)}")
n = 0
for x, y in zip(a, b):
    diffs = [k for k in ("status", "resultCode", "shape", "balance") if x.get(k) != y.get(k)]
    if diffs:
        n += 1
        print(f"[{x['step']}]")
        for k in diffs:
            print(f"   {k}: {json.dumps(x.get(k), ensure_ascii=False)[:160]}  →  {json.dumps(y.get(k), ensure_ascii=False)[:160]}")
print(f"diff {n}/{min(len(a), len(b))}")
