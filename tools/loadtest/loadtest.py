#!/usr/bin/env python3
"""
压测器 v3：支持多 cookie 轮转（每个线程绑定一个 cookie），用于绕开单用户限流。
用法: loadtest3.py <name> <url> <method> <threads> <duration_s> <cookiefile> [body]
cookiefile: 每行一个 cookie
"""
import sys
import time
import json
import threading
import requests
from requests.adapters import HTTPAdapter

name = sys.argv[1]
url = sys.argv[2]
method = sys.argv[3].upper()
threads = int(sys.argv[4])
duration = float(sys.argv[5])
cookiefile = sys.argv[6]
body = sys.argv[7] if len(sys.argv) > 7 else None
if body:
    body = body.encode("utf-8")

cookies = [l.strip() for l in open(cookiefile) if l.strip()]

errors = []
latencies = []
lock = threading.Lock()
stop_at = time.monotonic() + duration
counters = {"ok": 0, "err": 0}


def worker(idx):
    s = requests.Session()
    s.mount("http://", HTTPAdapter(pool_connections=4, pool_maxsize=8))
    headers = {"Content-Type": "application/json"}
    local = []
    ok = err = 0
    i = idx
    while time.monotonic() < stop_at:
        h = dict(headers)
        if cookies:
            h["Cookie"] = cookies[i % len(cookies)]
            i += 1
        t0 = time.monotonic()
        try:
            if method == "GET":
                r = s.get(url, headers=h, timeout=15)
            else:
                r = s.post(url, headers=h, data=body, timeout=15)
            dt = (time.monotonic() - t0) * 1000
            local.append(dt)
            if r.status_code < 400:
                ok += 1
            else:
                err += 1
                if len(errors) < 8:
                    with lock:
                        errors.append(f"{r.status_code}:{r.text[:70]}")
        except Exception as e:
            err += 1
            if len(errors) < 8:
                with lock:
                    errors.append(type(e).__name__ + ":" + str(e)[:70])
    with lock:
        latencies.extend(local)
        counters["ok"] += ok
        counters["err"] += err


ts = [threading.Thread(target=worker, args=(k,), daemon=True) for k in range(threads)]
t_start = time.monotonic()
for t in ts:
    t.start()
for t in ts:
    t.join()
elapsed = time.monotonic() - t_start

total = counters["ok"] + counters["err"]
qps = counters["ok"] / elapsed if elapsed > 0 else 0
err_rate = counters["err"] / total * 100 if total else 100


def pct(p):
    if not latencies:
        return 0
    s = sorted(latencies)
    return s[min(int(len(s) * p / 100), len(s) - 1)]


res = {
    "name": name, "threads": threads, "duration": round(elapsed, 1),
    "total": total, "ok": counters["ok"], "err": counters["err"],
    "err_rate": round(err_rate, 2), "qps": round(qps, 1),
    "p50": round(pct(50), 1), "p95": round(pct(95), 1),
    "p99": round(pct(99), 1), "max": round(max(latencies), 1) if latencies else 0,
    "errors": errors[:3],
}
print(json.dumps(res, ensure_ascii=False))
with open("/tmp/lt-results3.jsonl", "a") as f:
    f.write(json.dumps(res, ensure_ascii=False) + "\n")
