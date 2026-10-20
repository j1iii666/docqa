"""docqa 接口压测：只用标准库，不需要 pip 安装任何东西。

用法：
  python scripts/bench.py --token <JWT> [--url http://127.0.0.1:8082] [--path /api/search]
                         [--body '{"query":"缓存穿透","topK":3}'] [--threads 20] [--total 500]

输出 QPS、平均/中位/P95/P99 延迟、失败数。
只打只读接口（默认 /api/search），不会消耗大模型额度。
"""

import argparse
import json
import statistics
import sys
import threading
import time
import urllib.request
import urllib.error


def one_request(url, body_bytes, token, timeout=30.0):
    req = urllib.request.Request(url, data=body_bytes, method="POST")
    req.add_header("Content-Type", "application/json; charset=utf-8")
    req.add_header("Authorization", "Bearer " + token)
    start = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            resp.read()
            code = resp.status
    except urllib.error.HTTPError as e:
        e.read()
        code = e.code
    except Exception:
        code = 0
    return (time.perf_counter() - start) * 1000.0, code


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--url", default="http://127.0.0.1:8082")
    ap.add_argument("--path", default="/api/search")
    ap.add_argument("--body", default="@D:/JavaProjects/docqa/target/api-test/rq.json",
                    help='请求体 JSON；以 @ 开头表示从文件读取（推荐，避免命令行中文乱码）')
    ap.add_argument("--token", required=True)
    ap.add_argument("--threads", type=int, default=20)
    ap.add_argument("--total", type=int, default=500)
    args = ap.parse_args()

    target = args.url.rstrip("/") + args.path
    if args.body.startswith("@"):
        with open(args.body[1:], "rb") as f:
            body_bytes = f.read()
    else:
        body_bytes = args.body.encode("utf-8")

    # 先打一次做预热（JIT、连接池、缓存都还没热）
    one_request(target, body_bytes, args.token)

    latencies = []
    codes = []
    lock = threading.Lock()
    per_thread = args.total // args.threads
    remainder = args.total - per_thread * args.threads

    def worker(count):
        local_lat = []
        local_code = []
        for _ in range(count):
            ms, code = one_request(target, body_bytes, args.token)
            local_lat.append(ms)
            local_code.append(code)
        with lock:
            latencies.extend(local_lat)
            codes.extend(local_code)

    threads = []
    for i in range(args.threads):
        n = per_thread + (1 if i < remainder else 0)
        if n <= 0:
            continue
        threads.append(threading.Thread(target=worker, args=(n,)))

    started = time.perf_counter()
    for t in threads:
        t.start()
    for t in threads:
        t.join()
    elapsed = time.perf_counter() - started

    ok = sum(1 for c in codes if c == 200)
    failed = len(codes) - ok
    latencies.sort()

    def percentile(p):
        if not latencies:
            return 0.0
        idx = min(int(len(latencies) * p), len(latencies) - 1)
        return latencies[idx]

    print(f"目标           : POST {target}")
    print(f"并发 / 总请求  : {args.threads} 线程 / {args.total} 次")
    print(f"总耗时         : {elapsed:.2f}s")
    print(f"QPS            : {args.total / elapsed:.1f}")
    print(f"成功 / 失败    : {ok} / {failed}")
    if latencies:
        print(f"平均延迟       : {statistics.mean(latencies):.1f} ms")
        print(f"中位延迟       : {statistics.median(latencies):.1f} ms")
        print(f"P95            : {percentile(0.95):.1f} ms")
        print(f"P99            : {percentile(0.99):.1f} ms")
        print(f"最大           : {latencies[-1]:.1f} ms")
    if failed:
        from collections import Counter
        print("失败状态码分布 :", dict(Counter(c for c in codes if c != 200)))
        sys.exit(1)


if __name__ == "__main__":
    main()
