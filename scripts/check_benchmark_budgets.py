#!/usr/bin/env python3
"""Fails when a macrobenchmark result is over the budget in plan.md (constitution Principle II).

Usage: check_benchmark_budgets.py <directory containing *-benchmarkData.json>

Budgets are for a mid-range phone. CI runs on an emulator, which is slower, so the
EMULATOR_FACTOR loosens them there; the phone budgets are the ones that matter for release.
"""
import json
import os
import pathlib
import sys

# test name -> (metric, statistic, budget on a mid-range phone: ms, or percent for jank)
BUDGETS = {
    "coldStart": ("timeToInitialDisplayMs", "median", 1000),
    "warmStart": ("timeToInitialDisplayMs", "median", 300),
    # SC-005: under 1% janky frames scrolling 1,000 tasks, with or without a sync running
    # (FrameTimingGfxInfoMetric's jank percentage, in percent rather than ms).
    "scroll": ("gfxFrameJankPercent", "median", 1.0),
    "scrollWhileSyncing": ("gfxFrameJankPercent", "median", 1.0),
}
EMULATOR_FACTOR = float(os.environ.get("BENCHMARK_EMULATOR_FACTOR", "3.0"))


def main(root: str) -> int:
    files = list(pathlib.Path(root).rglob("*benchmarkData.json"))
    if not files:
        print(f"No benchmark results under {root}")
        return 1
    failures = []
    checked = 0
    for f in files:
        data = json.loads(f.read_text())
        emulator = data.get("context", {}).get("build", {}).get("model", "").lower().find("sdk") >= 0
        factor = EMULATOR_FACTOR if emulator else 1.0
        for bench in data.get("benchmarks", []):
            name = bench.get("name")
            if name not in BUDGETS:
                continue
            metric, stat, budget = BUDGETS[name]
            # Single-value metrics (startup) are under "metrics"; per-frame ones under "sampledMetrics".
            values = bench.get("metrics", {}).get(metric) or bench.get("sampledMetrics", {}).get(metric) or {}
            value = values.get(stat)
            if value is None:
                failures.append(f"{name}: metric {metric} missing")
                continue
            limit = budget * factor
            checked += 1
            status = "ok" if value <= limit else "OVER BUDGET"
            print(f"{name}: {metric} {stat} {value:.1f}, limit {limit:.1f} ({status})")
            if value > limit:
                failures.append(f"{name}: {metric} {stat} {value:.1f} > {limit:.1f}")
    if failures:
        print("\n".join(["Performance budget failures:"] + failures))
        return 1
    if checked == 0:
        print("No budgeted benchmarks found in results")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else "."))
